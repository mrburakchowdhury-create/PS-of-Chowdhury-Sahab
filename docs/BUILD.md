# বিল্ড নির্দেশ (পুনরুৎপাদনযোগ্য)

এই সেশনে বাস্তবে চালানো পরিবেশ: Linux x86_64, JDK 21 (CI-তে 17 যথেষ্ট), Android SDK cmdline-tools `16111833`
(sha1 `e025545c62a8e64c7559119566a569fb1dec5f60` — Google-এর `repository2-3.xml`-এর সঙ্গে মিলেছে)।

## SDK
```
sdkmanager "platforms;android-37.0" "build-tools;35.0.0" "platform-tools"
export ANDROID_HOME=<sdk path>
```
(`minSdk = 31` (Android 12+; S24 Ultra-র জন্য যথেষ্ট), `compileSdk = 37` — কারণ androidx.core 1.18 ইত্যাদি এটি দাবি করে; `targetSdk = 35`, `minSdk = 29`।)

## বিল্ড ও টেস্ট
```

./gradlew testDebugUnitTest      # ৫৪টি JVM/Robolectric টেস্ট
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
`adb install -r ChoudhurySahebsPS-release.apk` অথবা ফাইলটি ফোনে নিয়ে "অজানা উৎস থেকে ইনস্টল" অনুমতি দিয়ে খুলুন।

## আসল-Android পরীক্ষা (instrumented)
`app/src/androidTest/` — `SmokeCoreTest` (Keystore + Tink Vault, PDF নিষ্কাশন, অধ্যায়, এক্সপোর্ট TXT/DOCX/PDF, ব্যাকআপ→মুছে→ফেরত, Tesseract-এর আসল native লাইব্রেরি, PIN, বায়োমেট্রিক-কী) ও `SmokeUiTest` (প্রথম চালু → PIN → হোম → সহকারী → ফোন-পরীক্ষা)।
চালাতে: ফোন USB-তে (developer options + USB debugging) বা KVM-সহ ইমুলেটরে `./gradlew connectedDebugAndroidTest` (x86_64 ইমুলেটরে `-Pemu` যোগ করুন)।
**এগুলো এখনো কোনো আসল Android-এ চালানো হয়নি**: এই বিল্ড-মেশিনে KVM নেই; সফটওয়্যার-ইমুলেটরে Android 14 প্রতিবার watchdog-এ ভেঙে পড়েছে।


## Release সই (প্রাইভেট key)
CI-র release APK প্রাইভেট key দিয়ে সই হয় যদি এই repository secrets থাকে: `RELEASE_KEYSTORE_B64` (keystore ফাইলের base64), `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`। key বানানো: `tools/sign_release.sh`-এর মন্তব্যে `keytool` কমান্ড দেওয়া আছে; key, পাসওয়ার্ড কখনো repo বা চ্যাটে দেবেন না। secrets না থাকলে APK আগের পাবলিক "PS Debug" key-তে সই হয় (আপডেট বসে, কিন্তু key গোপন নয়)। **key বদলালে ফোনে আগের অ্যাপ আনইনস্টল করতে হয়** (অ্যান্ড্রয়েডের নিয়ম) — তাই সত্যিকারের ডেটা রাখার আগেই একবার প্রাইভেট key-তে সরে যান।

## নেটিভ লাইব্রেরি (16 KB) ও APK-যাচাই
`third_party/README.md` দেখুন। যাচাই: `python3 tools/check_elf16k.py <apk>` ও `python3 tools/check_apk.py <release apk> <aapt2> <apksigner>`।
