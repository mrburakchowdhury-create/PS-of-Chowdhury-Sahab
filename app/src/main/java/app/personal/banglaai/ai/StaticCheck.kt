package app.personal.banglaai.ai

/**
 * Local, deterministic static checks on source code. NOTHING is executed, compiled, imported or sent anywhere.
 *
 * What this is: a tokenizer-level scan (comments and string literals are masked first, so brackets/keywords inside them do not confuse it) plus a list of
 * well-known risky patterns per language. Every finding has a [Conf]idence: [Conf.CERTAIN] = the text itself is wrong by the language's grammar rules we check
 * (e.g. a bracket without partner, a Python block header without ':', invalid JSON), [Conf.LIKELY] = a pattern that is risky in almost every context,
 * [Conf.POSSIBLE] = a hint that depends on context the app cannot see.
 *
 * What this is NOT: a compiler or type-checker. Missing imports, wrong types, runtime errors, framework misuse and business logic are NOT found; [Result.notChecked]
 * says so, and the screen shows it. These findings are the program's own result and are kept apart from the AI's explanation (which may be wrong).
 */
object StaticCheck {
    enum class Lang(val bn: String) { PYTHON("Python"), JS("JavaScript/TypeScript"), JAVA("Java/Kotlin"), C("C/C++"), PHP("PHP"), SQL("SQL"), HTML("HTML"), CSS("CSS"), SHELL("Bash/Shell"), JSON("JSON"), OTHER("অজানা/অন্য") }
    enum class Kind(val bn: String) { SYNTAX("সিনট্যাক্স"), LOGIC("লজিক"), SECURITY("নিরাপত্তা-ঝুঁকি"), CONTEXT("প্রসঙ্গ/মান") }
    enum class Conf(val bn: String) { CERTAIN("নিশ্চিত (লেখাতেই ভুল)"), LIKELY("সম্ভবত (প্রচলিত ঝুঁকি)"), POSSIBLE("সম্ভাব্য (প্রসঙ্গ দেখা দরকার)") }

    data class Finding(
        val line: Int, val kind: Kind, val conf: Conf, val title: String, val why: String, val fix: String,
        val original: String, val replacement: String? = null,
    )
    data class Result(val lang: Lang, val findings: List<Finding>, val notChecked: String)

    private const val NOT_CHECKED = "এটি কম্পাইলার/টাইপ-চেকার নয়। কোড চালানো বা কম্পাইল করা হয়নি; import/টাইপ/রানটাইম ত্রুটি, ফ্রেমওয়ার্ক-ব্যবহার ও ব্যবসায়িক লজিক এই চেকে ধরা পড়ে না। ফল না-পাওয়া মানে কোড ঠিক আছে — তা নয়।"

    // ------------------------------------------------------------------ language detection

    fun detect(fileName: String?, code: String): Lang {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        when (ext) {
            "py" -> return Lang.PYTHON; "js", "jsx", "ts", "tsx", "mjs", "cjs" -> return Lang.JS; "java", "kt", "kts" -> return Lang.JAVA
            "c", "h", "cpp", "cc", "hpp", "cxx" -> return Lang.C; "php" -> return Lang.PHP; "sql" -> return Lang.SQL; "html", "htm" -> return Lang.HTML
            "css" -> return Lang.CSS; "sh", "bash", "zsh" -> return Lang.SHELL; "json" -> return Lang.JSON
        }
        val head = code.trimStart().take(400); val first = head.lineSequence().firstOrNull().orEmpty()
        return when {
            first.startsWith("#!") && Regex("sh\\b|bash").containsMatchIn(first) -> Lang.SHELL
            first.startsWith("#!") && first.contains("python") -> Lang.PYTHON
            head.startsWith("<?php") -> Lang.PHP
            Regex("(?i)^<!doctype html|^<html|^<div|^<body|^<head|^<script|^<p[ >]").containsMatchIn(head) -> Lang.HTML
            (head.startsWith("{") || head.startsWith("[")) && runCatching { Json.valid(code) }.getOrDefault(false) -> Lang.JSON
            Regex("(?im)^\\s*(select|insert\\s+into|update|delete\\s+from|create\\s+table|drop\\s+table|alter\\s+table)\\b").containsMatchIn(head) -> Lang.SQL
            Regex("(?m)^\\s*(def |class |import |from \\S+ import |elif |print\\()").containsMatchIn(code.take(2000)) && !code.take(2000).contains(";") -> Lang.PYTHON
            Regex("#include\\s*<|\\bint\\s+main\\s*\\(").containsMatchIn(code.take(2000)) -> Lang.C
            Regex("\\b(public|private)\\s+(static\\s+)?(class|void|int|String)\\b|\\bfun\\s+\\w+\\(|System\\.out").containsMatchIn(code.take(2000)) -> Lang.JAVA
            Regex("\\b(const|let|var|function)\\b|=>|console\\.log").containsMatchIn(code.take(2000)) -> Lang.JS
            Regex("(?m)^\\s*[.#]?[\\w-]+\\s*\\{\\s*$").containsMatchIn(code.take(2000)) && code.contains(":") && code.contains(";") && !code.contains("function") -> Lang.CSS
            else -> Lang.OTHER
        }
    }

    // ------------------------------------------------------------------ masking (comments / strings out of the way)

    private class Masked(val lines: List<String>, val raw: List<String>, val findings: MutableList<Finding>)

    private data class Syntax(val line: List<String>, val block: List<Pair<String, String>>, val quotes: String, val triple: Boolean, val sqlDoubleQuote: Boolean = false, val backtickMultiline: Boolean = false)

    private fun syntaxOf(l: Lang) = when (l) {
        Lang.PYTHON -> Syntax(listOf("#"), emptyList(), "\"'", true)
        Lang.JS -> Syntax(listOf("//"), listOf("/*" to "*/"), "\"'`", false, backtickMultiline = true)
        Lang.JAVA, Lang.C -> Syntax(listOf("//"), listOf("/*" to "*/"), "\"'", false)
        Lang.PHP -> Syntax(listOf("//", "#"), listOf("/*" to "*/"), "\"'", false)
        Lang.SQL -> Syntax(listOf("--"), listOf("/*" to "*/"), "'\"", false)
        Lang.SHELL -> Syntax(listOf("#"), emptyList(), "\"'", false)
        Lang.HTML -> Syntax(emptyList(), listOf("<!--" to "-->"), "", false)
        Lang.CSS -> Syntax(emptyList(), listOf("/*" to "*/"), "\"'", false)
        else -> Syntax(listOf("//", "#"), listOf("/*" to "*/"), "\"'", false)
    }

