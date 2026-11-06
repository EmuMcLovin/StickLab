package com.sticklab.app

import kotlin.math.roundToInt

// Umrechnung zwischen der Stellung eines Reglers (ganze Schritte 0, 1, 2 …) und dem Wert, den der Odin
// bekommt – für Sensitivity und Trigger (Schritt S, 26.10.2026). Die Deadzone hat ihre eigene Umrechnung
// (DeadzoneSteps), weil dort Auto-Set und Prüfungen daran hängen.
// Ohne Android-Teile, damit sie auf dem Laptop geprüft werden kann (SettingStepsTest)
class SettingSteps(private val range: ClosedFloatingPointRange<Float>, private val stepSize: Float) {

    // Höchste Reglerstellung (unterste = 0 = kleinster erlaubter Wert)
    val maxStep: Int = ((range.endInclusive - range.start) / stepSize).roundToInt()

    // Wert → nächste Reglerstellung, begrenzt auf 0 … maxStep. Ungültige Zahl (NaN) → 0
    fun toStep(value: Float): Int {
        if (value.isNaN()) return 0
        return ((value - range.start) / stepSize).roundToInt().coerceIn(0, maxStep)
    }

    // Reglerstellung → Wert (Sensitivity: 10 → 1.0; Trigger: 0 → 0.05)
    fun toValue(step: Int): Float = range.start + step.coerceIn(0, maxStep) * stepSize

    companion object {
        // Sensitivity 0,50–2,00 in 0,05-Schritten (Entscheidung S1): 30 Schritte, trifft 0,90 (Original)
        // und 1,00. Ein Steuerkreuz-Druck = 0,05
        val SENSITIVITY = SettingSteps(SettingLimits.SENSITIVITY, 0.05f)

        // Trigger 5–50 % in 1-%-Schritten wie das Odin-Menü (ganze Prozent): 45 Schritte
        val TRIGGER = SettingSteps(SettingLimits.TRIGGER_DEADZONE, 0.01f)

        // Äußere Deadzone 80–100 % und Anti-Deadzone 0–30 % in 1-%-Schritten (Vorschlag, Logbuch 05.11.2026):
        // feiner lässt sich am Stick kaum unterscheiden
        val OUTER_DEADZONE = SettingSteps(SettingLimits.OUTER_DEADZONE, 0.01f)
        val ANTI_DEADZONE = SettingSteps(SettingLimits.ANTI_DEADZONE, 0.01f)

        // Trigger voll ab 10–100 % in 5-%-Schritten (Vorschlag): 18 Schritte, trifft 50 %
        val TRIGGER_FULL_OUTPUT = SettingSteps(SettingLimits.TRIGGER_FULL_OUTPUT, 0.05f)
    }
}
