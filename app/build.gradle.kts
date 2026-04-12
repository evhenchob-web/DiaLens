import java.util.Properties // ПРЯМИЙ ІМПОРТ, ЩОБ НЕ БУЛО ПОМИЛКИ З UTIL

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")
}

// Тепер використовуємо Properties() напряму без префікса java.
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}
val geminiKey = localProperties.getProperty("GEMINI_API_KEY") ?: ""

android {
    namespace = "com.example.dialens"
    compileSdk = 36 // БУЛО 35, СТАВИМО 36
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        applicationId = "com.example.dialens"
        minSdk = 24
        targetSdk = 36 // БУЛО 35, СТАВИМО 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Правильне екранування ключа
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
    }

    buildTypes {
        release {
            // Вмикаємо стиснення та очищення коду
            isMinifyEnabled = true
            // Вмикаємо видалення невикористовуваних ресурсів
            isShrinkResources = true

            // Стандартні правила ProGuard
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Вказуємо назву для релізного білду (опціонально)
            signingConfig = signingConfigs.getByName("debug") // Поки що ставимо debug, пізніше замінимо на release
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation("androidx.exifinterface:exifinterface:1.3.6")
    implementation(libs.androidx.compose.foundation)
    val roomVersion = "2.7.0-alpha13" // Або "2.7.0-beta01", якщо вже доступна

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    implementation("com.google.ai.client.generativeai:generativeai:0.7.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.0")


    val camerax_version = "1.3.1"
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}