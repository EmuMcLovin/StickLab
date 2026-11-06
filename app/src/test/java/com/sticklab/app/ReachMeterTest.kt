package com.sticklab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Prüft die 8 Felder des Auto-Sets (Phase 3c-1): Richtung, Höchstausschlag, "erledigt" erst nach dem Loslassen
// (seit 3c-3 nur das Feld, aus dem losgelassen wurde)
class ReachMeterTest {

    private val tolerance = 0.01f

    // Stick in Richtung "degrees" (0 = oben, im Uhrzeigersinn) mit Ausschlag "percent"
    private fun at(degrees: Double, percent: Float): StickPosition {
        val radians = Math.toRadians(degrees)
        val length = percent / 100f
        return StickPosition((Math.sin(radians) * length).toFloat(), (-Math.cos(radians) * length).toFloat())
    }

    @Test
    fun eightDirectionsAreRecognized() {
        StickDirection.entries.forEachIndexed { i, direction ->
            assertEquals(direction, StickDirection.of(at(i * 45.0, 90f)))
            // Bis knapp vor die Grenze (±22,5°) bleibt es dieselbe Richtung
            assertEquals(direction, StickDirection.of(at(i * 45.0 + 22.0, 90f)))
            assertEquals(direction, StickDirection.of(at(i * 45.0 - 22.0, 90f)))
        }
        // y zeigt nach unten: (0, -1) ist oben, (1, 1) ist unten rechts
        assertEquals(StickDirection.UP, StickDirection.of(StickPosition(0f, -1f)))
        assertEquals(StickDirection.DOWN_RIGHT, StickDirection.of(StickPosition(1f, 1f)))
        assertEquals(StickDirection.LEFT, StickDirection.of(StickPosition(-1f, 0f)))
    }

    @Test
    fun fieldIsDoneOnlyAfterReachingAndLettingGo() {
        val meter = ReachMeter()
        meter.add(1000, at(90.0, 96f))                       // rechts am Anschlag
        assertEquals(FieldState.REACHED, meter.snapshot().field(StickDirection.RIGHT).state)
        meter.add(1020, StickPosition(0.004f, 0f))           // losgelassen, Rest 0,4 %
        meter.settle(1200)                                   // erst 180 ms still: noch nicht sicher
        assertEquals(FieldState.REACHED, meter.snapshot().field(StickDirection.RIGHT).state)
        meter.settle(1330)                                   // 310 ms still: Ruhepunkt
        val reach = meter.snapshot()
        assertEquals(FieldState.DONE, reach.field(StickDirection.RIGHT).state)
        assertEquals(96f, reach.field(StickDirection.RIGHT).peakPercent!!, tolerance)
        assertEquals(1, reach.rest.count)
        assertEquals(1, reach.doneCount)
    }

    @Test
    fun notFarEnoughStaysOpenButShowsThePeak() {
        val meter = ReachMeter()
        meter.add(1000, at(0.0, 60f))                        // nur 60 % nach oben
        meter.add(1020, StickPosition.CENTER)
        val reach = meter.result(endTimeMs = 2000)
        assertEquals(FieldState.OPEN, reach.field(StickDirection.UP).state)
        assertEquals(60f, reach.field(StickDirection.UP).peakPercent!!, tolerance)
        assertEquals(1, reach.rest.count)                    // losgelassen wurde trotzdem
    }

    @Test
    fun nextMovementAlsoCountsAsLettingGo() {
        val meter = ReachMeter()
        meter.add(1000, at(180.0, 97f))                      // unten am Anschlag
        meter.add(1010, StickPosition(0f, 0.01f))            // zurück, Rest 1 %
        meter.add(1500, at(270.0, 95f))                      // 490 ms später nach links: vorher lag er still
        val reach = meter.snapshot()
        assertEquals(FieldState.DONE, reach.field(StickDirection.DOWN).state)
        assertEquals(FieldState.REACHED, reach.field(StickDirection.LEFT).state)
    }

    @Test
    fun holdingAtTheEdgeIsNoRelease() {
        val meter = ReachMeter()
        meter.add(1000, at(45.0, 99f))                       // schräg oben rechts festgehalten
        meter.settle(3000)
        assertEquals(FieldState.REACHED, meter.snapshot().field(StickDirection.UP_RIGHT).state)
        assertEquals(0, meter.snapshot().rest.count)
    }

