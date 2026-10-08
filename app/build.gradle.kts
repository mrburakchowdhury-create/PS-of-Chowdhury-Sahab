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
        versionName = "0.3.0-" + (System.getenv("GITHUB_SHA")?.take(7) ?: "local")   // shown on the lock screen so the owner can tell which build is installed
        // Galaxy S24 Ultra is arm64. `-Pemu` additionally packs x86_64 so the same code can be smoke-tested on an x86 emulator.
        ndk { abiFilters += if (project.hasProperty("emu")) listOf("arm64-v8a", "x86_64") else listOf("arm64-v8a") }
    }
    // SIGNING. The app's identity is its signing key; a new key means "App not installed" on update, so the identity must stay stable.
    //  * "release" uses YOUR private key when RELEASE_KEYSTORE_FILE / RELEASE_KEYSTORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD are set (CI: repository secrets, see docs/BUILD.md).
    //  * Without them (local builds, CI before the secrets exist) the release APK is signed with the committed, NON-SECRET interim key "shared" so that it still installs over the earlier
    //    test builds (same key). That interim key is public: anyone could sign an APK that replaces this app. Move to your private key before putting real data in the app.
    signingConfigs {
        create("shared") { storeFile = file("debug-shared.keystore"); storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android" }
        val ks = System.getenv("RELEASE_KEYSTORE_FILE")
        if (!ks.isNullOrBlank()) create("private") {
            storeFile = file(ks); storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD"); keyAlias = System.getenv("RELEASE_KEY_ALIAS"); keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("shared") }
        release {
            isMinifyEnabled = true; isShrinkResources = true; isDebuggable = false
            signingConfig = signingConfigs.findByName("private") ?: signingConfigs.getByName("shared")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    // androidx.graphics-path ships a native lib that fails the 16 KB RELRO check; the app carries its own source-rebuilt copy in src/main/jniLibs and that one must win.
    packaging { jniLibs { pickFirsts += "**/libandroidx.graphics.path.so" } }
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
    // Tesseract4Android 4.9.0 rebuilt from source with NDK r29 + 16 KB linker flags (the published AAR fails the RELRO-end alignment check). See third_party/README.md.
    implementation(files("../third_party/tesseract4android/tesseract4android-4.9.0-ps16k.aar"))
    implementation("androidx.annotation:annotation:1.9.1")
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
