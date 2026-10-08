# প্রয়োজনীয়তা-ম্যাট্রিক্স (চাহিদা → কোথায় → অবস্থা → প্রমাণ → বাকি)

উৎস: `docs/REQUIREMENTS.md` (আপনার মূল নির্দেশনা §১–১৭) + সর্বশেষ নির্দেশনা (§১–১৪) + বর্তমান source। এই ফাইল প্রতিটি সংশোধনের সঙ্গে হালনাগাদ হয়।
**অবস্থার চিহ্ন (আলাদা রাখা হয়েছে):**
- ✅ **কোড + JVM/CI-যাচাই** — কোড আছে এবং কম্পিউটারে (JVM/Robolectric/স্ক্রিপ্ট/CI) স্বয়ংক্রিয় টেস্ট পাস। *এটি Android-ডিভাইসে চালানোর প্রমাণ নয়।*
- 📱 **কোড আছে, ডিভাইসে যাচাই বাকি** — লিখিত ও কম্পাইলকৃত; আসল ফোন/এমুলেটরে চলেনি।
- 🟡 **আংশিক/অসম্পূর্ণ** — কিছু অংশ কাজ করে, বাকি অংশ নেই (নিচে "বাকি" কলামে)।
- ⛔ **পরিবেশে আটকে (BLOCKED)** — এই সেশনের পরিবেশ (KVM নেই, মডেল ফাইল নেই, আপনার ফোন নেই) পরীক্ষা আটকে দিয়েছে।
- ❌ **নেই** — এখনো বাস্তবায়িত নয়।
- 🆕 **এই রাউন্ডে যোগ/সংশোধন** — কমিট-ইতিহাসে দেখুন।

## A. ভিত্তি, ফোন, ভাষা (§১–৩)
| # | চাহিদা | কোথায় | অবস্থা | প্রমাণ | বাকি |
|---|---|---|---|---|---|
| A1 | একক-ব্যবহারকারীর ব্যক্তিগত অফলাইন সহকারী, সব UI বাংলা | `ui/*` | ✅ | ScreenshotTest (১২ পর্দা) | — |
| A2 | লক্ষ্যফোন S24 Ultra, বর্তমান free RAM/storage মাপা | `ui/DiagnosticsScreen`, `doc/ResourceGuard`, `storage/StorageStats` | 📱 | ResourceGuard JVM-টেস্ট | ফোনে মাপা সংখ্যা (আপনার ফোনের "ফোন পরীক্ষা" পর্দা) |
| A3 | মূল ভাষার লেখা অক্ষত (ইংরেজি/আরবি/উর্দু/হিন্দি), target শুধু বাংলা | `ai/Prompts`, `ai/TranslationJob` | ✅ | TranslationJobTest | — |
| A4 | Kotlin/Compose বিদ্যমান আর্কিটেকচার | সমগ্র | ✅ | CI বিল্ড | — |
| A5 | বাংলা ফন্ট, RTL, টেক্সট-সাইজ, পড়ার গতি | `ui/Theme`, `ui/ReaderSection`, Exporter | 🟡 | ExporterTest (RTL docx) | UI-তে লাইট/ডার্ক পছন্দ নেই (থিম ডার্ক-নেভি; সিস্টেম-ডার্ক অনুসরণ নেই) |

## B. গোপনীয়তা, অফলাইন, খরচ (§২, নতুন §৯)
| # | চাহিদা | কোথায় | অবস্থা | প্রমাণ | বাকি |
|---|---|---|---|---|---|
| B1 | INTERNET permission নেই | `AndroidManifest.xml` | ✅ | ManifestSecurityTest + CI merged-manifest ও `tools/check_apk.py` | — |
| B2 | cloud AI/OCR/STT নেই; offline-only voice, কোনো fallback নেই | `speech/VoiceInput`, `TtsReader`, `CallSpeaker` | ✅/📱 | VoiceInputTest, VoiceInputUnavailableTest | ফোনে বাংলা অফলাইন voice-এর মান |
| B3 | analytics/telemetry/ads/remote crash নেই | সমগ্র | ✅ | manifest + dependency তালিকা (`docs/THIRD_PARTY.md`) | — |
| B4 | মডেল ম্যানুয়াল import, সঠিক সংস্করণ/আকার/checksum, বাংলা নির্দেশনা | `storage/ModelStore`, `KnownModels`, `ModelImporter` | ✅ | ModelStoreTest, ModelImportFailureTest, KnownModelsTest | E2B/E4B আসল ফোনে |
| B5 | প্রতিনিয়ত network-ডেটা-পাঠানো পরীক্ষা (SDK/IPC) | — | ⛔ | INTERNET নেই ⇒ socket খুলতে পারে না (OS-নীতি); সংযুক্ত লাইব্রেরির স্ট্যাটিক তালিকা | ডিভাইসে airplane-mode ও নেটওয়ার্ক-চালু ট্র্যাফিক-ধরা পরীক্ষা |