    @Test
    fun smallValuesCountForNoDirection() {
        val meter = ReachMeter()
        meter.add(1000, at(0.0, 5f))                         // 5 % = Drift, kein Drücken
        assertNull(meter.snapshot().field(StickDirection.UP).peakPercent)
    }

    @Test
    fun highestValueOfAllAttemptsIsKept() {
        val meter = ReachMeter()
        meter.add(1000, at(270.0, 94f))
        meter.add(1010, at(270.0, 96f))
        meter.add(1020, at(270.0, 90f))                      // Rückweg zählt nicht nach unten
        assertEquals(96f, meter.snapshot().field(StickDirection.LEFT).peakPercent!!, tolerance)
    }

    @Test
    fun allEightDoneAfterAFullRound() {
        val meter = ReachMeter()
        var time = 1000L
        for (i in 0 until 8) {
            meter.add(time, at(i * 45.0, 95f + i * 0.5f))
            meter.add(time + 10, StickPosition.CENTER)
            time += 500
        }
        val reach = meter.result(endTimeMs = time)
        assertTrue(reach.allDone)
        assertEquals(8, reach.rest.count)
    }

    @Test
    fun restPointsAreTheSameAsInTheEndOfMeasurementRule() {
        // Dieselben Werte live (mit settle zwischendurch) und nur am Ende ausgewertet: gleiche Ruhepunkte
        val values = listOf(
            1000L to at(90.0, 96f), 1010L to StickPosition(0.008f, 0f),
            1400L to at(0.0, 95f), 1420L to StickPosition(0f, -0.012f),
            1600L to at(180.0, 50f), 1610L to StickPosition(0f, 0.003f),
        )
        val live = ReachMeter()
        val endOnly = DriftMeter()
        values.forEach { (time, position) ->
            live.settle(time - 1)
            live.add(time, position)
            endOnly.add(time, position)
        }
        assertEquals(endOnly.result(2500), live.result(2500).rest)
        assertFalse(live.snapshot().allDone)
    }

    // 3c-3 (F4a): Einmal am Rand im Kreis herum und nur einmal loslassen = nur das letzte Feld erledigt.
    // So war es in der 25. Sitzung (Lauf 3 rechts): früher wurden davon alle 8 Felder grün
    @Test
    fun circlingTheEdgeAndLettingGoOnceDoesOnlyTheLastField() {
        val meter = ReachMeter()
        for (i in 0 until 8) meter.add(1000L + i * 10, at(i * 45.0, 96f))   // oben → … → oben links
        meter.add(1100, StickPosition.CENTER)                                 // einmal losgelassen
        val reach = meter.result(endTimeMs = 2000)
        assertEquals(1, reach.doneCount)
        assertEquals(FieldState.DONE, reach.field(StickDirection.UP_LEFT).state)
        assertEquals(7, reach.fields.count { it.state == FieldState.REACHED })
        assertEquals(1, reach.rest.count)
    }

    // 3c-3 (F4a): Loslassen ohne Anschlag seit dem letzten Ruhepunkt erledigt kein Feld (zählt aber als Rest)
    @Test
    fun lettingGoWithoutReachingTheEdgeDoesNoField() {
        val meter = ReachMeter()
        meter.add(1000, at(90.0, 96f))                       // rechts am Anschlag
        meter.add(1010, StickPosition.CENTER)                // losgelassen → rechts erledigt
        meter.add(1500, at(0.0, 60f))                        // nur 60 % nach oben
        meter.add(1510, StickPosition(0f, -0.01f))           // losgelassen
        val reach = meter.result(endTimeMs = 2500)
        assertEquals(1, reach.doneCount)
        assertEquals(FieldState.OPEN, reach.field(StickDirection.UP).state)
        assertEquals(2, reach.rest.count)
    }

    // 3c-3 (F4a): Zweimal aus derselben Richtung losgelassen bleibt ein Feld, beide Reste zählen
    @Test
    fun sameDirectionTwiceStaysOneField() {
        val meter = ReachMeter()
        meter.add(1000, at(180.0, 96f))
        meter.add(1010, StickPosition(0f, 0.01f))
        meter.add(1500, at(180.0, 97f))
        meter.add(1510, StickPosition(0f, 0.02f))
        val reach = meter.result(endTimeMs = 2500)
        assertEquals(1, reach.doneCount)
        assertEquals(FieldState.DONE, reach.field(StickDirection.DOWN).state)
        assertEquals(2, reach.rest.count)
    }
}
