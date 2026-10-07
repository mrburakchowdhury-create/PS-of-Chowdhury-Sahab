# স্থাপত্য (বাস্তবে যা আছে)

```
MainActivity ── Compose UI (ui/*): App(lock) → Home → DocScreen[Reader, Q&A] / CodeScreen
security/  PinStore (PBKDF2, delay) · BiometricGate (Keystore CryptoObject, invalidates on new enrollment)
storage/   Vault (Tink AES-GCM-HKDF streaming + Keystore master key) · ModelStore · KnownModels (official sha256) · StorageStats
doc/       PdfExtractor (pdfbox-android, per page) · ExtractionJob · PageTextStore · DocWorkflow (glue; explicit buttons only)
jobs/      JobState (per-page status, coverage) · JobStore (encrypted, atomic checkpoints; RUNNING→PAUSED after restart)
ocr/       TesseractOcr (bundled tessdata, sha256-verified) · OcrJob (only UNREADABLE pages) · CodeReconstructor (indent from boxes)
ai/        LocalLlm (LiteRT-LM, Gemma 4) · Prompts (<DOC> data wrapping) · Chunker · TranslationJob · Bm25Index · CodeDiff
speech/    TtsReader (offline Bengali voice only) · VoiceInput (on-device recognizer only) · VoiceCommands · ReadingPlan
importer/ OfficeImporter (DOCX, PPTX, XLSX, EPUB, CSV; no macros/formulas; zip-bomb limits)
userdata/  SealedList · Glossary · Marks (encrypted)
export/    Exporter (TXT, DOCX) · PdfExport (Android text stack)
backup/    PassphraseBackup (PBKDF2 → Tink streaming, authenticated header) · BackupService
```
## নীতি (কোডে প্রয়োগ করা)
- INTERNET permission নেই; `ManifestSecurityTest` + CI + `sign_release.sh` merged manifest-এ নজর রাখে।
- মডেল ও OCR/TTS/STT কোথাও cloud fallback নেই: voice না পেলে বা ভাষা-প্যাক না থাকলে বার্তা দেখায়।
- একসঙ্গে একটি ভারী মডেল: `LocalLlm` ব্যবহারের শেষে `close()`; OCR শেষে `close()`; স্ক্রিন ছাড়লে TTS/মাইক মুক্ত।
- সব দীর্ঘ কাজ বাটন-চালিত, চেকপয়েন্টসহ; নিজে থেকে পুনরায় শুরু হয় না।
- ডকুমেন্টের লেখা `<DOC>` ট্যাগে তথ্য হিসেবে যায়; ভেতরের `</DOC>` নিষ্ক্রিয় করা হয়; মডেলের কোনো টুল/নেটওয়ার্ক নেই।
## এখনো নেই
ছবি-ফাইল import, নির্বাচিত-শব্দ প্রশ্ন, semantic embedding অনুসন্ধান, SQLCipher।
