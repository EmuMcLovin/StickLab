package com.sticklab.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.edit

// Was der Bildschirm mit den Kurven tun kann (Reiter Sticks, Teil "Curve")
interface CurveActions {
    fun onCurveSideChosen(side: OdinMapping.Side)
    fun onCurveChosen(curve: Curve)
    fun onOuterStepChanged(step: Int)
    fun onAntiStepChanged(step: Int)
    fun onApplyCurves()
    fun onCurveEditorOpen()
    fun onCurveEditorClose()
    fun onCurvePointSelected(index: Int)
    fun onCurvePointMoved(index: Int, x: Float, y: Float)
    fun onCurvePointAdd()
    fun onCurvePointRemove()
    fun onCurveTriggersShown()
    fun onTriggerFullOutputStepChanged(side: OdinMapping.Side, step: Int)
    fun onCurveModeToggled()
    fun onCurveModeIntroConfirmed()
}

// Abläufe des Teils "Curve" im Reiter Sticks (Plan Phase 6d). Eigene Klasse, damit MainActivity nicht noch länger wird.
// Schieben und Antippen ändern nur die Wahl, gespeichert wird erst mit "Apply" – wie bei Deadzone, Sensitivity und Trigger.
// Gespeichert wird nur in die eigenen Dateien (CurveStore). Die Firmware-Werte am Odin ändert das nie: Wirken können Kurven nur
// im virtuellen Controller, den der Schalter "Curve mode" ein- und ausschaltet (Phase 6c, K6-1).
// onStored läuft nach dem Speichern, damit die Marke "changed" des aktiven Profils stimmt.
// askNotifications fragt nach der Erlaubnis für Benachrichtigungen (Stopp-Knopf). Ergebnis true = Android fragt gerade, dann ruft
// MainActivity danach startMode auf; false = nichts zu fragen
class CurveController(
    private val context: Context,
    private val ui: MainUiState,
    private val mapping: OdinMapping,
    private val onStored: () -> Unit,
    private val askNotifications: () -> Boolean,
) : CurveActions {

    private val store = CurveStore(context)
    private val appPrefs = AppStorage.open(context, AppStorage.AppFile.APP)
    private val curve get() = ui.curve
    private val handler = Handler(Looper.getMainLooper())

    // Schalter hat nach dieser Zeit nichts bewirkt: Das Programm braucht bis zu 11,5 s bis zum virtuellen Gerät (MirrorService)
    private val modeTimeout = Runnable {
        curve.modeBusy = false
        refreshMode()
        ui.statusText = R.string.status_curve_mode_failed
    }

    // Stand des Curve mode neu lesen (beim Zurückkehren in die App und wenn ein Eingabegerät kommt oder geht)
    fun refreshMode() {
        val running = CurveModeEngine.isRunning()
        val changed = running != curve.modeRunning
        curve.modeRunning = running
        ui.profiles.curveModeRunning = running
        if (changed && curve.modeBusy) {
            curve.modeBusy = false
            handler.removeCallbacks(modeTimeout)
            ui.statusText = if (running) R.string.status_curve_mode_on else R.string.status_curve_mode_off
        }
    }

    // Schalter "Curve mode". Einmal gedrückt, wartet der Schalter auf das Ergebnis, damit ein zweiter Tipp nichts durcheinanderbringt.
    // Beim allerersten Einschalten erst der Hinweis, was der Modus tut (Phase 6e)
    override fun onCurveModeToggled() {
        if (curve.modeBusy || !curve.modeAvailable) return
        when {
            curve.modeRunning -> {
                CurveModeEngine.stop(context)
                waitForResult()
            }
            !appPrefs.getBoolean(AppStorage.AppKeys.CURVE_MODE_ACCEPTED, false) -> ui.dialog = MainDialog.CURVE_MODE_INTRO
            else -> startMode()
        }
    }

    // OK im Hinweis: merken, einmal nach der Benachrichtigung fragen, dann starten
    override fun onCurveModeIntroConfirmed() {
        ui.dialog = MainDialog.NONE
        appPrefs.edit { putBoolean(AppStorage.AppKeys.CURVE_MODE_ACCEPTED, true) }
        if (!askNotifications()) startMode()
    }

    // Vor dem Start: Tabelle auf den gespeicherten Stand bringen und den virtuellen Controller auf die Schwarze Liste setzen,
    // sonst übernimmt ihn der AYN-Dienst. Klappt eins nicht, wird nicht gestartet
    fun startMode() {
        if (!store.writeTable()) {
            ui.statusText = R.string.status_curves_failed
            return
        }
        if (!mapping.allowVirtualPad()) {
            ui.statusText = R.string.status_curve_mode_failed
            return
        }
        CurveModeEngine.start(context)
        waitForResult()
    }

    private fun waitForResult() {
        curve.modeBusy = true
        handler.postDelayed(modeTimeout, MODE_TIMEOUT_MS)
        ui.statusText = R.string.status_curve_mode_working
    }

    // Gespeicherte Kurven laden und die Wahl darauf stellen (beim Öffnen, nach "Use" und Kachel: Profil gilt jetzt)
    fun load() {
        curve.applied = store.load()
        curve.chosen = curve.applied
        onCurveEditorClose()
    }

    override fun onCurveSideChosen(side: OdinMapping.Side) {
        curve.side = side
        curve.showTriggers = false
        if (curve.editorOpen) makeChosenCustom()
    }

    // Karte zeigt die Trigger statt eines Sticks (Z11)
    override fun onCurveTriggersShown() {
        curve.showTriggers = true
    }

    // Trigger voll ab: gilt nur für diese Seite (links = L2, rechts = R2), unabhängig vom gewählten Stick
    override fun onTriggerFullOutputStepChanged(side: OdinMapping.Side, step: Int) {
        val value = SettingLimits.limit(SettingSteps.TRIGGER_FULL_OUTPUT.toValue(step), SettingLimits.TRIGGER_FULL_OUTPUT) ?: return
        changeChosen(side) { it.copy(triggerFullOutput = value) }
    }

    // Editor öffnen. Eine fertige Kurve wird zum Startpunkt der eigenen (drei Punkte auf ihr). Steht schon "Custom", bleiben die Punkte
    override fun onCurveEditorOpen() {
        makeChosenCustom()
        curve.selectedPoint = 0
        curve.editorOpen = true
    }

    // Editor schließen. Die Änderungen bleiben als Wahl stehen, gespeichert wird mit "Apply"
    override fun onCurveEditorClose() {
        if (!curve.editorOpen) return
        curve.editorOpen = false
    }

    override fun onCurvePointSelected(index: Int) {
        curve.selectedPoint = index
    }

    override fun onCurvePointMoved(index: Int, x: Float, y: Float) {
        changeChosen { it.copy(custom = it.custom.withPointMoved(index, x, y)) }
    }

    override fun onCurvePointAdd() {
        changeChosen { it.copy(custom = it.custom.withPointAdded()) }
    }

    override fun onCurvePointRemove() {
        changeChosen { it.copy(custom = it.custom.withPointRemoved(curve.selectedPoint)) }
        curve.selectedPoint = curve.selectedPoint.coerceAtMost(curve.chosen.of(curve.side).custom.points.lastIndex)
    }

    private fun makeChosenCustom() {
        if (curve.chosen.of(curve.side).curve == Curve.CUSTOM) return
        changeChosen { it.copy(curve = Curve.CUSTOM, custom = CustomCurve.sampledFrom(it.curve)) }
    }

    override fun onCurveChosen(curve: Curve) {
        changeChosen { it.copy(curve = curve) }
    }

    override fun onOuterStepChanged(step: Int) {
        val value = SettingLimits.limit(SettingSteps.OUTER_DEADZONE.toValue(step), SettingLimits.OUTER_DEADZONE) ?: return
        changeChosen { it.copy(outerDeadzone = value) }
    }

    override fun onAntiStepChanged(step: Int) {
        val value = SettingLimits.limit(SettingSteps.ANTI_DEADZONE.toValue(step), SettingLimits.ANTI_DEADZONE) ?: return
        changeChosen { it.copy(antiDeadzone = value) }
    }

    // "Apply": die Wahl speichern. Danach gilt, was wirklich in der Datei steht
    override fun onApplyCurves() {
        val saved = store.save(curve.chosen)
        if (saved) {
            load()
            curve.lastApplySuccessAt = SystemClock.uptimeMillis()
        }
        onStored()
        ui.statusText = if (saved) R.string.status_curves_saved else R.string.status_curves_failed
    }

    // Ändert die Wahl einer Seite, ohne Angabe die des gerade gewählten Sticks. Die andere Seite bleibt, wie sie ist
    private fun changeChosen(side: OdinMapping.Side = curve.side, change: (CurveSettings) -> CurveSettings) {
        curve.chosen = curve.chosen.with(side, change(curve.chosen.of(side)))
        if (curve.isPending) {
            ui.statusText = R.string.status_not_applied
        } else if (ui.statusText == R.string.status_not_applied) {
            // Wahl ist wieder der gespeicherte Stand: Es gibt nichts mehr zu übernehmen
            ui.statusText = R.string.status_ready
        }
    }
}

// Etwas mehr als die 15 s, die MirrorService selbst auf das virtuelle Gerät wartet
private const val MODE_TIMEOUT_MS = 16_000L

// Wert einer Seite (links/rechts) ersetzen
fun <T> LeftRight<T>.with(side: OdinMapping.Side, value: T): LeftRight<T> =
    if (side == OdinMapping.Side.LEFT) copy(left = value) else copy(right = value)
