# অবস্থা — ধাপ ১ (ভিত্তি)

## পরিবেশ (এই সেশনে যাচাই করা)
| বিষয় | ফল |
|---|---|
| Java 21, Gradle 8.14 | আছে |
| Android SDK / NDK | **নেই** |
| dl.google.com, maven.google.com, Maven Central, services.gradle.org, plugins.gradle.org, huggingface.co | **নেটওয়ার্ক নীতিতে ব্লক (403)** |
| সিদ্ধান্ত | এখানে APK বিল্ড, মডেল ডাউনলোড বা checksum যাচাই **সম্ভব নয়**। কোড লেখা সম্ভব; কম্পাইল প্রমাণ GitHub Actions থেকে নেওয়া হবে (`.github/workflows/android.yml`)। |

## অবস্থা-তালিকা
**তৈরি (কম্পাইল ও ডিভাইস-পরীক্ষা বাকি):**
- Kotlin + Compose প্রকল্প; merged manifest-এ INTERNET নেই (CI-তে স্বয়ংক্রিয় যাচাই আছে)
- PIN (PBKDF2-SHA256, salt, ৩ বার ভুলের পর ৩০ সে. থেকে ১ ঘণ্টা পর্যন্ত বাড়তি অপেক্ষা; কখনো ডেটা মোছে না)
- Strong বায়োমেট্রিক (BiometricPrompt, BIOMETRIC_STRONG) বা PIN
- ৩০ সেকেন্ড পেছনে থাকলে/স্ক্রিন অফ হলে লক; FLAG_SECURE (স্ক্রিনশট/recents লুকানো)
- ফাইল import → AES-256-GCM streaming এনক্রিপশন (Tink + Android Keystore), ইনডেক্সও এনক্রিপ্টেড; মূল ফাইল অপরিবর্তিত
- মডেল ফাইল manual import + কপির সময় SHA-256 গণনা
- backup/device-transfer থেকে সব বাদ; `allowBackup=false`

**জানা সীমা (ধাপ ১):**
- বায়োমেট্রিক এখন Keystore key-এর সঙ্গে cryptographically বাঁধা নয় (শুধু UI গেট)। পরের ধাপে CryptoObject দিয়ে বাঁধব।
- PIN ভুলে গেলে recovery এখনো নেই (ধাপ ৫-এ এনক্রিপ্টেড offline recovery)।
- PIN-লকের সময় ঘড়ির ওপর নির্ভর করে; ফোনের ঘড়ি বদলালে অপেক্ষা এড়ানো যায়।
- ফাইল format parser, OCR, AI, TTS/STT — **কিছুই এখনো নেই**; UI-তে তা-ই লেখা আছে।

**ব্লকড (আপনার সিদ্ধান্ত/নেটওয়ার্ক দরকার):** নিচের "পরবর্তী ধাপ" দেখুন।

## পরবর্তী ধাপ
1. CI-র প্রথম build ঠিক করা (dependency version যাচাই হয়নি — আন্দাজ নয়, CI-ই প্রমাণ দেবে)।
2. স্থানীয় AI runtime ও মডেল বাছাই — সরকারি উৎস, লাইসেন্স, আসল ফাইল সাইজ ও checksum যাচাই করতে `huggingface.co`/`kaggle.com` ইত্যাদির নেটওয়ার্ক অ্যাক্সেস লাগবে।
3. PDF extraction + page mapping + persistent job queue (ধাপ ২)।

## নির্মাণ (কম্পিউটারে)
Android Studio (বা JDK 17 + Android SDK 35) → `cd android-app && gradle assembleDebug` → `app/build/outputs/apk/debug/*.apk`।
এটি **debug** বিল্ড; signed release পরে, কী আপনার কাছেই থাকবে।