## C. প্রমাণীকরণ, লক, ভল্ট (§৫, নতুন §১০)
| # | চাহিদা | কোথায় | অবস্থা | প্রমাণ | বাকি |
|---|---|---|---|---|---|
| C1 | শুরুতে locked; PIN retry-delay; ভুলে ডেটা মোছে না | `security/PinStore`, `ui/App` | ✅ | PinStoreTest | — |
| C2 | Keystore-সুরক্ষিত ভল্ট, চাবি PIN-এর সঙ্গে বাঁধা | `security/KeyVault`, `storage/Vault` | ✅ (fake-Keystore) / 📱 | KeyVaultTest (upgrade, lock, session) | আসল Keystore ডিভাইসে |
| C3 | strong-biometric, enrollment-বদলে PIN-ই ভরসা | `security/BiometricGate`, `MainActivity` | 📱 | কোড; JVM-এ Keystore নেই | ফোনে আঙুলের ছাপ; অন্য কারও ছাপ থাকলে সেও খুলতে পারে (UI-তে লেখা) |
| C4 | screen-off/inactivity ⇒ UI + crypto session একসঙ্গে লক; চলমান কাজ/stream বাতিল | `security/AppLock`, `GuardedStreamingAead`, `MainActivity` | ✅ | KeyVaultTest (lock, stream, job-pause) | ডিভাইসে screen-off পরীক্ষা |
| C5 | সীমিত অনুমোদিত background পড়া | `speech/ReadAloudController.onVaultLock`, `ReadingPlan.snapshot` | ✅/📱 | ReadingPlanSnapshotTest | ডিভাইসে |
| C6 | স্ক্রিনশট ডিফল্ট বন্ধ + opt-in; recents; লক-পর্দা সর্বদা সুরক্ষিত | `MainActivity.applyScreenshotPolicy` | 📱 | কোড | ডিভাইসে recents-preview দেখা |
| C7 | notification/lock-screen-এ private লেখা/ফাইলনাম নেই | `speech/ReadAloudService` | 📱 | ManifestSecurityTest (অংশত) | ডিভাইসে |
| C8 | নথির "ignore instructions/upload/run" ডেটা, কমান্ড নয় | `ai/Prompts.sanitize`, mic-only command path | ✅ (গঠনগত) / ⛔ (মডেল-সহ বাস্তব) | AdvisorPromptTest | বাস্তব মডেলে prompt-injection পরীক্ষা (মডেল ফাইল এখানে নেই) |
| C9 | নিজের TTS/বইয়ের শব্দ নতুন নির্দেশ নয় | mic কেবল Listening-এ, speak-এর সময় বন্ধ (`call/VoiceCallLogic`) | ✅ | VoiceCallLogicTest | ডিভাইসে echo |
| C10 | আনলক/ইমপোর্টে নিজে থেকে AI-কাজ শুরু হয় না | `ui/JobRunner`, বাটন-চালিত | ✅ (কাঠামো) | কোড-পর্যালোচনা + JobRunner শুধু বাটন থেকে | — |
| C11 | temporary plaintext (pdf-*, photos) সামলানো | `DocWorkflow.extract finally`, `MainActivity` | ✅/📱 | ExtractionJobTest | ডিভাইসে |

