package com.sticklab.app

import android.content.Context
import androidx.core.content.edit

// Ablauf einer Messung mit vorübergehender Deadzone (Phase 3b Loslass-Messung, seit 3c-1 das Auto-Set):
// Deadzone nur live umstellen – auf 0 für die Messung, seit 3c-2 auf den Vorschlag für die Kontrollmessung –,
// danach zurück.
// Der Rückweg steht an erster Stelle (Plan Regel 7):
// - Vor dem Umschalten wird die wirksame Deadzone gelesen (Aufruf 13) und als Merker in die Datei "app"
//   geschrieben – sofort und fest (commit), damit sie auch einen Absturz direkt danach übersteht
// - finish() stellt live zurück und löscht den Merker nur, wenn beide Seiten nachgelesen stimmen
// - restorePending() holt ein Zurückstellen nach, das wegen Absturz/hartem Beenden ausgefallen ist
// Die Systemeinstellung wird nie geschrieben: Ein Neustart bringt immer den gespeicherten Wert zurück
class DriftMeasurement(context: Context, private val mapping: OdinMapping, private val deadzone: DeadzoneControl) {

    private val prefs = AppStorage.open(context, AppStorage.AppFile.APP)

    // Deadzone vor der Messung (Bruchteile), null = keine Messung läuft
    var savedDeadzone: LeftRight<Float>? = null
        private set

    val isRunning: Boolean get() = savedDeadzone != null

    // Startet die Messung und setzt liveDeadzone (Bruchteile) nur live. Eine Seite mit null bleibt, wie sie ist
    // (Kontrollmessung ohne Vorschlag für diesen Stick). false = nichts verändert bzw. sofort zurückgestellt
    // (Statuszeile meldet es)
    fun start(liveDeadzone: LeftRight<Float?>): Boolean {
        if (isRunning || hasPendingRestore()) return false
        val before = readEffectiveDeadzone() ?: return false
        writeMarker(before)
        savedDeadzone = before
        val leftSet = setLiveIfGiven(OdinMapping.Side.LEFT, liveDeadzone.left)
        val rightSet = setLiveIfGiven(OdinMapping.Side.RIGHT, liveDeadzone.right)
        if (leftSet && rightSet) return true
        // Eine Seite hat nicht umgeschaltet: lieber gar nicht messen und gleich zurück
        finish()
        return false
    }

    // null = diese Seite nicht anfassen (zählt als gelungen)
    private fun setLiveIfGiven(side: OdinMapping.Side, value: Float?): Boolean =
        value == null || deadzone.setLiveOnly(side, value)

    // Beendet die Messung und stellt die alte Deadzone live zurück. true = beide Seiten wirken wieder.
    // false = Merker bleibt stehen, damit beim nächsten Öffnen noch einmal zurückgestellt wird
    fun finish(): Boolean {
        val before = savedDeadzone ?: return true
        savedDeadzone = null
        return restore(before)
    }

    // Beim Öffnen der App (sobald der Live-Weg steht): Merker einer abgebrochenen Messung zurückstellen.
    // null = es gab nichts zurückzustellen, true = zurückgestellt, false = hat nicht geklappt
    fun restorePending(): Boolean? {
        if (isRunning) return null
        val pending = readMarker() ?: return null
        return restore(pending)
    }

    // Stellt beide Seiten live zurück (je ein zweiter Versuch, Risiko R74). Merker weg nur bei Erfolg
    private fun restore(values: LeftRight<Float>): Boolean {
        val left = setWithRetry(OdinMapping.Side.LEFT, values.left)
        val right = setWithRetry(OdinMapping.Side.RIGHT, values.right)
        if (left && right) clearMarker()
        return left && right
    }

    private fun setWithRetry(side: OdinMapping.Side, value: Float): Boolean =
        deadzone.setLiveOnly(side, value) || deadzone.setLiveOnly(side, value)

    // Wirksame Deadzone beider Sticks (Aufruf 13). null, wenn eine Seite nicht lesbar ist oder außerhalb
    // 0–20 % liegt (dann könnte setLiveOnly sie nicht genau zurückstellen → lieber nicht messen)
    private fun readEffectiveDeadzone(): LeftRight<Float>? {
        val left = mapping.getDeadzone(OdinMapping.Side.LEFT) ?: return null
        val right = mapping.getDeadzone(OdinMapping.Side.RIGHT) ?: return null
        if (left !in SettingLimits.DEADZONE || right !in SettingLimits.DEADZONE) return null
        return LeftRight(left, right)
    }

    // ---------- Merker in der Datei "app" ----------

    private fun hasPendingRestore(): Boolean = readMarker() != null

    private fun readMarker(): LeftRight<Float>? {
        if (!prefs.contains(AppStorage.AppKeys.DRIFT_RESTORE_LEFT)) return null
        if (!prefs.contains(AppStorage.AppKeys.DRIFT_RESTORE_RIGHT)) return null
        return LeftRight(
            prefs.getFloat(AppStorage.AppKeys.DRIFT_RESTORE_LEFT, 0f),
            prefs.getFloat(AppStorage.AppKeys.DRIFT_RESTORE_RIGHT, 0f),
        )
    }

    // commit = true: sofort auf den Speicher schreiben, nicht erst irgendwann im Hintergrund
    private fun writeMarker(values: LeftRight<Float>) {
        prefs.edit(commit = true) {
            putFloat(AppStorage.AppKeys.DRIFT_RESTORE_LEFT, values.left)
            putFloat(AppStorage.AppKeys.DRIFT_RESTORE_RIGHT, values.right)
        }
    }

    private fun clearMarker() {
        prefs.edit(commit = true) {
            remove(AppStorage.AppKeys.DRIFT_RESTORE_LEFT)
            remove(AppStorage.AppKeys.DRIFT_RESTORE_RIGHT)
        }
    }

    companion object {
        // Während der Messung: keine Deadzone, damit der Rest nach dem Loslassen sichtbar wird (Plan 3b)
        const val MEASURE_DEADZONE = 0f

        // Höchstdauer des Auto-Sets (Entscheidung D, 17.10.2026: 25 s, endet früher, wenn alle 16 Felder
        // erledigt sind). Vorher 15 s für die Loslass-Messung (M2)
        const val MEASURE_MS = 25_000L
    }
}
