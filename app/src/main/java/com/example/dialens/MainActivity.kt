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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.shape.CircleShape
import androidx.room.PrimaryKey
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.dialens.MealEntry

// --- БАЗА ДАНИХ ТА МОДЕЛІ ---

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 0,
    val gender: String = "",
    val weight: Float = 0f,
    val height: Float = 0f,
    val age: Int = 0,
    val profileType: String = "Фітнес",
    val targetKcal: Float = 0f,
    val targetProteins: Float = 0f,
    val targetFats: Float = 0f,
    val targetCarbs: Float = 0f,
    val targetHO: Float = 0f
)

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
interface ProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 0")
    fun getProfileFlow(): kotlinx.coroutines.flow.Flow<UserProfile?> // Має бути Flow!

    @Upsert
    suspend fun saveProfile(profile: UserProfile)
}

// --- БАЗА ДАНИХ ---
@Database(entities = [MealEntry::class, UserProfile::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun profileDao(): ProfileDao
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "dialens-db"
        ).fallbackToDestructiveMigration().build()

        val mealDao = db.mealDao()
        val profileDao = db.profileDao()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val isDark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DiaLensMainScreen(mealDao = mealDao, profileDao = profileDao)
                }
            }
        }
    }
}

@Composable
fun DiaLensMainScreen(
    mealDao: MealDao,
    profileDao: ProfileDao // ДОДАЙ ЦЕЙ ПАРАМЕТР
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 1. Ініціалізація бази
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
// Усередині MainActivity
    val userProfileData by profileDao.getProfileFlow().collectAsState(initial = null)

    // БЕЗПЕЧНІ ЗМІННІ (якщо в базі null, беремо дефолтні значення)
    val userProfileType = userProfileData?.profileType ?: "Стандарт"
    val dailyKcalTarget = if (userProfileData?.targetKcal ?: 0f > 0) userProfileData!!.targetKcal else 2000f
    val proteinsGoal = userProfileData?.targetProteins ?: (dailyKcalTarget * 0.15f / 4)
    val fatsGoal = userProfileData?.targetFats ?: (dailyKcalTarget * 0.3f / 9)
    val carbsGoal = userProfileData?.targetCarbs ?: (dailyKcalTarget * 0.55f / 4)
    val hoGoal = userProfileData?.targetHO ?: (carbsGoal / 12f)   // Для діабетиків
    val startOfToday = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

// 2. Отримуємо історію тільки за сьогодні
    val history by mealDao.getTodayMeals(startOfToday).collectAsState(initial = emptyList<MealEntry>())
    val snackbarHostState = remember { SnackbarHostState() } // Для красивих повідомлень
    // 4. Синхронізація статистики
    LaunchedEffect(history) {
        consumedKcal = history.sumOf { it.kcal.toDouble() }.toFloat()
        consumedProteins = history.sumOf { it.proteins.toDouble() }.toFloat()
        consumedFats = history.sumOf { it.fats.toDouble() }.toFloat()
        consumedCarbs = history.sumOf { it.carbs.toDouble() }.toFloat()
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

        if (capturedBitmap == null && additionalInfo.isEmpty()) {
            cameraLauncher.launch() // Викликаємо лаунчер камери
            return // Виходимо, щоб не запускати аналіз порожнечі
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

                    3. ФОРМАТ КІНЦЕВОГО РЯДКА:
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
        // Контент додатка
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // --- 7. ЗАГОЛОВОК З ФОНОМ ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                // Фонове зображення
                Image(
                    painter = painterResource(id = R.drawable.header_bg),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // ПРАВИЛЬНИЙ ГРАДІЄНТ (додано список кольорів, щоб не крашилось)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.6f), // Темно зверху
                                    Color.Black.copy(alpha = 0.1f), // Напівпрозоро посередині
                                    Color.Black.copy(alpha = 0.8f)  // Темно знизу
                                )
                            )
                        )
                )

                // Контент у шапці
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp, start = 20.dp, end = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DiaLens AI",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        // ДОДАНО ТІНЬ ДЛЯ ЧИТАБЕЛЬНОСТІ
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.6f),
                                offset = Offset(2f, 4f),
                                blurRadius = 12f
                            )
                        )
                    )

                    // Іконка профілю з легким фоном для читабельності
                    IconButton(
                        onClick = { showProfileDialog = true },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Профіль",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // Основна колонка з контентом (з твоїми паддінгами)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .offset(y = (-54).dp) // Наплив контенту на шапку для стилю
            ) {
                // --- КНОПКИ ПРОФІЛІВ (СКЛЯНИЙ ЕФЕКТ) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Діабетик", "Спортсмен", "Фітнес").forEach { profile ->
                        val isSelected = userProfile == profile
                        Button(
                            onClick = { userProfile = profile },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                // 1. ПІДНІМАЄМО ВИЩЕ: y = (-44).dp точно посадить кнопки на край картинки
                                .offset(y = (-14).dp)
                                .border(
                                    width = 1.dp,
                                    // 2. БІЛЬШЕ БЛИСКУ: Робимо рамку яскравішою для скляного ефекту
                                    color = if (isSelected) Color.White.copy(0.9f) else Color.White.copy(
                                        0.9f
                                    ),
                                    shape = RoundedCornerShape(32.dp)
                                ),
                            shape = RoundedCornerShape(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                // 3. ПРОЗОРІСТЬ: Робимо не вибрані кнопки напівпрозорими "скевоморфними"
                                containerColor = if (isSelected) Color(0xFF2E7D32).copy(alpha = 0.9f)
                                else Color.Black.copy(alpha = 0.2f),
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(0.dp)
                        ) {
                            Text(
                                text = profile,
                                fontSize = 14.sp, // Трохи менше, щоб текст точно вліз в один рядок
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // КАРТКА СТАТИСТИКИ (ЛОГІКА ТА Ж САМА)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMealHistory = true },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSystemInDarkTheme()) Color(0xFF1E1E1E) else Color(0xFFF1F8E9)
                    ),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(Modifier.padding(32.dp)) {
                        // Використовуємо userProfileType з бази для перемикання режимів
                        when (userProfileType) {
                            "Спортсмен" -> {
                                // ТЕПЕР ПІДСТАВЛЯЄМО ЗМІННІ ЗАМІСТЬ ЧИСЕЛ
                                StatRow("Білки (г)", consumedProteins, proteinsGoal, Color(0xFF2196F3), mainTextColor)
                                StatRow("Жири (г)", consumedFats, fatsGoal, Color(0xFFFFC107), mainTextColor)
                                StatRow("Вуглеводи (г)", consumedCarbs, carbsGoal, Color(0xFF4CAF50), mainTextColor)
                            }
                            "Діабетик" -> {
                                // Виводимо ХО та Калорії для діабетика
                                StatRow("Хлібні одиниці (ХО)", consumedCarbs / 12f, hoGoal, Color(0xFF4CAF50), mainTextColor)
                                StatRow("Калорії (ккал)", consumedKcal, dailyKcalTarget, Color(0xFFFF9800), mainTextColor)
                            }
                            else -> { // "Стандарт" або будь-який інший
                                StatRow("Калорії (ккал)", consumedKcal, dailyKcalTarget, Color(0xFFFF9800), mainTextColor)
                                StatRow("Білки (г)", consumedProteins, proteinsGoal, Color(0xFF2196F3), mainTextColor)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ПОЛЕ ВВОДУ
                OutlinedTextField(
                    value = additionalInfo,
                    onValueChange = { additionalInfo = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Додай опис (напр. 'борщ з м'ясом')") },
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { analyzeMeal(); keyboardController?.hide() }),
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, null, tint = Color(0xFFF57C00), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Порада: Додайте долоню в кадр або опишіть порцію текстом.", fontSize = 14.sp, color = Color(0xFF5D4037))
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ОБЛАСТЬ ПРЕВ'Ю (ФОТО НА ВСЮ КАРТКУ)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f), // Зберігаємо твій weight
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSystemInDarkTheme()) Color(0xFF121212) else Color.White
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    // ВАЖЛИВО: У Box НЕ ПОВИННО бути .padding(16.dp)!
                    Box(Modifier.fillMaxSize()) {
                        if (resultText.isNotEmpty()) {
                            DetailedResultView(resultText, userProfile, mainTextColor, mealDao, coroutineScope) { _,_,_,_ -> resultText = ""; additionalInfo = ""; capturedBitmap = null }
                            IconButton(onClick = { resultText = "" }, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.Close, null, tint = Color.Gray) }
                        } else if (capturedBitmap != null) {
                            // ФОТО: Розтягуємо на всю картку
                            Image(
                                bitmap = capturedBitmap!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(), // Заповнює весь Box
                                contentScale = ContentScale.Crop // Обрізає зайве, зберігаючи пропорції
                            )

                            // Кнопка закриття поверх фото (з темною підкладкою для читабельності)
                            IconButton(
                                onClick = { capturedBitmap = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp) // Невеликий відступ від краю фото
                                    .background(Color.Black.copy(0.5f), RoundedCornerShape(16.dp))
                            ) { Icon(Icons.Default.Close, null, tint = Color.White) }
                        } else {
                            // ЗАГЛУШКА: Якщо фото немає, показуємо іконку по центру (з паддінгом, щоб не тулилась до краю)
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(16.dp) // Відступ тільки для іконки
                                    .clickable { cameraLauncher.launch() },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
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

                Spacer(Modifier.height(60.dp))
            }
        }

        // ГОЛОВНА КНОПКА (ЗАЛИШИЛАСЬ ЗНИЗУ)
        Button(
            onClick = { analyzeMeal() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2E7D32),
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Icon(Icons.Default.AutoAwesome, null, tint = Color.White)
                Spacer(Modifier.width(12.dp))
                Text("АНАЛІЗУВАТИ", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        // АНІМАЦІЯ ЗАВАНТАЖЕННЯ (БЕЗ ЗМІН)
        if (isLoading) {
            Box(modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.8f))
                .clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
                val infiniteTransition = rememberInfiniteTransition(label = "loading")
                val progress by infiniteTransition.animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(animation = tween(1500, easing = LinearEasing)), label = "progress"
                )
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
            // 'currentSettings' має збігатися з назвою в оголошенні функції ProfileDialog
            currentSettings = userProfileData ?: UserProfile(),
            onCloseDialog = { showProfileDialog = false },
            onSave = { updatedProfile ->
                coroutineScope.launch {
                    profileDao.saveProfile(updatedProfile)
                }
            }
        )
    }

    if (showMealHistory) {
        MealHistoryDialog(
            history = history,
            onDelete = { meal ->
                coroutineScope.launch { mealDao.deleteMeal(meal) }
            },
            onClose = { showMealHistory = false }
        )
    }
}// <--- ТЕПЕР ФУНКЦІЯ DiaLensMainScreen ЗАКРИВАЄТЬСЯ ТУТ

// 1. Додаємо функцію-розширення (можна винести за межі ProfileDialog)


@Composable
fun ProfileDialog(
    currentSettings: UserProfile,
    onCloseDialog: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    val context = LocalContext.current
    var gender by remember { mutableStateOf(currentSettings.gender.ifEmpty { "Чоловік" }) }
    var profileType by remember { mutableStateOf(currentSettings.profileType.ifEmpty { "Стандарт" }) }
    var weight by remember { mutableStateOf(currentSettings.weight.let { if (it == 0f) "" else it.toString() }) }
    var height by remember { mutableStateOf(currentSettings.height.let { if (it == 0f) "" else it.toString() }) }
    var age by remember { mutableStateOf(currentSettings.age.let { if (it == 0) "" else it.toString() }) }

    AlertDialog(
        onDismissRequest = onCloseDialog,
        modifier = Modifier.clip(RoundedCornerShape(28.dp)),
        containerColor = Color(0xFF1A1C1E),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Мій Профіль", color = Color.White, fontWeight = FontWeight.Bold)
                IconButton(onClick = onCloseDialog) {
                    Icon(Icons.Default.Close, contentDescription = "Закрити", tint = Color.Gray)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ProfileSelectionRow("Стать", listOf("Чоловік", "Жінка"), gender) { gender = it }
                ProfileSelectionRow("Режим", listOf("Стандарт", "Спортсмен", "Діабетик"), profileType) { profileType = it }

                val colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF4CAF50)
                )

                OutlinedTextField(
                    value = age, onValueChange = { age = it },
                    label = { Text("Вік") },
                    modifier = Modifier.fillMaxWidth(), colors = colors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weight, onValueChange = { weight = it },
                        label = { Text("Вага (кг)") },
                        modifier = Modifier.weight(1f), colors = colors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = height, onValueChange = { height = it },
                        label = { Text("Зріст (см)") },
                        modifier = Modifier.weight(1f), colors = colors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCloseDialog) {
                Text("Скасувати", color = Color.Gray)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = weight.toFloatOrNull() ?: 0f
                    val h = height.toFloatOrNull() ?: 0f
                    val a = age.toIntOrNull() ?: 0

                    if (w > 0 && h > 0 && a > 0) {
                        val goals = calculateGoals(profileType, gender, w, h, a)
                        onSave(currentSettings.copy(
                            gender = gender, profileType = profileType,
                            weight = w, height = h, age = a,
                            targetKcal = goals.kcal,
                            targetProteins = goals.proteins,
                            targetFats = goals.fats,
                            targetCarbs = goals.carbs,
                            targetHO = goals.ho
                        ))
                        // ВІЗУАЛЬНИЙ ФІДБЕК
                        android.widget.Toast.makeText(context, "Дані оновлено!", android.widget.Toast.LENGTH_SHORT).show()
                        onCloseDialog()
                    } else {
                        android.widget.Toast.makeText(context, "Заповніть усі поля", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Зберегти зміни")
            }
        }
    )
}

@Composable
fun ProfileSelectionRow(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val isSelected = selected == option
                Surface(
                    modifier = Modifier.weight(1f).height(40.dp).clickable { onSelect(option) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFF4CAF50) else Color(0xFF2C2E31),
                    border = if (isSelected) null else BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(option, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
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
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp)),
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

    Column(Modifier
        .verticalScroll(rememberScrollState())
        .padding(top = 16.dp, start = 16.dp, end = 16.dp)) {
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
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
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
    history: List<MealEntry>,
    onDelete: (MealEntry) -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Історія за сьогодні") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (history.isEmpty()) {
                    Text("Ви ще нічого не додали сьогодні")
                } else {
                    history.forEach { meal ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(meal.dishName, fontWeight = FontWeight.Bold)
                                Text("${meal.kcal} ккал")
                            }
                            IconButton(onClick = { onDelete(meal) }) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("Закрити") }
        }
    )
}

@Composable
fun MealItemRow(meal: MealEntry, onDelete: (MealEntry) -> Unit, onEdit: (MealEntry) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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

// МОДЕЛЬ ДАНИХ (Тільки одна!)
// МОДЕЛЬ ДАНИХ (Тільки одна!)
data class UserGoals(
    val kcal: Float = 0f,
    val proteins: Float = 0f,
    val fats: Float = 0f,
    val carbs: Float = 0f,
    val ho: Float = 0f
)

// ФУНКЦІЯ РОЗРАХУНКУ (Тільки одна!)
fun calculateGoals(
    profile: String,
    gender: String,
    weight: Float,
    height: Float,
    age: Int
): UserGoals {
    val bmr = if (gender == "Чоловік") {
        (10 * weight) + (6.25f * height) - (5 * age) + 5
    } else {
        (10 * weight) + (6.25f * height) - (5 * age) - 161
    }
    val totalKcal = bmr * 1.2f

    return when (profile) {
        "Спортсмен" -> {
            val p = weight * 2.2f
            val f = weight * 0.9f
            val c = (totalKcal - (p * 4) - (f * 9)) / 4
            UserGoals(totalKcal, p, f, c, c / 12f)
        }
        "Діабетик" -> {
            val p = weight * 1.5f
            val f = weight * 0.8f
            val c = (totalKcal - (p * 4) - (f * 9)) / 4
            UserGoals(totalKcal, p, f, c, c / 12f)
        }
        else -> {
            val p = weight * 1.2f
            val f = weight * 0.8f
            val c = (totalKcal - (p * 4) - (f * 9)) / 4
            UserGoals(totalKcal, p, f, c, c / 12f)
        }
    }
}