## D. ফাইল ফরম্যাট ও মূল তথ্য (§৬, নতুন §৪)
| # | ফরম্যাট | কোথায় | অবস্থা | বাকি |
|---|---|---|---|---|
| D1 | PDF | `doc/PdfExtractor` (PDFBox-Android) | ✅ synthetic ১০০০ পৃষ্ঠা; 📱 | পাসওয়ার্ড-PDF প্রত্যাখ্যাত (কারণ জানানো); অধ্যায়/outline ✅ |
| D2 | TXT | `DocWorkflow.extractTxt`, `TextDecoding` | ✅ | কৃত্রিম ৩০০০-অক্ষর "পৃষ্ঠা" |
| D3 | DOCX | `importer/OfficeImporter` | 🟡→🆕 | শিরোনাম/টেবিল/ফুটনোট সংরক্ষণ (CP5) |
| D4 | EPUB | একই | 🟡→🆕 | অধ্যায়-শিরোনাম (CP5) |
| D5 | CSV/XLSX | একই | ✅ (শিট নাম সহ; সূত্র/ফর্মুলা চালানো হয় না, cached মান) | — |
| D6 | PPTX | একই | 🟡→🆕 | স্পিকার-নোট (CP5) |
| D7 | JPG/PNG | `extractImage`/`ocrImage`, `ImageEditScreen` | ✅ (JVM) / 📱 OCR | — |
| D8 | source-code ফাইল (৩৩ এক্সটেনশন) | `extractCode`, `OfficeImporter.codeExtensions` | ✅ | — |
| D9 | মূল ফাইল অপরিবর্তিত; আলাদা version: নিষ্কাশিত/সংশোধিত/অনুবাদ/ব্যাখ্যা/অডিও | `Vault`, `PageTextStore` ×২ | 🟡→🆕 | ব্যবহারকারী-সংশোধিত লেখা (CP3); অডিও সংরক্ষণ করা হয় না (সচেতন: স্ট্রিম-করা TTS, ক্যাশ নেই) |
| D10 | পৃষ্ঠা/শিরোনাম/অধ্যায়/টেবিল/ফুটনোট/কোড-লাইন অবস্থান | `PageText.printedLabel`, `Section.title` | 🟡→🆕 | টেবিল/ফুটনোট চিহ্ন (CP5); মূল PDF-পৃষ্ঠা-দর্শক (CP5) |
| D11 | malformed/oversized/password/unsupported পরিষ্কার বার্তা; macros/formula চালানো নয় | `OfficeImporter`, `PdfExtractor` | ✅ | ZipLimitsTest, OfficeImporterTest |

## E. বড় PDF ও অনুবাদ (§৭, §১০, নতুন §৬)
| # | চাহিদা | কোথায় | অবস্থা | প্রমাণ | বাকি |
|---|---|---|---|---|---|
| E1 | ৫০০/৬০০/১০০০ পৃষ্ঠা chunk-প্রসেসিং, RAM-এ পুরো বই নয় | `ExtractionJob`, `PdfExtractor` | ✅ (synthetic) / 📱 | ExtractionJobTest | S24-তে সময়/মেমরি মাপা |
| E2 | পূর্ণ অনুবাদ; মোট/সম্পন্ন/pending/failed/unreadable + coverage report | `TranslationJob`, `JobState.coverageReport` | ✅ | TranslationJobTest | বাস্তব মডেলে ৪ ভাষা (⛔ মডেল নেই) |
| E3 | checkpoint, pause/cancel/resume, restart-পর pending দেখানো, cancelled নিজে চালু নয় | `JobStore`, `DocScreen` | ✅ | TranslationJobTest | 🆕 হোমে "অসমাপ্ত কাজ" তালিকা (CP3) |
| E4 | কম storage/RAM/battery/তাপে নিরাপদ pause | `doc/ResourceGuard` | ✅ (সিদ্ধান্ত) / 📱 | ResourceGuardTest | ডিভাইসে |
| E5 | printed↔viewer পৃষ্ঠা-ম্যাপ | `PdfExtractor`, `PageText.printedLabel` | ✅ | ExtractionJobTest | অস্পষ্ট হলে জিজ্ঞাসা (আংশিক) |
| E6 | সংখ্যা/তারিখ/উদ্ধৃতি/সূত্র অক্ষত রাখার নির্দেশ ও glossary | `Prompts.TRANSLATE_SYSTEM`, `Glossary` | ✅ (প্রম্পট-স্তর) / ⛔ (মডেল-মান) | AdvisorPromptTest | বাস্তব মডেলে নমুনা-পরীক্ষা |
| E7 | TXT/DOCX/PDF এক্সপোর্ট (বাংলা/RTL/কভারেজ) | `export/*` | ✅ TXT/DOCX; ⛔ PDF (JVM-এ নেই) | ExporterTest, ExporterLongDocumentTest | PDF-এক্সপোর্ট ডিভাইসে; টেবিল/ফুটনোট এক্সপোর্টে বিন্যাস-মিলানো বলা হয় না |

