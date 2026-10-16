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


## হালনাগাদ v4 — "PS Final Repair and Acceptance" প্রম্পট অনুযায়ী (U01–U10, C01–C06, §13, §14, AT01–AT31)

**অবস্থার অর্থ** (এর বেশি দাবি করা হয় না): **JVM ✅** = Robolectric/JUnit টেস্টে পাশ (ফোনে চালানো নয়) · **DESKTOP ✅** = আসল মডেল ডেস্কটপ CPU-তে · **CODE** = কোড আছে, কম্পাইল ও CI পাশ, কিন্তু কোনো টেস্ট/ফোনে যাচাই নেই · **PHONE NOT RUN** = মালিকের ফোনে এখনো চালানো হয়নি · **BLOCKED** = এই পরিবেশে করা যায় না (এমুলেটর: KVM নেই) · **NOT DONE** = করা হয়নি।
কোনো আইটেম "ফোনে পাশ" নয়, কারণ ফোনে কোনো পরীক্ষা চালানো হয়নি।

### ফোনের সমস্যা U01–U10
| ID | সমস্যা | করা কাজ | অবস্থা | বাকি |
|---|---|---|---|---|
| U01 | কল ১ সেকেন্ডে বন্ধ / এক action-এ কল চাই | ইঞ্জিন-ত্রুটিতে কল বন্ধ হয় না; ReadinessPanel; পুনরায় চেষ্টা; AI-model দিয়ে শোনার বিকল্প | CODE + JVM (CallLogic/SpeechCapture) ; PHONE NOT RUN | ৫ মিনিট/১০ টার্ন ফোনে দেখা (AT01) |
| U02 | লাইভ/কল: শোনা অনির্ভরযোগ্য | `ModelVoiceInput` + VAD; `VoiceInput` retry/watchdog; মাইক-পরীক্ষা (Phone check) | DESKTOP ✅ (synthetic, CER≈9.8%) ; PHONE NOT RUN | আসল ফোনে গতি/নির্ভুলতা |
| U03 | কিবোর্ড ইনপুট ঢেকে দেয় | সব স্ক্রিনে `imePadding()` (edge-to-edge, targetSdk 35) | CODE ; PHONE NOT RUN | Samsung/Gboard, landscape, বড় ফন্টে চোখে দেখা (AT06) |
| U04 | ভয়েস কমান্ড অনির্ভরযোগ্য | কমান্ড পার্সার + নাম-মেল (`FileMatcher`), বইয়ের নামে পড়ার কমান্ড | JVM ✅ (VoiceCommands টেস্ট) ; PHONE NOT RUN | Tap/hold স্পষ্টতা ফোনে (AT03) |
| U05 | ফাইল যোগের একাধিক জায়গা | Files ট্যাবে এক জায়গা: Add file / Scan / বইগুলো | CODE ; PHONE NOT RUN | AT07 |
| U06 | ক্যামেরা: ছবির পর কী হবে | **নতুন:** Scan review — একাধিক পৃষ্ঠা, Rotate, Retake last, Add page, Discard, Save (এক পৃষ্ঠা = ছবি, একাধিক = PDF); Save ছাড়া ভল্টে কিছু রাখা হয় না; এরপর ছবি খুলে ক্রপ/OCR | CODE ; PHONE NOT RUN | অ্যাপ বন্ধ হলে অসংরক্ষিত পৃষ্ঠা হারায় (ক্যাশে আছে); ক্রপ সংরক্ষণের পরের পর্দায়, প্রি-সেভ ক্রপ নেই |
| U07 | বইয়ের পরিচয়/পৃষ্ঠা-কমান্ড | ৮ বইয়ের alias + `FileMatcher`; বাংলা সংখ্যা; ইংরেজি পাতা স্বয়ংক্রিয় অনুবাদ→পড়া | JVM ✅ ; PHONE NOT RUN | একই নামের দুই সংস্করণের প্রশ্ন (ambiguity prompt) **NOT DONE** |
| U08 | সব বই থেকে প্রশ্ন, ৬১ কার্ড শুধু ভিত্তি | BM25 + `BnEnQuery` (মডেল ছাড়া বাংলা→ইংরেজি) ; **নতুন:** উত্তরের নিচে কভারেজ-লাইন (কয়টি বই/অংশ খোঁজা, অসম্পূর্ণ ফাইল) | JVM ✅ (BnEnQuery, BundledBooks) ; DESKTOP ✅ (LIBRARY_EVAL) | স্থায়ী এনক্রিপ্টেড ইনডেক্স **NOT DONE** (প্রতি প্রশ্নে মেমরিতে তৈরি); semantic/embedding retrieval **NOT DONE** |
| U09 | বৈধ সংবেদনশীল শিক্ষামূলক প্রশ্নে প্রত্যাখ্যান | কারণ: সিস্টেম-প্রম্পটে শিক্ষামূলক প্রসঙ্গের নির্দেশ ছিল না; এখন `ASSISTANT_SYSTEM`/`CALL_SYSTEM`-এ স্পষ্ট নির্দেশ (সাধারণ ফিল্টার নয়, শুধু সুনির্দিষ্ট ক্ষতির ধাপ বাদ) | CODE ; DESKTOP আংশিক: ২০টির মধ্যে ১৪টি প্রশ্নে প্রত্যাখ্যান-সদৃশ উত্তর ০টি (`docs/REFUSAL_EVAL.md`); বাকি ৬ + ক্ষতিকর ৩টি প্রোব ইঞ্জিন ক্র্যাশে NOT RUN | ২০টি প্রশ্নের ফোনে ফল (AT12) |
| U10 | একই শব্দ একই অর্থ এক জায়গায় | এক Glossary সব অনুবাদে (ডকুমেন্ট, কল, রিডার, **লাইভ, চ্যাট-অনুবাদ**); **নতুন:** একই শব্দের একাধিক অর্থ প্রসঙ্গ (domain) সহ | JVM ✅ (UserDataTest) | ফোনে দেখা (AT13) |

