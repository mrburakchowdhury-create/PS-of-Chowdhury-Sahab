# ব্যক্তিগত অফলাইন বাংলা AI সহকারী — চূড়ান্ত নির্মাণ নির্দেশনা

তুমি একজন অভিজ্ঞ Android engineer, on-device AI engineer, document-processing engineer এবং application-security reviewer-এর মান বজায় রেখে এই কাজ করো। নিচের সম্পূর্ণ নির্দেশনা পড়ে আমার ব্যক্তিগত Android অ্যাপ নির্মাণ শুরু করো। সুন্দর ইন্টারফেসের পাশাপাশি কার্যকর implementation, নিরাপত্তা এবং বাস্তব পরীক্ষার প্রমাণ চাই।

## ১. উদ্দেশ্য ও আমার ফোন

আমি একাই এই অ্যাপ ব্যবহার করব। অ্যাপ আমার দেওয়া ফাইল, বই, PDF, ছবি এবং কোড থেকে বাংলায় অনুবাদ করবে, পড়ে শোনাবে, প্রশ্নের উত্তর দেবে এবং কোডের সমস্যা ও সংশোধন বুঝিয়ে বলবে। সব কাজ আমার নির্দেশে হবে।

- ফোন: Samsung Galaxy S24 Ultra; ১২ GB RAM; Snapdragon 8 Gen 3; ২৫৬ GB storage।
- আমার দেওয়া স্ক্রিনশটে storage ব্যবহৃত ২০০.৯ GB; প্রায় ৫৫.১ GB খালি। প্রয়োজন হলে আরও খালি করতে পারব।
- একই স্ক্রিনশটে RAM ব্যবহৃত ৮.৮ GB/১২ GB দেখা গেছে। এটি সেই মুহূর্তের অবস্থা; স্থায়ী available RAM ধরে পরিকল্পনা করবে না।
- সব UI, নির্দেশনা, অনুবাদ ও ব্যাখ্যা বাংলায় হবে। মূল ভাষার লেখা এবং মূল কোড যথাস্থানে অক্ষত থাকবে।
- মূল উৎসভাষা ইংরেজি, আরবি, উর্দু ও হিন্দি; অন্য সমর্থিত ভাষাও গ্রহণ করা যাবে। অনুবাদের target শুধু বাংলা।
- এটি আমার ব্যক্তিগত শেখার ও কাজ বোঝার অ্যাপ। কোনো আলাদা মানব ডেভেলপার নিয়োগের পরিকল্পনা নেই; তোমার সাহায্যে নির্মাণ ও প্রয়োজনীয় setup করব।

## ২. বাধ্যতামূলক গোপনীয়তা, অফলাইন ব্যবহার ও খরচ

- প্রয়োজনীয় মডেল ও উপাদান বসানোর পর অনুবাদ, OCR, কোড বিশ্লেষণ, প্রশ্নোত্তর, speech recognition ও বাংলা text-to-speech ফোনের ভেতরে চলবে।
- runtime-এ আমার ফাইল, ছবি, কোড, কথা, প্রশ্ন, উত্তর, অনুবাদ, index, embedding বা অডিও কোনো বাইরের সার্ভারে পাঠাবে না। Cloud AI, cloud OCR এবং cloud speech ব্যবহার করবে না।
- Claude/OpenAI/Gemini বা অন্য paid API, API key, token credit, hosting, domain, remote backend বা মাসিক inference subscription-এর ওপর অ্যাপ নির্ভর করবে না।
- অ্যাপ তৈরির জন্য এই Claude সেশনের ব্যবহার এবং তৈরি অ্যাপের দৈনন্দিন ব্যবহার আলাদা। তৈরি অ্যাপ এই সেশনের account, quota বা API balance-এর ওপর নির্ভর করবে না।
- লাইসেন্স অনুযায়ী বিনামূল্যে স্থানীয়ভাবে ব্যবহারযোগ্য উপাদান বাছাই করো। অর্থপ্রদানের প্রয়োজন হলে কোনো সেবা চালু না করে কারণ জানাও।
- বিজ্ঞাপন, analytics, telemetry, remote crash reporting, remote admin, hidden access, auto-upload ও silent cloud fallback থাকবে না।
- release app-এর merged manifest-এ INTERNET permission থাকবে না। network permission বাদ দিলেই সব গোপনীয়তা নিশ্চিত হয়েছে ধরে নেবে না; SDK, অন্য service-এ পাঠানো তথ্য এবং IPC আচরণও যাচাই করবে।
- মডেল প্রথমে কম্পিউটার বা বিশ্বস্ত উৎস থেকে এনে manual local import করার ব্যবস্থা রাখো। app-এর সাধারণ runtime-এ model download বা automatic update দরকার হবে না।
- কোনো প্রয়োজনীয় মডেল অনুপস্থিত থাকলে পরিষ্কারভাবে জানাবে এবং স্থানীয়ভাবে বসানোর নির্দেশ দেবে। তখন cloud fallback করবে না।
- অ্যাপ তৈরির পরীক্ষায় সাধারণ বা synthetic নমুনা ব্যবহার করো। আমার ব্যক্তিগত নথি তোমার সেশনে আপলোড করানোকে নির্মাণের শর্ত বানাবে না।

