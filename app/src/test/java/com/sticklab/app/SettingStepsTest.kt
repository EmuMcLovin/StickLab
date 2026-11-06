package com.sticklab.app

import org.junit.Assert.assertEquals
import org.junit.Test

// Prüft die Reglerschritte für Sensitivity und Trigger (Schritt S): Originalwerte liegen genau auf einem
// Schritt, die Enden sind die Grenzen des Odin-Menüs, falsche Zahlen landen sicher am Rand
class SettingStepsTest {

    private val tolerance = 0.0001f

    @Test
    fun sensitivityHasThirtyStepsOfFiveHundredths() {
        assertEquals(30, SettingSteps.SENSITIVITY.maxStep)
        assertEquals(0.50f, SettingSteps.SENSITIVITY.toValue(0), tolerance)
        assertEquals(2.00f, SettingSteps.SENSITIVITY.toValue(30), tolerance)
    }

    // 0,90 = Original des Besitzers, 1,00 = sein heutiger Wert – beide müssen genau getroffen werden
    @Test
    fun ownerValuesLieExactlyOnASensitivityStep() {
        assertEquals(8, SettingSteps.SENSITIVITY.toStep(0.9f))
        assertEquals(0.90f, SettingSteps.SENSITIVITY.toValue(8), tolerance)
        assertEquals(10, SettingSteps.SENSITIVITY.toStep(1.0f))
        assertEquals(1.00f, SettingSteps.SENSITIVITY.toValue(10), tolerance)
    }

    // Trigger voll ab (Z11): 10–100 % in 5-%-Schritten, ganz rechts = aus, trifft 50 %
    @Test
    fun triggerFullOutputHasEighteenSteps() {
        val steps = SettingSteps.TRIGGER_FULL_OUTPUT
        assertEquals(18, steps.maxStep)
        assertEquals(18, steps.toStep(CurveSettings.NO_TRIGGER_LIMIT))
        assertEquals(8, steps.toStep(0.5f))
        assertEquals(0.10f, steps.toValue(0), tolerance)
    }

    @Test
    fun triggerGoesFromFiveToFiftyPercentInWholePercent() {
        assertEquals(45, SettingSteps.TRIGGER.maxStep)
        assertEquals(0, SettingSteps.TRIGGER.toStep(0.05f))
        assertEquals(0.05f, SettingSteps.TRIGGER.toValue(0), tolerance)
        assertEquals(5, SettingSteps.TRIGGER.toStep(0.10f))
        assertEquals(0.50f, SettingSteps.TRIGGER.toValue(45), tolerance)
    }

    @Test
    fun valuesOutsideTheRangeLandOnTheEdge() {
        assertEquals(0, SettingSteps.TRIGGER.toStep(0.0f))
        assertEquals(45, SettingSteps.TRIGGER.toStep(0.9f))
        assertEquals(0, SettingSteps.SENSITIVITY.toStep(Float.NaN))
        assertEquals(0.50f, SettingSteps.TRIGGER.toValue(999), tolerance)
    }
}
