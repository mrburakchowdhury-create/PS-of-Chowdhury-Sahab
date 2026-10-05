# অবস্থা (শেষ হালনাগাদ: ২০২৬-১০-০৫)

প্রতিটি আইটেম চারভাগে: **তৈরি** (কোড আছে) · **যাচাইকৃত** (এই সেশনে বাস্তবে চালিয়ে প্রমাণ পেয়েছি) · **ডিভাইস-টেস্ট বাকি** (S24 Ultra-তে চালানো হয়নি) · **ব্লকড**।

## পরিবেশ (বাস্তবে যাচাই)
Java 21; Android SDK (cmdline-tools 16111833, sha1 মিলেছে; platform 35/36/37.0; build-tools 35.0.0); Gradle wrapper 9.8.0 (+ distributionSha256Sum); AGP 9.4.1, Kotlin 2.4.20, Compose BOM 2026.09.00, Tink 1.23.0 — সব Maven metadata থেকে stable যাচাই করা। নেটওয়ার্ক খোলা (HF, Maven, Google)।

## বিল্ড ও টেস্ট
- ✅ যাচাইকৃত: `./gradlew assembleDebug` সফল (debug APK ~35 MB, `app-debug.apk`); merged manifest-এ **INTERNET নেই** (শুধু USE_BIOMETRIC, USE_FINGERPRINT)।
- ✅ যাচাইকৃত: ১৭টি JVM/Robolectric টেস্ট পাস (PinStore ৩, নিষ্কাশন+job ৭, অনুবাদ-job/chunker/prompt/মডেল-verdict ৭)।
- ⏳ ডিভাইস-টেস্ট বাকি: ফোনে ইনস্টল, বায়োমেট্রিক, Keystore/Tink Vault (Robolectric-এ Android Keystore নেই, তাই Vault-এর আসল এনক্রিপশন এখানে চলেনি; টেস্টে সাধারণ Tink কী ব্যবহার হয়েছে)।
- ❌ release APK/signing: **এখনো নয়**।

## ধাপ ১ — ভিত্তি
- তৈরি+যাচাইকৃত(কম্পাইল): PIN (PBKDF2, ক্রমবর্ধমান অপেক্ষা, ডেটা মোছে না — টেস্ট পাস), FLAG_SECURE, ইনঅ্যাক্টিভিটি লক, এনক্রিপ্টেড Vault, মডেল import।
- ডিভাইস-টেস্ট বাকি: উপরের সবটার আসল ফোনে আচরণ।
- জানা সীমা: বায়োমেট্রিক এখনো Keystore CryptoObject-এ বাঁধা নয়; PIN-recovery নেই; PIN-লক ঘড়ির ওপর নির্ভর করে।

## ধাপ ২ — PDF
- তৈরি+যাচাইকৃত (synthetic): `PdfExtractor` (pdfbox-android 2.0.27.0, পৃষ্ঠা-প্রতি, temp-file বাফার, password/malformed শনাক্ত), `JobState/JobStore` (এনক্রিপ্টেড চেকপয়েন্ট, atomic write, রিস্টার্টে RUNNING→PAUSED), `ExtractionJob` (pause/cancel/resume, সম্পন্ন পৃষ্ঠা পুনরায় নয়), coverage report, printed page label পড়া।
- যাচাই ফল (build-machine JVM, **ফোন নয়**): synthetic ৫০০ পৃষ্ঠা ≈ ০.৭ সে., ১০০০ পৃষ্ঠা ≈ ১.৬ সে.; ফাঁকা পৃষ্ঠা UNREADABLE হিসেবে ধরা পড়ে, বানানো হয় না।
- ⏳ বাকি/ব্লকড-নয়-তবে-অসম্পূর্ণ: **OCR এখনো নেই** (স্ক্যান PDF-এ UNREADABLE দেখায়)। Tesseract4Android (JitPack, 4.8.0) প্রার্থী; আরবি/উর্দু/বাংলা মান যাচাই হয়নি। প্রকৃত বই ও স্ক্যান PDF, বিভিন্ন encoding/font-এ pdfbox-এর আচরণ যাচাই হয়নি। Page-সংখ্যা ও ভাষা-ভিত্তিক RTL ক্রম যাচাই বাকি।
- ⏳ UI: PDF import থেকে নিষ্কাশন চালানোর বাটন/অগ্রগতি স্ক্রিন এখনো অ্যাপে যুক্ত নয়।

## ধাপ ৩ — মডেল ও অনুবাদ
- ✅ যাচাইকৃত (বাস্তবে নামিয়ে): Gemma 4 E4B ও E2B `.litertlm` — লাইসেন্স Apache-2.0, সাইজ ও SHA-256 নিজে হিসাব করে মিলেছে → `docs/MODELS.md`।
- ✅ যাচাইকৃত (ডেস্কটপ CPU, ফোন নয়): আসল E4B মডেলে ইংরেজি/আরবি/উর্দু/হিন্দি→বাংলা নমুনা ও কোড-ব্যাখ্যা ঠিকঠাক চলেছে; প্রম্পট-ইনজেকশন নমুনায় নির্দেশ মানা হয়নি → `docs/MODEL_EVAL.md`। ছোট নমুনা, চূড়ান্ত মানের প্রমাণ নয়।
- তৈরি (কম্পাইল হয়, ফোনে চলেনি): `LocalLlm` (LiteRT-LM 0.17.1 wrapper, load/cancel/close), `Prompts`, `Chunker`, `TranslationJob` (fake translator-এ লজিক যাচাইকৃত), `KnownModels` (import-এ official checksum মিলানো — UI-তে এখনো দেখানো হয়নি)।
- ⏳ ডিভাইস-টেস্ট বাকি: S24 Ultra-তে লোড সময়, tokens/sec, peak RAM, তাপ। (মডেল-কার্ডের সংখ্যা S26 Ultra-র, আপনার ফোনের নয়।)
- বাকি: অনুবাদ-job-এর UI/glossary, পাশাপাশি original-অনুবাদ viewer, TXT/DOCX/PDF export, কোড diff UI।

## ধাপ ৪–৬ — এখনো **শুরু হয়নি**
TTS/STT, ভয়েস কমান্ড, grounded Q&A, ব্যাকগ্রাউন্ড প্লেব্যাক, recovery/backup, export, কোড-স্ক্রিনশট OCR, release signing, ব্যবহারবিধি, ডিভাইস-টেস্ট চেকলিস্ট। কোনোটিকে সম্পন্ন বলা হচ্ছে না।

## পরবর্তী ধাপ (ক্রম)
1. অ্যাপে import→নিষ্কাশন→অনুবাদ UI যুক্ত করা (বাটন, অগ্রগতি, pause/resume/cancel, coverage)।
2. OCR (Tesseract4Android) যাচাই: ara/urd/ben/eng traineddata লাইসেন্স+সাইজ+checksum।
3. TTS/STT প্রার্থী যাচাই (ডিভাইসের অফলাইন বাংলা voice; sherpa-onnx)।
4. ডিভাইস-টেস্ট চেকলিস্ট ও release signing (কী আপনার কাছে)।