## ৩. Android implementation ও কাজের পরিবেশ

- native Android app-কে অগ্রাধিকার দাও। default হিসেবে Kotlin ও Jetpack Compose বিবেচনা করো; স্থানীয় AI runtime, document parser ও encrypted persistence-এর বাস্তব compatibility যাচাই করে stack ঠিক করো।
- পুরোনো version number বা dependency আন্দাজ করে বসাবে না। প্রয়োজনমতো official documentation, লাইসেন্স ও compatibility যাচাই করো।
- শুরুতে দেখো এই পরিবেশে source files লেখা, Android SDK/Gradle দিয়ে build, APK signing এবং tests চালানো যায় কি না। যা সম্ভব ও যা সম্ভব নয় আলাদা করে জানাও।
- browser preview, HTML page বা Claude Artifact-কে installable Android APK হিসেবে উপস্থাপন করবে না।
- APK build এখানে সম্ভব না হলে পূর্ণ source project, reproducible build scripts ও বাংলা setup guide দাও। কম্পিউটারে প্রয়োজনীয় ধাপের বাস্তব নির্দেশ দেবে; fake APK বা fake download link দেবে না।
- app সাধারণ Android sandbox-এ চলবে। root, Device Admin, Accessibility service, contacts, SMS, account access বা অপ্রয়োজনীয় broad storage permission চাইবে না।

## ৪. স্থানীয় AI, মডেল ও performance

- বাংলা ব্যাখ্যা, programming/code understanding এবং চারটি প্রধান উৎসভাষা থেকে বাংলা অনুবাদের জন্য উপযুক্ত স্থানীয় মডেল পরীক্ষা করে বাছাই করো। Gemma-এর mobile variants বা অন্য উপযুক্ত মডেল candidate হতে পারে; নাম দেখে মান নিশ্চিত ধরে নেবে না।
- মডেলের official source, exact version, license, actual file size, runtime requirements এবং সত্যিকারের file checksum নথিবদ্ধ করো। যাচাই হয়নি এমন link বা checksum বানাবে না।
- সব মডেল একসঙ্গে RAM-এ তুলবে না। OCR, speech, translation ও reasoning প্রয়োজন অনুযায়ী load/unload করো। memory pressure, cancellation ও thermal throttling সামলাও।
- হালকা মডেল দিয়ে কাজ চললেও জটিল কোডে অনিশ্চয়তা স্পষ্ট থাকবে। বড় অনলাইন AI-এর সমান নির্ভুলতা বা সব ভুল ধরার দাবি করবে না।
- বাস্তব পরীক্ষায় startup time, response latency, peak memory, storage ও sustained processing মাপো। অন্য ফোনের benchmark-কে আমার S24 Ultra-এর পরীক্ষার ফল বলবে না।

## ৫. একক ব্যবহারকারী, authentication ও নির্দেশের নিয়ন্ত্রণ

