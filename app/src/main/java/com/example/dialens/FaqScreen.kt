package com.example.dialens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource // НЕ ЗАБУДЬ ЦЕЙ ІМПОРТ
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.faq_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_button_desc)
                        )
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
            DisclaimerSection()

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.faq_title_label),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            // Питання 1
            FaqItem(
                stringResource(R.string.faq_q1_title),
                stringResource(R.string.faq_q1_answer)
            )

            // Питання 2
            FaqItem(
                stringResource(R.string.faq_q2_title),
                stringResource(R.string.faq_q2_answer)
            )

            // Питання 3
            FaqItem(
                stringResource(R.string.faq_q3_title),
                stringResource(R.string.faq_q3_answer)
            )

            // Питання 4
            FaqItem(
                stringResource(R.string.faq_q4_title),
                stringResource(R.string.faq_q4_answer)
            )
        }
    }
}

@Composable
fun DisclaimerSection() {
    Surface(
        color = Color(0xFFFFF3E0),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFFFB74D))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.warning_title),
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFE65100)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.disclaimer_content),
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
        Text(answer, fontSize = 14.sp, color = Color.Gray) // Змінив на Gray для кращої читабельності на білому фоні
        Divider(Modifier.padding(top = 12.dp), thickness = 0.5.dp, color = Color.LightGray)
    }
}