## F. OCR ও কোড-স্ক্রিনশট (§৮, নতুন §৫)
| # | চাহিদা | কোথায় | অবস্থা | প্রমাণ | বাকি |
|---|---|---|---|---|---|
| F1 | ৫ ভাষার OCR ডেটা APK-তে (fresh install) | `assets/tessdata/`, `ocr/TesseractOcr` | ✅ ফাইল/হ্যাশ; 📱 রানটাইম | TessdataAssetsTest, CI `check_apk.py`, ডেস্কটপ Tesseract | ⛔ এমুলেটর-টেস্ট (`OcrAllLanguagesTest`) চলেনি |
| F2 | মূল ছবি + লেখা পাশাপাশি, zoom/crop/rotate, ফের তোলা | `ImageEditScreen`, `PhotoCapture` | ✅ (CropMath) / 📱 | CropMathTest | — |
| F3 | কোডের case/quotes/brackets/indent সংরক্ষণ; 0/O,1/l/I সন্দেহ-চিহ্ন | `ocr/CodeReconstructor` | ✅ | CodeReconstructorTest (আসল Tesseract box) | — |
| F4 | ছবির লাইন-নম্বর বনাম নিষ্কাশিত লাইন-নম্বর | `CodeReconstructor` | 🟡 | — | 🆕 পার্থক্য-চিহ্ন (CP2) |

## G. কোড বোঝানো (§৯)
| # | চাহিদা | কোথায় | অবস্থা | প্রমাণ | বাকি |
|---|---|---|---|---|---|
| G1 | বাংলায় উদ্দেশ্য/লাইন/function/variable/data-flow | `ai/Prompts.CODE_SYSTEM`, `ui/CodeScreen` | ✅ (প্রম্পট) / ⛔ (মডেল-মান) | AdvisorPromptTest | বাস্তব মডেলে জটিল কোড |
| G2 | মূল/প্রস্তাবিত/diff, স্বয়ংক্রিয় overwrite নেই | `ai/CodeDiff` | ✅ | CodeDiffTest | — |
| G3 | নিরাপদ parser/static check-এর আসল ফল ≠ AI-অনুমান | — | ❌→🆕 | — | `StaticCheck` (CP2) |
| G4 | syntax/logic/security/missing-context আলাদা | প্রম্পট + StaticCheck | 🟡→🆕 | — | CP2 |
| G5 | code নিজে চালানো হয় না | — | ✅ (চালানোর কোডই নেই) | কোড-পর্যালোচনা | — |