- অ্যাপ শুরুতে locked থাকবে। একক owner setup থাকবে; multi-user account, online login বা OTP দরকার নেই।
- Android-এর নিজস্ব strong biometric authentication ও Keystore-সুরক্ষিত encryption ব্যবহার করো। biometric-এর সীমা এবং ফোনে অন্য কারও enrolled biometric থাকলে তার প্রভাব বুঝিয়ে দাও।
- প্রয়োজনমতো app-specific PIN/passphrase ও নিরাপদ offline recovery ব্যবস্থা রাখো। PIN বা recovery secret plaintext-এ, source code-এ বা logs-এ রাখবে না। ভুল চেষ্টায় delay/rate limit থাকবে; নিজে থেকে আমার data মুছবে না।
- voice recognition কাজের command input। শুধু কণ্ঠস্বর মিলে যাওয়াকে owner authentication হিসেবে ধরবে না; বলা PIN-ও একমাত্র security factor হবে না।
- unlock করা এবং কাজ শুরু করার নির্দেশ আলাদা। ফাইল import-এর পর নিজে থেকে পুরো অনুবাদ, AI analysis বা code modification শুরু হবে না।
- আমি বাটন, লিখিত নির্দেশ বা authenticated voice session দিয়ে নির্দিষ্ট কাজ বললে তার প্রয়োজনীয় processing শুরু হবে। একটি অনুমোদিত দীর্ঘ কাজ পরপর chunk চালাতে পারবে; সেটি নতুন কাজের অনুমতি নয়।
- সব দীর্ঘ কাজে pause, cancel এবং resume থাকবে। cancelled কাজের queued অংশ পরে নিজে থেকে চালু হবে না; app/phone restart-এর পরে pending কাজ দেখিয়ে আমার resume নির্দেশের অপেক্ষা করবে।
- inactivity ও screen-lock অবস্থার জন্য session policy থাকবে। নতুন প্রশ্ন, নতুন task বা private data access-এর আগে প্রয়োজনীয় authentication বজায় রাখো।

## ৬. ফাইল গ্রহণ ও মূল তথ্য সংরক্ষণ

- PDF, TXT, DOCX, EPUB, CSV/XLSX, PPTX, JPG/PNG এবং সাধারণ source-code files-এর জন্য support matrix দাও। যেসব format তখন কার্যকর নয় সেগুলো সৎভাবে unsupported/pending বলো; ভবিষ্যৎ support-কে বর্তমান সুবিধা দেখাবে না।
- ব্যবহারকারী নির্বাচিত ফাইলের জন্য Android file picker এবং ছবি নেওয়ার জন্য প্রয়োজনভিত্তিক camera permission ব্যবহার করো। অন্য ফাইল বা clipboard নিজে থেকে পড়বে না।
- original file অপরিবর্তিত রাখো। extracted text, user correction, translation, AI explanation ও audio আলাদা version হিসেবে রাখো।
- পৃষ্ঠা, paragraph, heading, chapter, table, footnote এবং code location সংরক্ষণ করো। original viewer থেকে উত্তরের উৎস খোলা যাবে।
- malformed, password-protected, oversized বা unsupported file পরিষ্কারভাবে সামলাও। macros, formulas, embedded scripts, external links বা uploaded executable চালাবে না।
- extraction/OCR-এ কিছু পাওয়া না গেলে আন্দাজ করে পূরণ করবে না। user correction থাকলে original-এর সঙ্গে তা আলাদা করে চিহ্নিত রাখো।

## ৭. ৫০০–১,০০০ পৃষ্ঠা এবং আরও বড় PDF

