# স্থাপত্য ও উপাদান-প্রার্থী (যাচাই বাকি)

> নিচের সবই **প্রার্থী**। নেটওয়ার্ক ব্লক থাকায় লাইসেন্স, ফাইল সাইজ, checksum ও বাংলা মান এখনো যাচাই হয়নি; কোনো সংখ্যা বা লিংক বানানো হয়নি।

| কাজ | প্রার্থী | যাচাই বাকি |
|---|---|---|
| বাংলা অনুবাদ/ব্যাখ্যা/কোড | Gemma-পরিবারের on-device ভ্যারিয়েন্ট (LiteRT/MediaPipe LLM) অথবা llama.cpp + GGUF | লাইসেন্স শর্ত, S24 Ultra-তে গতি/RAM, আরবি/উর্দু→বাংলা মান |
| OCR | ML Kit (লাতিন, দেবনাগরী) — **আরবি/উর্দু/বাংলা সমর্থন নেই বলে জানা আছে**; তাই Tesseract (Android বাইন্ডিং) + `ara`, `urd`, `ben` ডেটা | লাইসেন্স, নির্ভুলতা |
| PDF টেক্সট | pdfbox-android (Apache-2.0); রেন্ডার: android PdfRenderer। MuPDF বাদ (AGPL) | বড় ফাইলে স্ট্রিমিং |
| বাংলা TTS | ডিভাইসের offline বাংলা voice (Google/Samsung TTS) অথবা sherpa-onnx + বাংলা মডেল | offline voice আছে কি, মডেল লাইসেন্স |
| STT (বাংলা) | whisper.cpp-ধরনের স্থানীয় মডেল / Vosk | বাংলা মান |
| এনক্রিপশন | Tink streaming AEAD + Keystore (করা হয়েছে); ডাটাবেস: SQLCipher (পরের ধাপ) | — |

নীতি: মডেল একসঙ্গে লোড হবে না; job queue checkpoint-সহ; কোনো cloud fallback নয়।