## H. পড়া, প্রশ্ন, শেখা (§১১, §১২, নতুন §৭, §৮)
| # | চাহিদা | কোথায় | অবস্থা | প্রমাণ | বাকি |
|---|---|---|---|---|---|
| H1 | বাংলা পড়া: play/pause/resume/stop/speed/prev/next, পৃষ্ঠা/অধ্যায় | `TtsReader`, `ReaderSection` | ✅ (cursor-যুক্তি) / 📱 | SpeechLogicTest | ফোনে অফলাইন বাংলা কণ্ঠ |
| H2 | কোডের আক্ষরিক পড়া ও বাংলা লাইন-ব্যাখ্যা আলাদা | — | ❌→🆕 | — | `CodeSpeech` (CP2) |
| H3 | পড়ার অংশের highlight | `ReaderSection` | ❌→🆕 | — | CP2/CP5 |
| H4 | reading position (file/page/segment) এনক্রিপ্টেড | `userdata/ReadingPosition` | ✅ | KeyVaultTest/ব্যাকআপ round-trip | কোড-লাইন অবস্থান |
| H5 | প্রশ্নের পর resume একই জায়গায় | `ReaderSection` | ✅ (লজিক) / 📱 | — | — |
| H6 | source-সহ প্রশ্নোত্তর; tap ⇒ মূল উৎস | `QaSection`, `LibraryScreen` | ✅ | Bm25/Library টেস্ট | "ছবির অংশ" উৎস নেই; PDF-পৃষ্ঠা-দর্শক (CP5) |
| H7 | উৎস-উদ্ধৃতি/অনুবাদ/অনুমান/সাধারণ ব্যাখ্যা আলাদা শিরোনাম | `Prompts.QA_SYSTEM/LIB_SYSTEM` | ✅ (প্রম্পট) | AdvisorPromptTest | মডেল মানে কিনা ⛔ |
| H8 | notes, bookmarks, vocabulary, glossary | `userdata/*`, `UserDataSections` | ✅ | UserData টেস্ট, ব্যাকআপ round-trip | — |
| H9 | saved Q&A | — | ❌→🆕 | — | CP3 |
| H10 | নির্দেশে practice questions (মূল লেখার অংশ নয়) | `StudySection`, `Prompts.practice` | ✅ | — | — |
| H11 | voice: press-to-talk, mic স্পষ্ট, transcript সংশোধন | `ReaderSection`, `VoiceInput`, `HomeVoice` | ✅/📱 | VoiceInputTest, SpeechLogicTest | ডিভাইসে |
| H12 | hands-free শুধু অনুমোদিত সেশনে; সেশন শেষে mic বন্ধ | `VoiceCallScreen` | ✅ (VoiceCallLogic) / 📱 | VoiceCallLogicTest | ডিভাইসে |
| H13 | audio cache সীমা/মোছা | — | ✅ (ক্যাশই নেই: অডিও সংরক্ষিত হয় না) | কোড | — |
| H14 | কণ্ঠ-বাছাই/গতি সেটিং | `ReaderSection` (গতি) | 🟡 | — | কণ্ঠ-বাছাই (offline বাংলা ভয়েস একটিই ধরা হয়) |

## I. জ্ঞানভাণ্ডার ও বই (নতুন §৩)
| # | চাহিদা | কোথায় | অবস্থা | প্রমাণ | বাকি |
|---|---|---|---|---|---|
| I1 | ৫ বিভাগ অক্ষত, ৬১ কার্ড = প্রাথমিক ভিত্তি | `assets/knowledge/*.json`, `knowledge/KnowledgeBase` | ✅ | KnowledgeBaseTest | গভীরতা/সূত্র-উন্নয়ন (CP4) |
| I2 | স্বাস্থ্য/ধর্ম/আইনে নির্দিষ্ট উৎস, তারিখ, অনিশ্চয়তা; "সাধারণ সূত্র" নয় | `ref` ক্ষেত্র | ❌→🆕 | — | CP4 |
| I3 | ৮টি বই: edition/completeness/source/license যাচাই | `library/Library`, `assets/library/*.txt` | 🟡→🆕 | — | `BookManifest` + টেস্ট (CP4) |
| I4 | মূল পাঠ ≠ বাংলা অনুবাদ ≠ সারাংশ ≠ AI ব্যাখ্যা (আলাদা) | `LibraryScreen` | 🟡→🆕 | — | প্যাসেজ-অনুবাদ বাটন (CP4) |
| I5 | নিজের বই যোগ/খোঁজা/পড়া/উৎস-সহ প্রশ্নোত্তর | `LibraryStore`, `LibraryScreen` | ✅/📱 | LibraryTest | — |