- ৫০০, ৬০০ এবং ১,০০০ পৃষ্ঠার PDF-কে acceptance-test target ধরো। প্রযুক্তিগতভাবে আরও বড় ফাইল সমর্থিত হলে গ্রহণ করা যাবে; unlimited capacity দাবি করবে না।
- page count, file size, scan resolution ও memory আলাদা বিবেচনা করো। পুরো PDF একবারে RAM বা AI context-এ তোলার চেষ্টা করবে না।
- text PDF থেকে text extraction করো; প্রয়োজনীয় scanned pages-এ local OCR করো। page/chapter অনুযায়ী chunk processing, local search/index এবং persistent job checkpoints রাখো।
- সম্পূর্ণ অনুবাদের নির্দেশ দিলে সব প্রাসঙ্গিক লেখা অনুবাদ হবে। summary, প্রথম কিছু পৃষ্ঠা বা নির্বাচিত অংশ দিয়ে full translation complete দেখাবে না।
- মোট, সম্পন্ন, pending, failed এবং unreadable পৃষ্ঠা/অংশ দেখাবে। শেষ হওয়ার পরে page/section coverage report দাও, যাতে বাদ পড়া অংশ বোঝা যায়।
- কাজ থামলে completed অংশ আবার অকারণে process করবে না। remaining অংশ আমার resume নির্দেশে চালাবে। low storage, battery, memory বা তাপের সমস্যায় সতর্কভাবে pause/recover করবে।
- document নিয়ে প্রশ্নের জন্য প্রয়োজনীয় অংশ স্থানীয়ভাবে retrieve করো। একটি retrieved অংশকে পুরো বই যাচাইয়ের প্রমাণ বলবে না; ব্যাপক প্রশ্নে পর্যাপ্ত অনুসন্ধান না হলে সীমা জানাও।
- viewer-এর physical page number ও বইয়ে printed page number আলাদা হতে পারে। mapping রাখো; অস্পষ্ট হলে আমাকে জিজ্ঞেস করবে।

## ৮. OCR, ছবির লেখা ও code screenshot

- ইংরেজি/Latin code এবং আরবি, উর্দু, হিন্দি ও বাংলার জন্য প্রয়োজনীয় স্থানীয় OCR সমর্থন যাচাই করো। একটি OCR SDK সব script সমর্থন করে ধরে নেবে না।
- মূল ছবি এবং extracted text/code পাশাপাশি দেখাও। zoom, crop, rotation এবং প্রয়োজনে পুনরায় ছবি নেওয়ার সুবিধা রাখো।
- code-এর case, punctuation, quotes, brackets, operators, spacing এবং indentation সংরক্ষণে বিশেষ নজর দাও। 0/O, 1/l/I বা অস্পষ্ট symbol নীরবে অনুমান করবে না।
- সন্দেহজনক অংশ highlight করে আমাকে সংশোধন/নিশ্চিত করতে দাও। OCR-এর ভুলকে নিশ্চিত programming error বলে উপস্থাপন করবে না।
- ছবিতে থাকা line number এবং extraction-এর line number পৃথক হলে সেটি চিহ্নিত করো। source position বা image region দিয়ে সমস্যার জায়গা দেখাও।

## ৯. কোড বোঝানো, ভুল খোঁজা ও পরিবর্তনের প্রস্তাব

- Python, JavaScript/TypeScript, Java, C/C++, PHP, SQL, HTML/CSS, Bash/Shell এবং অন্যান্য সমর্থিত ভাষার code গ্রহণ করো। ভাষা ও version অস্পষ্ট হলে জানতে চাও।
- আমি code দিলে বাংলায় বোঝাবে: উদ্দেশ্য, গুরুত্বপূর্ণ লাইন, function, variable, parameter, data flow ও dependency-এর কাজ।
- সমস্যা দেখালে location, কারণ, সম্ভাব্য প্রভাব, নিশ্চিততার ভিত্তি, কতটুকু পরিবর্তন দরকার এবং ঠিক কী replacement লিখতে হবে দেখাও। পুরোনো ও প্রস্তাবিত নতুন code-এর diff রাখো।
- syntax problem, logical problem, security concern এবং missing context আলাদা করে বোঝাও। অতিরিক্ত files, error message, framework version বা environment প্রয়োজন হলে কারণসহ চাও।
- original code-এর variable/function name, keyword ও logic বাংলায় অনুবাদ করে পরিবর্তন করবে না। মূল code, সংশোধনের প্রস্তাব ও শেখানোর example আলাদা থাকবে।
- নিরাপদ local parser/static checks যুক্ত করা যেতে পারে। uploaded code, shell command, website, network scan বা exploit app নিজে চালাবে না। code explanation কোনো execution permission নয়।
- predicted output ও actually verified result পৃথক রাখো। যে test চলেনি সেটি passed বলবে না। original file-এর ওপর স্বয়ংক্রিয় overwrite করবে না।