### পরিচিত ত্রুটি C01–C06
| ID | করা কাজ | অবস্থা |
|---|---|---|
| C01 | রিস্টোরে ডকুমেন্ট অ্যাটমিক (আগে থেকেই); **নতুন:** ব্যবহারকারী-ডেটা (marks, glossary, vocab, reading, savedqa) ধরন-ভিত্তিক গণনা ও ব্যর্থতার তালিকা রিপোর্টে | JVM ✅ (BackupServiceTest). সীমা: ব্যবহারকারী-ডেটা মার্জ ডকুমেন্ট কমিটের **পরে**, তাই এক ধরন ব্যর্থ হলে ডকুমেন্ট ফেরত থাকে ও ব্যর্থ ধরন স্পষ্ট জানানো হয় (পুরো রোলব্যাক নয়) |
| C02 | আনইনস্টল-পরামর্শ বাদ; কারণ আলাদা: ভুল PIN / ডিভাইস-কী অনুপলব্ধ / মেটাডেটা নষ্ট; সব বার্তায় "ডেটা মোছা হয়নি" | JVM ✅ (KeyVaultTest.failureCausesAreSeparated). Keystore-invalidated আসল ফোনে **PHONE NOT RUN** |
| C03 | পুরো ট্রান্সক্রিপ্ট খণ্ডে খণ্ডে ক্রমানুসারে অনুবাদ (>৩,৫০০ ইউনিট), অগ্রগতি, ব্যর্থ হলে কোন অংশ বাদ তা লেখা | JVM ✅ (ChunkedTranslateTest). আসল মডেলে লাইভ **PHONE NOT RUN**. utterance-ID/সম্পাদনযোগ্য ট্রান্সক্রিপ্ট **NOT DONE** |
| C04 | EPUB: namespace-aware, একক-উদ্ধৃতি, prefix, `../` ও %-escape পথ, XXE/DOCTYPE বন্ধ, অনুপস্থিত অধ্যায় চিহ্নিত | JVM ✅ (OfficeImporterTest ৩টি নতুন) |
| C05 | DOCX: আসল `w:tbl`/`w:tc`, `footnotes.xml`/`endnotes.xml`, document.xml.rels, content-types; OCR-পৃষ্ঠায় পাদটীকা | JVM ✅ (ExporterTest). Word-এ খুলে দেখা **NOT RUN** (শুধু XML বৈধতা) |
| C06 | `setVoice()` ফল ও ভয়েস-নাম যাচাই; ব্যর্থ হলে "প্রস্তুত নয়"; নেটওয়ার্ক-ভয়েস কখনো না | CODE ; PHONE NOT RUN |

