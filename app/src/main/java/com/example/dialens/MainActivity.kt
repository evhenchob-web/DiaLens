package com.example.dialens

import android.os.Bundle
import android.graphics.BitmapFactory
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.room.*
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.dialens.BuildConfig
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
// --- БАЗА ДАНИХ ТА МОДЕЛІ ---

// 1. Опис таблиць (Сутності)
@Entity(tableName = "meals")
data class MealEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dishName: String,
    val kcal: Float,
    val proteins: Float,
    val fats: Float,
    val carbs: Float,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1, // Завжди 1, бо профіль лише один
    val gender: String = "Чоловік",
    val age: Int = 30,
    val height: Int = 175,
    val weight: Int = 75,
    val customKcalLimit: Float? = null
)

// 2. Інтерфейси доступу (DAO)
@Dao
interface MealDao {
    // Тепер ми просимо базу дати тільки ті страви, які були додані після певного часу
    @Query("SELECT * FROM meals WHERE timestamp >= :startOfDay ORDER BY timestamp DESC")
    fun getTodayMeals(startOfDay: Long): Flow<List<MealEntry>>

    @Insert suspend fun insertMeal(meal: MealEntry)
    @Delete suspend fun deleteMeal(meal: MealEntry)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserProfile(): Flow<UserProfile?> // Назва має збігатися з викликом

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: UserProfile)
}

// 3. Конфігурація бази даних
@Database(entities = [MealEntry::class, UserProfile::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun settingsDao(): SettingsDao
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDark = isSystemInDarkTheme()
            val view = LocalView.current
            SideEffect {
                val window = (view.context as ComponentActivity).window
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
            }
            MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DiaLensMainScreen()
                }
            }
        }
    }
}

