package com.sticklab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Prüft die Regel der Loslass-Messung (Phase 3b, Risiko R71): Was zählt als "liegen geblieben"?
class DriftMeterTest {

    private val tolerance = 0.001f

    // Rechter Stick aus Test A (10.10.2026): Rohwert (-217, 62) nach dem Loslassen = ca. 0,69 %
    private val restFromTestA = StickPosition(-217f / 32767f, 62f / 32767f)

    @Test
    fun valueBeforeSilenceIsARestPoint() {
        val meter = DriftMeter()
        meter.add(1000, StickPosition(0.9f, 0f))     // Stick am Anschlag
        meter.add(1010, StickPosition(0.4f, 0f))     // schnellt zurück
        meter.add(1020, restFromTestA)               // bleibt liegen
        val result = meter.result(endTimeMs = 2000)
        assertEquals(1, result.count)
        assertEquals(0.689f, result.maxPercent!!, tolerance)
    }

    @Test
    fun valuesDuringMovementAreNoRestPoints() {
        val meter = DriftMeter()
        // Alle 10 ms ein neuer Wert, nie 300 ms Stille → nur der letzte zählt (danach Stille bis zum Ende)
        for (i in 0 until 20) meter.add(1000L + i * 10, StickPosition(0.01f * (20 - i), 0f))
        val result = meter.result(endTimeMs = 2000)
        assertEquals(1, result.count)
        assertEquals(1f, result.maxPercent!!, tolerance)
    }

    @Test
    fun heldStickIsNoRestPoint() {
        val meter = DriftMeter()
        meter.add(1000, StickPosition(0.5f, 0f))     // 50 % festgehalten, dann Stille
        assertEquals(0, meter.result(endTimeMs = 2000).count)
    }

    @Test
    fun lastValueRightBeforeTheEndDoesNotCount() {
        val meter = DriftMeter()
        meter.add(1900, restFromTestA)               // nur 100 ms vor dem Ende: noch nicht sicher in Ruhe
        assertEquals(0, meter.result(endTimeMs = 2000).count)
    }

    @Test
    fun repeatedSamePositionIsIgnored() {
        val meter = DriftMeter()
        meter.add(1000, restFromTestA)
        // Der andere Stick bewegt sich: dieser Stick kommt mit gleichem Wert in jeder Meldung mit
        for (i in 1..50) meter.add(1000L + i * 10, restFromTestA)
        assertEquals(1, meter.result(endTimeMs = 3000).count)
    }

    @Test
    fun largestRestWinsAndIsComparedWithTheDeadzone() {
        val meter = DriftMeter()
        meter.add(1000, StickPosition(0.005f, 0f))   // 0,5 %
        meter.add(2000, StickPosition(0.9f, 0f))
        meter.add(2010, StickPosition(0.021f, 0f))   // 2,1 %
        val result = meter.result(endTimeMs = 4000)
        assertEquals(2, result.count)
        assertEquals(2.1f, result.maxPercent!!, tolerance)
        assertTrue(result.fitsInside(0.025f)!!)      // 2,5 % reicht
        assertFalse(result.fitsInside(0.015f)!!)     // 1,5 % reicht nicht
    }

    // Seit 3c: Ruhepunkt wird schon während der Messung erkannt, sobald 300 ms Stille vorbei sind
    @Test
    fun restPointIsRecognizedWhileMeasuring() {
        val meter = DriftMeter()
        assertFalse(meter.add(1000, StickPosition(0.9f, 0f)))
        assertFalse(meter.add(1010, restFromTestA))
        assertFalse(meter.settle(1200))                  // erst 190 ms still
        assertTrue(meter.settle(1310))                   // 300 ms still → neuer Ruhepunkt
        assertFalse(meter.settle(1500))                  // derselbe Punkt zählt nicht zweimal
        assertEquals(1, meter.resultSoFar().count)
        assertEquals(1, meter.result(endTimeMs = 2000).count)
    }

    @Test
    fun noReleaseMeansNoData() {
        val result = DriftMeter().result(endTimeMs = 2000)
        assertEquals(0, result.count)
        assertNull(result.maxPercent)
        assertNull(result.fitsInside(0.025f))
    }
}
