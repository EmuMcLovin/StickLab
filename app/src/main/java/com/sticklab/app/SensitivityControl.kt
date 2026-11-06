package com.sticklab.app

import kotlin.math.abs

// Setzt die Stick-Sensitivity. Die gibt es nur bei com.odin.mapping (Aufruf 9 setzen, 10 lesen), nicht als
// Systemeinstellung (Test C1). Der Wert bleibt nach einem Neustart erhalten (gesichert 17. Sitzung) –
// Rückweg ist darum nur "Restore original" in StickLab oder das Odin-Menü ("Gamepad test").
// Erlaubt ist nur der Bereich des Odin-Menüs, 0,50–2,00 (SettingLimits.SENSITIVITY)
class SensitivityControl(private val mapping: OdinMapping) {

    // Setzt die Sensitivity eines Sticks (größer = Stick erreicht den Rand früher, gemessen 15.10.2026).
    // Ergebnis wie bei der Deadzone, aber ohne "nach Neustart": klappt der Live-Weg nicht, ist nichts gesetzt
    fun apply(side: OdinMapping.Side, value: Float): DeadzoneControl.Result {
        val safe = SettingLimits.limit(value, SettingLimits.SENSITIVITY) ?: return DeadzoneControl.Result.FAILED
        if (!mapping.setSensitivity(side, safe)) return DeadzoneControl.Result.FAILED
        val effective = mapping.getSensitivity(side) ?: return DeadzoneControl.Result.FAILED
        return if (abs(effective - safe) <= READBACK_TOLERANCE) DeadzoneControl.Result.ACTIVE_NOW
        else DeadzoneControl.Result.FAILED
    }

    private companion object {
        // Nachgelesener Wert darf so weit abweichen: die Hälfte eines Reglerschritts (0,05, SettingSteps.SENSITIVITY)
        const val READBACK_TOLERANCE = 0.025f
    }
}