    /** Replaces comment text and string contents by spaces (same length/positions) and reports unterminated strings/comments. */
    private fun mask(code: String, lang: Lang): Masked {
        val syn = syntaxOf(lang); val raw = code.split("\n").map { it.removeSuffix("\r") }
        val out = ArrayList<String>(); val findings = ArrayList<Finding>()
        var blockEnd: String? = null; var blockStart = 0; var tripleQuote: String? = null; var tripleStart = 0; var tplStart = 0; var inTemplate = false
        for ((i, ln) in raw.withIndex()) {
            val sb = StringBuilder(ln); var p = 0
            while (p < ln.length) {
                if (blockEnd != null) { val e = ln.indexOf(blockEnd, p); if (e < 0) { for (k in p until ln.length) sb.setCharAt(k, ' '); p = ln.length } else { for (k in p until e + blockEnd.length) sb.setCharAt(k, ' '); p = e + blockEnd.length; blockEnd = null }; continue }
                if (tripleQuote != null) { val e = ln.indexOf(tripleQuote, p); if (e < 0) { for (k in p until ln.length) sb.setCharAt(k, ' '); p = ln.length } else { for (k in p until e) sb.setCharAt(k, ' '); p = e + 3; tripleQuote = null }; continue }
                if (inTemplate) { val e = ln.indexOf('`', p); if (e < 0) { for (k in p until ln.length) sb.setCharAt(k, ' '); p = ln.length } else { for (k in p until e) sb.setCharAt(k, ' '); p = e + 1; inTemplate = false }; continue }
                val c = ln[p]
                val lc = syn.line.firstOrNull { ln.startsWith(it, p) }
                if (lc != null && !(lang == Lang.PHP && lc == "#" && ln.startsWith("#[", p))) { for (k in p until ln.length) sb.setCharAt(k, ' '); p = ln.length; continue }
                val bc = syn.block.firstOrNull { ln.startsWith(it.first, p) }
                if (bc != null) { blockEnd = bc.second; blockStart = i + 1; for (k in p until p + bc.first.length) sb.setCharAt(k, ' '); p += bc.first.length; continue }
                if (c in syn.quotes) {
                    if (syn.triple && ln.startsWith("$c$c$c", p)) { tripleQuote = "$c$c$c"; tripleStart = i + 1; for (k in p until p + 3) sb.setCharAt(k, ' '); p += 3; continue }
                    if (c == '`' && syn.backtickMultiline) { inTemplate = true; tplStart = i + 1; p++; continue }
                    var q = p + 1; var closed = false
                    while (q < ln.length) {
                        val d = ln[q]
                        if (d == '\\' && lang != Lang.SQL && !(lang == Lang.SHELL && c == '\'')) { q += 2; continue }
                        if (d == c) { if (lang == Lang.SQL && q + 1 < ln.length && ln[q + 1] == c) { q += 2; continue }; closed = true; break }
                        q++
                    }
                    if (!closed) {
                        val continues = (lang == Lang.PYTHON || lang == Lang.C || lang == Lang.JAVA || lang == Lang.JS || lang == Lang.PHP) && ln.endsWith("\\")
                        val sqlMulti = lang == Lang.SQL || lang == Lang.SHELL || lang == Lang.PHP   // strings may legally span lines there
                        if (!continues && !sqlMulti) findings += Finding(i + 1, Kind.SYNTAX, Conf.CERTAIN, "অসমাপ্ত স্ট্রিং", "`$c` দিয়ে শুরু হওয়া লেখা একই লাইনে বন্ধ হয়নি; ভাষাটি এখানে লাইন-ভাঙা স্ট্রিং অনুমোদন করে না — পুরো ফাইল সিনট্যাক্স-ত্রুটিতে থামে।", "লাইনের শেষে মিলযুক্ত `$c` বসান।", ln.trim())
                        for (k in p + 1 until ln.length) sb.setCharAt(k, ' '); p = ln.length; continue
                    }
                    for (k in p + 1 until q) sb.setCharAt(k, ' '); p = q + 1; continue
                }
                p++
            }
            out += sb.toString()
        }
        if (blockEnd != null) findings += Finding(blockStart, Kind.SYNTAX, Conf.CERTAIN, "অসমাপ্ত মন্তব্য", "মন্তব্য-ব্লক শুরু হয়েছে কিন্তু শেষ হয়নি; এর পরের সব কোড মন্তব্য হয়ে যাবে।", "`$blockEnd` যোগ করুন।", raw.getOrElse(blockStart - 1) { "" }.trim())
        if (tripleQuote != null) findings += Finding(tripleStart, Kind.SYNTAX, Conf.CERTAIN, "অসমাপ্ত তিন-উদ্ধৃতি স্ট্রিং", "`$tripleQuote` বন্ধ হয়নি।", "শেষে `$tripleQuote` বসান।", raw.getOrElse(tripleStart - 1) { "" }.trim())
        if (inTemplate) findings += Finding(tplStart, Kind.SYNTAX, Conf.CERTAIN, "অসমাপ্ত টেমপ্লেট-স্ট্রিং", "ব্যাকটিক ` বন্ধ হয়নি।", "শেষে ` বসান।", raw.getOrElse(tplStart - 1) { "" }.trim())
        return Masked(out, raw, findings)
    }

    // ------------------------------------------------------------------ entry point

    fun check(code: String, lang: Lang): Result {
        if (code.isBlank()) return Result(lang, emptyList(), NOT_CHECKED)
        val f = ArrayList<Finding>()
        if (lang == Lang.JSON) { Json.error(code)?.let { (line, msg) -> f += Finding(line, Kind.SYNTAX, Conf.CERTAIN, "অবৈধ JSON", msg, "উল্লিখিত জায়গাটি ঠিক করুন (কমা/উদ্ধৃতি/ব্র্যাকেট)।", code.lines().getOrElse(line - 1) { "" }.trim()) }; return Result(lang, f, NOT_CHECKED) }
        val m = mask(code, lang); f += m.findings
        if (lang != Lang.HTML) brackets(m, lang, f)
        when (lang) {
            Lang.PYTHON -> python(m, f); Lang.JS -> js(m, f); Lang.JAVA -> java(m, f); Lang.C -> c(m, f); Lang.PHP -> php(m, f)
            Lang.SQL -> sql(m, f); Lang.SHELL -> shell(m, f); Lang.HTML -> html(m, f); else -> {}
        }
        secrets(code.split("\n"), f)
        return Result(lang, f.distinctBy { Triple(it.line, it.title, it.kind) }.sortedWith(compareBy({ it.line }, { it.kind.ordinal })), NOT_CHECKED)
    }

    private fun add(f: MutableList<Finding>, m: Masked, i: Int, kind: Kind, conf: Conf, title: String, why: String, fix: String, replacement: String? = null) {
        f += Finding(i + 1, kind, conf, title, why, fix, m.raw[i].trim(), replacement)
    }

    // ------------------------------------------------------------------ brackets

