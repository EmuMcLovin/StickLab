package com.sticklab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Prüft, dass Werte vor dem Setzen sicher begrenzt werden (Plan Regel 7)
class SettingLimitsTest {

    @Test
    fun valueInsideRangeStaysUnchanged() {
        assertEquals(0.025f, SettingLimits.limit(0.025f, SettingLimits.DEADZONE))
    }

    @Test
    fun tooLargeDeadzoneIsCutToTwentyPercent() {
        assertEquals(0.20f, SettingLimits.limit(0.9f, SettingLimits.DEADZONE))
    }

    @Test
    fun negativeDeadzoneBecomesZero() {
        assertEquals(0.0f, SettingLimits.limit(-0.1f, SettingLimits.DEADZONE))
    }

    @Test
    fun infinityIsCutToRange() {
        assertEquals(2.0f, SettingLimits.limit(Float.POSITIVE_INFINITY, SettingLimits.SENSITIVITY))
    }

    // Bereiche wie das Odin-Menü (gemessen 26.10.2026): Sensitivity 0,50–2,00, Trigger 5–50 %
    @Test
    fun sensitivityAndTriggerStayInsideOdinMenuRange() {
        assertEquals(0.5f, SettingLimits.limit(0.1f, SettingLimits.SENSITIVITY))
        assertEquals(0.05f, SettingLimits.limit(0.0f, SettingLimits.TRIGGER_DEADZONE))
        assertEquals(0.50f, SettingLimits.limit(0.9f, SettingLimits.TRIGGER_DEADZONE))
    }

    @Test
    fun notANumberIsRejected() {
        assertNull(SettingLimits.limit(Float.NaN, SettingLimits.DEADZONE))
    }
}