## ১০. বাংলায় পূর্ণ অনুবাদ ও উৎসের প্রতি বিশ্বস্ততা

- target language স্থায়ীভাবে বাংলা। Chinese বা অন্য target-language workflow দরকার নেই। বাংলা source হলে অযথা আবার অনুবাদ করবে না।
- original এবং বাংলা অনুবাদ পাশাপাশি দেখাবে। names, numbers, dates, quotations, references, units, formulas ও technical terms সংরক্ষণ করবে।
- অনুবাদ, summary ও AI explanation আলাদা mode; একটির ফল অন্যটির নামে দেখাবে না। মূল উৎসে নেই এমন কথা অনুবাদের মধ্যে যোগ করবে না।
- দীর্ঘ document-এর terminology consistent রাখতে local glossary ব্যবহার করো। ধর্মীয় ও সূক্ষ্ম ভাষার ক্ষেত্রে সন্দেহ বা একাধিক অর্থ হলে মূল শব্দসহ অনিশ্চয়তা জানাও।
- পূর্ণ বাংলা translation স্থানীয়ভাবে TXT/DOCX/PDF-এ export করার ব্যবস্থা রাখো। Unicode Bengali ও Arabic/Urdu direction, table ও footnote যাচাই করো। layout বদলালে original page mapping বজায় রাখো; exact pagination দাবি করবে না।

## ১১. পড়ে শোনানো ও reading position

- বাংলা translation, মূল বাংলা document এবং বাংলা explanation স্বাভাবিক কণ্ঠে পড়বে। code-এর জন্য আলাদা literal-reading ও line-by-line Bengali explanation mode রাখো।
- offline Bengali voice-এর সমর্থন ও মান যাচাই করো। network-required voice বা cloud speech-এ নীরবে বদলাবে না। বাংলা voice না চললে সীমা ও প্রয়োজনীয় স্থানীয় setup জানাও।
- play, pause, resume, stop, speed, repeat, previous/next এবং page/chapter/line নির্বাচন থাকবে। পড়ার সঙ্গে সংশ্লিষ্ট text highlight করো।
- বড় audio segment ধরে তৈরি/চালাও। পুরো বইয়ের audio RAM-এ তুলবে না। audio cache-এর সীমা ও user-controlled deletion থাকবে।
- file/version/page/paragraph/sentence বা code line অনুযায়ী reading position সংরক্ষণ করো। প্রশ্ন শেষে আমার resume নির্দেশে একই জায়গা থেকে পড়বে।
- আগে অনুমোদিত playback-এর background, screen-off ও headphone controls বাস্তব Android নিয়ম মেনে রাখো। foreground notification-এ private text/filename প্রকাশ করবে না। নতুন private task বা query lock screen থেকে authentication ছাড়া শুরু হবে না।

## ১২. পড়ার মাঝখানে প্রশ্ন ও শেখা

- পড়া থামিয়ে আমি শব্দ, বাক্য বা code line সম্পর্কে লিখিতভাবে, নির্বাচন করে অথবা voice দিয়ে প্রশ্ন করতে পারব। উত্তর বাংলায় text ও চাইলে audio-তে দেবে।
- current reading cursor এবং আমার selection দিয়ে প্রশ্নের target বোঝো। target অস্পষ্ট হলে ছোট clarification করো; নিজের মতো অন্য শব্দ বেছে নেবে না।
- উত্তরে file/page/paragraph/line বা image region-এর source reference থাকবে; tap করলে original খুলবে।
- source quotation, বাংলা translation, document থেকে inference এবং AI-এর সাধারণ explanation পৃথকভাবে চিহ্নিত করো। document-এ নেই এমন তথ্য document-এর বক্তব্য হিসেবে বলবে না।
- bookmarks, ব্যক্তিগত notes, saved Q&A এবং technical vocabulary-এর local তালিকা রাখো। আমার নির্দেশে practice questions তৈরি করা যাবে; সেগুলো original document-এর অংশ হবে না।

