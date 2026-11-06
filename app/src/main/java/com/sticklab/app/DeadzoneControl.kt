package com.sticklab.app

import kotlin.math.abs

// Setzt die Stick-Deadzone so wie das Odin-Menü (Entscheidung E7):
// 1. live über com.odin.mapping (Aufruf 12) → wirkt sofort
// 2. als Systemeinstellung über PServerBinder → gilt auch nach dem Neustart
//    (beim Hochfahren schickt com.odin.settings den Systemwert, PROJEKT_DOKU "Was beim Hochfahren passiert")
// Nach dem Live-Setzen wird der wirksame Wert nachgelesen (Aufruf 13). Die App meldet
// "wirkt sofort" nur, wenn er stimmt, sonst "wirkt nach Neustart" (Entscheidung E8)
class DeadzoneControl(private val mapping: OdinMapping) {

    // Ergebnis für die Statuszeile. Reihenfolge vom besten zum schlechtesten (MainActivity zeigt das schlechteste)
    enum class Result { ACTIVE_NOW, AFTER_RESTART, FAILED }

    // Setzt die Deadzone eines Sticks (Bruchteil, 2,5 % = 0.025), vorher auf 0–20 % begrenzt
    fun apply(side: OdinMapping.Side, value: Float): Result {
        val safe = SettingLimits.limit(value, SettingLimits.DEADZONE) ?: return Result.FAILED
        val activeNow = setLive(side, safe)
        val saved = OdinService.putSystemFloat(systemSettingOf(side), safe)
        return when {
            !saved -> Result.FAILED
            activeNow -> Result.ACTIVE_NOW
            else -> Result.AFTER_RESTART
        }
    }

    // Setzt die Deadzone nur live (Aufruf 12 + Nachlesen 13), die Systemeinstellung bleibt unberührt.
    // Für die Loslass-Messung (Phase 3b): Nach einem Neustart gilt wieder der gespeicherte Wert
    // (gesichert 17. Sitzung). true = der Controller benutzt den Wert jetzt wirklich
    fun setLiveOnly(side: OdinMapping.Side, value: Float): Boolean {
        val safe = SettingLimits.limit(value, SettingLimits.DEADZONE) ?: return false
        return setLive(side, safe)
    }

    // Live setzen und prüfen, ob der Controller den Wert wirklich übernommen hat
    private fun setLive(side: OdinMapping.Side, value: Float): Boolean {
        if (!mapping.setDeadzone(side, value)) return false
        val effective = mapping.getDeadzone(side) ?: return false
        return abs(effective - value) <= READBACK_TOLERANCE
    }

    companion object {
        // Nachgelesener Wert darf so weit abweichen (Rundung von Kommazahlen).
        // 0,05 % ist die Hälfte eines Reglerschritts (0,1 %, DeadzoneSteps): ein um einen Schritt
        // anderer Wert fällt also noch auf (Risiko R104: Firmware rundet doch gröber)
        private const val READBACK_TOLERANCE = 0.0005f

        // Systemeinstellung, die zu einem Stick gehört
        fun systemSettingOf(side: OdinMapping.Side): OdinService.FloatSetting = when (side) {
            OdinMapping.Side.LEFT -> OdinService.FloatSetting.LEFT_DEADZONE
            OdinMapping.Side.RIGHT -> OdinService.FloatSetting.RIGHT_DEADZONE
        }
    }
}
