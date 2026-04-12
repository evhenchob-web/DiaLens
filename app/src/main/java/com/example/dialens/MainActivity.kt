package com.example.dialens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Locale

// --- БАЗА ДАНИХ ТА МОДЕЛІ ---

@Entity(tableName = "user_profile")
data class userProfile(
    @PrimaryKey val id: Int = 0,
    val gender: String = "Male",        // Було "Чоловік" -> Стало "Male"
    val weight: Float = 0f,
    val height: Float = 0f,
    val age: Int = 0,
    val profileType: String = "Standard", // Було "Стандарт" -> Стало "Standard"
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

    @Insert
    suspend fun insertMeal(meal: MealEntry)
    @Delete
    suspend fun deleteMeal(meal: MealEntry)
}


@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 0")
    fun getProfileFlow(): kotlinx.coroutines.flow.Flow<userProfile?> // Має бути Flow!

    @Upsert
    suspend fun saveProfile(profile: userProfile)
}

// --- БАЗА ДАНИХ ---
@Database(entities = [MealEntry::class, userProfile::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun profileDao(): ProfileDao
}

@ExperimentalMaterial3Api
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
@ExperimentalMaterial3Api
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
    var resultText by remember { mutableStateOf("") }
    var additionalInfo by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    var consumedKcal by remember { mutableFloatStateOf(0f) }
    var consumedProteins by remember { mutableFloatStateOf(0f) }
    var consumedFats by remember { mutableFloatStateOf(0f) }
    var consumedCarbs by remember { mutableFloatStateOf(0f) }

    var showFaq by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    var showTipSheet by remember { mutableStateOf(false) }

    val prefs = remember {
        context.getSharedPreferences(
            "dialens_prefs",
            android.content.Context.MODE_PRIVATE
        )
    }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showQuickProfileSelection by remember { mutableStateOf(false) }

    val userProfileData by profileDao.getProfileFlow().collectAsState(initial = null)

    // Перевіряємо, чи заповнено профіль раніше (за замовчуванням false)
    // МАЄ БУТИ САМЕ ТАК:
    // Тепер тут зберігається технічний ключ: "Standard", "Athlete" або "Diabetic"
    var userProfile by remember { mutableStateOf("Standard") }

    LaunchedEffect(userProfileData) {
        userProfileData?.let {
            if (it.profileType.isNotEmpty()) {
                userProfile = it.profileType // Тут буде технічне значення з бази
            }
        }
    }

    // 3. Зчитування даних
// Усередині MainActivity
    // БЕЗПЕЧНІ ЗМІННІ (якщо в базі null, беремо дефолтні значення)
    val dailyKcalTarget =
        if (userProfileData?.targetKcal ?: 0f > 0) userProfileData!!.targetKcal else 2000f
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
    val history by mealDao.getTodayMeals(startOfToday)
        .collectAsState(initial = emptyList<MealEntry>())
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
    val visionModel =
        remember { GenerativeModel(modelName = "gemini-3.1-flash-lite-preview", apiKey = apiKey) }
    val textModel = remember { GenerativeModel(modelName = "gemma-3n-e2b-it", apiKey = apiKey) }
    // 6. Лаунчери

    // 1. Створюємо "ланчер" для запиту дозволів
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[android.Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[android.Manifest.permission.RECORD_AUDIO] ?: false

        if (cameraGranted && audioGranted) {
            // Усе добре, можна відкривати камеру/мікрофон
        } else {
            // Користувач відмовив — можна показати Toast "Нам потрібні дозволи"
        }
    }

    // 2. Функція для перевірки та запиту
    fun checkAndRequestPermissions(context: Context) {
        val permissions = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        )

        // Перевіряємо, чи вже є дозволи
        if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            // Вже є дозволи — відкриваємо функцію
        } else {
            // Запитуємо
            permissionLauncher.launch(permissions)
        }
    }
    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                capturedBitmap = bitmap; resultText = ""
            }
        }

    val galleryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                context.contentResolver.openInputStream(it)
                    ?.use { stream -> capturedBitmap = BitmapFactory.decodeStream(stream) }
            }
        }

    val speechLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                additionalInfo =
                    result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
                        ?: ""
            }
        }

    fun analyzeMeal(context: Context) {
        if (!isNetworkAvailable(context)) {
            resultText = context.getString(R.string.error_no_internet)
            return
        }

        // 1. Зберігаємо поточний стан у тимчасові змінні (локальні копії)
        // Це гарантує, що запит не зміниться, навіть якщо ми очистимо поля відразу
        val textToAnalyze = additionalInfo
        val bitmapToAnalyze = capturedBitmap

        // Перевірка на порожнечу (використовуємо локальні копії)
        if (bitmapToAnalyze == null && textToAnalyze.isEmpty()) {
            cameraLauncher.launch()
            return
        }

        isLoading = true
        coroutineScope.launch {
            try {
                val currentLanguage = Locale.getDefault().language
                val languageInstruction = if (currentLanguage == "uk") "Пиши відповідь українською." else "Write the response in English."

                val profileContext = when (userProfile) {
                    "Athlete" -> "Спортсмен (висока потреба в білку та енергії)"
                    "Diabetic" -> "Діабетик (суворий контроль вуглеводів та ГІ)"
                    else -> "Стандартний (збалансоване харчування)"
                }

                // Твій повний промпт без жодних скорочень
                val expertPrompt = """
                $languageInstruction
                Ти — професійний дієтолог-ендокринолог. 
                Клієнт має профіль: $profileContext.

                ЗАВДАННЯ:
                1. Проаналізуй страву: "$textToAnalyze".
                2. Якщо в описі страви вказана конкретна вага (наприклад, 100г) — розрахуй КБЖВ СУВОРО на цю вагу. 
                   Якщо вага НЕ вказана — бери середню порцію 250г.
                3. Дай коротку пораду щодо вживання цієї страви для цього профілю та один цікавий факт.

                ТЕХНІЧНІ ВИМОГИ (НЕ виводь ці заголовки у відповіді):
                - Будь реалістичним (обід 300-700 ккал, не більше 2000 ккал на страву).
                - ПИШИ ТІЛЬКИ ОДНЕ КОНКРЕТНЕ ЧИСЛО. Жодних діапазонів (наприклад, не пиши "15-20").
                - Якщо не впевнений — бери середнє значення.
                - Пиши тільки цілими числами, без ком та символів.

                В КІНЦІ ВІДПОВІДІ ОБОВ'ЯЗКОВО ДОДАЙ ТІЛЬКИ ЦЕЙ РЯДОК (без заголовків):
                ---
                СТРАВА: [назва] | ККАЛ: [число] | БІЛКИ: [число] | ЖИРИ: [число] | ВУГЛЕВОДИ: [число]
            """.trimIndent()

                val response = if (bitmapToAnalyze != null) {
                    val bitmapToSend = resizeBitmap(bitmapToAnalyze)
                    visionModel.generateContent(
                        com.google.ai.client.generativeai.type.content {
                            image(bitmapToSend)
                            text("Контекст: $textToAnalyze \n\n $expertPrompt")
                        }
                    )
                } else {
                    textModel.generateContent("Аналізуй: $textToAnalyze \n\n $expertPrompt")
                }

                val responseText = response.text ?: ""

                if (responseText.isNotBlank()) {
                    resultText = responseText

                    // --- ОЧИЩЕННЯ ПРИ УСПІХУ ---
                    additionalInfo = ""   // Очищуємо текстове поле
                    capturedBitmap = null // Очищуємо зроблене фото
                }

                keyboardController?.hide()

            } catch (e: Exception) {
                // При помилці поля НЕ очищуються (блок вище пропускається)
                resultText = context.resources.getString(R.string.error_api)
            } finally {
                isLoading = false
            }
        }
    }


    // ГОЛОВНИЙ КОНТЕЙНЕР
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 90.dp)
        ) {
            // --- ВЕРХНІЙ БЛОК: ЗАГОЛОВОК З ФОНОМ ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                // 1. Фон
                Image(
                    painter = painterResource(id = R.drawable.header_bg),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // 2. Градієнт
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.7f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f)
                                )
                            )
                        )
                )

                // 3. НАЗВА DIALENS — ТЕПЕР ВГОРІ ЛІВОРУЧ
                Text(
                    text = "DIALENS",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 2.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart) // Притискаємо до верхнього лівого кута
                        .padding(start = 24.dp, top = 50.dp) // Відступи від країв
                )

                // БЛОК КНОПОК ПРАВОРУЧ
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // 1. КНОПКА НАЛАШТУВАНЬ (тепер вона зверху)
                    IconButton(
                        onClick = { showProfileDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF2E7D32).copy(alpha = 0.8f), CircleShape)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
                    }

                    Spacer(modifier = Modifier.height(12.dp)) // Відступ по вертикалі

                    // 2. КНОПКА FAQ (тепер вона під налаштуваннями)
                    IconButton(
                        onClick = { showFaq = true },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "FAQ", tint = Color.White)
                    }
                }
                // 5. НАЗВА ПРОФІЛЮ — ВНИЗУ ПО ЦЕНТРУ
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .clickable { showQuickProfileSelection = true },
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(800.dp),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(Color(0xFF4CAF50), CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))

                        // МАПІНГ: Використовуємо твою змінну userProfile
                        val displayProfileName = when (userProfile) {
                            "Standard" -> stringResource(R.string.profile_label_standard)
                            "Athlete" -> stringResource(R.string.profile_label_athlete)
                            "Diabetic" -> stringResource(R.string.profile_label_diabetic)
                            else -> userProfile // На випадок, якщо в базі порожньо
                        }

                        Text(
                            text = displayProfileName.uppercase(), // Тепер тут буде "СТАНДАРТ"
                            fontSize = 18.sp,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                // 2. КАРТКА СТАТИСТИКИ
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMealHistory = true },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSystemInDarkTheme()) Color(
                            0xFF1E1E1E
                        ) else Color(0xFFF1F8E9)
                    ),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(Modifier.padding(24.dp)) {
                        when (userProfile) {
                            "Athlete" -> { // ТЕХНІЧНА НАЗВА
                                StatRow(
                                    stringResource(R.string.stat_proteins),
                                    consumedProteins,
                                    proteinsGoal,
                                    Color(0xFF2196F3),
                                    mainTextColor
                                )
                                StatRow(
                                    stringResource(R.string.stat_fats),
                                    consumedFats,
                                    fatsGoal,
                                    Color(0xFFFFC107),
                                    mainTextColor
                                )
                                StatRow(
                                    stringResource(R.string.stat_carbs),
                                    consumedCarbs,
                                    carbsGoal,
                                    Color(0xFF4CAF50),
                                    mainTextColor
                                )
                            }

                            "Diabetic" -> { // ТЕХНІЧНА НАЗВА
                                StatRow(
                                    stringResource(R.string.stat_ho),
                                    consumedCarbs / 12f,
                                    hoGoal,
                                    Color(0xFF4CAF50),
                                    mainTextColor
                                )
                                StatRow(
                                    stringResource(R.string.stat_kcal),
                                    consumedKcal,
                                    dailyKcalTarget,
                                    Color(0xFFFF9800),
                                    mainTextColor
                                )
                            }

                            else -> { // Standard
                                StatRow(
                                    stringResource(R.string.stat_kcal),
                                    consumedKcal,
                                    dailyKcalTarget,
                                    Color(0xFFFF9800),
                                    mainTextColor
                                )
                                StatRow(
                                    stringResource(R.string.stat_proteins),
                                    consumedProteins,
                                    proteinsGoal,
                                    Color(0xFF2196F3),
                                    mainTextColor
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 3. ПОЛЕ ВВОДУ ТА МІКРОФОН
                OutlinedTextField(
                    value = additionalInfo,
                    onValueChange = { additionalInfo = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        // Замінюємо "Додай опис..." на ресурс
                        Text(stringResource(R.string.input_placeholder))
                    },
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),

                    // 2. Визначаємо, що робити при натисканні на цю лупу
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            analyzeMeal(context) // Запускаємо аналіз
                            additionalInfo = ""  // 2. Очищуємо поле після запуску
                            keyboardController?.hide() // Ховаємо клавіатуру
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4CAF50), // Зелена рамка при фокусі
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color(0xFF4CAF50),       // Твій зелений курсор!

                        // Налаштування кольору виділення тексту (опціонально, але стильно)
                        selectionColors = TextSelectionColors(
                            handleColor = Color(0xFF4CAF50),      // Колір "крапельки" під курсором
                            backgroundColor = Color(0xFF4CAF50).copy(alpha = 0.4f) // Колір фону виділеного тексту
                        )
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            // 1. Перевіряємо, чи надано дозвіл на запис аудіо
                            val isGranted = ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (isGranted) {
                                // 2. Якщо дозвіл є — запускаємо диктофон
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                    )
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
                                }
                                try {
                                    speechLauncher.launch(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Speech recognition not available", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                // 3. Якщо дозволу немає — запитуємо його через лаунчер
                                permissionLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO))
                            }
                        }) {
                            Icon(Icons.Default.Mic, null, tint = Color(0xFF4CAF50))
                        }
                    }
                )

                Spacer(Modifier.height(16.dp))

                // 4. ВЕЛИКА КАРТКА ПРЕВ'Ю (КАМЕРА + ГАЛЕРЕЯ)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = 250.dp)
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSystemInDarkTheme()) Color(0xFF1E1E1E) else Color.White
                    ),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Box(Modifier.fillMaxSize()) {
                        if (resultText.isNotEmpty()) {
                            // СТАН 1: РЕЗУЛЬТАТ
                            Box(Modifier.fillMaxSize()) {
                                DetailedResultView(
                                    resultText,
                                    userProfile,
                                    mainTextColor,
                                    mealDao,
                                    coroutineScope
                                ) { _, _, _, _ -> resultText = ""; capturedBitmap = null }

                                IconButton(
                                    onClick = { resultText = ""; capturedBitmap = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                        .size(32.dp)
                                        .background(Color.Black.copy(0.5f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }

                        } else if (capturedBitmap != null) {
                            // СТАН 2: ТВІЙ СТАРИЙ ELSE IF (ПРЕВ'Ю ФОТО)
                            Image(
                                bitmap = capturedBitmap!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { capturedBitmap = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .background(Color.Black.copy(0.5f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, null, tint = Color.White)
                            }

                        } else {
                            // СТАН: Початковий, фото ще немає
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        val permissions = arrayOf(
                                            android.Manifest.permission.CAMERA,
                                            android.Manifest.permission.RECORD_AUDIO
                                        )

                                        val allGranted = permissions.all {
                                            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                                        }

                                        if (allGranted) {
                                            cameraLauncher.launch()
                                        } else {
                                            permissionLauncher.launch(permissions)
                                        }
                                    }
                            ) {
                                // 1. --- ПОРАДА ЗВЕРХУ (додається поверх всього) ---
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter) // Притискаємо до верхнього центру
                                        .padding(16.dp), // Відступ від країв картки
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.Gray.copy(alpha = 0.15f), // Легкий фон
                                    border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lightbulb,
                                            contentDescription = null,
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            // Замінюємо хардкод на ключ із нашого списку
                                            text = stringResource(R.string.camera_tip),
                                            color = Color.Gray,
                                            fontSize = 13.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }

                                // 2. --- ІКОНКИ ЧІТКО ПО ЦЕНТРУ (як і було) ---
                                Column(
                                    modifier = Modifier.align(Alignment.Center), // Центруємо Column в Box
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(contentAlignment = Alignment.BottomEnd) {
                                        Icon(
                                            Icons.Default.PhotoCamera,
                                            null,
                                            tint = Color.Gray,
                                            modifier = Modifier.size(100.dp)
                                        )
                                        // Твоя зелена кнопка галереї
                                        Surface(
                                            modifier = Modifier
                                                .size(45.dp)
                                                .offset(12.dp, 12.dp)
                                                .clickable { galleryLauncher.launch("image/*") },
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFF4CAF50),
                                            shadowElevation = 6.dp
                                        ) {
                                            Icon(
                                                Icons.Default.Collections,
                                                null,
                                                tint = Color.White,
                                                modifier = Modifier.padding(6.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.camera_hint),
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                } // КІНЕЦЬ CARD
            } // КІНЕЦЬ ВНУТРІШНЬОЇ COLUMN (padding 16.dp)
        } // КІНЕЦЬ ГОЛОВНОЇ COLUMN (яка йде після шапки)

// ТЕПЕР КНОПКА ЗНАХОДИТЬСЯ ПРЯМО В BOX
        Button(
            onClick = { analyzeMeal(context) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp) // Збільшуємо цей відступ (було 16.dp)
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
                Text(
                    text = stringResource(R.string.analyze_button),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }

    if (showFaq) {
        ModalBottomSheet(
            onDismissRequest = { showFaq = false },
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            // Викликаємо наш екран FAQ прямо тут
            FaqScreen(onBack = { showFaq = false })
        }
    }
    // ДІАЛОГИ (БЕЗ ЗМІН)
    if (showProfileDialog) {
        ProfileDialog(
            // 1. Використовуємо UserProfile(), бо так називається твій клас у рядку 654
            currentSettings = userProfileData ?: userProfile(),
            onCloseDialog = { showProfileDialog = false },
            onSave = { updatedProfile ->
                coroutineScope.launch {
                    // 2. Використовуємо userProfile (це твоя змінна стану з рядка 53)
                    // 3. Копіюємо в поле profileType (рядок 656 твого файлу)
                    val profileToSave = updatedProfile.copy(profileType = userProfile)
                    profileDao.saveProfile(profileToSave)
                }
                showProfileDialog = false
            }
        )
    }
    if (showMealHistory) {
        MealHistoryDialog(
            history = history,
            onDelete = { meal -> coroutineScope.launch { mealDao.deleteMeal(meal) } },
            onClose = { showMealHistory = false })
    }
    // Перевірка стану: якщо true — показуємо наш новий Composable
    if (showQuickProfileSelection) {
        QuickProfileSelectionDialog(
            currentActiveProfile = userProfile,
            onProfileChosen = { selected ->
                // 1. Оновлюємо UI миттєво
                userProfile = selected
                showQuickProfileSelection = false

                // 2. Зберігаємо в базу, щоб LaunchedEffect і розрахунки калорій підхопили зміни
                coroutineScope.launch {
                    val currentData = userProfileData ?: userProfile()
                    profileDao.saveProfile(currentData.copy(profileType = selected))
                }

                // 3. Резервне збереження в префи
                prefs.edit().putString("user_profile", selected).apply()
            },
            onDismissRequest = { showQuickProfileSelection = false }
        )
    }
    if (showTipSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTipSheet = false },
            sheetState = sheetState,
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF4CAF50)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.tip_expert), // Замінили хардкод
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = Color(0xFF2E7D32)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // ВИПРАВЛЕНО: використовуємо resultText замість response
                val tip = resultText.substringAfter("---").substringBefore("СТРАВА:").trim()
                Text(
                    text = tip,
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
            }
        }
    }
}// <--- ТЕПЕР ФУНКЦІЯ DiaLensMainScreen ЗАКРИВАЄТЬСЯ ТУТ

// 1. Додаємо функцію-розширення (можна винести за межі ProfileDialog)

@Composable
fun QuickProfileSelectionDialog(
    currentActiveProfile: String,
    onProfileChosen: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(R.string.select_mode), // "Оберіть режим"
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF2E7D32)
            )
        },
        text = {
            Column {
                // Мапінг: технічний ключ -> ресурс рядка для відображення
                val optionsMap = mapOf(
                    "Standard" to R.string.profile_label_standard,
                    "Athlete" to R.string.profile_label_athlete,
                    "Diabetic" to R.string.profile_label_diabetic
                )

                optionsMap.forEach { (technicalKey, labelRes) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProfileChosen(technicalKey) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (technicalKey == currentActiveProfile),
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF4CAF50),
                                unselectedColor = Color.Gray
                            )
                        )
                        Text(
                            text = stringResource(labelRes), // Відображаємо перекладений текст
                            modifier = Modifier.padding(start = 12.dp),
                            fontSize = 18.sp,
                            fontWeight = if (technicalKey == currentActiveProfile) FontWeight.Bold else FontWeight.Normal,
                            color = Color.White // Додав білий колір, оскільки фон діалогу темний
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(
                    text = stringResource(R.string.btn_cancel), // "СКАСУВАТИ"
                    color = Color(0xFFD32F2F),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun ProfileDialog(
    currentSettings: userProfile,
    onCloseDialog: () -> Unit,
    onSave: (userProfile) -> Unit
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
                Text(stringResource(R.string.my_profile), color = Color.White, fontWeight = FontWeight.Bold)
                IconButton(onClick = onCloseDialog) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.btn_close),
                        tint = Color.Gray
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Використовуємо технічні ключі "Male", "Female" для внутрішньої логіки
                // Усередині Column в ProfileDialog
                ProfileSelectionRow(
                    label = stringResource(R.string.gender_label),
                    options = listOf("Male", "Female"),
                    selected = gender, // ВИПРАВЛЕНО: було selectedOption
                    onSelect = { gender = it }
                )
                val colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF4CAF50),
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color(0xFF4CAF50),
                    unfocusedLabelColor = Color.Gray
                )

                OutlinedTextField(
                    value = age, onValueChange = { age = it },
                    label = { Text(stringResource(R.string.age)) },
                    modifier = Modifier.fillMaxWidth(), colors = colors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weight, onValueChange = { weight = it },
                        label = { Text(stringResource(R.string.weight)) },
                        modifier = Modifier.weight(1f), colors = colors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = height, onValueChange = { height = it },
                        label = { Text(stringResource(R.string.height)) },
                        modifier = Modifier.weight(1f), colors = colors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCloseDialog) {
                Text(stringResource(R.string.btn_cancel), color = Color.Gray)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = weight.toFloatOrNull() ?: 0f
                    val h = height.toFloatOrNull() ?: 0f
                    val a = age.toIntOrNull() ?: 0

                    if (w > 0 && h > 0 && a > 0) {
                        // profileType має бути доступним у цьому scope
                        val goals = calculateGoals(profileType, gender, w, h, a)

                        context.getSharedPreferences("dialens_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("profile_filled", true)
                            .apply()

                        onSave(
                            currentSettings.copy(
                                gender = gender,
                                profileType = profileType,
                                weight = w,
                                height = h,
                                age = a,
                                targetKcal = goals.kcal,
                                targetProteins = goals.proteins,
                                targetFats = goals.fats,
                                targetCarbs = goals.carbs,
                                targetHO = goals.ho
                            )
                        )

                        // ВИПРАВЛЕНО: використання ресурсу для Toast
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.toast_updated),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        onCloseDialog()
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.toast_fill_all),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White // Білий колір тексту повернуто
                ),
                shape = RoundedCornerShape(12.dp) // Закруглення 12.dp повернуто
            ) {
                Text(stringResource(R.string.btn_save))
            }
        }
    )
}