### §13 মালিক-পরিচয় ও সক্রিয়করণ
| বিষয় | অবস্থা |
|---|---|
| `OWNER_ADDRESS_BN` একটি ধ্রুবক; প্রতি assistant response-এ **একবার** (চ্যাট UI + TTS, কলের প্রথম বাক্য); মূল লেখা/অনুবাদ/এক্সপোর্ট/কোডে নয় | JVM ✅ (OwnerAddressTest) ; উচ্চারণ ফোনে শোনা **PHONE NOT RUN** |
| Owner ID + About (আনলকের পরে) ; সেটিংস/চ্যাট/রিস্টোর থেকে বদলানো যায় না (কোডে ধ্রুবক) | CODE |
| অফলাইন সক্রিয়করণ: Keystore ডিভাইস-কী (বের করা যায় না) + issuer-স্বাক্ষরিত টোকেন; APK-তে শুধু public key (`assets/issuer_public_key.b64`) | JVM ✅ (OwnerIdentityTest: অন্য ফোন/অন্য issuer/অন্য owner/বদলানো টোকেন = Invalid). **issuer key এখনো তৈরি হয়নি** → বিল্ড এখন "NotConfigured" দেখায়, কিছু আটকায় না। `tools/activation_tool.py genkey` মালিক নিজের কম্পিউটারে চালাবেন; private key কখনো repo/চ্যাটে নয় |
| সক্রিয় না থাকলে কোনো ডেটা মোছা/লক হয় না ; self-destruct নেই | নকশা অনুযায়ী |
| "সক্রিয় না হলে private অপারেশন বন্ধ" (গেটিং) | **NOT DONE** — ইচ্ছাকৃত: issuer key ছাড়া গেট করলে মালিকই আটকে যেতেন; key তৈরির পর আলাদা ধাপ |
| R8 অবফাসকেশন / প্যাকেজ-সিগনেচার সততা-যাচাই | **NOT DONE** (বিদ্যমান release signing আছে; interim public key) |
| কণ্ঠ-যাচাই (speaker verification) | **NOT DONE** — এই পরিবেশে আস্থাযোগ্য অফলাইন speaker-embedding মডেল/লাইসেন্স যাচাই করা হয়নি; দাবি করা হচ্ছে না। PIN/আঙুলের ছাপ কাজ করে |

### §14 UI পরিষ্কার
| বিষয় | অবস্থা |
|---|---|
| স্থায়ী আঙুলের-ছাপ সতর্কবার্তা বাদ | CODE (AT30 ফোনে) |
| স্থায়ী "✅ AI মডেল বসানো আছে" বাদ; মডেল না থাকলে প্রয়োজনের জায়গায় "এই কাজের জন্য AI মডেল প্রয়োজন। মডেল যোগ করুন।"; নষ্ট মডেলে আলাদা বার্তা ; প্রস্তুত আইটেম তালিকায় দেখানো হয় না | CODE (AT31 ফোনে) |

### AT01–AT31 সারসংক্ষেপ
সবগুলো ফোন-ভিত্তিক পরীক্ষা **PHONE NOT RUN**। JVM/ডেস্কটপ সমর্থন: AT05/AT15/AT14/AT17/AT18/AT26(আংশিক)/AT28(টোকেন-যুক্তি)/AT13 ইউনিট-টেস্ট ✅ ; AT12 ডেস্কটপ-স্ক্রিনিং ; AT19/AT20 পূর্বের synthetic ডেস্কটপ টেস্ট ; AT23 CI-তে ARM64/16 KB/সিগনার চেক ✅ (বিল্ডের লগ দেখুন) ; AT27 **NOT DONE** ; AT22 নকশায় (INTERNET permission নেই) ✅ কিন্তু ফোনে পরীক্ষা নয়।


