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

নোটিশ: Apache-2.0 লাইসেন্সের শর্তমতো এই উপাদানগুলোর লাইসেন্স ও কপিরাইট নোটিশ বজায় থাকবে। যে মডেল/ডেটা আপনি পরে নিজে বসাবেন, তার লাইসেন্স আপনাকেই যাচাই করতে হবে।
**এড়ানো হয়েছে:** MuPDF (AGPL), Gemma 3n (HF-এ gated), Meta MMS-TTS বাংলা (`facebook/mms-tts-ben`: HF API-তে যাচাইকৃত লাইসেন্স `cc-by-nc-4.0`, অর্থাৎ অবাণিজ্যিক-শর্তযুক্ত; ডিফল্ট করা হয়নি — ব্যক্তিগত ব্যবহারে চলবে কি না আপনি সিদ্ধান্ত নিন)।