@Composable
fun DiaLensMainScreen() {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 1. Ініціалізація бази
    val db = remember { Room.databaseBuilder(context, AppDatabase::class.java, "dialens-db").build() }
    val mealDao = db.mealDao()
    val settingsDao = db.settingsDao()
    var showMealHistory by remember { mutableStateOf(false) }

    // 2. Оголошення станів
    var userProfile by remember { mutableStateOf("Діабетик") }
    var resultText by remember { mutableStateOf("") }
    var additionalInfo by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    var consumedKcal by remember { mutableFloatStateOf(0f) }
    var consumedProteins by remember { mutableFloatStateOf(0f) }
    var consumedFats by remember { mutableFloatStateOf(0f) }
    var consumedCarbs by remember { mutableFloatStateOf(0f) }

    val prefs = remember { context.getSharedPreferences("dialens_prefs", android.content.Context.MODE_PRIVATE) }

    // Перевіряємо, чи заповнено профіль раніше (за замовчуванням false)
    var showProfileDialog by remember {
        mutableStateOf(!prefs.getBoolean("profile_filled", false))
    }

    // 3. Зчитування даних
    val userProfileData by settingsDao.getUserProfile().collectAsState(initial = null)
    // 1. Обчислюємо початок сьогоднішнього дня
    val startOfToday = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

// 2. Отримуємо історію тільки за сьогодні
    val history by mealDao.getTodayMeals(startOfToday).collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() } // Для красивих повідомлень
    // 4. Синхронізація статистики
    LaunchedEffect(history) {
        consumedKcal = history.sumOf { it.kcal.toDouble() }.toFloat()
        consumedProteins = history.sumOf { it.proteins.toDouble() }.toFloat()
        consumedFats = history.sumOf { it.fats.toDouble() }.toFloat()
        consumedCarbs = history.sumOf { it.carbs.toDouble() }.toFloat()
    }

   val dailyKcalTarget = remember(userProfileData) {
        userProfileData?.customKcalLimit ?: userProfileData?.let { profile ->
            val bmr = (10f * profile.weight + 6.25f * profile.height - 5f * profile.age) +
                    (if (profile.gender == "Чоловік") 5f else -161f)
            bmr * 1.2f
        } ?: 2000f
    }

    val mainTextColor = if (isSystemInDarkTheme()) Color.White else Color(0xFF212121)
    val apiKey = BuildConfig.GEMINI_API_KEY
    val visionModel = remember { GenerativeModel(modelName = "gemini-3.1-flash-lite-preview", apiKey = apiKey) }
    val textModel = remember { GenerativeModel(modelName = "gemma-3n-e2b-it", apiKey = apiKey) }
    // 6. Лаунчери
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) { capturedBitmap = bitmap; resultText = "" }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { context.contentResolver.openInputStream(it)?.use { stream -> capturedBitmap = BitmapFactory.decodeStream(stream) } }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            additionalInfo = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0) ?: ""
        }
    }

    fun analyzeMeal() {
        // 1. ПЕРЕВІРКА: чи є інтернет взагалі?
        if (!isNetworkAvailable(context)) {
            resultText = "⚠️ Відсутній інтернет. Перевірте з'єднання та спробуйте ще раз."
            return // Зупиняємо виконання, не витрачаючи ресурси
        }

        if (capturedBitmap != null || additionalInfo.isNotEmpty()) {
            isLoading = true
            coroutineScope.launch {
                try {
                    val expertPrompt = """
                    Ти — професійний дієтолог-ендокринолог. 
                    Твій клієнт має профіль: $userProfile.

                    СТРУКТУРА ТВОЄЇ ВІДПОВІДІ:
                    1. Оціни склад страви: $additionalInfo. 
                    Дай конкретну пораду щодо вживання цієї страви саме для профілю $userProfile. 
                    Додай один цікавий факт про інгредієнти.

                    2. Розрахуй дані на порцію (~150-250г, якщо не вказано інше). 
                    ВАЖЛИВО: Будь реалістичним. Звичайний обід — це 300-700 ккал. 
                    Жодних чисел понад 2000 ккал для однієї страви!

                    3. ФОРМАТ КІНЦЕВОГО РЯДКА (СТРОГО):
                    ---
                    СТРАВА: [назва] | ККАЛ: [число] | БІЛКИ: [число] | ЖИРИ: [число] | ВУГЛЕВОДИ: [число]

                    Пиши тільки цілими числами. Не використовуй коми в цифрах.
                """.trimIndent()

                    val response = if (capturedBitmap != null) {
                        val bitmapToSend = resizeBitmap(capturedBitmap!!)
                        visionModel.generateContent(
                            com.google.ai.client.generativeai.type.content {
                                image(bitmapToSend)
                                text("Контекст: $additionalInfo \n\n $expertPrompt")
                            }
                        )
                    } else {
                        textModel.generateContent("Аналізуй: $additionalInfo \n\n $expertPrompt")
                    }

                    resultText = response.text ?: ""
                    keyboardController?.hide()
                } catch (e: Exception) {
                    // ОБРОБКА ПОМИЛОК МЕРЕЖІ (таймаут, обрив зв'язку)
                    resultText = "Помилка зв'язку з сервером. Спробуйте пізніше."
                } finally {
                    isLoading = false
                }
            }
        }
    }


    // ГОЛОВНИЙ КОНТЕЙНЕР
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp) // ПОВЕРНУВ ПРАВИЛЬНИЙ ПАДДІНГ ДЛЯ ВСЬОГО ЕКРАНУ
                //.verticalScroll(rememberScrollState())
        ) {
            // 7. ЗАГОЛОВОК
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("DiaLens AI", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(0xFF4CAF50))
                IconButton(onClick = { showProfileDialog = true }) {
                    Icon(Icons.Default.AccountCircle, "Профіль", tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
                }
            }

            // КНОПКИ ПРОФІЛІВ
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Діабетик", "Спортсмен", "Фітнес").forEach { profile ->
                    val isSelected = userProfile == profile
                    Button(
                        onClick = { userProfile = profile },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) Color(0xFF2E7D32) else Color(0xFF424242))
                    ) {
                        Text(profile, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // КАРТКА СТАТИСТИКИ
            Card(
                modifier = Modifier.fillMaxWidth().clickable { showMealHistory = true },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = if (isSystemInDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFF1F8E9))
            ) {
                Column(Modifier.padding(16.dp)) {
                    when (userProfile) {
                        "Спортсмен" -> {
                            StatRow("Білки (г)", consumedProteins, 160f, Color(0xFF2196F3), mainTextColor)
                            StatRow("Жири (г)", consumedFats, 70f, Color(0xFFFFC107), mainTextColor)
                            StatRow("Вуглеводи (г)", consumedCarbs, 280f, Color(0xFF4CAF50), mainTextColor)
                        }
                        "Фітнес" -> StatRow("Денні Калорії", consumedKcal, dailyKcalTarget, Color(0xFFFF9800), mainTextColor)
                        else -> StatRow("Хлібні одиниці (ХО)", consumedCarbs / 12f, 20f, Color(0xFF4CAF50), mainTextColor)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ПОЛЕ ВВОДУ
            OutlinedTextField(
                value = additionalInfo,
                onValueChange = { additionalInfo = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Додай опис (напр. 'борщ з м'ясом')") },
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        analyzeMeal() // Викликаємо твою функцію аналізу
                        keyboardController?.hide() // Цей рядок приховає клавіатуру
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4CAF50),
                    cursorColor = Color(0xFF4CAF50)
                ),
                trailingIcon = {
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
                        }
                        try { speechLauncher.launch(intent) } catch (e: Exception) {}
                    }) { Icon(Icons.Default.Mic, null, tint = Color(0xFF4CAF50)) }
                }
            )

            Spacer(Modifier.height(8.dp))

            // ПОРАДА
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lightbulb, null, tint = Color(0xFFF57C00), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Порада: Додайте долоню в кадр або опишіть порцію текстом.", fontSize = 14.sp, color = Color(0xFF5D4037))
                }
            }

            Spacer(Modifier.height(12.dp))

            // ОБЛАСТЬ ПРЕВ'Ю
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), // ЗАМІНИВ height(350.dp) НА weight(1f)
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (isSystemInDarkTheme()) Color(0xFF121212) else Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Box(Modifier.fillMaxSize().padding(16.dp)) {
                    if (resultText.isNotEmpty()) {
                        DetailedResultView(resultText, userProfile, mainTextColor, mealDao, coroutineScope) { _,_,_,_ -> resultText = ""; additionalInfo = ""; capturedBitmap = null }
                        IconButton(onClick = { resultText = "" }, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.Close, null, tint = Color.Gray) }
                    } else if (capturedBitmap != null) {
                        Image(capturedBitmap!!.asImageBitmap(), null, Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                        IconButton(onClick = { capturedBitmap = null }, modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(0.5f), RoundedCornerShape(16.dp))) { Icon(Icons.Default.Close, null, tint = Color.White) }
                    } else {
                        Column(Modifier.align(Alignment.Center).clickable { cameraLauncher.launch() }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Icon(Icons.Default.PhotoCamera, null, tint = Color.Gray, modifier = Modifier.size(100.dp))
                                Surface(modifier = Modifier.size(45.dp).offset(12.dp, 12.dp).clickable { galleryLauncher.launch("image/*") }, shape = RoundedCornerShape(10.dp), color = Color(0xFF4CAF50), shadowElevation = 6.dp) {
                                    Icon(Icons.Default.Collections, null, tint = Color.White, modifier = Modifier.padding(6.dp))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Натисніть для фото або галереї", color = Color.Gray, fontSize = 15.sp)
                        }
                    }
                }
            }

            // ВІДСТУП ПІД КНОПКУ
            Spacer(Modifier.height(88.dp))
        }

        // ГОЛОВНА КНОПКА (ЗАЛИШИЛАСЬ ЗНИЗУ)
        Button(
            onClick = { analyzeMeal() }, // ПРОСТО ВИКЛИК ФУНКЦІЇ
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            elevation = ButtonDefaults.buttonElevation(8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Icon(Icons.Default.AutoAwesome, null, tint = Color.White)
                Spacer(Modifier.width(12.dp))
                Text(
                    "АНАЛІЗУВАТИ",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }


        // АНІМАЦІЯ
        if (isLoading) {
            val infiniteTransition = rememberInfiniteTransition(label = "loading")
            val progress by infiniteTransition.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(animation = tween(1500, easing = LinearEasing)), label = "progress"
            )

            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.8f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { 1f }, modifier = Modifier.size(120.dp), color = Color.White.copy(0.1f), strokeWidth = 8.dp)
                        CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(120.dp), color = Color(0xFF4CAF50), strokeWidth = 8.dp)
                        Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("DiaLens сканує нутрієнти...", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }


    if (showProfileDialog) {
        ProfileDialog(
            currentSettings = userProfileData ?: UserProfile(),
            prefs = prefs,
            onCloseDialog = { showProfileDialog = false },
            onDismiss = { showProfileDialog = false },
            onSave = { updatedProfile ->
                coroutineScope.launch { settingsDao.saveProfile(updatedProfile) }
            }
        )
    }

    if (showMealHistory) MealHistoryDialog(history, { showMealHistory = false }, { coroutineScope.launch { mealDao.deleteMeal(it) } }, {})
}// <--- ТЕПЕР ФУНКЦІЯ DiaLensMainScreen ЗАКРИВАЄТЬСЯ ТУТ



    @Composable
    fun ProfileDialog(
        currentSettings: UserProfile,
        prefs: android.content.SharedPreferences, // Кома в кінці обов'язкова!
        onCloseDialog: () -> Unit,
        onDismiss: () -> Unit,
        onSave: (UserProfile) -> Unit
    ) {
        var weight by remember { mutableStateOf(currentSettings.weight.toString()) }
        var height by remember { mutableStateOf(currentSettings.height.toString()) }
        var age by remember { mutableStateOf(currentSettings.age.toString()) }
        var manualKcal by remember { mutableStateOf(currentSettings.customKcalLimit?.toString() ?: "") }
        var gender by remember { mutableStateOf(currentSettings.gender) }

        val inputColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF4CAF50),
            unfocusedBorderColor = Color(0xFF4CAF50).copy(alpha = 0.5f),
            focusedLabelColor = Color(0xFF4CAF50),
            cursorColor = Color(0xFF4CAF50)
        )

        AlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(24.dp),
            confirmButton = {
                Button(
                    onClick = {
                        onSave(currentSettings.copy(
                            weight = weight.toIntOrNull() ?: 75,
                            height = height.toIntOrNull() ?: 175,
                            age = age.toIntOrNull() ?: 30,
                            gender = gender,
                            customKcalLimit = manualKcal.toFloatOrNull()
                        ))
                        prefs.edit().putBoolean("profile_filled", true).apply()
                        onCloseDialog()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text("ЗБЕРЕГТИ", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            title = {
                Text("Налаштування профілю", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF4CAF50))
            },
            text = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            listOf("Чоловік", "Жінка").forEach { item ->
                                val isSelected = gender == item
                                Button(
                                    onClick = { gender = item },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) Color(0xFF2E7D32) else Color(0xFF424242)
                                    )
                                ) {
                                    Text(item, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = weight, onValueChange = { weight = it },
                            label = { Text("Вага (кг)") }, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            colors = inputColors
                        )

                        OutlinedTextField(
                            value = height, onValueChange = { height = it },
                            label = { Text("Зріст (см)") }, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            colors = inputColors
                        )

                        OutlinedTextField(
                            value = age, onValueChange = { age = it },
                            label = { Text("Вік") }, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            colors = inputColors
                        )

                        HorizontalDivider(thickness = 1.dp, color = Color.Gray.copy(0.3f))

                        OutlinedTextField(
                            value = manualKcal, onValueChange = { manualKcal = it },
                            label = { Text("Норма від лікаря (ккал)") }, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            colors = inputColors
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-50).dp)
                    ) {
                        Icon(Icons.Default.Close, "Закрити", tint = Color(0xFF4CAF50))
                    }
                }
            },
            containerColor = if (isSystemInDarkTheme()) Color(0xFF1A1A1A) else Color.White,
            shape = RoundedCornerShape(28.dp)
        )
    }