## হালনাগাদ v5 — মালিকের নতুন অনুরোধ (সব CODE/JVM; ফোনে PHONE NOT RUN)
| # | অনুরোধ | করা কাজ | অবস্থা |
|---|---|---|---|
| R1 | স্ক্রিনশট নেওয়া যায় না | আনলকের পর স্ক্রিনশট ডিফল্টে অনুমোদিত (Settings-এ বন্ধ করা যায়); লক-স্ক্রিন সবসময় সুরক্ষিত | CODE |
| R2 | ফাইল যোগ → অনুবাদ; যেকোনো ভাষা → বাংলা/English/আরবি | ফাইল যোগ করলে সরাসরি ফাইল খোলে; এক "Translate" বাটন (লেখা বের করা + অনুবাদ); লক্ষ্য ভাষা চিপ; ভাষা বদলালে আগের অনুবাদ মুছে নতুন; এক্সপোর্ট লক্ষ্য-ভাষা অনুযায়ী | JVM ✅ (TargetLangTest) ; PHONE NOT RUN |
| R3 | বাংলা পড়ার সময় ইংরেজি স্পষ্ট নয় | লেখা ভাষা-অনুযায়ী টুকরো (বাংলা/ইংরেজি/আরবি), প্রতিটির নিজস্ব অফলাইন কণ্ঠ; কণ্ঠ না থাকলে বাংলা কণ্ঠই ভরসা | JVM ✅ (LangRunsTest, বিভাজন) ; কণ্ঠ-শব্দ PHONE NOT RUN (ফোনে ইংরেজি অফলাইন voice ইনস্টল থাকতে হবে) |
| R4 | পড়ার পাশে বুঝিয়ে বলা/প্রশ্ন/কমেন্ট; Pause/Stop কাজ করে না | Explain this, Ask (পড়া থামে), Save comment (পৃষ্ঠায় নোট); Pause/Stop সবসময় স্পিচ-ইঞ্জিন থামায় | CODE ; PHONE NOT RUN |
| R5 | "জানি না" বলে; ধীর; ফোন গরম | প্রম্পটে সরাসরি উত্তরের নির্দেশ; ডেস্কটপ মডেলে ৪টি সাধারণ প্রশ্নে সরাসরি উত্তর এসেছে (ঢাকার জনসংখ্যা ভুল এসেছিল — মডেলের সীমা); ধীর/গরম: কারণ কোড দেখে পাওয়া যায়নি | আংশিক |
| R6 | চ্যাট-ইতিহাস | এনক্রিপ্টেড সংরক্ষণ, History/New chat, একটি বা সব মোছা, সংরক্ষণ বন্ধের সুইচ; ব্যাকআপে (v4) যুক্ত | JVM ✅ (ChatHistoryTest) |
| R7 | পরামর্শদাতা সীমাহীন ও গভীর | ADVISOR_SYSTEM নতুন: সরাসরি, গভীর কৌশল-বিশ্লেষণ; একটিমাত্র সীমা (শারীরিক ক্ষতি/অস্ত্র/যৌন-শোষণ/গুরুতর অপরাধের নির্দিষ্ট পরিকল্পনা) | CODE ; মান ফোনে/মডেলে দেখা বাকি |

### v6 — ফোন গরম/চার্জ-খরচ (মালিকের অভিযোগ)
| বিষয় | করা কাজ | অবস্থা |
|---|---|---|
| কারণ খোঁজা | কোড পড়ে দেখা গেছে: অ্যাপ খোলার সময় মডেল লোড/ব্যাকগ্রাউন্ড লুপ নেই; স্থায়ী CPU-খরচের জায়গা শুধু AI চালানো (সব কোরে ১০০%), ভারী জব ও কলের অ্যানিমেশন। ফোনে মেপে নিশ্চিত হওয়া যায়নি | কারণ **অনিশ্চিত** |
| মাপার যন্ত্র | Phone check-এ "অ্যাপ এখন কত CPU নিচ্ছে" (মোট % + সবচেয়ে ব্যস্ত থ্রেডের নাম) — গরম লাগলে স্ক্রিনশট দেখে কারণ ধরা যাবে | CODE |
| কমানো | AI-র CPU থ্রেড ডিফল্ট ৪ (আগে লাইব্রেরির ডিফল্ট, সম্ভবত সব কোর); Settings-এ ঠান্ডা(২)/মাঝারি(৪)/দ্রুত(৬); মডেল ৯০ সেকেন্ড অলস থাকলে RAM ছাড়ে (আগে ৪ মিনিট) | CODE ; ফোনে প্রভাব PHONE NOT RUN |
| সুরক্ষা | ফোনের নিজস্ব তাপ-অবস্থা (Android thermal): ≥গরম হলে হোমে সতর্কবার্তা; ≥বেশ গরম হলে অনুবাদ-জব থামে, AI বন্ধ হয়, মডেল RAM ছাড়ে — কিছু মোছে না | CODE |

