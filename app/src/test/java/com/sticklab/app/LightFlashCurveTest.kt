package com.sticklab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Prüft den Lichtblitz (Phase 5c) ohne Odin: Verlauf weich ein/halten/aus, Ende genau bei der Farbe von vorher,
// und das Lesen/Schreiben des LED-Textes der Systemeinstellung
class LightFlashCurveTest {

    private val black = LedColors.both(0xFF000000.toInt())
    private val blue = ProfileColor.BLUE.argb

    @Test
    fun mixRisesHoldsAndFallsBack() {
        assertEquals(0f, LightFlashCurve.mix(0L), 0f)
        assertEquals(0.5f, LightFlashCurve.mix(LightFlashCurve.FADE_IN_MS / 2), 0.001f)
        assertEquals(1f, LightFlashCurve.mix(LightFlashCurve.FADE_IN_MS), 0f)
        assertEquals(1f, LightFlashCurve.mix(LightFlashCurve.FADE_IN_MS + LightFlashCurve.HOLD_MS - 1), 0f)
        assertEquals(0f, LightFlashCurve.mix(LightFlashCurve.TOTAL_MS), 0f)
        assertEquals(0f, LightFlashCurve.mix(LightFlashCurve.TOTAL_MS + 500), 0f)
    }

    // Weich heißt: nie ein Sprung rückwärts beim Einblenden, nie vorwärts beim Ausblenden
    @Test
    fun mixNeverJumpsBackDuringFades() {
        val fadeIn = (0L..LightFlashCurve.FADE_IN_MS).map(LightFlashCurve::mix)
        assertTrue(fadeIn.zipWithNext().all { (a, b) -> b >= a })
        val fadeOutStart = LightFlashCurve.FADE_IN_MS + LightFlashCurve.HOLD_MS
        val fadeOut = (fadeOutStart..LightFlashCurve.TOTAL_MS).map(LightFlashCurve::mix)
        assertTrue(fadeOut.zipWithNext().all { (a, b) -> b <= a })
    }

    @Test
    fun totalIsOneSecond() {
        assertEquals(1000L, LightFlashCurve.TOTAL_MS)
        assertFalse(LightFlashCurve.isFinished(LightFlashCurve.TOTAL_MS - 1))
        assertTrue(LightFlashCurve.isFinished(LightFlashCurve.TOTAL_MS))
    }

    @Test
    fun holdShowsProfileColorOnBothSides() {
        val colors = LightFlashCurve.colorsAt(LightFlashCurve.FADE_IN_MS + 100, black, blue)
        assertEquals("#ff0a84ff,#ff0a84ff", colors.toSetting())
    }

    @Test
    fun startAndEndAreTheColorsFromBefore() {
        val before = LedColors(0xFF102030.toInt(), 0xFF000000.toInt())
        assertEquals(before, LightFlashCurve.colorsAt(0L, before, blue))
        assertEquals(before, LightFlashCurve.colorsAt(LightFlashCurve.TOTAL_MS, before, blue))
    }

    @Test
    fun halfwayMixesEachSideOnItsOwn() {
        val before = LedColors(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        val colors = LightFlashCurve.colorsAt(LightFlashCurve.FADE_IN_MS / 2, before, 0xFF0000FF.toInt())
        assertEquals("#ff000080,#ff8080ff", colors.toSetting())
    }

    @Test
    fun parseReadsTheOdinFormat() {
        val colors = LedColors.parse("#ff000000,#FF0A84FF")
        assertEquals(LedColors(0xFF000000.toInt(), 0xFF0A84FF.toInt()), colors)
        assertEquals("#ff000000,#ff0a84ff", colors?.toSetting())
    }

    @Test
    fun parseRefusesUnknownFormats() {
        assertNull(LedColors.parse(null))
        assertNull(LedColors.parse(""))
        assertNull(LedColors.parse("#ff000000"))
        assertNull(LedColors.parse("#ff000000,#ff000000,#ff000000"))
        assertNull(LedColors.parse("#000000,#000000"))
        assertNull(LedColors.parse("#ff000000;#ff000000"))
        assertNull(LedColors.parse("#ff00000g,#ff000000"))
    }
}
