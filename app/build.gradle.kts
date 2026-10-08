plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.personal.banglaai"
    compileSdk = 37
    defaultConfig {
        applicationId = "app.personal.banglaai"
        minSdk = 31
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()   // CI run number: every build is a newer version, so it installs as an update
        versionName = "0.2.0-" + (System.getenv("GITHUB_SHA")?.take(7) ?: "local")   // shown on the lock screen so the owner can tell which build is installed
        // Galaxy S24 Ultra is arm64. `-Pemu` additionally packs x86_64 so the same code can be smoke-tested on an x86 emulator.
        ndk { abiFilters += if (project.hasProperty("emu")) listOf("arm64-v8a", "x86_64") else listOf("arm64-v8a") }
    }
    // Test (debug) builds are signed with ONE fixed, non-secret key kept in the repo (password "android", never used for the real release), so every
    // new test APK installs over the previous one. A different key per CI run is what produced "App not installed" on update.
    signingConfigs { create("shared") { storeFile = file("debug-shared.keystore"); storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android" } }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("shared") }
        release {
            isMinifyEnabled = true; isShrinkResources = true; isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.fragment:fragment-ktx:1.9.1")   // BiometricPrompt needs FragmentActivity
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("com.google.crypto.tink:tink-android:1.23.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.github.adaptech-cz.Tesseract4Android:tesseract4android:4.8.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
