package app.personal.banglaai.library

/**
 * What is known — and NOT known — about each bundled book file. The SHA-256 pins the exact bytes shipped in the APK (a test fails if the file changes without this record changing).
 * [checkedAgainstOriginal] is false whenever nobody has compared the text word-for-word with the publisher's file: the structure markers only prove the file starts/ends where it should and has the expected number of chapter headings.
 */
data class BookRecord(
    val id: String, val edition: String, val source: String, val licenseBasis: String,
    val sha256: String, val startMarker: String, val endMarker: String,
    val minHeadings: Int, val headingRegex: String, val limitation: String, val checkedAgainstOriginal: Boolean = false,
)

object BookManifest {
    private const val PD_US = "লেখক ও অনুবাদক ১৯২৯-এর আগের প্রকাশনা — মার্কিন যুক্তরাষ্ট্রে সর্বজনীন-ডোমেইন; আপনার দেশের আইন নিজে দেখে নিন (এটি আইনি পরামর্শ নয়)"
    val all = listOf(
        BookRecord("prince", "W. K. Marriott অনুবাদ, ১৯০৮", "Project Gutenberg", PD_US,
            "c18899e57c8b42baeb2fa8bd7901de46972e37581c05dd860c9113a095f27faf", "CHAPTER I. HOW MANY KINDS OF PRINCIPALITIES", "should be born, not in Lucca, but in Macedonia or Rome.", 26, "(?im)^\\s*chapter\\s+[ivxlc]+\\b", "ভূমিকা ও জীবনী-অংশসহ; ২৬টি অধ্যায় আছে"),
        BookRecord("artofwar", "Lionel Giles অনুবাদ, ১৯১০", "Project Gutenberg", PD_US,
            "3494fb72a639ce7849db65b087110cf511bd0fb5441f784f76a4b48c0f9891f3", "THE OLDEST MILITARY TREATISE", "Spies are a most important element in war", 13, "(?im)^\\s*chapter\\s+[ivxlc]+\\b", "১৩টি অধ্যায় + টীকা; সূচিতে শিরোনাম দুবার আসে"),
        BookRecord("gracian", "Joseph Jacobs অনুবাদ, ১৮৯২", "Internet Archive (স্ক্যান-OCR)", PD_US,
            "7c6463a576c41147b3943f458fa79c39283f8aabc76247220504afb1cfe7e10e", "PREFACE", "THE END", 0, "(?m)^\\s*\\d{1,3}\\.\\s", "OCR-ভুল আছে; ৩০০ সূত্রের সব নম্বর নির্ভরযোগ্যভাবে শনাক্ত হয় না; অধ্যায় নয়, অনুচ্ছেদ-গুচ্ছ হিসেবে ভাগ হয়"),
        BookRecord("arthashastra", "R. Shamasastry অনুবাদ, ১৯১৫ (৪র্থ সংস্করণ, মহীশূর ১৯৫১)", "Internet Archive (স্ক্যান-OCR)",
            "অনুবাদ ১৯১৫ — সর্বজনীন-ডোমেইন; কিন্তু ১৯৫১-র সংস্করণের ভূমিকা/টীকার স্বত্ব যাচাই হয়নি — পুনর্বণ্টনের আগে যাচাই করুন",
            "89e738236b146ef31f1823b902d01c8f8c887a2fb397a5f44f8520e23153ff06", "INTRODUCTORY NOTE", "Vishnu Gupta himself has made (this) Sutra and commentary.", 15, "(?im)^\\s*book\\s+[ivxlc]+\\b", "OCR-ভুল আছে; ১৫টি ‘বুক’ আছে, প্রতিটি অধ্যায় আলাদাভাবে যাচাই হয়নি"),
        BookRecord("callieres", "A. F. Whyte অনুবাদ, ১৯১৯", "Project Gutenberg", PD_US,
            "8039477f0dc9c1b75ab353b141ddc2e9b0a95ba608cbc331272b2fe2af273032", "ON THE MANNER OF", "no profession are disappointments so amply outweighed", 0, "(?m)^\\s*[IVX]+\\.\\s", "অধ্যায়-শিরোনাম সনাক্ত হয় না, তাই অনুচ্ছেদ-গুচ্ছ হিসেবে ভাগ হয়"),
        BookRecord("discourses", "Ninian Hill Thomson অনুবাদ, ১৮৮৩", "Project Gutenberg", PD_US,
            "0e78c24294829362f07c08b37170abed7148c3089dd19c4d72c2cb26fa26dbf4", "Discourses on the First Decade", "THE END.", 142, "(?im)^\\s*chapter\\s+[ivxlc]+\\b", "প্রথম দশক (লিভির ১ম–১০ম বই) অবলম্বনে ৩ খণ্ড; ১৪২টি অধ্যায় (৬০+৩৩+৪৯)"),
        BookRecord("crowd", "ইংরেজি অনুবাদ, ১৮৯৬ (T. Fisher Unwin)", "Project Gutenberg", PD_US,
            "f13970695c44829634cf1fcff9e8c2770ce676245dee158e2f4c4bcf364c35b9", "THE CROWD", "such is the cycle of the life of a people.", 13, "(?im)^\\s*chapter\\s+[ivxlc]+\\b", "৩ বই, ১৩ অধ্যায়; অনুবাদকের নাম ফাইলে নেই"),
        BookRecord("onwar", "J. J. Graham অনুবাদ (১৮৭৩), F. N. Maude-র ১৯০৯ সংস্করণ", "Project Gutenberg", PD_US,
            "e1ea7947c9eee49cc0a936823ed2273a25279cbcc8f0f050f0f4bd0458b05ce5", "TRANSLATED BY COLONEL J.J. GRAHAM", "he who neglects the possible in quest of the impossible is a fool.", 8, "(?im)^\\s*book\\s+[ivxlc]+\\b", "৮ বই; ফাইল ৮ম বইয়ের ৯ম অধ্যায়ে শেষ — মূল গ্রন্থের শেষ অংশের সাথে শব্দে শব্দে মেলানো হয়নি"),
    )
    fun of(id: String) = all.first { it.id == id }
}