## J. ব্যাকআপ, আপগ্রেড, রিলিজ (§১৪, নতুন §১১–১২)
| # | চাহিদা | কোথায় | অবস্থা | প্রমাণ | বাকি |
|---|---|---|---|---|---|
| J1 | এনক্রিপ্টেড ব্যাকআপ, staged ও atomic restore | `backup/*` | ✅ | BackupServiceTest (১৮) | 🆕 নতুন stores অন্তর্ভুক্তি (CP3) |
| J2 | ZIP সীমা (সব entry/byte/সময়/cancel) | `OfficeImporter.readZip` | ✅ | ZipLimitsTest | — |
| J3 | cloud-backup/transfer বন্ধ | manifest, `res/xml/*rules.xml` | ✅ | ManifestSecurityTest | — |
| J4 | আপগ্রেডে PIN/চাবি/ডেটা অক্ষত; স্থিতিশীল সই | `KeyVault` migration | ✅ (JVM) / ⛔ (ডিভাইস) | KeyVaultTest.upgrade… | ফোনে আপগ্রেড |
| J5 | release `debuggable=false`, শুধু লঞ্চার exported, লগ নেই | build.gradle, manifest | ✅ | `tools/check_apk.py` (CI) | — |
| J6 | ARM64 নেটিভ ZIP/LOAD/RELRO 16 KB | `third_party/*`, `tools/check_elf16k.py` | ✅ (স্ট্যাটিক) / ⛔ (রানটাইম) | CI | ডিভাইস/16 KB এমুলেটর রানটাইম |
| J7 | প্রাইভেট signing key (গোপন) | CI secrets | 🟡 | — | আপনাকে secrets বসাতে হবে; এখন পাবলিক interim key |

## K. পরীক্ষার অবস্থা (সংক্ষেপ; বিস্তারিত চূড়ান্ত রিপোর্টে)
- JVM/CI: ✅ (সংখ্যা চূড়ান্ত রিপোর্টে)। Android রানটাইম (এমুলেটর/ফোন): ⛔ **BLOCKED/INCOMPLETE** — KVM-বিহীন এমুলেটরে কোনো টেস্ট শেষ হয়নি (logcat: system_server watchdog/"bg anr" kill, load≈28)।

## হালনাগাদ v2 (CP3–CP5) — অবস্থা-চিহ্ন একই; ✅ = কোড + JVM/CI টেস্ট, 📱 = ফোনে এখনো চালানো হয়নি, ⛔ = পরিবেশের কারণে আটকা

