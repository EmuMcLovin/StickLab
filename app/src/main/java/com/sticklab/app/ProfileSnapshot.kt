package com.sticklab.app

// Liest für "Save current" (Phase 5a, Entscheidung P1) alle Werte, die gerade am Odin wirken – nur lesen, nichts setzen:
// Deadzone (Aufruf 13), Sensitivity (Aufruf 10), Trigger (Aufruf 15) über den Live-Weg, M1/M2 aus der Systemeinstellung.
// Fehlt ein Wert oder liegt er außerhalb dessen, was StickLab setzen darf (SettingLimits), gibt es kein Ergebnis,
// sondern den fehlenden Teil – ein Profil wird nie still mit einem Ersatzwert gespeichert (Risiko R119)
// curves liefert die gerade eingestellten Kurven (eigene Datei, CurveStore): Die kann immer gelesen werden
class ProfileSnapshot(
    private val mapping: OdinMapping,
    private val buttons: ButtonMappingControl,
    private val curves: () -> LeftRight<CurveSettings>,
) {

    // Teil, der fehlt (für die Meldung in der Statuszeile)
    enum class Part { DEADZONE, SENSITIVITY, TRIGGER, BUTTONS }

    sealed interface Result {
        data class Read(val values: OdinValues) : Result
        data class Missing(val part: Part) : Result
    }

    fun read(): Result {
        val deadzone = readPair(mapping::getDeadzone, SettingLimits.DEADZONE) ?: return Result.Missing(Part.DEADZONE)
        val sensitivity = readPair(mapping::getSensitivity, SettingLimits.SENSITIVITY)
            ?: return Result.Missing(Part.SENSITIVITY)
        val trigger = readPair(mapping::getTriggerDeadzone, SettingLimits.TRIGGER_DEADZONE)
            ?: return Result.Missing(Part.TRIGGER)
        val m1 = buttons.read(BackButton.M1) ?: return Result.Missing(Part.BUTTONS)
        val m2 = buttons.read(BackButton.M2) ?: return Result.Missing(Part.BUTTONS)
        return Result.Read(OdinValues(deadzone, sensitivity, trigger, m1, m2, curves()))
    }

    // Beide Seiten lesen; null, wenn eine fehlt (Live-Weg nicht verbunden) oder außerhalb des Bereichs liegt
    private fun readPair(
        read: (OdinMapping.Side) -> Float?,
        range: ClosedFloatingPointRange<Float>,
    ): LeftRight<Float>? {
        val left = withinRange(read(OdinMapping.Side.LEFT), range) ?: return null
        val right = withinRange(read(OdinMapping.Side.RIGHT), range) ?: return null
        return LeftRight(left, right)
    }

    // Wert im Bereich (mit winziger Toleranz am Rand), dann genau auf den Bereich begrenzt; sonst null.
    // Die Toleranz braucht es, weil z. B. der Trigger des Besitzers genau auf der Untergrenze 5 % steht und eine
    // gelesene Kommazahl knapp darunter liegen kann (0.0499999)
    private fun withinRange(value: Float?, range: ClosedFloatingPointRange<Float>): Float? {
        if (value == null || value.isNaN()) return null
        if (value < range.start - EDGE_TOLERANCE || value > range.endInclusive + EDGE_TOLERANCE) return null
        return value.coerceIn(range)
    }

    private companion object {
        // Halber feinster Reglerschritt (Deadzone 0,1 % = 0.001), wie OdinValues.MATCH_TOLERANCE
        const val EDGE_TOLERANCE = 0.0005f
    }
}
