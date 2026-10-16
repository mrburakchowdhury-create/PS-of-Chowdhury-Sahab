package app.personal.banglaai.doc

import app.personal.banglaai.export.ExportPage
import org.junit.Assert.*
import org.junit.Test

class TranslationQualityCheckTest {
    @Test fun cleanTranslationHasNoIssues() {
        val pages = listOf(
            ExportPage(1, null, "এটি একটি স্বাভাবিক দৈর্ঘ্যের অনুচ্ছেদ যার অনুবাদও যথাযথ দৈর্ঘ্যের হওয়া উচিত।", "This is a normal-length paragraph whose translation should also be a reasonable length."),
            ExportPage(2, null, "আরেকটি সম্পূর্ণ আলাদা অনুচ্ছেদ, একই রকম দীর্ঘ।", "Another completely different paragraph, similarly long."),
        )
        assertTrue(TranslationQualityCheck.run(pages).isEmpty())
        assertTrue(TranslationQualityCheck.summarize(emptyList()).contains("কোনো সমস্যা পাওয়া যায়নি"))
    }

    @Test fun suspiciouslyShortTranslationIsFlagged() {
        val longOriginal = "এটি একটি দীর্ঘ অনুচ্ছেদ। ".repeat(20)
        val pages = listOf(ExportPage(1, null, longOriginal, "ছোট।"))
        val issues = TranslationQualityCheck.run(pages)
        assertEquals(1, issues.size); assertEquals(1, issues[0].page); assertTrue(issues[0].message.contains("অস্বাভাবিক ছোট"))
    }

    @Test fun identicalConsecutiveTranslationsAreFlaggedAsPossibleDuplication() {
        val pages = listOf(
            ExportPage(1, null, "প্রথম পৃষ্ঠার মূল লেখা", "same translation"),
            ExportPage(2, null, "সম্পূর্ণ আলাদা দ্বিতীয় পৃষ্ঠার মূল লেখা", "same translation"),
        )
        val issues = TranslationQualityCheck.run(pages)
        assertTrue(issues.any { it.message.contains("হুবহু একই") })
    }

    @Test fun inconsistentGlossaryUsageAcrossPagesIsFlagged() {
        val pages = listOf(
            ExportPage(1, null, "firewall is important", "ফায়ারওয়াল গুরুত্বপূর্ণ"),
            ExportPage(2, null, "a firewall blocks traffic", "একটি প্রতিরক্ষা-দেয়াল ট্রাফিক আটকায়"),   // same term, different Bengali wording
        )
        val issues = TranslationQualityCheck.run(pages, glossary = listOf("firewall" to "ফায়ারওয়াল"))
        assertTrue(issues.any { it.message.contains("firewall") && it.message.contains("সমানভাবে ব্যবহার হয়নি") })
    }
}