### v7 — সাইবার সিকিউরিটি শেখার মোড (AFX-এর জন্য, মালিকের অনুরোধে)
| বিষয় | করা কাজ | অবস্থা |
|---|---|---|
| নতুন চ্যাট-মোড | "সাইবার সিকিউরিটি" চিপ (প্রশ্ন/পরামর্শদাতা/অনুবাদের পাশে); নেটওয়ার্কিং, Linux, ওয়েব/অ্যাপ সিকিউরিটি (XSS, SQLi, CSRF, IDOR ইত্যাদি), OSINT, defensive security শেখায়: ধারণা → কারণ → নিরাপদ lab/CTF-এ পরীক্ষা → শনাক্তকরণ → সমাধান, এবং আক্রমণকারী/রক্ষক দুই দৃষ্টিকোণ | JVM ✅ (CybersecPromptTest) |
| সীমা | শুধু একটি: মালিকের নিজের নয় এমন আসল ব্যক্তি/কোম্পানি/সিস্টেমের বিরুদ্ধে সরাসরি আক্রমণের ধাপ (পাসওয়ার্ড/টোকেন চুরি, অনুপ্রবেশ, DDoS ইত্যাদি) দেয় না — তখন একই কৌশলের নিরাপদ lab সংস্করণ দেয়; মালিকের নিজের সিস্টেম/অনুমোদিত scope-এ পূর্ণ বিস্তারিত সাহায্য করে | CODE |
| চ্যাট-ইতিহাসে | এই মোডের কথোপকথনও এনক্রিপ্টেড সংরক্ষিত হয়, মোড-লেবেল "সাইবার সিকিউরিটি" | CODE |
| ফোনে মান যাচাই | মডেলের উত্তরের টেকনিক্যাল নির্ভুলতা ফোনে/মডেলে এখনো দেখা হয়নি | PHONE NOT RUN |

### v8 — চ্যাটে ছবি/স্ক্রিনশট দিয়ে জিজ্ঞাসা
| বিষয় | করা কাজ | অবস্থা |
|---|---|---|
| স্ক্রিনশট দিয়ে প্রশ্ন | Assistant পর্দায় "🖼️ স্ক্রিনশট" বাটনে ছবি বেছে নেওয়া যায়; প্রিভিউ দেখায়; প্রশ্ন লিখে (বা খালি রেখে) Send করলে মডেলের ভিশন এনকোডার দিয়ে ছবি বিশ্লেষণ করে ধাপে ধাপে গাইড করে | CODE (LocalLlm.askStreamWithImage, EngineConfig.visionBackend); ফোনে/মডেলে ভিশন আসলে কাজ করে কি না PHONE NOT RUN |
| মডেল না বুঝলে | "জানি না/পারব না" এড়ানোর নির্দেশ আগে থেকেই ASSISTANT_SYSTEM ও CYBERSEC_SYSTEM-এ আছে; অস্পষ্ট ছবিতে যা দেখা যাচ্ছে না তা "যাচাই করুন" বলে চিহ্নিত করবে, flat refusal নয় | CODE |