@Composable
fun ProfileSelectionRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selected

                // МАПІНГ: перетворюємо технічний ключ на перекладений текст
                val displayLabel = when (option) {
                    "Standard" -> stringResource(R.string.profile_label_standard)
                    "Athlete" -> stringResource(R.string.profile_label_athlete)
                    "Diabetic" -> stringResource(R.string.profile_label_diabetic)
                    "Male" -> stringResource(R.string.gender_male)
                    "Female" -> stringResource(R.string.gender_female)
                    else -> option
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(option) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Color(0xFF4CAF50) else Color(0xFF252525),
                    border = if (isSelected) null else BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = displayLabel, // ТЕПЕР ТУТ ПЕРЕКЛАДЕНИЙ ТЕКСТ
                        modifier = Modifier.padding(vertical = 12.dp),
                        textAlign = TextAlign.Center,
                        color = if (isSelected) Color.White else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp // Трохи зменшив, щоб довгі слова (Спортсмен) влазили
                    )
                }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: Float, target: Float, color: Color, textColor: Color) {
    Column(Modifier.padding(vertical = 2.dp)) { // Зменшено з 4.dp до 2.dp
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            ) // Зменшено шрифт
            Text("${value.toInt()}/${target.toInt()}", fontSize = 12.sp, color = textColor)
        }
        LinearProgressIndicator(
            progress = { (value / target).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp) // Зменшено з 10.dp до 8.dp
                .clip(RoundedCornerShape(4.dp)),
            color = if (value > target) Color.Red else color,
            trackColor = color.copy(alpha = 0.2f)
        )
    }
}