    private fun brackets(m: Masked, lang: Lang, f: MutableList<Finding>) {
        val pairs = mapOf(')' to '(', ']' to '[', '}' to '{'); val stack = ArrayList<Triple<Char, Int, Int>>()
        for ((i, ln) in m.lines.withIndex()) {
            if (lang == Lang.SQL && ln.trimStart().startsWith("--")) continue
            for ((p, ch) in ln.withIndex()) {
                if (lang == Lang.SHELL && (ch == '[' || ch == ']' || ch == '{' || ch == '}' || ch == '(' || ch == ')') && false) continue
                when (ch) {
                    '(', '[', '{' -> stack += Triple(ch, i, p)
                    ')', ']', '}' -> {
                        if (lang == Lang.SHELL && ch == ')' && (ln.contains("case ") || Regex("^\\s*[^()]*\\)\\s*($|;;|[a-z])").containsMatchIn(ln) && stack.isEmpty())) continue   // case patterns "a)"
                        val top = stack.lastOrNull()
                        if (top == null) add(f, m, i, Kind.SYNTAX, Conf.CERTAIN, "বাড়তি `$ch`", "`$ch`-এর কোনো মিলযুক্ত খোলা বন্ধনী নেই; কম্পাইলার/ইন্টারপ্রেটার এখানে সিনট্যাক্স-ত্রুটি দেবে।", "বাড়তি `$ch` সরান অথবা আগে `${pairs[ch]}` যোগ করুন।")
                        else if (top.first != pairs[ch]) { add(f, m, i, Kind.SYNTAX, Conf.CERTAIN, "ভুল জোড়া: `${top.first}` (লাইন ${top.second + 1}) ↔ `$ch`", "খোলা `${top.first}`-এর জোড়ায় `${when (top.first) { '(' -> ')'; '[' -> ']'; else -> '}' }}` আসার কথা, এসেছে `$ch`।", "বন্ধনীর ধরন মেলান।"); stack.removeAt(stack.lastIndex) }
                        else stack.removeAt(stack.lastIndex)
                    }
                }
            }
        }
        for ((ch, i, _) in stack) add(f, m, i, Kind.SYNTAX, Conf.CERTAIN, "`$ch` বন্ধ হয়নি", "এই `$ch`-এর কোনো মিলযুক্ত বন্ধ-বন্ধনী ফাইলে নেই।", "উপযুক্ত জায়গায় `${when (ch) { '(' -> ')'; '[' -> ']'; else -> '}' }}` বসান।")
    }

    // ------------------------------------------------------------------ Python

    private val pyHeader = Regex("^(async\\s+def|def|class|if|elif|else|for|while|try|except|finally|with|match|case)\\b(.*)$")

    /** Index of the first [ch] that is outside any bracket in the (already masked) text, or -1. */
    private fun topLevel(t: String, ch: Char): Int { var d = 0; for ((i, c) in t.withIndex()) { if (c in "([{") d++ else if (c in ")]}") d = (d - 1).coerceAtLeast(0) else if (c == ch && d == 0) return i }; return -1 }

