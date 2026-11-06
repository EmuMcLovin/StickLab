package com.sticklab.app

import org.junit.Assert.assertEquals
import org.junit.Test

// Prüft die Umrechnung Regler ↔ Deadzone-Wert (Risiko R53: falsche Umrechnung = falscher Wert per "Apply").
// Seit 19.10.2026 ein Schritt = 0,1 % (Entscheidung R1, Risiko R102: Rundung bei Kommazahlen)
class DeadzoneStepsTest {

    // Erlaubte Rechenungenauigkeit bei Kommazahlen
    private val tolerance = 0.00001f

    @Test
    fun ownerValuesBecomeTheRightSteps() {
        // Werte auf dem Odin des Besitzers: links 1,5 %, rechts 2,5 % – müssen im Raster liegen (sonst verstellt "Apply")
        assertEquals(15, DeadzoneSteps.toStep(0.015f))
        assertEquals(25, DeadzoneSteps.toStep(0.025f))
    }

    @Test
    fun pointTwoValuesExist() {
        // 0,2-%-Werte aus dem Plan (A4: 0.022 nahm der Odin an)
        assertEquals(22, DeadzoneSteps.toStep(0.022f))
        assertEquals(16, DeadzoneSteps.toStep(0.016f))
        assertEquals(0.024f, DeadzoneSteps.toFraction(24), tolerance)
    }

    @Test
    fun stepsBecomeTheRightFractions() {
        assertEquals(0.015f, DeadzoneSteps.toFraction(15), tolerance)
        assertEquals(0.030f, DeadzoneSteps.toFraction(30), tolerance)
        assertEquals(1.5f, DeadzoneSteps.toPercent(15), tolerance)
    }

    @Test
    fun maxStepIsTwentyPercent() {
        assertEquals(200, DeadzoneSteps.MAX_STEP)
        assertEquals(0.20f, DeadzoneSteps.toFraction(DeadzoneSteps.MAX_STEP), tolerance)
    }

    @Test
    fun valuesOutsideTheRangeAreLimited() {
        assertEquals(200, DeadzoneSteps.toStep(0.9f))
        assertEquals(0, DeadzoneSteps.toStep(-0.1f))
        assertEquals(0, DeadzoneSteps.toStep(Float.NaN))
        assertEquals(0.20f, DeadzoneSteps.toFraction(999), tolerance)
        assertEquals(0.0f, DeadzoneSteps.toFraction(-1), tolerance)
    }

    @Test
    fun valueBetweenTwoStepsIsRounded() {
        // 1,24 % liegt näher an 1,2 % (Schritt 12), 1,26 % näher an 1,3 % (Schritt 13)
        assertEquals(12, DeadzoneSteps.toStep(0.0124f))
        assertEquals(13, DeadzoneSteps.toStep(0.0126f))
    }

    @Test
    fun everyStepSurvivesTheRoundTrip() {
        for (step in 0..DeadzoneSteps.MAX_STEP) {
            assertEquals(step, DeadzoneSteps.toStep(DeadzoneSteps.toFraction(step)))
        }
    }
}
