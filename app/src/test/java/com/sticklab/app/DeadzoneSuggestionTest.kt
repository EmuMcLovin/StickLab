package com.sticklab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Prüft die Rechnung der Auto-Set-Vorschläge (Phase 3c-1, Entscheidungen S1 und V; seit 19.10.2026 im 0,1-Raster, V1)
class DeadzoneSuggestionTest {

    private val tolerance = 0.001f

    // Ein Stick mit genau einem Ruhepunkt von "percent" Prozent
    private fun restOf(percent: Float) = DriftResult(listOf(StickPosition(percent / 100f, 0f)))

    @Test
    fun restPlusReserveIsRoundedUpToTheNextStep() {
        assertEquals(12, DeadzoneSuggestion.deadzoneStep(restOf(0.85f)))  // 3b Lauf 1 rechts: 1,15 → 1,2 %
        assertEquals(18, DeadzoneSuggestion.deadzoneStep(restOf(1.47f)))  // 3b Lauf 1 links: 1,77 → 1,8 %
        assertEquals(32, DeadzoneSuggestion.deadzoneStep(restOf(2.821f))) // 3c-3 Lauf B links: 3,121 → 3,2 %
        assertEquals(3, DeadzoneSuggestion.deadzoneStep(restOf(0f)))       // nur Reserve: 0,3 %
    }

    @Test
    fun valueExactlyOnAStepIsNotRoundedFurther() {
        // 1,2 + 0,3 = 1,5 genau (als Kommazahl 1,5000001) → bleibt 1,5 %, nicht 1,6 %
        assertEquals(15, DeadzoneSuggestion.deadzoneStep(restOf(1.2f)))
        assertEquals(25, DeadzoneSuggestion.deadzoneStep(restOf(2.2f)))
        assertEquals(21, DeadzoneSuggestion.deadzoneStep(restOf(1.8f)))   // 2,1 genau → 2,1 %
    }

    @Test
    fun largestRestCounts() {
        val rest = DriftResult(listOf(StickPosition(0.004f, 0f), StickPosition(0.0216f, 0f)))
        assertEquals(25, DeadzoneSuggestion.deadzoneStep(rest))          // 2,16 + 0,3 = 2,46 → 2,5 %
    }

    @Test
    fun neverAboveTheLimit() {
        assertEquals(DeadzoneSteps.MAX_STEP, DeadzoneSuggestion.deadzoneStep(restOf(25f)))
    }

    @Test
    fun noReleaseMeansNoSuggestion() {
        assertNull(DeadzoneSuggestion.deadzoneStep(DriftResult(emptyList())))
    }

    // Ein Stick mit 8 erledigten Feldern und diesen Höchstausschlägen
    private fun reachOf(vararg peaks: Float?, state: FieldState = FieldState.DONE) =
        StickReach(peaks.map { ReachField(it, state) }, DriftResult(emptyList()))

    @Test
    fun outerDeadzoneStartsBelowTheSmallestPeak() {
        // 17. Sitzung rechts geradeaus 95,1 % → ab 94 %
        val reach = reachOf(96.6f, 100f, 95.1f, 100f, 96f, 100f, 95.5f, 100f)
        assertEquals(94f, DeadzoneSuggestion.outerDeadzonePercent(reach)!!, tolerance)
        // 95,5 − 1 = 94,5 liegt genau auf einem Schritt
        val exact = reachOf(96f, 100f, 95.5f, 100f, 96f, 100f, 97f, 100f)
        assertEquals(94.5f, DeadzoneSuggestion.outerDeadzonePercent(exact)!!, tolerance)
    }

    @Test
    fun outerDeadzoneNeverAbove100() {
        val reach = reachOf(105f, 120f, 104f, 120f, 106f, 120f, 103f, 120f)
        assertEquals(100f, DeadzoneSuggestion.outerDeadzonePercent(reach)!!, tolerance)
    }

    @Test
    fun noOuterDeadzoneWhenAFieldIsMissing() {
        val missing = StickReach(
            List(7) { ReachField(96f, FieldState.DONE) } + ReachField(null, FieldState.OPEN),
            DriftResult(emptyList()),
        )
        assertNull(DeadzoneSuggestion.outerDeadzonePercent(missing))
        // Anschlag erreicht, aber nicht losgelassen zählt auch nicht
        assertNull(DeadzoneSuggestion.outerDeadzonePercent(reachOf(96f, 96f, 96f, 96f, 96f, 96f, 96f, 96f, state = FieldState.REACHED)))
    }
}
