package com.example.dialens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Інструкція та FAQ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // --- СЕКЦІЯ ДИСКЛЕЙМЕР (ВАЖЛИВО) ---
            DisclaimerSection()

            Spacer(Modifier.height(24.dp))

            Text("Часті питання (FAQ)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            FaqItem("Як отримати найточніший результат?",
                "Для того, щоб AI вірно розрахував порцію, покладіть поруч зі стравою долоню або стандартний предмет (наприклад, столові прибори). Намагайтеся робити фото при гарному освітленні зверху.")

            FaqItem("Чому дані можуть бути приблизними?",
                "DiaLens використовує візуальний аналіз. ШІ не може знати точний склад соусів, кількість олії всередині або приховані інгредієнти. Похибка у 10-20% є нормальною для такого методу.")

            FaqItem("Чи замінює додаток поради лікаря?",
                "Категорично ні. Розрахунки DiaLens мають лише ознайомчий характер. Якщо у вас є хронічні захворювання (наприклад, діабет), завжди звіряйтеся з медичними приладами.")

            FaqItem("Чи працює додаток без інтернету?",
                "Ні, для аналізу зображення ми використовуємо потужні сервери Gemini AI, тому активне підключення обов'язкове.")
        }
    }
}

@Composable
fun DisclaimerSection() {
    Surface(
        color = Color(0xFFFFF3E0), // Світло-оранжевий фон для уваги
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFFFB74D))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100))
                Spacer(Modifier.width(8.dp))
                Text("ВАЖЛИВО", fontWeight = FontWeight.ExtraBold, color = Color(0xFFE65100))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "DiaLens — це AI-помічник, а не медичний пристрій. Розрахунки КБЖВ (калорії, білки, жири, вуглеводи) та ХО (хлібні одиниці) базуються на візуальній оцінці та можуть містити похибки. \n\n" +
                        "Використовуйте отримані дані лише для загального контролю раціону. Розробник не несе відповідальності за наслідки використання цих даних у медичних цілях.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = Color.Black
            )
        }
    }
}

@Composable
fun FaqItem(question: String, answer: String) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(question, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2E7D32))
        Spacer(Modifier.height(4.dp))
        Text(answer, fontSize = 14.sp, color = Color.LightGray)
        Divider(Modifier.padding(top = 12.dp), thickness = 0.5.dp, color = Color.LightGray)
    }
}