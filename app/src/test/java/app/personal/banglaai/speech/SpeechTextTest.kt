package app.personal.banglaai.speech

import org.junit.Assert.*
import org.junit.Test

class SpeechTextTest {
    @Test fun markdownSymbolsAreNotSpoken() {
        val t = SpeechText.clean("**জ্ঞান অর্জন** খুবই জরুরি।\n\n* প্রথম কারণ\n* দ্বিতীয় কারণ\n\n1. এক\n2. দুই")
        assertFalse(t.contains("*")); assertFalse(t.contains("1.")); assertFalse(t.contains("2."))
        assertTrue(t.contains("জ্ঞান অর্জন খুবই জরুরি")); assertTrue(t.contains("প্রথম কারণ")); assertTrue(t.contains("দ্বিতীয় কারণ"))
    }
    @Test fun headingsLinksCodeEmojiAndTablesAreDropped() {
        val t = SpeechText.clean("## শিরোনাম\nদেখুন https://example.com/x ✅ ঠিক আছে | কলাম |\n```py\nprint(1)\n```")
        assertFalse(t.contains("#")); assertFalse(t.contains("http")); assertFalse(t.contains("✅")); assertFalse(t.contains("|")); assertFalse(t.contains("print"))
        assertTrue(t.contains("শিরোনাম")); assertTrue(t.contains("ঠিক আছে")); assertTrue(t.contains("কোড দেখুন"))
    }
    @Test fun plainBengaliAndNumbersStayUnchanged() {
        assertEquals("রাসূল (সা.) ১৪ নম্বর হাদিসে বলেছেন, সত্য বলো।", SpeechText.clean("রাসূল (সা.) ১৪ নম্বর হাদিসে বলেছেন, সত্য বলো।"))
        assertEquals("1.5 ডলার", SpeechText.clean("1.5 ডলার"))
    }
}