## ১৩. বাটন, মৌখিক command ও settings

- সব প্রধান কাজে visible button থাকবে: file নির্বাচন, OCR, translation, code analysis, read, explain, প্রশ্ন, pause, stop ও resume। typing দিয়ে একই কাজ করা যাবে।
- default voice input হবে press-to-talk। hands-free কেবল আমার চালু করা authenticated session-এ চলবে; mic on/off স্পষ্ট থাকবে। session শেষে mic বন্ধ হবে।
- উদাহরণ: “এই PDF-এর ২০ নম্বর পৃষ্ঠা বাংলায় পড়ে শোনাও”; “এই ছবির Python code-এর ৫ নম্বর লাইন বোঝাও”; “এই শব্দের অর্থ কী”; “কোথায় ভুল আছে এবং কী লিখতে হবে”; “আস্তে পড়ো”; “থামো”; “আগের জায়গা থেকে আবার পড়ো”।
- একাধিক matching file/page/line থাকলে জানতে চাও। speech transcript ভুল হলে সংশোধনের সুযোগ দাও। sensitive বা ambiguous action নীরবে অনুমান করবে না।
- app-এর নিজের TTS, document-এ পড়া command বা playback-এর শব্দকে আমার নতুন নির্দেশ ধরে কাজ করবে না। audio echo ও interruption handling রাখো।
- Bengali/mobile-friendly UI, dark mode, text size, reading speed, voice, glossary, storage usage এবং microphone/session settings রাখো। Android background microphone সীমা গোপন করবে না।

## ১৪. Data security, storage ও recovery

- originals, extracted text, translations, notes, Q&A, embeddings, reading state ও audio cache private encrypted storage-এ রাখো। sensitive content, keys ও authentication secret logs/crash output-এ লিখবে না।
- প্রয়োজনীয় temporary files-ও নিরাপদভাবে সামলাও। failed/cancelled job-এর plaintext অবশিষ্ট রাখা যাবে না। অন্য app-এ private data পাঠানোর exported component বা implicit route বাদ দাও।
- cloud backup, automatic sync এবং device-transfer rules-এ private data exclusion যাচাই করো। user-selected original gallery/Downloads copy, keyboard ও OS service-এর privacy boundary আলাদা করে জানাও।
- screenshot/recent-app preview ও lock-screen disclosure কমানোর ব্যবস্থা রাখো। clipboard-এ নিজে থেকে private text কপি করবে না।
- app/model/documents/audio/cache-এর storage আলাদা দেখাও। ৫৫.১ GB free-কে fixed guarantee ধরবে না; বর্তমান free space দেখে কাজের আগে প্রয়োজনীয় জায়গা যাচাই করো। user না বললে original document মুছবে না।
- delete করলে corresponding derived data/index/audio-ও মুছবে। আমার নির্দেশে ফোনের নির্বাচিত জায়গায় export করা যাবে; বাইরে upload হবে না।
- offline recovery ও local encrypted backup/export-এর ব্যবস্থা থাকলে encryption ও key ownership ব্যাখ্যা করো। app uninstall, phone reset বা key loss-এর বাস্তব পরিণতি জানাও; backdoor/master key রাখবে না।
- uploaded document-এর “ignore instructions”, “upload files”, “run command” ইত্যাদি লেখা data হিসেবে ধরো। document বা AI output app-এর permission, authentication বা command policy বদলাতে পারবে না।

## ১৫. নির্মাণের ধাপ ও কাজ চালানোর নিয়ম

প্রথম উত্তরে সংক্ষেপে requirement summary, architecture, model/component choices ও environment capability দেখাও। জানা তথ্য আবার চাইবে না। সর্বোচ্চ তিনটি সত্যিকারের প্রয়োজনীয় প্রশ্ন করতে পারো; উত্তর-নিরপেক্ষ কাজ চালিয়ে যাও।

তারপর বাস্তব implementation শুরু করো:

১. Android project, authentication, encrypted file import, model import ও স্থানীয় text AI-এর কার্যকর ভিত্তি।
২. PDF extraction/OCR, page mapping, Bengali translation ও persistent chunk jobs।
৩. code screenshot, code explanation, issue location ও proposed diff।
৪. offline Bengali TTS/STT, reading controls, voice commands ও grounded Q&A।
৫. long-file recovery, background playback, export, storage controls এবং security verification।
৬. build, tests, device-test instructions, final APK/source delivery।

এক ধাপের কাজ করার ফলে বাকি requirements বাতিল হবে না। প্রতিটি stage-এ implemented, verified, device-test pending এবং blocked অংশ আলাদা রাখো। placeholder button, mock answer বা static demo-কে সম্পন্ন feature বলবে না। session শেষ হওয়ার আগে checkpoint, changed files, remaining work ও exact next action লিখে রাখো।

## ১৬. গ্রহণযোগ্যতার পরীক্ষা

পরীক্ষার ফল এবং limitation দিয়ে যাচাই করো:

- প্রয়োজনীয় model বসানোর পরে airplane mode-এ translation, code explanation, Bengali voice ও Q&A চলে কি না।
- Internet চালু থাকলেও app ও ব্যবহৃত service থেকে private data/telemetry বাইরে যাচ্ছে কি না; airplane-mode test-ই zero-transmission-এর একমাত্র প্রমাণ নয়।
- invalid authentication-এ data inaccessible; app খুললেও explicit task command ছাড়া AI job শুরু হয় না।
- code screenshot-এ symbols/indentation ঠিক থাকে; OCR uncertainty দেখানো হয়; issue/diff source-এর সঙ্গে মেলে; uploaded code execute হয় না।
- চার প্রধান ভাষার representative sample থেকে বাংলা translation; source fidelity ও terminology যাচাই।
- ৫০০ ও ১,০০০ পৃষ্ঠার text/scanned PDF-এর coverage, partial failures, low-memory handling এবং cancel/restart/resume। synthetic test-কে বাস্তব বইয়ের মান যাচাইয়ের সমতুল্য বলবে না।
- voice দিয়ে সঠিক file/page/line নির্বাচন, reading interrupt, source-linked answer ও একই জায়গায় resume। নিজের TTS থেকে command চালু হয় না।
- Unicode Bengali/RTL export, table/reference retention, background playback, private notification এবং audio cache limits।
- encrypted storage, backup exclusion, minimal permissions, prompt-injection resistance ও user-requested deletion।
- actual APK build/signature এবং S24 Ultra-তে installation/performance। এই ফোনে test চালাতে না পারলে device test pending বলবে; গতি, battery বা memory-এর সংখ্যা বানাবে না।

## ১৭. চূড়ান্ত deliverables

- সম্ভব হলে পরীক্ষিত, signed, non-debuggable release APK; শুধু debug build হলে সেটি স্পষ্ট label করো।
- পূর্ণ source project, build scripts, exact dependency/model manifest, license notices ও reproducible build instructions।
- প্রয়োজনীয় local model files বা verified official source এবং বাংলা manual-import guide। বড় model আলাদা package হলে কীভাবে বসাতে হবে জানাও।
- প্রথম setup, fingerprint/PIN, file import, PDF translation, code explanation, voice use, pause/resume, export ও recovery-এর বাংলা ব্যবহারবিধি।
- বাস্তব storage requirements, পরীক্ষার report, known limitations ও device-test checklist।
- signing/recovery secrets আমার নিয়ন্ত্রণে থাকবে; chat/source/public repository-তে secret প্রকাশ করবে না। remote control বা provider-controlled hidden access থাকবে না।

আমি নিরাপত্তা, বাংলা ব্যাখ্যার মান, source fidelity এবং কার্যকর ফলকে অগ্রাধিকার দিচ্ছি। অসম্ভব দাবি, fake test বা fake download link দিয়ে কাজ শেষ করবে না। environment সীমা থাকলে তা জানিয়ে প্রয়োজনীয় বাস্তব কাজ যতদূর সম্ভব সম্পন্ন করো। এখন requirements যাচাই করে প্রথম কার্যকর implementation ধাপ শুরু করো।
