plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.personal.banglaai"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.personal.banglaai"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-stage1"
    }
    buildTypes {
        release { isMinifyEnabled = true; isDebuggable = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")   // BiometricPrompt needs FragmentActivity
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("com.google.crypto.tink:tink-android:1.15.0")
}
