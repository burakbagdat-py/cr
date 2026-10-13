plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.kriptoradar"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kriptoradar"
        minSdk = 26
        targetSdk = 35
        // CI her derlemede artan sürüm numarası verir; uygulama içi güncelleme bunu karşılaştırır.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 2
        versionName = System.getenv("VERSION_NAME") ?: "1.1.0"
    }

    signingConfigs {
        // Güncellemelerin eski sürümün üzerine kurulabilmesi için tüm sürümler aynı anahtarla imzalanmalı.
        // Varsayılan olarak repodaki anahtar kullanılır; GitHub Secrets ile kendi anahtarınızı verebilirsiniz.
        create("release") {
            val custom = System.getenv("KEYSTORE_FILE")
            storeFile = if (custom != null) file(custom) else rootProject.file("keystore/release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "kriptoradar"
            keyAlias = System.getenv("KEY_ALIAS") ?: "kriptoradar"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "kriptoradar"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        // Lint uyarıları raporlanır ama APK üretimini durdurmaz
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
}
