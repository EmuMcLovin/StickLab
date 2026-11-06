package com.sticklab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Prüft die Rechnung der Live-Anzeige (Phase 3a): Abstand zur Mitte, "exakt 0", Punkt im Kreis, Trigger
class StickPositionTest {

    // Erlaubte Rechenungenauigkeit bei Kommazahlen
    private val tolerance = 0.001f

    @Test
    fun centerIsExactlyZero() {
        assertTrue(StickPosition.CENTER.isExactlyCenter())
        assertEquals(0f, StickPosition.CENTER.distancePercent(), tolerance)
    }

    @Test
    fun tinyDriftIsNotExactlyZero() {
        // Ein einziger Rohwert-Schritt neben der Mitte (1 / 32767) ist schon "nicht 0"
        val drift = StickPosition.fromAxes(1f / 32767f, 0f)
        assertFalse(drift.isExactlyCenter())
        assertEquals(0.00305f, drift.distancePercent(), tolerance)
    }

    @Test
    fun distanceIsRadialNotPerAxis() {
        assertEquals(100f, StickPosition(1f, 0f).distancePercent(), tolerance)
        assertEquals(100f, StickPosition(0f, -1f).distancePercent(), tolerance)
        // 0,6 / 0,8 liegt genau auf dem Kreisrand
        assertEquals(100f, StickPosition(-0.6f, 0.8f).distancePercent(), tolerance)
        // Ecke: beide Achsen am Anschlag
        assertEquals(141.421f, StickPosition(1f, 1f).distancePercent(), tolerance)
    }

    @Test
    fun dotStaysInsideTheCircle() {
        val corner = StickPosition(1f, 1f).clampedToCircle()
        assertEquals(100f, corner.distancePercent(), tolerance)
        assertEquals(corner.x, corner.y, tolerance)
        // Innerhalb des Kreises bleibt alles, wie es ist
        val inside = StickPosition(0.3f, -0.2f)
        assertEquals(inside, inside.clampedToCircle())
    }

    @Test
    fun badAxisValuesAreCleanedUp() {
        assertEquals(StickPosition(1f, -1f), StickPosition.fromAxes(1.5f, -7f))
        assertEquals(StickPosition.CENTER, StickPosition.fromAxes(Float.NaN, Float.POSITIVE_INFINITY))
    }

    @Test
    fun triggerPressureIsPercentAndLimited() {
        assertEquals(5f, TriggerPressure.percentOf(0.05f), tolerance)
        assertEquals(100f, TriggerPressure.percentOf(1f), tolerance)
        assertEquals(0f, TriggerPressure.percentOf(-0.1f), tolerance)
        assertEquals(100f, TriggerPressure.percentOf(1.2f), tolerance)
        assertEquals(0f, TriggerPressure.percentOf(Float.NaN), tolerance)
    }
}
