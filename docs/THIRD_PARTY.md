# থার্ড-পার্টি উপাদান ও লাইসেন্স (POM/অফিসিয়াল রিপো থেকে পড়া)

| উপাদান | সংস্করণ | লাইসেন্স | কীভাবে যাচাই |
|---|---|---|---|
| LiteRT-LM (`litertlm-android`) | 0.17.1 | Apache-2.0 | Maven POM |
| Gemma 4 E4B / E2B `.litertlm` | — | Apache-2.0 | HuggingFace মডেল-ট্যাগ `license:apache-2.0` (docs/MODELS.md) |
| pdfbox-android | 2.0.27.0 | Apache-2.0 | Maven POM |
| Tink (`tink-android`) | 1.23.0 | Apache-2.0 | Maven POM |
| Tesseract4Android | 4.8.0 | Apache-2.0 | রিপোর LICENSE ফাইল (POM-এ লাইসেন্স লেখা নেই) |
| Tesseract OCR (ইঞ্জিন, Tesseract4Android-এর ভেতরে) | 5.x | Apache-2.0 | (অনুমান নয়: upstream LICENSE; তবে আলাদা করে ফাইল-স্তরে যাচাই হয়নি) |
| tessdata_fast (`eng, ben, ara, urd, hin`) | main@2026-10-05 | Apache-2.0 | রিপোর LICENSE ফাইল; ফাইলের SHA-256 `OCR_EVAL.md`/`TesseractOcr.kt`-এ |
| AndroidX (biometric, compose, activity, fragment), kotlinx-coroutines | docs/BUILD.md | Apache-2.0 | Maven POM |
| অ্যাপে বান্ডেল করা ৮টি বই (`assets/library/`) | — | সর্বজনীন-ডোমেইন (পুরোনো অনুবাদ) | The Prince, Art of War, On War, Discourses, The Crowd, Callières — Project Gutenberg; Gracián (Jacobs ১৮৯২) ও Arthashastra (Shamasastry ১৯১৫) — Internet Archive স্ক্যান। **আর্থশাস্ত্র ফাইলটি ১৯৫১-র মহীশূর ৪র্থ সংস্করণ:** ১৯১৫-র অনুবাদ সর্বজনীন, তবে ১৯৫১-র সংযোজিত ভূমিকা/নোটের স্বত্ব যাচাই বাকি। Gutenberg-এর লাইসেন্স-বয়লারপ্লেট বাদ দিয়ে শুধু বইয়ের লেখা রাখা হয়েছে |
| জ্ঞানভাণ্ডারের ৬১টি কার্ড (`assets/knowledge/`) | — | নিজস্ব লেখা (সাধারণ জ্ঞানভিত্তিক সারাংশ) | কোনো বই/নথি থেকে অনুলিপি নয়; `ref` ঘরে শুধু সাধারণ সূত্রের নাম — নির্দিষ্ট নথি/বছর/লিংক যাচাই করা হয়নি |

নোটিশ: Apache-2.0 লাইসেন্সের শর্তমতো এই উপাদানগুলোর লাইসেন্স ও কপিরাইট নোটিশ বজায় থাকবে। যে মডেল/ডেটা আপনি পরে নিজে বসাবেন, তার লাইসেন্স আপনাকেই যাচাই করতে হবে।
**এড়ানো হয়েছে:** MuPDF (AGPL), Gemma 3n (HF-এ gated), Meta MMS-TTS বাংলা (`facebook/mms-tts-ben`: HF API-তে যাচাইকৃত লাইসেন্স `cc-by-nc-4.0`, অর্থাৎ অবাণিজ্যিক-শর্তযুক্ত; ডিফল্ট করা হয়নি — ব্যক্তিগত ব্যবহারে চলবে কি না আপনি সিদ্ধান্ত নিন)।

**0.3.0-এ যোগ/পরিবর্তন:** Tesseract4Android এখন source থেকে rebuild করা 4.9.0 (`third_party/tesseract4android/`, Apache-2.0, 16 KB-এর জন্য NDK r29 + linker flags); androidx `graphics-path`-এর JNI .so AOSP source থেকে rebuild (`third_party/graphics-path/`, Apache-2.0); tessdata_fast ফাইল `assets/tessdata/` (Apache-2.0, `LICENSE.txt` সহ, upstream `main`-এর সঙ্গে SHA-256 মিলিয়ে দেখা)।