### v9 — সাইবার কারিকুলাম A-to-Z gap review (মালিকের অনুরোধে)
| বিষয় | করা কাজ | অবস্থা |
|---|---|---|
| গ্যাপ পাওয়া গেছে | আগের তালিকায় ছিল না: ক্রিপ্টোগ্রাফি/PKI, Windows/Active Directory (শুধু Linux ছিল), ক্লাউড সিকিউরিটি, মোবাইল অ্যাপ সিকিউরিটি, ওয়্যারলেস/Wi-Fi, ম্যালওয়্যার/endpoint, Secure SDLC/DevSecOps, কমপ্লায়েন্স/ঝুঁকি (ISO 27001/GDPR/PCI-DSS), ডিজিটাল ফরেনসিক্স, MITRE ATT&CK, সার্টিফিকেশন রোডম্যাপ | সংযোজন সম্পূর্ণ |
| টেকনিক্যাল সীমা | মডেলের মোট context ৪০৯৬ টোকেন; system prompt বড় করলে উত্তরের জায়গা কমে — তাই নতুন বিষয় সংক্ষিপ্ত নামে যোগ, বিস্তারিত ব্যাখ্যা মডেলের নিজস্ব জ্ঞান থেকে। এখন system ≈২১৩০ টোকেন (সীমা ২৮০০, উত্তরের জন্য ≈১৯৬৫ টোকেন বাকি, ছোট প্রশ্নে) | CODE, হিসাব করা হয়েছে |
| যাচাই | CybersecPromptTest-এ প্রতিটি নতুন ক্ষেত্রের নাম উপস্থিত আছে কিনা তা পরীক্ষা হয় | JVM ✅ (২৭৬টি টেস্ট পাশ) |

### v10 — "আরও লিখো" (উত্তর মাঝপথে থেমে গেলে চালিয়ে যাওয়া)
| বিষয় | করা কাজ | অবস্থা |
|---|---|---|
| কারণ | মডেলের context মাত্র ৪০৯৬ টোকেন (system+প্রশ্ন+উত্তর একসাথে); বড় curriculum-এর system prompt (~২১৩০ টোকেন) দীর্ঘ A-to-Z উত্তরের জায়গা কমায়; context বাড়ালে RAM/তাপ বাড়ে, তাই MAX_TOKENS বাড়ানো হয়নি | ব্যাখ্যা করা হয়েছে, সিদ্ধান্ত নথিভুক্ত |
| সমাধান | প্রতিটি উত্তরের নিচে "➕ আরও লিখো" বাটন; চাপলে আগের উত্তরের শেষ অংশ (সর্বোচ্চ ৯০০ অক্ষর) ও মূল প্রশ্ন context হিসেবে পাঠিয়ে একই বুদবুদে চালিয়ে লেখে — শুরু থেকে আবার লেখে না | JVM ✅ (CybersecPromptTest.continueAnswerKeepsOnlyTheTailAndAsksNotToRepeat) ; ফোনে PHONE NOT RUN |

### v11 — ডিফল্টে কম CPU/গরম
| বিষয় | করা কাজ | অবস্থা |
|---|---|---|
| ডিফল্ট থ্রেড ৪ থেকে ২ | নতুন ইনস্টলে AI এখন ডিফল্টে ২টা কোর ব্যবহার করে (আগে ৪টা); Phone check-এ মাঝারি(৪)/দ্রুত(৬) করে নিজে বাড়ানো যায় | CODE |

### v12 — History আলাদা জায়গায়, OSINT-জাতীয় শব্দের উচ্চারণ, গরম হওয়ার পুনঃতদন্ত
| বিষয় | করা কাজ | অবস্থা |
|---|---|---|
| হোম পর্দায় "অসমাপ্ত কাজ" স্তূপ | নতুন **History** ট্যাব (Setup হাব থেকে, বা হোমে এক লাইনের পয়েন্টার থেকে); হোম পর্দায় শুধু "⏸ Nটি অসমাপ্ত কাজ — History-তে দেখুন" — পুরো তালিকা আর টালিগুলোর ওপরে জমে থাকে না | CODE |
| OSINT বানান-বানান করে বলা | `SpeechText.wordAcronyms`: ইংরেজিতে শব্দ-হিসেবে-উচ্চারিত সংক্ষিপ্তরূপ (OSINT→ওসিন্ট, JWT→জট, CSRF, SSRF, IDOR, MITRE, SAST, DAST, IAM, RADIUS) বলার আগে বাংলা বানানে বদলে দেয়; যেগুলো ইংরেজিতেও অক্ষর ধরে ধরে বলা হয় (DNS, API, PKI...) সেগুলো অপরিবর্তিত | JVM ✅ (SpeechTextTest.wordAcronymsAreSpokenAsWordsNotSpelledOut) |
| "অ্যাপ খুললেই গরম" | কোড আবার খুঁজে দেখা হয়েছে: Home পর্দা খোলার সময় কোথাও AI মডেল লোড হয় না (`LocalLlm(` কোনো কল নেই App.kt-এ), কোনো চলমান animation-লুপ নেই (শুধু ভয়েস-কল পর্দায় আছে), আর আগের CPU-মিটার স্ক্রিনশটে অলস অবস্থায় রিডিং কমতে কমতে ৮%-এ এসেছিল। তাই নির্দিষ্ট কোনো 'লুকানো ব্যাকগ্রাউন্ড কাজ' কোডে পাওয়া যায়নি | তদন্ত চলছে — আরও তথ্য দরকার |

