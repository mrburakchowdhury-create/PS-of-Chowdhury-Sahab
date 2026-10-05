# বিল্ড নির্দেশ (পুনরুৎপাদনযোগ্য)

এই সেশনে বাস্তবে চালানো পরিবেশ: Linux x86_64, JDK 21 (CI-তে 17 যথেষ্ট), Android SDK cmdline-tools `16111833`
(sha1 `e025545c62a8e64c7559119566a569fb1dec5f60` — Google-এর `repository2-3.xml`-এর সঙ্গে মিলেছে)।

## SDK
```
sdkmanager "platforms;android-37.0" "build-tools;35.0.0" "platform-tools"
export ANDROID_HOME=<sdk path>
```
(`compileSdk = 37` — কারণ androidx.core 1.18 ইত্যাদি এটি দাবি করে; `targetSdk = 35`, `minSdk = 29`।)

## বিল্ড ও টেস্ট
```
cd android-app
./gradlew testDebugUnitTest      # ৪৫+ JVM/Robolectric টেস্ট
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk  (≈ 88 MB, debug)
./gradlew assembleRelease        # app-release-unsigned.apk (≈ 47 MB, arm64-v8a, R8 minified)
```
Gradle wrapper 9.8.0 (`distributionSha256Sum` বসানো), AGP 9.4.1, Kotlin 2.4.20 (Compose compiler plugin), Compose BOM 2026.09.00 — প্রতিটি Maven metadata থেকে stable যাচাই করা।
Tesseract4Android শুধু JitPack-এ আছে; `settings.gradle.kts`-এ JitPack কেবল `com.github.adaptech-cz*` গ্রুপের জন্য সীমাবদ্ধ।

## সই (signing) — আপনার চাবি আপনার কাছেই
```
keytool -genkeypair -v -keystore ~/banglaai-release.jks -alias banglaai -keyalg RSA -keysize 4096 -validity 9125
ANDROID_HOME=... tools/sign_release.sh ~/banglaai-release.jks banglaai
```
স্ক্রিপ্ট zipalign + apksigner করে, `apksigner verify` চালায়, এবং INTERNET permission পেলে ব্যর্থ হয়। কী/পাসওয়ার্ড কখনো রিপোতে বা চ্যাটে দেবেন না; হারালে অ্যাপ আপডেট করা যাবে না।
এই সেশনে সই-প্রক্রিয়া একটি **অস্থায়ী** চাবিতে যাচাই হয়েছে (`apksigner verify` → v3 পাস); সেই চাবি মুছে ফেলা হয়েছে। **কোনো signed release APK এই রিপোতে নেই।**

## ফোনে ইনস্টল
`adb install -r BanglaAI-release.apk` অথবা ফাইলটি ফোনে নিয়ে "অজানা উৎস থেকে ইনস্টল" অনুমতি দিয়ে খুলুন।
