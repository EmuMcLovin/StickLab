package com.sticklab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Prüft die Rechnung der Kurven-Engine (Phase 6, Schritt 6a) ohne Odin: jede Tabelle beginnt bei 0, steigt nie ab,
// endet bei 100 % (R136); Linear ändert nichts; äußere Deadzone, Anti-Deadzone mit Schwelle (K6-3) und Grenzen
class CurveTableTest {

    private val max = CurveTable.MAX_OUTPUT

    // Wert der Tabelle beim Ausschlag in Prozent (nächster Stützpunkt)
    private fun IntArray.at(percent: Int): Int = this[Math.round(percent / 100f * (CurveTable.SIZE - 1))]

    // Nur was wirklich etwas ändert, braucht den Curve mode (Hinweis in App und Kachel)
    @Test
    fun needsCurveModeOnlyWhenSomethingChanges() {
        assertFalse(CurveSettings().needsCurveMode)
        assertTrue(CurveSettings(Curve.SHOOTER).needsCurveMode)
        assertTrue(CurveSettings(outerDeadzone = 0.95f).needsCurveMode)
        assertTrue(CurveSettings(antiDeadzone = 0.1f).needsCurveMode)
        assertTrue(CurveSettings(triggerFullOutput = 0.5f).needsCurveMode)
    }

    @Test
    fun everyCurveGivesAValidTable() {
        for (curve in Curve.entries) {
            for (anti in listOf(0f, 0.10f, 0.30f)) {
                for (outer in listOf(1f, 0.95f, 0.80f)) {
                    val table = CurveTable.build(CurveSettings(curve, outer, anti))
                    assertTrue("$curve outer=$outer anti=$anti", CurveTable.isValid(table))
                }
            }
        }
    }

    // Linear ohne äußere und Anti-Deadzone = 1:1 (erster Test des Hilfsprogramms in 6b: Ausgabe = Eingabe)
    @Test
    fun linearChangesNothing() {
        val table = CurveTable.build(CurveSettings())
        for (index in 0 until CurveTable.SIZE) {
            assertEquals(Math.round(index * max / 255f), table[index])
        }
    }

    // Die Werte aus der Erklärung an den Besitzer (27.10.2026), auf 1 % genau
    @Test
    fun curvesHaveTheExplainedShape() {
        fun percentAt(curve: Curve, x: Float) = Math.round(CurveTable.shape(curve, x) * 100)
        assertEquals(listOf(8, 29, 60, 83), listOf(0.25f, 0.5f, 0.75f, 0.9f).map { percentAt(Curve.PRECISION, it) })
        assertEquals(listOf(44, 66, 84, 94), listOf(0.25f, 0.5f, 0.75f, 0.9f).map { percentAt(Curve.FAST, it) })
        assertEquals(listOf(16, 50, 84, 97), listOf(0.25f, 0.5f, 0.75f, 0.9f).map { percentAt(Curve.S_CURVE, it) })
        assertEquals(listOf(18, 36, 60, 81), listOf(0.25f, 0.5f, 0.75f, 0.9f).map { percentAt(Curve.SHOOTER, it) })
    }

    @Test
    fun everyShapeStartsAtZeroAndEndsAtOne() {
        for (curve in Curve.entries) {
            assertEquals("$curve", 0f, CurveTable.shape(curve, 0f), 0.0001f)
            assertEquals("$curve", 1f, CurveTable.shape(curve, 1f), 0.0001f)
        }
    }

    // Äußere Deadzone 95 %: ab 95 % Ausschlag schon 100 %, darunter gleichmäßig gestreckt
    @Test
    fun outerDeadzoneReachesFullOutputEarly() {
        val settings = CurveSettings(outerDeadzone = 0.95f)
        assertEquals(1f, CurveTable.outputFor(settings, 0.95f), 0.0001f)
        assertEquals(1f, CurveTable.outputFor(settings, 0.97f), 0.0001f)
        assertEquals(0.5f / 0.95f, CurveTable.outputFor(settings, 0.5f), 0.0001f)
    }

    // Anti-Deadzone 10 %: unter der Schwelle 1 % bleibt es 0 (Drift-Reste, R137), ab der Schwelle mindestens 10 %
    @Test
    fun antiDeadzoneStartsOnlyAboveTheThreshold() {
        val settings = CurveSettings(antiDeadzone = 0.10f)
        assertEquals(0f, CurveTable.outputFor(settings, 0.003f), 0f)
        assertEquals(0f, CurveTable.outputFor(settings, 0.0099f), 0f)
        assertTrue(CurveTable.outputFor(settings, 0.01f) >= 0.10f)
        assertEquals(0.55f, CurveTable.outputFor(settings, 0.5f), 0.0001f)
        assertEquals(1f, CurveTable.outputFor(settings, 1f), 0.0001f)
    }

    // Ohne Anti-Deadzone gibt es keine Schwelle – sonst wäre sie eine zweite Deadzone
    @Test
    fun noThresholdWithoutAntiDeadzone() {
        assertEquals(0.005f, CurveTable.outputFor(CurveSettings(), 0.005f), 0.0001f)
    }

    @Test
    fun restStaysExactlyZero() {
        for (curve in Curve.entries) {
            assertEquals(0, CurveTable.build(CurveSettings(curve, 0.9f, 0.3f))[0])
        }
    }

    // Zu große oder kaputte Werte werden begrenzt (Plan Regel 7), die Tabelle bleibt gültig
    @Test
    fun outOfRangeSettingsAreLimited() {
        val wild = CurveTable.build(CurveSettings(Curve.FAST, outerDeadzone = 0.1f, antiDeadzone = 0.9f))
        assertTrue(CurveTable.isValid(wild))
        assertEquals(CurveTable.build(CurveSettings(Curve.FAST, 0.80f, 0.30f)).toList(), wild.toList())
        val broken = CurveTable.build(CurveSettings(Curve.LINEAR, Float.NaN, Float.NaN))
        assertEquals(CurveTable.build(CurveSettings()).toList(), broken.toList())
    }

    @Test
    fun isValidRefusesBrokenTables() {
        val good = CurveTable.build(CurveSettings())
        assertTrue(CurveTable.isValid(good))
        assertFalse(CurveTable.isValid(good.copyOf(255)))
        assertFalse(CurveTable.isValid(good.copyOf().also { it[0] = 5 }))
        assertFalse(CurveTable.isValid(good.copyOf().also { it[255] = max - 1 }))
        assertFalse(CurveTable.isValid(good.copyOf().also { it[100] = it[99] - 1 }))
        assertFalse(CurveTable.isValid(good.copyOf().also { it[10] = -1 }))
    }
}