### v13 — স্বয়ংক্রিয় তাপ-নিয়ন্ত্রণ (কাজ বন্ধ না করে)
| বিষয় | করা কাজ | অবস্থা |
|---|---|---|
| ম্যানুয়াল সুইচের বদলে স্বয়ংক্রিয় | মালিকের বেছে নেওয়া কোর-সংখ্যা (`preferredThreads`) এখন শুধু "আপনি কী চান" রেকর্ড করে; ফোনের নিজস্ব তাপ-সংকেত (thermal status) পরিবর্তনের সাথে সাথে `ThermalGuard.applyThreads()` প্রকৃত কোর-সংখ্যা (`threads`) নিজে থেকেই সমন্বয় করে — গরম হলে সর্বোচ্চ ২ কোরে নামিয়ে আনে, স্বাভাবিক হলে মালিকের পূর্ণ পছন্দে ফিরিয়ে দেয়; মাঝপথে কোনো চলমান উত্তর থামে না | JVM ✅ (ThermalGuardTest: গরমে কমে, স্বাভাবিকে ফেরে, আগে থেকেই ঠান্ডায় থাকলে আরও কমে না) |
| দেখা যায় | Phone check-এ এখন দেখাবে যদি সাময়িক ক্যাপ চালু থাকে ("ফোন এখন গরম — তাই সাময়িকভাবে ২টি কোর; ঠান্ডা হলে নিজে থেকেই ফিরবে") | CODE |
| কার্যকারিতা অক্ষুণ্ণ | কোনো ফিচার বন্ধ হয় না, শুধু গরম অবস্থায় সাময়িকভাবে ধীর হয় — এটাই অনুরোধ অনুযায়ী ("কাজের অসুবিধা না হয়") | নকশা অনুযায়ী |

### v14 — "PSA — Final Feature & Intelligence Upgrade" ১৫-দফা নির্দেশনার প্রথম ধাপ (সততার সাথে অবস্থা)
মালিকের ১৫-দফা নির্দেশনার মধ্যে যেগুলো **এখনই বাস্তবভাবে, কম ঝুঁকিতে implement করা গেছে** তার তালিকা এখানে। বাকি দফাগুলো (বড় document translation architecture বদল, XLSX export, format-aware export, quality-check রিপোর্ট বিস্তৃতকরণ) এখনো শুরু হয়নি — পরের ধাপে।