| # | চাহিদা | কোথায় | অবস্থা | প্রমাণ | বাকি |
|---|---|---|---|---|---|
| N1 | সংরক্ষিত প্রশ্নোত্তর (উৎসসহ, বইয়ের/ডকুমেন্টের) | `userdata/SavedQa`, `ui/SavedQaSection` | ✅ / 📱 | SavedQaAndFixTest, BackupServiceTest (v3 round-trip) | ফোনে UI দেখা |
| N2 | ব্যবহারকারীর সংশোধিত লেখা আলাদা সংস্করণ; অনুবাদ/এক্সপোর্টে ব্যবহার; শুধু সেই পৃষ্ঠার অনুবাদ বাসি | `DocWorkflow.saveFix/effective`, `DocScreen` | ✅ / 📱 | SavedQaAndFixTest | ফোনে UI দেখা |
| N3 | রিস্টার্টে অসমাপ্ত কাজের তালিকা (বাতিল করা কাজ নিজে চলে না) | `ui/App` pendingJobs | ✅ / 📱 | JobStore/TranslationJob টেস্ট | ফোনে UI দেখা |
| N4 | ৮টি বইয়ের পরিচয়-নথি: সংস্করণ, উৎস, স্বত্ব-ভিত্তি, SHA-256, শুরু/শেষ চিহ্ন, শিরোনাম-সংখ্যা | `library/BookManifest`, `LibraryScreen` | ✅ (কাঠামো) | BookManifestTest | **মূল প্রকাশকের ফাইলের সঙ্গে শব্দে শব্দে মেলানো হয়নি** (`checkedAgainstOriginal=false`); Arthashastra ১৯৫১ সংস্করণের স্বত্ব অযাচাইকৃত; Callières ও Gracián-এ অধ্যায়-শিরোনাম নেই |
| N5 | জ্ঞানভাণ্ডার: নির্দিষ্ট উৎস + পাতার তারিখ + দেখার তারিখ + অনিশ্চয়তা | `knowledge/KnowledgeBase`, `KnowledgeScreen`, assets | 🟡 | KnowledgeSourcesTest; ৬১ কার্ডের ৬০টিতে অন্তত একটি উৎস (প্রতিটি URL আমি নিজে HTTP 200 পেয়েছি; পাতার লেখার সঙ্গে দাবি মেলানো এজেন্টের কাজ, আমি নিজে সব মিলিয়ে দেখিনি) | **বেশিরভাগ উৎস কার্ডের শুধু অংশ সমর্থন করে** — প্রতিটি কার্ডের "অনিশ্চয়তা" লাইনে অসমর্থিত অংশ লেখা; `structure` কার্ডে উৎস নেই (লেখকের পরামর্শ); `greene` কার্ডের উৎস শুধু উইকিপিডিয়া; ধর্মীয় উৎস শুধু সুন্নি ও শুধু স্বমেহন/গোসল; Nari-o-Shishu আইন খোলা যায়নি |
| N6 | DOCX শিরোনাম/টেবিল/পাদটীকা; PPTX বক্তার নোট; XLSX শিটের নাম; EPUB অধ্যায়-নাম | `importer/OfficeImporter` | ✅ | OfficeImporterTest (নতুন ৩টি) | সত্যিকারের Word/PowerPoint ফাইলে পরীক্ষা বাকি (শুধু হাতে বানানো ন্যূনতম XML) |
| N7 | মূল PDF পৃষ্ঠা ছবি (জুম, ঘোরানো), অস্থায়ী কপি ব্যবহারের পরই মোছা | `DocWorkflow.renderOriginal`, `ui/OriginalPageView` | 📱 | কম্পাইল ✅; রেন্ডারিং JVM-এ চলে না (NOT RUN) | ফোনে পরীক্ষা; পাশাপাশি (side-by-side) বিন্যাস নয়, উপরে-নিচে |
| N8 | প্রম্পট-ইনজেকশন: সব প্রম্পটে <DOC> মোড়ক ভাঙা যায় না | `ai/Prompts` | ✅ (কাঠামো) | PromptInjectionStructureTest | আসল মডেল নির্দেশ মানে কি না — NOT RUN (এখানে মডেল ফাইল নেই) |
| N9 | গাঢ়/হালকা/ফোনের-মতো থিম, কনট্রাস্ট ≥ ৪.৫:১ | `ui/Theme`, `ui/App` | ✅ / 📱 | ThemeContrastTest | ফোনে চোখে দেখা |
| N10 | অফলাইন বাংলা কণ্ঠ-বাছাই | `speech/BengaliVoices`, সেটিংস | 📱 | কম্পাইল ✅ | ফোনে কণ্ঠ আছে কি না ও কেমন শোনায় দেখা |
| N11 | লাইব্রেরির প্রতি অংশে আলাদা বাংলা অনুবাদ (AI, মূল অক্ষত) | `LibraryScreen.translatePassage` | 📱 NOT RUN (মডেল লাগে) | কম্পাইল ✅ | মডেল বসিয়ে ফোনে পরীক্ষা |
| N12 | Android-runtime / instrumented / ফোন-পরীক্ষা | — | ⛔ NOT RUN | এমুলেটর এই পরিবেশে (KVM নেই) অস্থিরতায় আটকা | মালিকের ফোন বা KVM-যুক্ত মেশিনে `./gradlew connectedDebugAndroidTest` |