@Composable
fun DetailedResultView(
    text: String,
    userProfile: String, // Тут тепер прилітає "Diabetic", "Athlete" або "Standard"
    textColor: Color,
    mealDao: MealDao,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onConfirm: (Float, Float, Float, Float) -> Unit
) {
    val kcal = parseVal(text, "ККАЛ")
    val b = parseVal(text, "БІЛКИ")
    val j = parseVal(text, "ЖИРИ")
    val v = parseVal(text, "ВУГЛЕВОДИ")
    val dishName = text.substringAfter("СТРАВА:", "Страва").substringBefore("|").trim()

    val fullDescription = text.substringBefore("СТРАВА:")
        .replace("**", "").replace("##", "").replace("*", "•")
        .trim()

    var isExpanded by remember { mutableStateOf(false) }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
    ) {
        Text(
            text = dishName.uppercase(),
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF4CAF50),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Surface(
            color = if (isSystemInDarkTheme()) Color(0xFF252525) else Color(0xFFF5F5F5),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = if (isExpanded || fullDescription.length <= 130) fullDescription
                    else fullDescription.take(130).substringBeforeLast(" ") + "...",
                    color = textColor.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    lineHeight = 18.sp
                )

                Text(
                    text = if (isExpanded) stringResource(R.string.collapse)
                    else stringResource(R.string.more_info),
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
                // Використовуємо формат %1$d з strings.xml
                Text(
                    text = stringResource(R.string.energy_label, kcal.toInt()),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = textColor
                )
                // Використовуємо формат Б: %1$dg | Ж: %2$dg | В: %3$dg
                Text(
                    text = stringResource(R.string.macros_summary, b.toInt(), j.toInt(), v.toInt()),
                    color = textColor.copy(alpha = 0.7f)
                )
            }

            // ПЕРЕВІРКА: тепер порівнюємо з "Diabetic"
            if (userProfile == "Diabetic") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val ho = v / 12f
                    Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            // Виводимо число ХО
                            text = String.format("%.1f", ho) + " " + stringResource(R.string.stat_ho).filter { it.isLetter() },
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.stat_ho),
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                coroutineScope.launch {
                    mealDao.insertMeal(
                        MealEntry(
                            dishName = dishName,
                            kcal = kcal,
                            proteins = b,
                            fats = j,
                            carbs = v
                        )
                    )
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
            Text(
                text = stringResource(R.string.save_to_log),
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
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
        modifier = Modifier.clip(RoundedCornerShape(28.dp)),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Event,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    // Замінено: "Історія за сьогодні"
                    text = stringResource(R.string.history_today),
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF2E7D32)
                )
            }
        },
        text = {
            Box(modifier = Modifier.heightIn(max = 420.dp)) {
                if (history.isEmpty()) {
                    Text(
                        // Замінено: "Сьогодні ще не було записів"
                        text = stringResource(R.string.no_entries_today),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                } else {
                    LazyColumn {
                        items(history) { meal ->
                            MealHistoryItem(meal = meal, onDelete = onDelete)
                            Divider(
                                color = Color.Gray.copy(alpha = 0.2f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text(
                    // Замінено: "ЗАКРИТИ"
                    text = stringResource(R.string.btn_close),
                    color = Color(0xFFD32F2F),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}@Composable
fun MealHistoryItem(meal: MealEntry, onDelete: (MealEntry) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = meal.dishName,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White // Додав для кращої читаємості в темній темі
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Adjust,
                    contentDescription = null,
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))

                // ВИПРАВЛЕННЯ: Використовуємо energy_label, але витягуємо тільки "ккал"
                // або просто підставляємо число в шаблон
                Text(
                    text = stringResource(R.string.energy_label, meal.kcal.toInt()).replace("🔥 ", ""),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
        IconButton(onClick = { onDelete(meal) }) {
            Icon(
                imageVector = Icons.Default.Delete,
                // ВИПРАВЛЕННЯ: опис кнопки з ресурсів
                contentDescription = stringResource(R.string.back_button_desc), // Можна використати інший ключ, якщо є
                tint = Color(0xFFD32F2F).copy(alpha = 0.7f)
            )
        }
    }
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
            Text(
                text = meal.dishName,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White // Додаємо білий колір для темної теми
            )

            // ВИПРАВЛЕННЯ: Використовуємо наш шаблон macros_summary та energy_label
            val kcalText = stringResource(R.string.energy_label, meal.kcal.toInt()).replace("🔥 ", "")
            val macrosText = stringResource(
                R.string.macros_summary,
                meal.proteins.toInt(),
                meal.fats.toInt(),
                meal.carbs.toInt()
            )

            Text(
                text = "$kcalText | $macrosText",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        Row {
            IconButton(onClick = { onDelete(meal) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.back_button_desc), // Або інший ключ для видалення
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ОНОВЛЕНИЙ БРОНЕБІЙНИЙ ПАРСЕР
fun parseVal(text: String, key: String): Float {
    return try {
        // 1. Знаходимо частину тексту після ключа (наприклад, після "ККАЛ:")
        val regex = "$key:\\s*([^|\\n]+)".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(text)
        val valueStr = match?.groupValues?.get(1)?.trim() ?: "0"

        // 2. Беремо символи, поки вони є цифрами або крапкою/комою.
        // Як тільки зустрінемо тире (-) або пробіл — зупиняємось.
        val firstPart = valueStr.takeWhile { it.isDigit() || it == '.' || it == ',' }

        // 3. Чистимо кому на крапку для Float та повертаємо результат
        firstPart.replace(",", ".").toFloatOrNull() ?: 0f
    } catch (e: Exception) {
        0f
    }
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
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
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
    // Тепер gender порівнюємо з технічним "Male"
    val bmr = if (gender == "Male") {
        (10 * weight) + (6.25f * height) - (5 * age) + 5
    } else {
        (10 * weight) + (6.25f * height) - (5 * age) - 161
    }
    val totalKcal = bmr * 1.2f

    return when (profile) {
        "Athlete" -> {
            val p = weight * 2.2f
            val f = weight * 0.9f
            val c = (totalKcal - (p * 4) - (f * 9)) / 4
            UserGoals(totalKcal, p, f, c, c / 12f)
        }
        "Diabetic" -> {
            val p = weight * 1.5f
            val f = weight * 0.8f
            val c = (totalKcal - (p * 4) - (f * 9)) / 4
            UserGoals(totalKcal, p, f, c, c / 12f)
        }
        else -> { // Standard
            val p = weight * 1.2f
            val f = weight * 0.8f
            val c = (totalKcal - (p * 4) - (f * 9)) / 4
            UserGoals(totalKcal, p, f, c, c / 12f)
        }
    }
}