    private fun python(m: Masked, f: MutableList<Finding>) {
        var depth = 0; val indents = ArrayList<Int>(); indents += 0
        var tabs = 0; var spaces = 0; var prevHeader: Int? = null; var prevHeaderIndent = 0
        for ((i, ln) in m.lines.withIndex()) {
            val raw = m.raw[i]; val code = ln.trimEnd()
            val opens = ln.count { it == '(' || it == '[' || it == '{' }; val closes = ln.count { it == ')' || it == ']' || it == '}' }
            val wasContinuation = depth > 0 || (i > 0 && m.lines[i - 1].trimEnd().endsWith("\\"))
            if (code.isNotBlank() && !wasContinuation) {
                val ind = raw.takeWhile { it == ' ' || it == '\t' }
                if (ind.contains('\t')) tabs++ else if (ind.isNotEmpty()) spaces++
                val width = ind.replace("\t", "        ").length
                prevHeader?.let { h ->
                    if (width <= prevHeaderIndent) add(f, m, h, Kind.SYNTAX, Conf.CERTAIN, "ব্লকের ভেতরে কিছু নেই / indentation কম", "`:`-এ শেষ হওয়া লাইনের পরের লাইনটি ভেতরে সরানো (indent) নয়; Python এখানে `IndentationError: expected an indented block` দেয়।", "পরের লাইনটি ভেতরে সরান, অথবা ফাঁকা ব্লকের জন্য `pass` লিখুন।")
                    prevHeader = null
                }
                if (width > indents.last()) indents += width
                else if (width < indents.last()) {
                    while (indents.size > 1 && width < indents.last()) indents.removeAt(indents.lastIndex)
                    if (width != indents.last()) add(f, m, i, Kind.SYNTAX, Conf.CERTAIN, "indentation আগের কোনো স্তরের সঙ্গে মেলে না", "এই লাইনের বাঁ-দিকের ফাঁকা আগের কোনো ব্লকের স্তরের সমান নয়; Python `IndentationError: unindent does not match` দেয়।", "আগের কোনো ব্লকের সমান ফাঁকা দিন।")
                }
                val t = code.trim(); val h = pyHeader.find(t)
                if (h != null && h.groupValues[1] !in setOf("match", "case")) {
                    val kw = h.groupValues[1]
                    val colon = topLevel(t, ':')
                    val hasColon = colon >= 0 || opens > closes   // a header may continue inside open brackets
                    if (!hasColon) add(f, m, i, Kind.SYNTAX, Conf.CERTAIN, "`$kw` লাইনের শেষে `:` নেই", "Python-এ `$kw`-এর শিরোনাম-লাইন `:` দিয়ে শেষ হয়; নইলে `SyntaxError: expected ':'`।", "লাইনের শেষে `:` যোগ করুন।", raw.trimEnd() + ":")
                    if (t.endsWith(":")) { prevHeader = i; prevHeaderIndent = width }
                    if (kw in setOf("if", "elif", "while") && colon > 0) {
                        val cond = t.substring(kw.length, colon)
                        val eq = topLevel(cond, '=')
                        if (eq >= 0 && cond.getOrNull(eq + 1) != '=' && cond.getOrNull(eq - 1) !in setOf('=', '!', '<', '>', ':') && !cond.contains(":="))
                            add(f, m, i, Kind.SYNTAX, Conf.CERTAIN, "শর্তে `=` (অ্যাসাইনমেন্ট) আছে", "শর্তে তুলনার জন্য `==` লাগে; `=` থাকলে Python `SyntaxError` দেয়।", "`=` বদলে `==` লিখুন।", raw.replaceFirst(cond, cond.substring(0, eq) + "==" + cond.substring(eq + 1)))
                    }
                }
                if (Regex("^def\\s+\\w+\\(.*=\\s*(\\[\\]|\\{\\}|set\\(\\)|dict\\(\\)|list\\(\\))").containsMatchIn(t))
                    add(f, m, i, Kind.LOGIC, Conf.LIKELY, "mutable default argument", "ডিফল্ট `[]`/`{}` ফাংশন-সংজ্ঞার সময় একবারই তৈরি হয় ও সব কলে ভাগ হয়ে যায় — আগের কলের ডেটা পরের কলে জমে থাকতে পারে।", "ডিফল্ট `None` দিন এবং ফাংশনের ভেতরে `if x is None: x = []` লিখুন।")
                if (Regex("^except\\s*:").containsMatchIn(t)) add(f, m, i, Kind.LOGIC, Conf.LIKELY, "খালি `except:`", "সব ধরনের ত্রুটি (Ctrl-C ও `SystemExit`-সহ) গিলে ফেলে; আসল সমস্যা লুকিয়ে যায়।", "নির্দিষ্ট ত্রুটি লিখুন, যেমন `except ValueError:`।")
                if (Regex("[!=]=\\s*None\\b").containsMatchIn(ln)) add(f, m, i, Kind.CONTEXT, Conf.LIKELY, "`None`-এর সঙ্গে `==`/`!=`", "Python-এ `None` তুলনায় `is`/`is not` ব্যবহার করা হয় (PEP 8)।", "`is None` / `is not None` লিখুন।", raw.replace("== None", "is None").replace("!= None", "is not None"))
            }
            depth = (depth + opens - closes).coerceAtLeast(0)
        }
        prevHeader?.let { h -> add(f, m, h, Kind.SYNTAX, Conf.CERTAIN, "ব্লকের ভেতরে কিছু নেই", "ফাইলের শেষে `:`-সহ লাইনের পর কোনো ভেতরের লাইন নেই।", "ব্লকের কাজ লিখুন বা `pass` দিন।") }
        if (tabs > 0 && spaces > 0) f += Finding(1, Kind.SYNTAX, Conf.LIKELY, "ট্যাব ও স্পেস মেশানো indentation", "ফাইলে ট্যাব ও স্পেস দুই ধরনের indentation আছে; Python 3 এ `TabError` দিতে পারে এবং OCR-এ ফাঁকা মাপ ভুল পড়া হলেও এমন দেখায়।", "একই ধরন (সাধারণত ৪ স্পেস) ব্যবহার করুন।", m.raw.firstOrNull().orEmpty().trim())
        val sec = listOf(
            Triple("\\beval\\s*\\(", "eval(…)", "স্ট্রিং থেকে কোড চালায়; ইনপুট বাইরের হলে যেকোনো কোড চালানো সম্ভব (code injection)।") to "`ast.literal_eval` (শুধু ডেটার জন্য) বা পরিষ্কার পার্সার ব্যবহার করুন।",
            Triple("\\bexec\\s*\\(", "exec(…)", "স্ট্রিং থেকে কোড চালায়; বাইরের ইনপুটে কোড-ইনজেকশন।") to "exec এড়ান; দরকারি কাজ সরাসরি ফাংশন দিয়ে লিখুন।",
            Triple("\\bos\\.system\\s*\\(", "os.system(…)", "শেল-কমান্ড চালায়; ইনপুট জোড়া লাগানো থাকলে command injection।") to "`subprocess.run([\"cmd\", arg], shell=False)` ব্যবহার করুন।",
            Triple("subprocess\\.\\w+\\(.*shell\\s*=\\s*True", "subprocess + shell=True", "শেলের মাধ্যমে চালানো হয়; ইনপুটে `;`/`&&` থাকলে বাড়তি কমান্ড চলে।") to "আর্গুমেন্ট-তালিকা দিন ও `shell=False` রাখুন।",
            Triple("\\bpickle\\.loads?\\s*\\(", "pickle.load(s)", "অবিশ্বস্ত ডেটা pickle করলে কোড চলে যেতে পারে।") to "অবিশ্বস্ত উৎসের জন্য JSON ব্যবহার করুন।",
            Triple("\\byaml\\.load\\s*\\((?!.*Loader\\s*=\\s*yaml\\.SafeLoader)", "yaml.load(…) (SafeLoader ছাড়া)", "ডিফল্ট লোডার অবজেক্ট তৈরি করে কোড চালাতে পারে।") to "`yaml.safe_load(...)` ব্যবহার করুন।",
            Triple("verify\\s*=\\s*False", "TLS-যাচাই বন্ধ (verify=False)", "সার্ভারের সার্টিফিকেট যাচাই না করায় মাঝখানে আড়ি পাতা সম্ভব।") to "`verify=True` রাখুন বা সঠিক CA দিন।",
            Triple("hashlib\\.(md5|sha1)\\s*\\(", "দুর্বল হ্যাশ (MD5/SHA-1)", "পাসওয়ার্ড/সিগনেচারের জন্য নিরাপদ নয়।") to "পাসওয়ার্ডে `bcrypt`/`argon2`/`hashlib.scrypt`, অন্যত্র SHA-256।",
            Triple("(execute|executemany)\\s*\\(\\s*(f[\"']|[\"'].*[\"']\\s*(%|\\+)|.*\\.format\\()", "SQL স্ট্রিং জোড়া লাগিয়ে চালানো", "ইনপুট সরাসরি SQL-এ ঢুকলে SQL injection।") to "প্যারামিটার ব্যবহার করুন: `cur.execute(\"... WHERE id = ?\", (id,))`।",
        )
        scan(m, f, sec, Kind.SECURITY, Conf.LIKELY, Regex("^"))
        val yamlFix = Regex("yaml\\.load\\(")
        m.lines.forEachIndexed { i, l -> if (yamlFix.containsMatchIn(l) && f.any { it.line == i + 1 && it.title.startsWith("yaml.load") }) { val idx = f.indexOfFirst { it.line == i + 1 && it.title.startsWith("yaml.load") }; f[idx] = f[idx].copy(replacement = m.raw[i].replace("yaml.load(", "yaml.safe_load(")) } }
    }

    /** Applies (pattern, title, why) → fix rules on masked lines. Patterns that must see string text (SQL concat) run on the raw line instead. */
    private fun scan(m: Masked, f: MutableList<Finding>, rules: List<Pair<Triple<String, String, String>, String>>, kind: Kind, conf: Conf, @Suppress("UNUSED_PARAMETER") gate: Regex) {
        for ((rule, fix) in rules) {
            val re = Regex(rule.first)
            for (i in m.lines.indices) {
                val onRaw = rule.second.contains("SQL") || rule.second.contains("স্ট্রিং জোড়া")
                if (re.containsMatchIn(if (onRaw) m.raw[i].let { r -> stripLineComment(r) } else m.lines[i])) add(f, m, i, kind, conf, rule.second, rule.third, fix)
            }
        }
    }
    private fun stripLineComment(s: String) = s.trimStart().let { if (it.startsWith("#") || it.startsWith("//") || it.startsWith("--")) "" else s }

