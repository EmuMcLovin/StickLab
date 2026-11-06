package com.sticklab.app

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

// Vorschläge des Auto-Sets (Phase 3c-1). Werden nur angezeigt, nichts wird gesetzt
// (Übernehmen erst nach einer Kontrollmessung, 3c-2).
// Ohne Android-Teile, damit die Rechnung auf dem Laptop geprüft werden kann (DeadzoneSuggestionTest)
object DeadzoneSuggestion {

    // Reserve auf den größten Rest (Entscheidung V, 17.10.2026)
    const val RESERVE_PERCENT = 0.3f

    // Äußere Deadzone: so viel unter dem kleinsten Höchstausschlag beginnen (Plan 3c), auf 0,5 % abgerundet
    const val OUTER_MARGIN_PERCENT = 1f
    const val OUTER_STEP_PERCENT = 0.5f

    // Voller Ausschlag. Ein Vorschlag ab 100 % heißt: keine äußere Deadzone nötig
    const val FULL_PERCENT = 100f

    // Ein Reglerschritt in Prozent (DeadzoneSteps: seit 19.10.2026 0,1 %, Entscheidung V1: Vorschlag im feinen Raster)
    private const val STEP_PERCENT = DeadzoneSteps.STEP_FRACTION * 100f

    // Rechnen mit Kommazahlen ist nie ganz genau (1,2 + 0,3 ergibt 1,5000001). Ohne diese kleine
    // Toleranz würde ein Wert genau auf einem Schritt einen ganzen Schritt zu weit gerundet
    private const val ROUNDING_TOLERANCE = 0.001f

    // Vorschlag Deadzone als Reglerstellung (DeadzoneSteps, 3 = 1,5 %): größter Rest + Reserve,
    // aufgerundet auf den nächsten Schritt, höchstens 20 %. null = der Stick wurde nie losgelassen
    fun deadzoneStep(rest: DriftResult): Int? {
        val largestRest = rest.maxPercent ?: return null
        val steps = ceil((largestRest + RESERVE_PERCENT) / STEP_PERCENT - ROUNDING_TOLERANCE).roundToInt()
        return steps.coerceIn(0, DeadzoneSteps.MAX_STEP)
    }

    // Vorschlag äußere Deadzone in Prozent: kleinster Höchstausschlag aller 8 Richtungen − 1 %,
    // abgerundet auf 0,5 %, höchstens 100 %. null = nicht alle 8 Felder erledigt (Plan 3c Lauf 2)
    fun outerDeadzonePercent(reach: StickReach): Float? {
        if (!reach.allDone) return null
        val smallestPeak = reach.fields.minOf { it.peakPercent ?: return null }
        val start = floor((smallestPeak - OUTER_MARGIN_PERCENT) / OUTER_STEP_PERCENT + ROUNDING_TOLERANCE) * OUTER_STEP_PERCENT
        return start.coerceAtMost(FULL_PERCENT)
    }
}
