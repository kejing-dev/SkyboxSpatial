package com.kejing.skyboxspatial

import com.kejing.skyboxspatial.domain.PromptRules
import com.kejing.skyboxspatial.domain.model.JobStatus
import com.kejing.skyboxspatial.domain.model.SkyboxStyle
import com.kejing.skyboxspatial.ui.main.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptRulesTest {
    @Test
    fun titleStopsAtFirstClause() {
        assertEquals("清晨的高山湖泊", PromptRules.titleFor("清晨的高山湖泊，松林环绕，水面有薄雾。"))
        assertEquals("A quiet beach at dusk", PromptRules.titleFor("A quiet beach at dusk, golden sunset over the sea."))
    }

    @Test
    fun titleIsTruncatedAndFallsBack() {
        assertEquals("清晨的高山湖泊松林环绕水面有…", PromptRules.titleFor("清晨的高山湖泊松林环绕水面有薄雾远处是雪山"))
        assertEquals("the quick brown fox jumps over…", PromptRules.titleFor("the quick brown fox jumps over the lazy dog again"))
        assertEquals("Environment", PromptRules.titleFor("   "))
    }

    @Test
    fun maxCharsRespectsStyleLimit() {
        assertEquals(300, PromptRules.maxChars(null))
        assertEquals(300, PromptRules.maxChars(390))
        assertEquals(120, PromptRules.maxChars(120))
        assertEquals("abc", PromptRules.clamp("abcdef", 3))
    }

    @Test
    fun generateNeedsSomeText() {
        assertFalse(PromptRules.canGenerate(" a "))
        assertTrue(PromptRules.canGenerate("海边"))
    }

    @Test
    fun jobStatusMapsServiceValues() {
        assertEquals(JobStatus.COMPLETE, JobStatus.from("complete"))
        assertEquals(JobStatus.ERROR, JobStatus.from("ERROR"))
        assertEquals(JobStatus.PENDING, JobStatus.from(null))
        assertTrue(JobStatus.ABORT.finished)
        assertFalse(JobStatus.PROCESSING.finished)
    }

    @Test
    fun defaultStylePrefersPhotorealModel3() {
        val styles = listOf(
            SkyboxStyle(155, "Open World", "4", 1000),
            SkyboxStyle(90, "Cyberpunk", "3", 390),
            SkyboxStyle(67, "Photoreal", "3", 390),
        )
        assertEquals(67, MainViewModel.defaultStyle(styles)?.id)
        assertEquals(90, MainViewModel.defaultStyle(styles.filter { it.id != 67 })?.id)
        assertEquals(155, MainViewModel.defaultStyle(styles.filter { it.modelVersion == "4" })?.id)
    }
}