    // ------------------------------------------------------------------ JS / TS

    private fun js(m: Masked, f: MutableList<Finding>) {
        val sec = listOf(
            Triple("\\beval\\s*\\(", "eval(…)", "স্ট্রিং থেকে কোড চালায়; বাইরের ইনপুটে কোড-ইনজেকশন (XSS/RCE)।") to "`JSON.parse` বা সরাসরি ফাংশন ব্যবহার করুন।",
            Triple("new\\s+Function\\s*\\(", "new Function(…)", "eval-এর মতোই স্ট্রিং থেকে কোড বানায়।") to "এড়ান; ফাংশন সরাসরি লিখুন।",
            Triple("\\.innerHTML\\s*=|insertAdjacentHTML\\s*\\(|document\\.write\\s*\\(", "innerHTML/document.write দিয়ে HTML বসানো", "ইনপুটে `<script>`/`onerror=` থাকলে XSS।") to "`textContent` ব্যবহার করুন বা ইনপুট escape/sanitize করুন।",
            Triple("dangerouslySetInnerHTML", "dangerouslySetInnerHTML", "React-এর escape বন্ধ করে; অবিশ্বস্ত HTML-এ XSS।") to "বিশ্বস্ত ও sanitize-করা HTML ছাড়া ব্যবহার করবেন না।",
            Triple("child_process|\\bexec\\s*\\(\\s*[`\"'].*\\$\\{|\\bexecSync\\s*\\(", "child_process exec", "শেল-কমান্ডে ইনপুট জোড়া লাগলে command injection।") to "`execFile`/`spawn` + আর্গুমেন্ট-তালিকা ব্যবহার করুন।",
            Triple("rejectUnauthorized\\s*:\\s*false", "TLS-যাচাই বন্ধ", "সার্টিফিকেট যাচাই না হওয়ায় মাঝখানে আড়ি পাতা সম্ভব।") to "`rejectUnauthorized: true` রাখুন।",
            Triple("(localStorage|sessionStorage)\\.setItem\\(\\s*[\"'](token|jwt|password|secret)", "টোকেন/পাসওয়ার্ড localStorage-এ", "XSS হলে সহজেই চুরি যায়।") to "httpOnly কুকি বিবেচনা করুন।",
        )
        scan(m, f, sec, Kind.SECURITY, Conf.LIKELY, Regex("^"))
        for ((i, l) in m.lines.withIndex()) {
            if (Regex("\\b(if|while)\\s*\\([^()=!<>]*[^=!<>]=[^=>][^()]*\\)").containsMatchIn(l)) add(f, m, i, Kind.LOGIC, Conf.LIKELY, "শর্তে `=` (অ্যাসাইনমেন্ট)", "শর্তে `=` মান বসায়, তুলনা করে না — প্রায় সবসময়ই ভুল; শর্ত সবসময় সত্য/মিথ্যা হয়ে যেতে পারে।", "তুলনায় `===` লিখুন।")
            if (Regex("[^=!]==[^=]|!=[^=]").containsMatchIn(l)) add(f, m, i, Kind.CONTEXT, Conf.POSSIBLE, "ঢিলা তুলনা (`==`/`!=`)", "ধরন বদলে তুলনা করে (যেমন `0 == \"\"` সত্য)।", "সাধারণত `===`/`!==` ভালো।")
            if (Regex("^\\s*var\\s").containsMatchIn(l)) add(f, m, i, Kind.CONTEXT, Conf.POSSIBLE, "`var`", "scope function-স্তরের; অপ্রত্যাশিত hoisting-এর কারণ হতে পারে।", "`let`/`const` ব্যবহার করুন।")
        }
    }

    // ------------------------------------------------------------------ Java / Kotlin

    private fun java(m: Masked, f: MutableList<Finding>) {
        val sec = listOf(
            Triple("Runtime\\.getRuntime\\(\\)\\.exec|new\\s+ProcessBuilder", "বাইরের কমান্ড চালানো", "ইনপুট জোড়া লাগলে command injection।") to "আর্গুমেন্ট আলাদা অ্যারেতে দিন, ইনপুট যাচাই করুন।",
            Triple("MessageDigest\\.getInstance\\(\\s*\"(MD5|SHA-?1)\"", "দুর্বল হ্যাশ (MD5/SHA-1)", "পাসওয়ার্ড/সিগনেচারের জন্য নিরাপদ নয়।") to "SHA-256 বা পাসওয়ার্ডের জন্য PBKDF2/bcrypt/Argon2।",
            Triple("ObjectInputStream|readObject\\s*\\(", "Java deserialization", "অবিশ্বস্ত ডেটা deserialize করলে কোড চলতে পারে।") to "JSON ব্যবহার করুন বা ফিল্টার সেট করুন।",
            Triple("(executeQuery|executeUpdate|execute|createQuery|rawQuery)\\s*\\(\\s*\"[^\"]*\"\\s*\\+", "SQL স্ট্রিং জোড়া লাগিয়ে চালানো", "ইনপুট সরাসরি SQL-এ ঢুকলে SQL injection।") to "`PreparedStatement` ও `?` প্যারামিটার ব্যবহার করুন।",
            Triple("setHostnameVerifier|ALLOW_ALL_HOSTNAME_VERIFIER|TrustAllCerts|X509TrustManager", "TLS-যাচাই দুর্বল/বন্ধ", "যেকোনো সার্টিফিকেট মেনে নিলে আড়ি পাতা সম্ভব।") to "ডিফল্ট যাচাই রাখুন।",
        )
        scan(m, f, sec, Kind.SECURITY, Conf.LIKELY, Regex("^"))
        for ((i, l) in m.lines.withIndex()) {
            val r = m.raw[i]
            if (Regex("catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}").containsMatchIn(l)) add(f, m, i, Kind.LOGIC, Conf.LIKELY, "খালি catch", "ত্রুটি চুপচাপ গিলে ফেলা হচ্ছে; সমস্যা ধরা পড়বে না।", "অন্তত লগ করুন বা পুনরায় throw করুন।")
            if (Regex("==\\s*\"|\"\\s*==").containsMatchIn(r) && !l.contains("Kotlin")) add(f, m, i, Kind.LOGIC, Conf.POSSIBLE, "স্ট্রিং `==` তুলনা", "Java-তে `==` রেফারেন্স তুলনা করে, মান নয় (Kotlin-এ `==` ঠিক আছে)।", "Java হলে `.equals(...)` ব্যবহার করুন।")
            if (Regex("\\b(if|while)\\s*\\([^()=!<>]*[^=!<>]=[^=][^()]*\\)").containsMatchIn(l)) add(f, m, i, Kind.LOGIC, Conf.LIKELY, "শর্তে `=` (অ্যাসাইনমেন্ট)", "শর্তে `=` মান বসায়, তুলনা করে না।", "তুলনায় `==` লিখুন।")
        }
    }

