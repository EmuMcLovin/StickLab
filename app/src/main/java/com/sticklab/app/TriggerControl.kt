package com.sticklab.app

import kotlin.math.abs

// Setzt die Trigger-Deadzone L2/R2 so wie die Stick-Deadzone (DeadzoneControl, Entscheidung E7):
// 1. live über com.odin.mapping (Aufruf 14) → wirkt sofort, danach nachlesen (Aufruf 15)
// 2. als Systemeinstellung l2/r2_dead_zone_radius über PServerBinder → gilt auch nach dem Neustart
//    (beim Hochfahren schickt com.odin.settings den Systemwert, gesichert 17. Sitzung auch für den Trigger)
// Erlaubt ist nur der Bereich des Odin-Menüs, 5–50 % (SettingLimits.TRIGGER_DEADZONE)
class TriggerControl(private val mapping: OdinMapping) {

    // Setzt die Trigger-Deadzone einer Seite (Bruchteil, 10 % = 0.10). Ergebnis wie bei der Deadzone
    fun apply(side: OdinMapping.Side, value: Float): DeadzoneControl.Result {
        val safe = SettingLimits.limit(value, SettingLimits.TRIGGER_DEADZONE) ?: return DeadzoneControl.Result.FAILED
        val activeNow = setLive(side, safe)
        val saved = OdinService.putSystemFloat(systemSettingOf(side), safe)
        return when {
            !saved -> DeadzoneControl.Result.FAILED
            activeNow -> DeadzoneControl.Result.ACTIVE_NOW
            else -> DeadzoneControl.Result.AFTER_RESTART
        }
    }

    // Live setzen und prüfen, ob der Controller den Wert wirklich übernommen hat
    private fun setLive(side: OdinMapping.Side, value: Float): Boolean {
        if (!mapping.setTriggerDeadzone(side, value)) return false
        val effective = mapping.getTriggerDeadzone(side) ?: return false
        return abs(effective - value) <= READBACK_TOLERANCE
    }

    companion object {
        // Nachgelesener Wert darf so weit abweichen (Rundung von Kommazahlen): die Hälfte eines Reglerschritts
        // (1 %, SettingSteps.TRIGGER) – ein um einen Schritt anderer Wert fällt also auf
        private const val READBACK_TOLERANCE = 0.005f

        // Systemeinstellung, die zu einem Trigger gehört (Seite LEFT = L2, RIGHT = R2, Setz-Tests 11.10.2026)
        fun systemSettingOf(side: OdinMapping.Side): OdinService.FloatSetting = when (side) {
            OdinMapping.Side.LEFT -> OdinService.FloatSetting.L2_DEADZONE
            OdinMapping.Side.RIGHT -> OdinService.FloatSetting.R2_DEADZONE
        }
    }
}