## হালনাগাদ v3 (ব্যবহারকারীর ফোন-পরীক্ষার অভিযোগ অনুযায়ী)
| # | অভিযোগ | কারণ (কোডে পাওয়া) | সমাধান | অবস্থা |
|---|---|---|---|---|
| G1 | “Call” চাপলে কিছুই হয় না | AI model না থাকলে/মাইক-অনুমতি না থাকলে শুধু ছোট ধূসর লেখা আসত | `ReadinessPanel`: লাল বাক্স, প্রতিটির ঠিক করার বোতাম; model না থাকলে বোতাম নিষ্ক্রিয় | ✅ JVM + রেন্ডার-দেখা / 📱 ফোন |
| G2 | কল ১ সেকেন্ডে বন্ধ | ভয়েস-চেনা ইঞ্জিনের যেকোনো ত্রুটিতে `endCall` | ত্রুটিতে কল বন্ধ হয় না; ৩ বার স্বয়ংক্রিয় পুনঃচেষ্টা; “Speak again”; লাইভ আংশিক লেখা; ত্রুটি-কোড | ✅ লজিক / 📱 ফোনে ইঞ্জিন কেমন আচরণ করে অজানা |
| G3 | লাইভ ৪–৫ বারে ২–৩ সেকেন্ড চলে কেটে যায় | প্রতি চাপে বাড়তি recogniser তৈরি/ধ্বংস (BUSY/CLIENT); model না থাকলে বাক্য শোনার পরে থামা | প্যাক-চেক একবার; retry; মাইকের আগে model-চেক | ✅ / 📱 |
| G4 | ভয়েস কমান্ড কিছু নেয় না; “দ্য প্রিন্স পড়ো” বই পায় না | বইগুলো ফাইল তালিকায় ছিল না; নাম-মেলানো কঠোর | বই ফাইল হিসেবে ভল্টে; বাংলা/ইংরেজি নাম-ডাকনাম; `withNamedDocument` | ✅ BundledBooksTest |
| G5 | আভিধানিক/পারিবেশিক অর্থে একই শব্দ আবার লিখতে হয় | কোনো অফলাইন তালিকা ছিল না | `Dictionary` (Wiktionary + হাতে-বাছাই ★) + পাতার শব্দে চাপ + glossary প্রস্তাব | ✅ DictionaryTest; তালিকা অসম্পূর্ণ |
| G6 | ৬১ প্রশ্নই শুধু; বইয়ের যেকোনো প্রশ্ন নয় | model ছাড়া বাংলা প্রশ্নে ইংরেজি বই মিলত না | `BnEnQuery` (বাংলা→ইংরেজি খোঁজ-শব্দ), বই-বাছাই চিপ, কার্ড সরানো Setup-এ | ✅ BnEnQueryTest |
| G7 | ক্যামেরার ছবি কোথায় গেল | ছবি নিঃশব্দে ফাইল-তালিকায় যেত | ছবির পর্দা সরাসরি খোলে + এক-চাপে OCR | ✅ কম্পাইল / 📱 |
| G8 | খালি বোতাম/ফোল্ডার | ১৫টি টাইল | ৯টি; ফাইল+ভয়েস+ক্যামেরা-ফল একত্র; Setup হাব | ✅ রেন্ডার দেখা |
| G9 | বাংলা নির্দেশ-বাক্য “আনস্মার্ট” | — | বোতামের ক্রিয়া ইংরেজিতে (Call, Back, Send, Open…) | ✅ |
| G10 | ইংরেজি বই বাংলা কণ্ঠে পড়া | অনুবাদ আগে চালাতে হতো | পাতা-পিছু স্বয়ংক্রিয় অনুবাদ → পড়া (পড়ার পর্দা ও কল দুটোতে) | ✅ লজিক-টেস্ট / 📱 + model |
| G11 | ফোনের Google অফলাইন ভয়েস-চেনা অনির্ভরযোগ্য | ফোনভেদে recogniser/ভাষা-প্যাক | **AI model দিয়ে শোনা** (`ModelVoiceInput`): অ্যাপ নিজে মাইক রেকর্ড করে (`PcmRecorder`, থামা-শনাক্তকরণ `EndpointDetector`), WAV বানিয়ে Gemma 4-কে দেয়; Settings-এ Auto / AI model / Phone's service | ✅ টেস্ট: WAV, থামা-শনাক্তকরণ; ✅ ডেস্কটপে model: ৬টি কৃত্রিম (TTS) ক্লিপে গড় অক্ষর-ত্রুটি ≈ ৯.৮% (`docs/STT_EVAL.md`) / 📱 আসল গলা, ফোনের মাইক, শব্দ, গতি অজানা |