    // ------------------------------------------------------------------ C / C++

    private fun c(m: Masked, f: MutableList<Finding>) {
        val sec = listOf(
            Triple("\\bgets\\s*\\(", "gets(…)", "দৈর্ঘ্য-সীমা নেই ⇒ buffer overflow; আধুনিক C স্ট্যান্ডার্ড থেকে বাদ দেওয়া হয়েছে।") to "`fgets(buf, sizeof buf, stdin)` ব্যবহার করুন।",
            Triple("\\bstrcpy\\s*\\(", "strcpy(…)", "গন্তব্যের আকার যাচাই করে না ⇒ buffer overflow।") to "`snprintf`/`strncpy` (শেষে `\\0` নিশ্চিত করে) বা `strlcpy`।",
            Triple("\\bstrcat\\s*\\(", "strcat(…)", "গন্তব্যের আকার যাচাই করে না ⇒ buffer overflow।") to "`strncat` বা `snprintf` ব্যবহার করুন।",
            Triple("\\bsprintf\\s*\\(", "sprintf(…)", "আউটপুটের আকার সীমাহীন ⇒ buffer overflow।") to "`snprintf(buf, sizeof buf, ...)` ব্যবহার করুন।",
            Triple("\\bscanf\\s*\\(\\s*\"[^\"]*%s", "scanf(\"%s\")", "দৈর্ঘ্য-সীমা ছাড়া স্ট্রিং পড়লে buffer overflow।") to "`%99s`-এর মতো সীমা দিন বা `fgets`।",
            Triple("\\bsystem\\s*\\(", "system(…)", "শেল-কমান্ড চালায়; ইনপুট জোড়া লাগলে command injection।") to "`execv`-ধরনের কল ও আর্গুমেন্ট-তালিকা।",
        )
        scan(m, f, sec, Kind.SECURITY, Conf.LIKELY, Regex("^"))
        for ((i, l) in m.lines.withIndex()) {
            if (Regex("\\b(printf\\s*\\(\\s*[A-Za-z_][^,;\"]*\\)|(fprintf|syslog)\\s*\\([^,\"]+,\\s*[A-Za-z_][^,;\"]*\\))\\s*;").containsMatchIn(l) && !Regex("\"").containsMatchIn(l)) add(f, m, i, Kind.SECURITY, Conf.POSSIBLE, "ফরম্যাট-স্ট্রিং হিসেবে ভেরিয়েবল", "`printf(var)`-এ var-এ `%n`/`%s` থাকলে মেমরি পড়া/লেখা সম্ভব (format-string bug)।", "`printf(\"%s\", var)` লিখুন।")
            if (Regex("\\b(if|while)\\s*\\([^()=!<>]*[^=!<>]=[^=][^()]*\\)").containsMatchIn(l)) add(f, m, i, Kind.LOGIC, Conf.LIKELY, "শর্তে `=` (অ্যাসাইনমেন্ট)", "`if (a = b)` মান বসায়; সাধারণত `==` উদ্দেশ্য ছিল।", "`==` লিখুন (ইচ্ছাকৃত হলে বাড়তি বন্ধনী ও মন্তব্য দিন)।")
            if (Regex("for\\s*\\([^;]*;[^;]*<=\\s*(\\w+\\.)?(length|size\\(\\)|len\\b|sizeof)").containsMatchIn(l)) add(f, m, i, Kind.LOGIC, Conf.POSSIBLE, "সম্ভাব্য off-by-one (`<=` দৈর্ঘ্যের সঙ্গে)", "সূচক ০ থেকে শুরু হলে শেষ বৈধ সূচক দৈর্ঘ্য−১; `<=` দিলে একটি বাড়তি ধাপ বাইরে চলে যায়।", "সাধারণত `<` ব্যবহার হয় — প্রসঙ্গ দেখে নিশ্চিত হোন।")
        }
    }

    // ------------------------------------------------------------------ PHP

    private fun php(m: Masked, f: MutableList<Finding>) {
        val sec = listOf(
            Triple("\\b(eval|assert)\\s*\\(", "eval/assert(…)", "স্ট্রিং থেকে কোড চালায় ⇒ কোড-ইনজেকশন।") to "এড়ান।",
            Triple("\\b(system|exec|shell_exec|passthru|popen|proc_open)\\s*\\(", "শেল-কমান্ড চালানো", "ইনপুট জোড়া লাগলে command injection।") to "`escapeshellarg()` ও অনুমোদিত-তালিকা ব্যবহার করুন।",
            Triple("\\bunserialize\\s*\\(", "unserialize(…)", "অবিশ্বস্ত ডেটায় object-injection/কোড চলা সম্ভব।") to "`json_decode` ব্যবহার করুন।",
            Triple("\\b(include|require)(_once)?\\s*\\(?\\s*\\$", "ভেরিয়েবল দিয়ে include/require", "ইনপুট ফাইলের পথ হলে Local/Remote File Inclusion।") to "অনুমোদিত ফাইলের তালিকা থেকে বাছুন।",
            Triple("\\b(md5|sha1)\\s*\\(", "দুর্বল হ্যাশ (MD5/SHA-1)", "পাসওয়ার্ডের জন্য নিরাপদ নয়।") to "`password_hash()` / `password_verify()`।",
            Triple("mysql_query\\s*\\(|(mysqli_query|->query)\\s*\\([^)]*\\${'$'}_(GET|POST|REQUEST|COOKIE)|\"\\s*(SELECT|INSERT|UPDATE|DELETE)[^\"]*\\${'$'}_(GET|POST|REQUEST|COOKIE)", "ইনপুট সরাসরি SQL-এ", "ব্যবহারকারীর ইনপুট ছাড়াই SQL বানালে SQL injection।") to "prepared statement (`mysqli_prepare`/PDO) ব্যবহার করুন।",
            Triple("echo\\s+[^;]*\\${'$'}_(GET|POST|REQUEST|COOKIE)", "ইনপুট সরাসরি echo", "escape ছাড়া HTML-এ বসালে XSS।") to "`htmlspecialchars(${'$'}x, ENT_QUOTES, 'UTF-8')` ব্যবহার করুন।",
        )
        scan(m, f, sec, Kind.SECURITY, Conf.LIKELY, Regex("^"))
    }

    // ------------------------------------------------------------------ SQL

