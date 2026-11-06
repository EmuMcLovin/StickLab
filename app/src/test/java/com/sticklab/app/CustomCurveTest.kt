package com.sticklab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Plan Phase 7: Anfang und Ende fest, 3 bis 5 Punkte, die Kurve darf nie fallen (R136)
class CustomCurveTest {

    private val steep = CustomCurve(listOf(CurvePoint(0.2f, 0.02f), CurvePoint(0.5f, 0.05f), CurvePoint(0.9f, 0.95f)))

    // Prüft eine Kurve in feinen Schritten auf "fällt nie" und im Bereich 0 bis 1
    private fun assertNeverFalls(curve: CustomCurve) {
        var last = 0f
        for (i in 0..1000) {
            val y = curve.outputAt(i / 1000f)
            assertTrue("bei ${i / 1000f}: $y < $last", y >= last)
            assertTrue(y in 0f..1f)
            last = y
        }
    }

    @Test
    fun startsAtZeroEndsAtOne() {
        assertEquals(0f, steep.outputAt(0f), 0f)
        assertEquals(1f, steep.outputAt(1f), 0f)
    }

    @Test
    fun runsThroughItsPoints() {
        for (point in steep.points) assertEquals(point.y, steep.outputAt(point.x), 0.0001f)
    }

    // Auch mit flachen Stücken und sehr steilen Sprüngen: nie rückwärts, kein Überschwingen
    @Test
    fun neverFalls() {
        assertNeverFalls(CustomCurve.DEFAULT)
        assertNeverFalls(steep)
        assertNeverFalls(CustomCurve(listOf(CurvePoint(0.1f, 0.5f), CurvePoint(0.12f, 0.5f), CurvePoint(0.14f, 1f))))
        assertNeverFalls(CustomCurve(listOf(CurvePoint(0.3f, 0f), CurvePoint(0.6f, 0f), CurvePoint(0.98f, 0f))))
        assertNeverFalls(CustomCurve(listOf(CurvePoint(0.02f, 1f), CurvePoint(0.5f, 1f), CurvePoint(0.9f, 1f))))
    }

    // Die Gerade bleibt eine Gerade: Standard-Punkte verändern nichts
    @Test
    fun defaultIsStraight() {
        for (i in 0..100) assertEquals(i / 100f, CustomCurve.DEFAULT.outputAt(i / 100f), 0.0001f)
    }

    // Ein Punkt kann nicht an seinem Nachbarn vorbei und nicht aus dem Bereich: Regeln bleiben immer eingehalten
    @Test
    fun movedPointStaysBetweenNeighbours() {
        val base = CustomCurve.DEFAULT
        val tooFarRight = base.withPointMoved(0, 0.9f, 0.9f)
        assertTrue(tooFarRight.isValid())
        assertTrue(tooFarRight.points[0].x < base.points[1].x)
        assertEquals(base.points[1].y, tooFarRight.points[0].y, 0f)
        assertTrue(base.withPointMoved(2, 5f, 5f).isValid())
        assertTrue(base.withPointMoved(1, -5f, -5f).isValid())
        assertEquals(base, base.withPointMoved(7, 0.5f, 0.5f))
        assertEquals(base, base.withPointMoved(0, Float.NaN, 0.5f))
    }

    @Test
    fun addsUpToFivePointsRemovesDownToThree() {
        var curve = CustomCurve.DEFAULT
        repeat(5) { curve = curve.withPointAdded() }
        assertEquals(CustomCurve.MAX_POINTS, curve.points.size)
        assertTrue(curve.isValid())
        repeat(5) { curve = curve.withPointRemoved(0) }
        assertEquals(CustomCurve.MIN_POINTS, curve.points.size)
        assertTrue(curve.isValid())
    }

    // Ein neuer Punkt liegt auf der Kurve, ihre Form bleibt also fast gleich
    @Test
    fun addedPointSitsOnTheCurve() {
        val added = steep.withPointAdded().points.first { it !in steep.points }
        assertEquals(steep.outputAt(added.x), added.y, 0f)
    }

    @Test
    fun survivesEncodeAndDecode() {
        val decoded = CustomCurve.decode(steep.encode())!!
        assertEquals(steep.points.size, decoded.points.size)
        steep.points.zip(decoded.points).forEach { (a, b) ->
            assertEquals(a.x, b.x, 0.0001f)
            assertEquals(a.y, b.y, 0.0001f)
        }
    }

    @Test
    fun brokenTextGivesNull() {
        assertNull(CustomCurve.decode(null))
        assertNull(CustomCurve.decode(""))
        assertNull(CustomCurve.decode("irgendetwas"))
        assertNull(CustomCurve.decode("0.2:0.2;0.5:0.5"))                     // zu wenige Punkte
        assertNull(CustomCurve.decode("0.5:0.5;0.2:0.2;0.8:0.8"))             // falsche Reihenfolge
        assertNull(CustomCurve.decode("0.2:0.9;0.5:0.2;0.8:0.8"))             // fällt
        assertNull(CustomCurve.decode("0.2:0.2;0.5:NaN;0.8:0.8"))             // keine Zahl
        assertNull(CustomCurve.decode("0.2:0.2;0.5:0.5;1.5:1.5"))             // außerhalb
        assertFalse(CustomCurve(emptyList()).isValid())
    }

    // Tabelle für das Hilfsprogramm: auch mit eigener Kurve gültig (gleiche Regel wie im C-Programm)
    @Test
    fun customCurveGivesValidTable() {
        val table = CurveTable.build(CurveSettings(Curve.CUSTOM, custom = steep))
        assertTrue(CurveTable.isValid(table))
        val withEverything = CurveTable.build(CurveSettings(Curve.CUSTOM, 0.9f, 0.1f, steep))
        assertTrue(CurveTable.isValid(withEverything))
    }

    // Kaputte Punkte dürfen nie in eine Tabelle kommen: dann gilt die Gerade
    @Test
    fun brokenPointsInSettingsFallBackToStraight() {
        val broken = CustomCurve(listOf(CurvePoint(0.5f, 0.9f), CurvePoint(0.2f, 0.1f)))
        val table = CurveTable.build(CurveSettings(Curve.CUSTOM, custom = broken))
        assertTrue(CurveTable.isValid(table))
        assertEquals(CurveTable.build(CurveSettings(Curve.LINEAR)).toList(), table.toList())
    }

    @Test
    fun sampledCurveFollowsPreset() {
        val sampled = CustomCurve.sampledFrom(Curve.PRECISION)
        assertTrue(sampled.isValid())
        assertEquals(CurveTable.shape(Curve.PRECISION, 0.5f), sampled.outputAt(0.5f), 0.0001f)
    }
}
