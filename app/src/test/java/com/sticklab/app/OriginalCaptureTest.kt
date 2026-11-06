package com.sticklab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Prüft die Regel für das Original: nur leere Felder füllen, nie überschreiben (Entscheidung R-1)
class OriginalCaptureTest {

    // Werte wie am Odin des Besitzers (gelesen 15.10.2026)
    private val odinNow = mapOf(
        OriginalField.DEADZONE_LEFT to "0.015",
        OriginalField.DEADZONE_RIGHT to "0.025",
        OriginalField.TRIGGER_L2 to "0.05",
        OriginalField.TRIGGER_R2 to "0.05",
        OriginalField.SENSITIVITY_LEFT to "0.9",
        OriginalField.SENSITIVITY_RIGHT to "0.9",
        OriginalField.M1 to "96",
        OriginalField.M2 to "97",
    )

    @Test
    fun firstStartSavesEverything() {
        assertEquals(odinNow, OriginalCapture.valuesToAdd(emptySet(), odinNow))
    }

    @Test
    fun savedFieldIsNeverOverwritten() {
        val changedLater = odinNow + (OriginalField.DEADZONE_LEFT to "0.035")
        val toAdd = OriginalCapture.valuesToAdd(setOf(OriginalField.DEADZONE_LEFT.key), changedLater)
        assertTrue(OriginalField.DEADZONE_LEFT !in toAdd)
        assertEquals(odinNow.size - 1, toAdd.size)
    }

    @Test
    fun allSavedMeansNothingToAdd() {
        val allKeys = OriginalField.entries.map { it.key }.toSet()
        assertTrue(OriginalCapture.valuesToAdd(allKeys, odinNow).isEmpty())
    }

    @Test
    fun unreadableValueIsSkippedAndCanComeLater() {
        val noLiveWay = odinNow + mapOf(
            OriginalField.SENSITIVITY_LEFT to null,
            OriginalField.SENSITIVITY_RIGHT to null,
        )
        val firstStart = OriginalCapture.valuesToAdd(emptySet(), noLiveWay)
        assertTrue(OriginalField.SENSITIVITY_LEFT !in firstStart)

        // Beim nächsten Mal ist der Live-Weg da: nur die Sensitivity kommt dazu
        val saved = firstStart.keys.map { it.key }.toSet()
        val secondStart = OriginalCapture.valuesToAdd(saved, odinNow)
        assertEquals(
            mapOf(OriginalField.SENSITIVITY_LEFT to "0.9", OriginalField.SENSITIVITY_RIGHT to "0.9"),
            secondStart,
        )
    }

    @Test
    fun blankValueIsSkipped() {
        val blank = mapOf(OriginalField.M1 to "  ", OriginalField.M2 to "")
        assertTrue(OriginalCapture.valuesToAdd(emptySet(), blank).isEmpty())
    }
}