@Composable
fun StatRow(label: String, value: Float, target: Float, color: Color, textColor: Color) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
            Text("${value.toInt()}/${target.toInt()}", fontSize = 13.sp, color = textColor)
        }
        LinearProgressIndicator(
            progress = { (value / target).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            // Колір стає червоним, якщо ліміт перевищено
            color = if (value > target) Color.Red else color,
            trackColor = color.copy(alpha = 0.2f)
        )
    }
}

@Composable
fun DetailedResultView(
    text: String,
    userProfile: String,
    textColor: Color,
    mealDao: MealDao, // Додано
    coroutineScope: kotlinx.coroutines.CoroutineScope, // Додано
    onConfirm: (Float, Float, Float, Float) -> Unit
) {
    val kcal = parseVal(text, "ККАЛ")
    val b = parseVal(text, "БІЛКИ")
    val j = parseVal(text, "ЖИРИ")
    val v = parseVal(text, "ВУГЛЕВОДИ")
    val dishName = text.substringAfter("СТРАВА:", "Страва").substringBefore("|").trim()

    // Оновлена обробка опису (видаляємо технічні символи)
    val fullDescription = text.substringBefore("СТРАВА:")
        .replace("**", "").replace("##", "").replace("*", "•")
        .trim()

    // Стан для кнопки "Більше цікавого"
    var isExpanded by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(end = 8.dp)) {
        Text(
            text = dishName.uppercase(),
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF4CAF50),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Картка з текстом та кнопкою розгортання
        Surface(
            color = if (isSystemInDarkTheme()) Color(0xFF252525) else Color(0xFFF5F5F5),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = if (isExpanded) fullDescription else fullDescription.take(130).substringBeforeLast(" ") + "...",
                    color = textColor.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    lineHeight = 18.sp
                )

                Text(
                    text = if (isExpanded) "Згорнути" else "Більше цікавого про страву",
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { isExpanded = !isExpanded }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("🔥 Енергія: ${kcal.toInt()} ккал", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = textColor)
                Text("Б: ${b.toInt()}г | Ж: ${j.toInt()}г | В: ${v.toInt()}г", color = textColor.copy(alpha = 0.7f))
            }

            if (userProfile == "Діабетик") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val ho = v / 12f
                    Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = String.format("%.1f ХО", ho),
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Text("Хлібні одиниці", fontSize = 10.sp, color = Color.Gray)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                coroutineScope.launch {
                    mealDao.insertMeal(MealEntry(dishName = dishName, kcal = kcal, proteins = b, fats = j, carbs = v))
                    onConfirm(kcal, b, j, v)
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
        ) {
            Icon(Icons.Default.AddCircleOutline, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("ЗБЕРЕГТИ В ЖУРНАЛ", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun MealHistoryDialog(
    meals: List<MealEntry>,
    onDismiss: () -> Unit,
    onDelete: (MealEntry) -> Unit,
    onEdit: (MealEntry) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Історія за сьогодні", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold) },
        text = {
            if (meals.isEmpty()) {
                Text("Список порожній")
            } else {
                // LazyColumn дозволяє скролити список, якщо страв багато
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(meals) { meal ->
                        MealItemRow(meal, onDelete, onEdit)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("ЗАКРИТИ", color = Color(0xFF4CAF50)) }
        }
    )
}

@Composable
fun MealItemRow(meal: MealEntry, onDelete: (MealEntry) -> Unit, onEdit: (MealEntry) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(meal.dishName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("${meal.kcal.toInt()} ккал | Б:${meal.proteins.toInt()} Ж:${meal.fats.toInt()} В:${meal.carbs.toInt()}", fontSize = 12.sp, color = Color.Gray)
        }
        Row {
            IconButton(onClick = { onDelete(meal) }) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(24.dp))
            }
        }
    }
}
// ОНОВЛЕНИЙ БРОНЕБІЙНИЙ ПАРСЕР
fun parseVal(text: String, key: String): Float {
    return try {
        // Шукаємо текст після ключа, ігноруючи все до наступного роздільника |
        val regex = "$key:\\s*([^|\\n]+)".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(text)
        val valueStr = match?.groupValues?.get(1) ?: "0"
        // Залишаємо тільки цифри та крапку/кому
        val cleanValue = valueStr.replace(Regex("[^0-9.,]"), "").replace(",", ".")
        cleanValue.toFloatOrNull() ?: 0f
    } catch (e: Exception) { 0f }
}
fun resizeBitmap(source: android.graphics.Bitmap, maxLength: Int = 1024): android.graphics.Bitmap {
    val aspectRatio = source.width.toFloat() / source.height.toFloat()
    val targetWidth: Int
    val targetHeight: Int

    if (source.width > source.height) {
        targetWidth = maxLength
        targetHeight = (maxLength / aspectRatio).toInt()
    } else {
        targetHeight = maxLength
        targetWidth = (maxLength * aspectRatio).toInt()
    }
    return android.graphics.Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
}
fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}