    private fun sql(m: Masked, f: MutableList<Finding>) {
        val text = m.lines.joinToString("\n"); val stmtRe = Regex("(?is)\\b(update|delete\\s+from)\\b[^;]*?(;|$)")
        for (mt in stmtRe.findAll(text)) {
            val s = mt.value; if (Regex("(?i)\\bwhere\\b").containsMatchIn(s)) continue
            val line = text.substring(0, mt.range.first).count { it == '\n' } + 1
            f += Finding(line, Kind.LOGIC, Conf.LIKELY, "WHERE ছাড়া ${if (s.trim().startsWith("update", true)) "UPDATE" else "DELETE"}", "সারণির সব সারি বদলে/মুছে যাবে — প্রায় সবসময় অনিচ্ছাকৃত।", "শর্ত (`WHERE ...`) যোগ করুন; আগে `SELECT` দিয়ে কোন সারি প্রভাবিত হবে দেখে নিন।", m.raw.getOrElse(line - 1) { "" }.trim())
        }
        for ((i, l) in m.lines.withIndex()) {
            if (Regex("(?i)\\bdrop\\s+(table|database|schema)\\b").containsMatchIn(l)) add(f, m, i, Kind.SECURITY, Conf.POSSIBLE, "DROP — অপরিবর্তনীয় মোছা", "টেবিল/ডেটাবেস স্থায়ীভাবে মুছে যায়।", "ব্যাকআপ নিয়ে `IF EXISTS` ও সঠিক ডেটাবেসে আছেন কিনা নিশ্চিত করুন।")
            if (Regex("(?i)\\bselect\\s+\\*").containsMatchIn(l)) add(f, m, i, Kind.CONTEXT, Conf.POSSIBLE, "`SELECT *`", "সব কলাম আনে; স্কিমা বদলালে কোড ভাঙতে পারে ও ধীর হতে পারে।", "দরকারি কলামের নাম লিখুন।")
            if (Regex("(?i)\\bwhere\\b.*=\\s*null\\b").containsMatchIn(l)) add(f, m, i, Kind.LOGIC, Conf.LIKELY, "`= NULL` তুলনা", "SQL-এ `= NULL` কখনো সত্য হয় না (ফল অজানা)।", "`IS NULL` / `IS NOT NULL` লিখুন।", m.raw[i].replace(Regex("(?i)=\\s*null"), "IS NULL"))
        }
    }

    // ------------------------------------------------------------------ Shell

    private fun shell(m: Masked, f: MutableList<Finding>) {
        for ((i, l) in m.lines.withIndex()) {
            val r = m.raw[i]
            if (Regex("(curl|wget)[^|]*\\|\\s*(sudo\\s+)?(ba|z)?sh\\b").containsMatchIn(r)) add(f, m, i, Kind.SECURITY, Conf.LIKELY, "ডাউনলোড করে সরাসরি শেলে চালানো", "দেখার সুযোগ ছাড়াই অজানা কোড চলে।", "আগে ফাইলে নামিয়ে পড়ে দেখুন ও checksum মেলান।")
            if (Regex("\\brm\\s+-[a-zA-Z]*r[a-zA-Z]*f?[^#]*\\$\\{?\\w+\\}?(/|\\s|$)").containsMatchIn(l) && !Regex("\"\\$").containsMatchIn(r)) add(f, m, i, Kind.SECURITY, Conf.LIKELY, "`rm -rf` + কোটহীন ভেরিয়েবল", "ভেরিয়েবল ফাঁকা হলে `rm -rf /` ধরনের বিপর্যয় সম্ভব।", "`\"${'$'}{VAR:?}\"` ব্যবহার করুন ও ভেরিয়েবল কোট দিন।")
            if (Regex("chmod\\s+(-R\\s+)?[0-7]*777").containsMatchIn(l)) add(f, m, i, Kind.SECURITY, Conf.LIKELY, "chmod 777", "সবাই পড়তে/লিখতে/চালাতে পারে।", "ন্যূনতম অনুমতি (যেমন 750/640) দিন।")
            if (Regex("\\beval\\b").containsMatchIn(l)) add(f, m, i, Kind.SECURITY, Conf.LIKELY, "eval", "স্ট্রিং থেকে কমান্ড চালায়; ইনপুটে injection।", "এড়ান বা অ্যারে/`printf %q` ব্যবহার করুন।")
            if (Regex("\\[\\s+\\$\\w+\\s+(=|==|-eq)").containsMatchIn(l)) add(f, m, i, Kind.LOGIC, Conf.POSSIBLE, "`[ ${'$'}var … ]`-এ কোটহীন ভেরিয়েবল", "ভেরিয়েবল ফাঁকা বা ফাঁকা-যুক্ত হলে `[` ত্রুটি দেয়।", "`[ \"${'$'}var\" = ... ]` লিখুন।")
        }
        val raw = m.raw
        if (raw.none { it.trim().startsWith("set -e") || it.contains("set -euo") || it.contains("set -o errexit") } && raw.size > 5) f += Finding(1, Kind.CONTEXT, Conf.POSSIBLE, "`set -e` নেই", "একটি কমান্ড ব্যর্থ হলেও স্ক্রিপ্ট এগিয়ে যায়।", "শুরুতে `set -euo pipefail` বিবেচনা করুন।", raw.firstOrNull().orEmpty().trim())
    }

    // ------------------------------------------------------------------ HTML

    private val voidTags = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr", "!doctype")

    private fun html(m: Masked, f: MutableList<Finding>) {
        val stack = ArrayList<Pair<String, Int>>(); val tag = Regex("<(/?)([A-Za-z][A-Za-z0-9-]*|!DOCTYPE)([^<>]*?)(/?)>", RegexOption.IGNORE_CASE)
        var skipTo: String? = null
        for ((i, l) in m.lines.withIndex()) {
            for (t in tag.findAll(l)) {
                val closing = t.groupValues[1] == "/"; val name = t.groupValues[2].lowercase(); val self = t.groupValues[4] == "/"
                if (skipTo != null) { if (closing && name == skipTo) skipTo = null; continue }
                if (!closing && (name == "script" || name == "style") && !self) { stack += name to i; skipTo = name; continue }
                if (name in voidTags || self) continue
                if (!closing) stack += name to i
                else {
                    val idx = stack.indexOfLast { it.first == name }
                    if (idx < 0) add(f, m, i, Kind.SYNTAX, Conf.CERTAIN, "বাড়তি `</$name>`", "এই বন্ধ-ট্যাগের কোনো খোলা-ট্যাগ নেই।", "বাড়তি ট্যাগ সরান বা খোলা-ট্যাগ যোগ করুন।")
                    else { while (stack.size - 1 > idx) { val (n, ln) = stack.removeAt(stack.lastIndex); add(f, m, ln, Kind.SYNTAX, Conf.LIKELY, "`<$n>` বন্ধ হয়নি", "`</$name>` (লাইন ${i + 1}) আসার আগে `<$n>` বন্ধ করা হয়নি; ব্রাউজার নিজে অনুমান করে ঠিক করে, ফলে লেআউট অপ্রত্যাশিত হতে পারে।", "`</$n>` যোগ করুন।") }; stack.removeAt(stack.lastIndex) }
                }
            }
            val r = m.raw[i]
            if (Regex("(?i)\\son(error|load|click|mouseover|focus)\\s*=").containsMatchIn(r)) add(f, m, i, Kind.SECURITY, Conf.POSSIBLE, "inline event handler", "ইনপুট থেকে এলে XSS-এর সাধারণ পথ; CSP-ও আটকায়।", "`addEventListener` ব্যবহার করুন।")
            if (Regex("(?i)(href|src)\\s*=\\s*[\"']\\s*javascript:").containsMatchIn(r)) add(f, m, i, Kind.SECURITY, Conf.LIKELY, "`javascript:` URL", "লিংকে কোড চালানো — XSS-এর পথ।", "সত্যিকারের URL/ইভেন্ট ব্যবহার করুন।")
            if (Regex("(?i)<script[^>]+src\\s*=\\s*[\"']http://").containsMatchIn(r)) add(f, m, i, Kind.SECURITY, Conf.LIKELY, "`http://` থেকে স্ক্রিপ্ট", "এনক্রিপ্ট-বিহীন পথে স্ক্রিপ্ট বদলে দেওয়া সম্ভব।", "`https://` ব্যবহার করুন।")
        }
        for ((n, ln) in stack) add(f, m, ln, Kind.SYNTAX, Conf.LIKELY, "`<$n>` বন্ধ হয়নি", "ফাইলের শেষে `</$n>` পাওয়া যায়নি।", "`</$n>` যোগ করুন।")
    }