| দফা | করা কাজ | অবস্থা |
|---|---|---|
| #৮,#৯ (সাইবার সিকিউরিটি: এক-এক ধাপ শেখানো) | `CYBERSEC_SYSTEM`-এ নতুন নিয়ম (৮): পুরো roadmap একবার দেখানো যায়, কিন্তু শেখানো হবে এক ধাপ করে — Concept→কেন জরুরি→প্রয়োজনীয় prerequisite→Step-by-Step→প্রত্যাশিত ফল→মালিকের আসল ফল বিশ্লেষণ→সমস্যা হলে সমাধান→পরের ধাপ; মালিক একবারে সব চাইলে তখনই সব দেয় | CODE; ফোনে/মডেলে আসলে এই প্যাটার্নে উত্তর দেয় কি না PHONE NOT RUN |
| #১১ (অফলাইন/সাম্প্রতিক তথ্য স্পষ্টীকরণ, সাইবার মোডে) | `CYBERSEC_SYSTEM`-এ নতুন নিয়ম (৯): app সম্পূর্ণ অফলাইন, তাই সাম্প্রতিক ভার্সন/দাম/বর্তমান নিয়ম জিজ্ঞাসা হলে স্পষ্ট করে বলবে জ্ঞান একটা সময় পর্যন্তই ও অনলাইনে/অফিসিয়াল সোর্সে যাচাই করতে বলবে — তারপরও যা জানে তা দিয়ে সাহায্য করবে, উত্তর এড়াবে না। (ASSISTANT_SYSTEM ও CALL_SYSTEM-এ এই সতর্কতা আগে থেকেই ছিল — এখন CYBERSEC_SYSTEM-এও যোগ হলো) | JVM ✅ (CybersecPromptTest, কম্পাইল হয়েছে) |
| #১২ (একাধিক পৃষ্ঠা একসাথে, "১ থেকে ২০ পর্যন্ত পড়ো") | ইতিমধ্যে `ReadingPlan.next()`-এ পৃষ্ঠা-সীমা পেরিয়ে অটো-চলা ছিল; এখন পড়ার সময় লাইভ "পৃষ্ঠা N / M পড়া হচ্ছে…" দেখায় (আগে শুধু "পৃষ্ঠা N" দেখাত, মোট কত পৃষ্ঠা পর্যন্ত পড়বে বোঝা যেত না), আর সীমা স্বাভাবিকভাবে শেষ হলে (ম্যানুয়াল থামানো নয়) "✅ পড়া শেষ হয়েছে — পৃষ্ঠা A–B" দেখায় | CODE (TtsReader.onFinished, ReadAloudController.endPage/finishedRange, ReaderSection UI); ফোনে TTS আসলে এভাবেই চলে কি না PHONE NOT RUN |
| #১৩,#১৪ (অধ্যায়-শেষে/প্রতি-N-পৃষ্ঠায় স্বয়ংক্রিয় সারাংশ) | নতুন প্রম্পট `Prompts.SUMMARY_SYSTEM`/`summary()` — মূল বিষয়/সহজ ব্যাখ্যা/মূল শিক্ষা/Practical Lesson/২-৫টি গুরুত্বপূর্ণ Point, **শুধু যা পড়া হয়েছে তার ভিত্তিতে, উৎসের বিরুদ্ধে কিছু না বলে**; ReaderSection-এ দুটি ঐচ্ছিক টগল ("অধ্যায় শেষে সামারি", "প্রতি ৫/১০/২০ পৃষ্ঠা") — শর্ত মিললে পড়া থেমে সারাংশ শোনানো হয়, মালিক Resume চেপে আবার পড়া চালিয়ে নেয় (Explain/Ask-এর মতোই প্যাটার্ন) | JVM ✅ (SummaryPromptTest: ৫টি শিরোনাম আছে, উৎসের বিরুদ্ধে না বলার নির্দেশ আছে, ইনজেকশন-নিরাপদ); ফোনে/মডেলে বাস্তব সারাংশের মান PHONE NOT RUN |
| যা এখনো করা হয়নি (সততার সাথে) | বড় document (PDF/DOCX/XLSX, ১০–১৫০০+ পৃষ্ঠা) translation-এর জন্য নতুন architecture (#১–#৩; বর্তমান `DocWorkflow.translate`/`TranslationJob`-এর পুরোনো per-page chunking দিয়ে ইতিমধ্যে resumable/pausable কাজ চলে, কিন্তু নতুন করে ডিজাইন বা পুনর্লিখন হয়নি), post-translation quality-check রিপোর্ট (#৪), XLSX export ও format-aware export decision (#৫,#৬), voice assistant-কে text-mode-এর মতো গভীর বিশ্লেষণধর্মী করা (#৭ — বড় অংশ ইতিমধ্যে ASSISTANT_SYSTEM/CALL_SYSTEM-এ আছে, কিন্তু "গবেষণা করে উত্তর" পুরোপুরি network-বিহীন বলে সীমিত), general accuracy discipline-এর আরও বিস্তৃত প্রয়োগ (#১১, cybersec ছাড়া অন্য মোডে) | পরের ধাপে করা হবে |
