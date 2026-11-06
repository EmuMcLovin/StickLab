package com.sticklab.app

import kotlin.math.roundToInt

// Umrechnung zwischen der Stellung eines Deadzone-Reglers (ganze Schritte 0, 1, 2 …)
// und dem Deadzone-Wert als Bruchteil wie in den Odin-Einstellungen (2,5 % = 0.025).
// Ohne Android-Teile, damit sie auf dem Laptop geprüft werden kann (DeadzoneStepsTest, Risiko R53)
object DeadzoneSteps {

    // Ein Reglerschritt = 0,1 % Deadzone (Entscheidung R1 vom 19.10.2026, vorher 0,5 % seit v1.0).
    // 0,1 statt 0,2: enthält alle 0,2-Werte und auch 1,5 % / 2,5 % (Original, Odin-Menü).
    // Die Firmware nimmt solche Werte an (0.022 live gesetzt und genau so nachgelesen, A4, 11.10.2026)
    const val STEP_FRACTION = 0.001f

    // Höchste Reglerstellung = obere Grenze aus SettingLimits.DEADZONE (20 % → 200 Schritte)
    val MAX_STEP: Int = (SettingLimits.DEADZONE.endInclusive / STEP_FRACTION).roundToInt()

    // Startwert des Reglers, wenn der gespeicherte Wert nicht gelesen werden kann (wie v1.0: 2 %)
    const val FALLBACK_FRACTION = 0.02f

    // Bruchteil → nächste Reglerstellung, begrenzt auf 0 … MAX_STEP. Ungültige Zahl (NaN) → 0
    fun toStep(fraction: Float): Int {
        if (fraction.isNaN()) return 0
        return (fraction / STEP_FRACTION).roundToInt().coerceIn(0, MAX_STEP)
    }

    // Reglerstellung → Bruchteil (3 → 0.015)
    fun toFraction(step: Int): Float = step.coerceIn(0, MAX_STEP) * STEP_FRACTION

    // Reglerstellung → Prozent für die Beschriftung (3 → 1.5)
    fun toPercent(step: Int): Float = toFraction(step) * 100f
}