    // ------------------------------------------------------------------ hard-coded secrets (all languages)

    private fun secrets(raw: List<String>, f: MutableList<Finding>) {
        val kv = Regex("(?i)(password|passwd|pwd|secret|api[_-]?key|access[_-]?key|auth[_-]?token|token)\\s*[:=]\\s*[\"'][^\"'\\s]{6,}[\"']")
        for ((i, l) in raw.withIndex()) {
            val t = l.trim(); if (t.startsWith("#") || t.startsWith("//")) continue
            if (kv.containsMatchIn(l)) f += Finding(i + 1, Kind.SECURITY, Conf.POSSIBLE, "কোডে সরাসরি লেখা গোপন মান?", "পাসওয়ার্ড/কী/টোকেন সোর্সে থাকলে কোড শেয়ার বা কমিটের সঙ্গে ফাঁস হয় (এটি শুধু নামের-ধরন দেখে অনুমান; ডামি মান হলে সমস্যা নেই)।", "পরিবেশ-চলক বা গোপন-ভাণ্ডার থেকে পড়ুন; এই মান ফাঁস হয়ে থাকলে বদলে ফেলুন।", t.take(120))
            if (Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----").containsMatchIn(l)) f += Finding(i + 1, Kind.SECURITY, Conf.LIKELY, "প্রাইভেট key কোডে", "প্রাইভেট key সোর্সে থাকলে যে কেউ পেলে আপনার পরিচয়ে কাজ করতে পারে।", "key সরিয়ে বদলে ফেলুন।", "-----BEGIN … PRIVATE KEY-----")
            if (Regex("\\bAKIA[0-9A-Z]{16}\\b").containsMatchIn(l)) f += Finding(i + 1, Kind.SECURITY, Conf.LIKELY, "AWS access key-র ধরন", "AWS Access Key ID-র গঠনের সঙ্গে মেলে।", "ফাঁস ধরে নিয়ে key বদলান।", t.take(120))
        }
    }

    // ------------------------------------------------------------------ JSON (real parser)

    internal object Json {
        fun valid(s: String) = error(s) == null
        /** Returns (line, message) of the first error, or null if [s] is valid JSON (RFC 8259, no comments/trailing commas). */
        fun error(s: String): Pair<Int, String>? {
            var i = 0; var line = 1
            fun fail(msg: String): Nothing = throw IllegalStateException("$line|$msg")
            fun ws() { while (i < s.length && s[i].isWhitespace()) { if (s[i] == '\n') line++; i++ } }
            fun str() {
                i++
                while (true) {
                    if (i >= s.length) fail("স্ট্রিং বন্ধ হয়নি")
                    val c = s[i]
                    if (c == '"') { i++; return }
                    if (c == '\n') fail("স্ট্রিং-এর ভেতরে নতুন লাইন (JSON-এ `\\n` লিখতে হয়)")
                    if (c == '\\') { i++; if (i >= s.length || s[i] !in "\"\\/bfnrtu") fail("অবৈধ escape `\\${s.getOrNull(i) ?: ""}`"); if (s[i] == 'u') { if (i + 4 >= s.length || !s.substring(i + 1, i + 5).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) fail("অবৈধ \\u escape"); i += 4 } }
                    else if (c < ' ') fail("স্ট্রিং-এ অবৈধ নিয়ন্ত্রণ-অক্ষর")
                    i++
                }
            }
            fun value(depth: Int) {
                if (depth > 200) fail("নেস্টিং অত্যধিক গভীর")
                ws(); if (i >= s.length) fail("মান পাওয়া যায়নি")
                when (val c = s[i]) {
                    '{' -> { i++; ws(); if (s.getOrNull(i) == '}') { i++; return }
                        while (true) { ws(); if (s.getOrNull(i) != '"') fail("অবজেক্টের key `\"` দিয়ে শুরু হতে হবে${if (s.getOrNull(i) == '}') " (শেষ কমার পর কিছু নেই — trailing comma অনুমোদিত নয়)" else ""}"); str(); ws(); if (s.getOrNull(i) != ':') fail("key-এর পরে `:` নেই"); i++; value(depth + 1); ws()
                            when (s.getOrNull(i)) { ',' -> i++; '}' -> { i++; return }; else -> fail("`,` বা `}` আশা করা হয়েছিল") } } }
                    '[' -> { i++; ws(); if (s.getOrNull(i) == ']') { i++; return }
                        while (true) { value(depth + 1); ws(); when (s.getOrNull(i)) { ',' -> i++; ']' -> { i++; return }; else -> fail("`,` বা `]` আশা করা হয়েছিল") } } }
                    '"' -> str()
                    else -> {
                        val m = Regex("-?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d+)?|true|false|null").matchAt(s, i) ?: fail("অবৈধ মান `${s.substring(i, minOf(s.length, i + 8)).lineSequence().first()}`")
                        i += m.value.length; if (c.isLetter() && m.value.isEmpty()) fail("অবৈধ মান")
                    }
                }
            }
            return try { value(0); ws(); if (i < s.length) fail("মানের পরে বাড়তি লেখা"); null } catch (e: IllegalStateException) { val (l, msg) = e.message!!.split("|", limit = 2); l.toInt() to msg }
        }
    }
}
