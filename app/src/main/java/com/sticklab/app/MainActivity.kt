package com.sticklab.app

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.hardware.input.InputManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

// Der Bildschirm von StickLab. Das Aussehen steht in MainScreen (Compose), hier liegt nur,
// was mit dem Odin und Android spricht: Deadzone setzen, Original merken, Ambilight, Licht-Werte, Profile (über
// ProfilesController).
// Bleibt eine AppCompatActivity, damit die App wie bisher immer dunkel ist (auch die Debug-Bildschirme)
class MainActivity : AppCompatActivity(), MainActions {

    // Live-Weg zu com.odin.mapping und die Deadzone-Steuerung, die ihn benutzt
    private lateinit var mapping: OdinMapping
    private lateinit var deadzoneControl: DeadzoneControl

    // Die Werte von vor StickLab 2 ("Original") für den Knopf "Restore original"
    private lateinit var originalValues: OriginalValues

    // Sensitivity und Trigger (Reiter Sticks, Schritt S): Wege zum Odin (auch für Profile, 5b-1) und je Teil
    // Anzeige, Reglerschritte, lesen, setzen, Original
    private lateinit var triggerControl: TriggerControl
    private lateinit var sensitivityControl: SensitivityControl
    private lateinit var pairs: Map<StickSetting, PairSetting>

    // Belegung von M1/M2 und Layout (Reiter Buttons, Phase 4). Gedrückte Tasten getrennt nach Herkunft:
    // Tasten kommen als KeyEvent, das Steuerkreuz als Achse (HAT) – zusammen ergeben sie ui.pressedParts
    private lateinit var buttonMapping: ButtonMappingControl
    private var pressedKeyParts = emptySet<OdinPart>()
    private var pressedDpadParts = emptySet<OdinPart>()

    // Reiter Profiles (Phase 5a): Abläufe in eigener Klasse, hier nur weitergereicht.
    // seenTileSwitchAt: letzter Wechsel über die Kachel, den der Bildschirm schon zeigt (5d, R133)
    private lateinit var profiles: ProfilesController
    private lateinit var curves: CurveController
    override val curveActions: CurveActions get() = curves

    // Curve mode: Das virtuelle Gerät kommt oder geht, dann den Schalter nachziehen (auch nach Stopp aus der Benachrichtigung)
    private val curveModeWatcher = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = curves.refreshMode()
        override fun onInputDeviceRemoved(deviceId: Int) = curves.refreshMode()
        override fun onInputDeviceChanged(deviceId: Int) = Unit
    }
    private var seenTileSwitchAt = ProfileTileService.lastSwitchAt

    // Auto-Set (Phase 3c-1): Ablauf mit Deadzone 0 und Rückweg (aus 3b), die 8 Felder je Stick,
    // Schutzregel fürs Verschlucken und ein Takt, der regelmäßig nachsieht (siehe autoSetTick)
    private lateinit var driftMeasurement: DriftMeasurement
    private var reachSession: ReachSession? = null
    private val swallowGate = InputSwallowGate()
    private val handler = Handler(Looper.getMainLooper())
    private val autoSetTick = Runnable { onAutoSetTick() }

    // Alles, was der Bildschirm anzeigt (siehe MainUiState)
    private val ui = MainUiState()

    // Wird aufgerufen, nachdem das Mitlesen des Bildschirms erlaubt oder abgelehnt wurde
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            startAmbilight(result.resultCode, data)
        } else {
            ui.statusText = R.string.status_denied
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Die App ist immer dunkel
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)

        // Wurde die App mitten in einem Lichtblitz beendet, die LED-Farbe von vorher zurückstellen (5c, L4, R112)
        LightFlash.restoreInterrupted(this)

        mapping = OdinMapping(this)
        deadzoneControl = DeadzoneControl(mapping)
        originalValues = OriginalValues(this, mapping)
        driftMeasurement = DriftMeasurement(this, mapping, deadzoneControl)
        buttonMapping = ButtonMappingControl(contentResolver)
        triggerControl = TriggerControl(mapping)
        sensitivityControl = SensitivityControl(mapping)
        pairs = createPairSettings()
        profiles = createProfilesController()
        profiles.load()
        curves = CurveController(
            this,
            ui,
            mapping,
            onStored = profiles::refreshOdinValues,
            askNotifications = ::askNotificationPermission,
        )
        curves.load()

        // Regler auf die gespeicherten Systemwerte stellen
        loadDeadzoneSliders()

        // Original merken, bevor man irgendetwas setzen kann (Knöpfe wirken erst nach onCreate).
        // Die Sensitivity braucht den Live-Weg und wird darum nachgeholt, sobald er steht
        captureOriginals()

        // Verbindung zum Live-Weg aufbauen (steht nach wenigen Millisekunden), dann Anzeige füllen.
        // Zuerst eine abgebrochene Messung zurückstellen (Rückweg nach Absturz, R70)
        mapping.onConnectionChanged = { connected ->
            if (connected) {
                restoreInterruptedDrift()
                captureOriginals()
                // Erstes Öffnen ohne Profil: "Standard" aus den Werten am Odin (Entscheidung D, braucht den Live-Weg)
                profiles.createStandardIfNeeded()
            }
            showDeadzoneNow()
            showAllPairsNow()
            profiles.refreshOdinValues()
        }
        showDeadzoneNow()
        showAllPairsNow()
        mapping.connect()

        ui.ambilightRunning = AmbilightService.running
        loadLightSettings()
        ui.dialog = startDialog()

        // Vor dem ersten Bild, sonst blitzte beim Öffnen kurz der Standard-Hintergrund auf
        ui.background = AppBackground.fromId(appPrefs().getString(AppStorage.AppKeys.BACKGROUND, null))
        setContent {
            StickLabTheme {
                MainScreen(ui, this)
            }
        }
    }
    // Curve mode: einmal nach Benachrichtigungen fragen (Stopp-Knopf). true = Android fragt, Start folgt im Ergebnis
    private fun askNotificationPermission(): Boolean {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return false
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
        return true
    }

    // Auch ohne Erlaubnis starten: Stopp geht dann über den Schalter
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_REQUEST) curves.startMode()
    }


    // Beim Zurückkehren in die App neu nachlesen (der Wert kann z. B. im Odin-Menü geändert worden sein),
    // auch M1/M2 und das Layout. Beim ersten Öffnen läuft onResume ebenfalls, darum steht es nur hier.
    // Den Ambilight-Knopf hier bewusst nicht neu setzen: direkt nach der Erlaubnis zur Bildschirmaufnahme
    // kommt onResume, bevor der Dienst läuft – der Knopf würde dann falsch "einschalten" zeigen
    override fun onResume() {
        super.onResume()
        showDeadzoneNow()
        showAllPairsNow()
        loadButtonMapping()
        profiles.refreshOdinValues()
        showTileSwitchIfAny()
        loadTileAdded()
        getSystemService(InputManager::class.java).registerInputDeviceListener(curveModeWatcher, handler)
        curves.refreshMode()
    }

    // Beim Verlassen der App die Live-Anzeige leeren: Was der Stick im Hintergrund tut, sieht die App nicht,
    // ein alter Punkt würde sonst nach dem Zurückkehren eine falsche Stellung zeigen
    // Ein laufendes Auto-Set endet hier und stellt die Deadzone zurück (Rückweg b, Plan 3b/3c)
    // Ebenso die Gedrückt-Anzeige: Das Loslassen einer Taste kommt im Hintergrund nicht mehr an
    override fun onPause() {
        super.onPause()
        getSystemService(InputManager::class.java).unregisterInputDeviceListener(curveModeWatcher)
        finishAutoSet()
        ui.liveInput = null
        pressedKeyParts = emptySet()
        pressedDpadParts = emptySet()
        showPressedParts()
    }

    // Stick-Bewegungen für die Karte "Live" mitlesen (Phase 3a) und normal weitergeben (F1, R68),
    // damit Steuerkreuz und linker Stick die Oberfläche bedienen.
    // Ausnahme: Solange die Sticks nur zum Anschauen da sind, werden sie verschluckt (sticksOnlyShown), aber erst,
    // wenn die Schutzregel es erlaubt (InputSwallowGate, R76)
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val input = StickInput.read(event) ?: return super.dispatchGenericMotionEvent(event)
        ui.liveInput = input
        showPressedDpad(event)
        // Messung: auch die gebündelten Zwischenwerte, sonst kann ein kurzer Spitzenwert fehlen (Lauf 2)
        reachSession?.let { session ->
            StickInput.readAll(event).forEach { session.add(it.timeMs, it.input) }
            showAutoSetProgress(session.snapshot())
        }
        swallowGate.update(wanted = sticksOnlyShown())
        // Das Steuerkreuz bedient den Reiter Sticks weiter, nur während der Messung nicht (Entscheidung M3 aus 3b)
        val dpadPasses = reachSession == null && StickInput.isDpadPressed(event)
        if (swallowGate.shouldSwallow() && !dpadPasses) return true
        val handled = super.dispatchGenericMotionEvent(event)
        swallowGate.passed(neutral = StickInput.isNavigationNeutral(event))
        return handled
    }

    // Im Reiter Sticks bewegt man die Sticks, um sie anzuschauen (Live, Drift, Kurven-Editor): Sonst würde der linke Stick dabei durch die
    // Knöpfe wandern und Regler verschieben (Wunsch Besitzer 06.11.2026). Auch während einer Messung, falls man den Reiter verlässt
    private fun sticksOnlyShown(): Boolean = ui.selectedTab == MainTab.STICKS || reachSession != null

    // Tasten des Controllers für die Gedrückt-Anzeige im Reiter Buttons mitlesen. M1/M2 kommen hier als die
    // Taste an, die sie nachahmen. Danach normal weitergeben – außer im Reiter Buttons (dort nur Touch)
    // und Android-"B" (schließt die App nie)
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        showPressedKey(event)
        if (swallowsControllerKey(event) || swallowsButtonB(event)) return true
        return super.dispatchKeyEvent(event)
    }

    // Gedrückt-Anzeige: Teil im Bild beim Drücken an, beim Loslassen aus
    private fun showPressedKey(event: KeyEvent) {
        val generatedByAndroid = event.flags and KeyEvent.FLAG_FALLBACK != 0
        val part = ButtonLayoutMap.partOfPressedKey(event.keyCode, ui.controllerLayout, generatedByAndroid) ?: return
        when (event.action) {
            KeyEvent.ACTION_DOWN -> pressedKeyParts = pressedKeyParts + part
            KeyEvent.ACTION_UP -> pressedKeyParts = pressedKeyParts - part
        }
        showPressedParts()
    }

    // Reiter Buttons nur per Touch (Entscheidung Besitzer 26.10.2026): Dort bedienen die Controller-Tasten nichts,
    // sie leuchten nur im Bild. Sonst würde z. B. M1 auf RIGHT beim Ausprobieren die Auswahl weiterblättern oder
    // M1 auf BACK den Reiter verlassen. Gilt auch für die Richtungen, die Android aus dem linken Stick macht
    // (gleiches Gerät). Lautstärke bleibt frei; Home und Recent apps erreichen eine App gar nicht
    private fun swallowsControllerKey(event: KeyEvent): Boolean =
        ui.selectedTab == MainTab.BUTTONS && isFromController(event) && event.keyCode !in KEYS_ALWAYS_PASSED

    // Kommt die Taste vom Controller (eingebauter Controller = Gamepad mit Steuerkreuz und Sticks)?
    private fun isFromController(event: KeyEvent): Boolean =
        event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_DPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            event.device?.supportsSource(InputDevice.SOURCE_GAMEPAD) == true

    // Android macht aus einem nicht behandelten "B" (im Xbox-Layout die Taste mit Aufdruck A) ein "Zurück" –
    // auf dem Hauptbildschirm schließt das die App (R93, gesehen 26.10.2026). Entscheidung Besitzer: das nie.
    // Darum hier abfangen (Drücken und Loslassen), außer im Auto-Set: dort beendet "Zurück" die Runde bzw.
    // die Ansicht (3c-3). Nachfragen haben ein eigenes Fenster und bekommen "B" vermutlich weiter als "Zurück"
    // (im Test prüfen).
    // Die echte Zurück-Taste und die Wisch-Geste sind nicht betroffen
    private fun swallowsButtonB(event: KeyEvent): Boolean =
        event.keyCode == KeyEvent.KEYCODE_BUTTON_B && ui.autoSet == AutoSetUi.Idle

    // Verbindung zum Live-Weg wieder lösen, wenn der Bildschirm endet (vorher Messung beenden)
    override fun onDestroy() {
        finishAutoSet()
        mapping.disconnect()
        super.onDestroy()
    }

    // ---------- Auto-Set (Reiter Sticks, große Ansicht, Phase 3c-1 + Kontrollmessung 3c-2) ----------

    // Knopf "Auto-Set" / "Again": Deadzone live auf 0, höchstens 25 s die 8 Felder je Stick sammeln,
    // dann von selbst zurück
    override fun onAutoSetStart() {
        // Im Curve mode käme schon die umgerechnete Stellung an, die Messung wäre falsch
        if (ui.curve.modeRunning) {
            ui.statusText = R.string.status_autoset_curve_mode
            return
        }
        startAutoSetRun(checking = null)
    }

    // Knopf "Check": dieselbe Messung, aber mit den Vorschlägen nur live statt 0 (Kontrollmessung, K2a)
    override fun onAutoSetCheck() {
        val result = (ui.autoSet as? AutoSetUi.Finished)?.result ?: return
        if (result.anyNeedsCheck) startAutoSetRun(checking = result)
    }

    // Startet eine Runde: checking = null → Messung mit Deadzone 0, sonst Kontrollmessung dieses Ergebnisses
    private fun startAutoSetRun(checking: AutoSetResult?) {
        // Steht noch ein Merker von einer Messung, die nicht zurückgestellt werden konnte: erst das nachholen
        restoreInterruptedDrift()
        val liveDeadzone = checking?.checkDeadzone()
            ?: LeftRight<Float?>(DriftMeasurement.MEASURE_DEADZONE, DriftMeasurement.MEASURE_DEADZONE)
        if (!driftMeasurement.start(liveDeadzone)) {
            ui.statusText = R.string.status_drift_failed
            showDeadzoneNow()
            return
        }
        val session = ReachSession()
        reachSession = session
        val endsAt = SystemClock.uptimeMillis() + DriftMeasurement.MEASURE_MS
        ui.autoSet = AutoSetUi.Running(endsAt, session.snapshot(), checking)
        ui.statusText = if (checking == null) R.string.status_drift_running else R.string.status_check_running
        handler.postDelayed(autoSetTick, AUTO_SET_TICK_MS)
        showDeadzoneNow()
    }

    // Takt während des Auto-Sets. In Ruhe schickt der Controller nichts (Test A) – ohne diesen Takt würde
    // ein Feld erst bei der nächsten Bewegung grün. Beendet das Auto-Set, wenn die Zeit um ist oder alle
    // 16 Felder erledigt sind (Entscheidung D)
    private fun onAutoSetTick() {
        val session = reachSession ?: return
        val running = ui.autoSet as? AutoSetUi.Running ?: return
        val now = SystemClock.uptimeMillis()
        session.settle(now)
        val reach = session.snapshot()
        showAutoSetProgress(reach)
        if (now >= running.endsAt || (reach.left.allDone && reach.right.allDone)) {
            finishAutoSet()
        } else {
            handler.postDelayed(autoSetTick, AUTO_SET_TICK_MS)
        }
    }

    // Neuen Stand der Felder anzeigen – nur, wenn sich etwas geändert hat (spart Neuzeichnen)
    private fun showAutoSetProgress(reach: LeftRight<StickReach>) {
        val running = ui.autoSet as? AutoSetUi.Running ?: return
        if (reach != running.reach) ui.autoSet = running.copy(reach = reach)
    }

    // Knopf "Stop" (oder Zeit um, alle Felder erledigt, App verlassen, Apply/Restore): Runde beenden,
    // Deadzone zurück, Ergebnis zeigen. Nach einer Kontrollmessung bekommt jeder Stick sein Urteil
    // (bestanden / 0,5 % höher / unvollständig, DeadzoneCheck). Ohne laufende Runde passiert nichts
    private fun finishAutoSet() {
        val before = driftMeasurement.savedDeadzone ?: return
        val checking = (ui.autoSet as? AutoSetUi.Running)?.checking
        handler.removeCallbacks(autoSetTick)
        val restored = driftMeasurement.finish()
        val reach = (reachSession ?: ReachSession()).result(SystemClock.uptimeMillis())
        reachSession = null
        val result = checking?.withCheck(reach) ?: AutoSetResult.ofMeasurement(before, reach)
        ui.autoSet = AutoSetUi.Finished(result)
        ui.statusText = when {
            !restored -> R.string.status_drift_restore_failed
            checking != null -> R.string.status_check_done
            else -> R.string.status_drift_done
        }
        showDeadzoneNow()
        forgetLiveInput()
        profiles.refreshOdinValues()
    }

    override fun onAutoSetStop() = finishAutoSet()

    // Knopf "Done": zurück zu den normalen Karten
    override fun onAutoSetDone() {
        ui.autoSet = AutoSetUi.Idle
    }

    // Knopf "Apply" im Auto-Set: Nachfrage mit alt → neu, nur für Seiten mit bestandener Kontrolle (E1, Ü3).
    // Gesetzt wird erst mit OK (onAutoSetApplyConfirmed)
    override fun onAutoSetApply() {
        val result = (ui.autoSet as? AutoSetUi.Finished)?.result ?: return
        if (!result.anyPassed) return
        ui.autoSetApplyMessage = autoSetApplyMessage(result.deadzoneBefore, result.applicableDeadzone())
        ui.dialog = MainDialog.AUTOSET_APPLY
    }

    // OK in der Nachfrage: bestandene Seiten wie "Apply" setzen (live + Systemeinstellung, gilt nach dem
    // Neustart), Regler darauf stellen, zurück zu den normalen Karten. Die andere Seite bleibt, wie sie ist
    override fun onAutoSetApplyConfirmed() {
        ui.dialog = MainDialog.NONE
        val result = (ui.autoSet as? AutoSetUi.Finished)?.result ?: return
        val values = result.applicableDeadzone()
        if (values.left == null && values.right == null) return
        val outcome = applyDeadzoneValues(values.left, values.right)
        values.left?.let { ui.leftStep = DeadzoneSteps.toStep(it) }
        values.right?.let { ui.rightStep = DeadzoneSteps.toStep(it) }
        if (outcome != DeadzoneControl.Result.FAILED) ui.lastApplySuccessAt = SystemClock.uptimeMillis()
        ui.autoSet = AutoSetUi.Idle
    }

    // Text der Nachfrage: je Seite "Left 1.5 % → 4.5 %" oder "Right stays 2.5 %", dazu der Hinweis, dass der
    // Wert gespeichert wird und wie man zum Original zurückkommt (Risiken R81/R87)
    private fun autoSetApplyMessage(before: LeftRight<Float>, values: LeftRight<Float?>): String {
        val lines = listOf(
            applyLine(R.string.live_left, before.left, values.left),
            applyLine(R.string.live_right, before.right, values.right),
        )
        var message = lines.joinToString("\n") + "\n\n" + getString(R.string.autoset_apply_saved)
        originalDeadzone()?.let { (left, right) ->
            message += " " + getString(R.string.autoset_apply_restore, percentText(left), percentText(right))
        }
        return message
    }

    // Eine Zeile der Nachfrage für eine Seite, value = null → bleibt
    private fun applyLine(@StringRes name: Int, before: Float, value: Float?): String =
        if (value == null) getString(R.string.autoset_apply_stays, getString(name), percentText(before))
        else getString(R.string.autoset_apply_change, getString(name), percentText(before), percentText(value))

    // Deadzone einer abgebrochenen Messung zurückstellen (Merker in der Datei "app").
    // Läuft beim Öffnen (sobald der Live-Weg steht) und vor jeder neuen Messung
    private fun restoreInterruptedDrift() {
        when (driftMeasurement.restorePending()) {
            true -> ui.statusText = R.string.status_drift_recovered
            false -> ui.statusText = R.string.status_drift_restore_failed
            null -> Unit
        }
    }

    // ---------- Deadzone (Reiter Sticks) ----------

    // Stellt beide Regler auf den gespeicherten Systemwert
    private fun loadDeadzoneSliders() {
        ui.leftStep = DeadzoneSteps.toStep(readSystemDeadzone(OdinMapping.Side.LEFT))
        ui.rightStep = DeadzoneSteps.toStep(readSystemDeadzone(OdinMapping.Side.RIGHT))
    }

    // Liest den gespeicherten Deadzone-Wert einer Seite.
    // Scheitert das: Ersatzwert und Hinweis in der Statuszeile (wie v1.0)
    private fun readSystemDeadzone(side: OdinMapping.Side): Float {
        val value = SystemSettings.readFloat(contentResolver, DeadzoneControl.systemSettingOf(side).key)
        if (value == null) ui.statusText = R.string.status_read_failed
        return value ?: DeadzoneSteps.FALLBACK_FRACTION
    }

    // Regler bewegt: nur merken und anzeigen, gesetzt wird erst mit "Apply" (Wunsch Besitzer 15.10.2026)
    override fun onDeadzoneStepChanged(side: OdinMapping.Side, step: Int) {
        when (side) {
            OdinMapping.Side.LEFT -> ui.leftStep = step
            OdinMapping.Side.RIGHT -> ui.rightStep = step
        }
        showPendingStatus()
    }

    // Knopf "Apply": setzt die beiden Regler-Werte. Hat es geklappt (sofort oder nach Neustart),
    // zeigt der Knopf kurz einen Haken
    override fun onApplyDeadzone() {
        // Eine laufende Messung zuerst beenden, sonst würde ihr Zurückstellen den neuen Wert überschreiben
        finishAutoSet()
        val result = applyDeadzoneValues(DeadzoneSteps.toFraction(ui.leftStep), DeadzoneSteps.toFraction(ui.rightStep))
        if (result != DeadzoneControl.Result.FAILED) ui.lastApplySuccessAt = SystemClock.uptimeMillis()
    }

    // Setzt die Deadzone der Sticks (live + System) und zeigt danach den wirksamen Wert. null = diese Seite
    // nicht anfassen (Übernehmen aus dem Auto-Set nur für bestandene Seiten, E1).
    // Gemeinsam benutzt von "Apply", "Restore original" und dem Auto-Set. Gibt das schlechtere Ergebnis zurück
    private fun applyDeadzoneValues(left: Float?, right: Float?): DeadzoneControl.Result {
        val results = listOfNotNull(
            left?.let { deadzoneControl.apply(OdinMapping.Side.LEFT, it) },
            right?.let { deadzoneControl.apply(OdinMapping.Side.RIGHT, it) },
        )
        // Die Statuszeile meldet das schlechtere der Ergebnisse (ohne Seite: nichts gesetzt = Fehler)
        val worst = results.maxOrNull() ?: DeadzoneControl.Result.FAILED
        ui.statusText = statusTextOf(worst)
        showDeadzoneNow()
        forgetLiveInput()
        profiles.refreshOdinValues()
        return worst
    }

    // Nach einer Änderung der Deadzone den zuletzt gesehenen Stick-Wert vergessen: Der Controller schickt in
    // Ruhe nichts Neues, die Live-Karte würde sonst den alten Rest weiter zeigen (R77, Fund F3, 25. Sitzung).
    // Die Karte zeigt dann "–" und "Move once", bis der Stick bewegt wird (Entscheidung L1)
    private fun forgetLiveInput() {
        ui.liveInput = null
    }

    // Text für die Statuszeile nach dem Setzen der Deadzone
    private fun statusTextOf(result: DeadzoneControl.Result): Int = when (result) {
        DeadzoneControl.Result.ACTIVE_NOW -> R.string.status_active_now
        DeadzoneControl.Result.AFTER_RESTART -> R.string.status_after_restart
        DeadzoneControl.Result.FAILED -> R.string.status_error
    }

    // Anzeige "Deadzone now": der Wert, den der Controller gerade wirklich benutzt (Aufruf 13).
    // Ohne Live-Weg steht dort "–". Die Reglerstellungen dieses Werts zeigen, ob noch etwas zu übernehmen ist
    private fun showDeadzoneNow() {
        val left = mapping.getDeadzone(OdinMapping.Side.LEFT)
        val right = mapping.getDeadzone(OdinMapping.Side.RIGHT)
        ui.deadzoneNowText = getString(R.string.deadzone_now, percentText(left), percentText(right))
        ui.deadzoneAppliedSteps =
            if (left != null && right != null) LeftRight(DeadzoneSteps.toStep(left), DeadzoneSteps.toStep(right)) else null
    }

    // Bruchteil als Prozent mit einer Nachkommastelle (0.025 → "2.5 %"), unbekannt → "–"
    private fun percentText(fraction: Float?): String =
        if (fraction == null) getString(R.string.deadzone_unknown)
        else getString(R.string.deadzone_percent, fraction * 100f)

    // ---------- Original + "Restore original" ----------

    // Merkt fehlende Originalwerte und schaltet den Knopf "Restore original" erst frei,
    // wenn für beide Sticks ein Original da ist
    private fun captureOriginals() {
        originalValues.captureMissing()
        ui.restoreEnabled = originalDeadzone() != null
        pairs.values.forEach { it.ui.restoreEnabled = it.originalBoth() != null }
    }

    // Gemerkte Deadzone beider Sticks (links, rechts), null = noch nicht vollständig gemerkt
    private fun originalDeadzone(): Pair<Float, Float>? {
        val left = originalValues.deadzone(OdinMapping.Side.LEFT) ?: return null
        val right = originalValues.deadzone(OdinMapping.Side.RIGHT) ?: return null
        return left to right
    }

    // Knopf "Restore original": öffnet die Nachfrage mit den Werten (Entscheidung R-3)
    override fun onRestoreClicked() {
        // Wie bei "Apply": eine laufende Messung zuerst beenden
        finishAutoSet()
        val (left, right) = originalDeadzone() ?: return
        var message = getString(R.string.restore_values, percentText(left), percentText(right))
        // Über 20 % setzt DeadzoneControl nur 20 % (SettingLimits.DEADZONE) – das vorher sagen
        val limit = SettingLimits.DEADZONE.endInclusive
        if (left > limit || right > limit) {
            message += "\n\n" + getString(R.string.restore_capped)
        }
        ui.restoreMessage = message
        ui.dialog = MainDialog.RESTORE
    }

    // OK in der Nachfrage: setzt das Original für beide Sticks und stellt die Regler darauf
    override fun onRestoreConfirmed() {
        ui.dialog = MainDialog.NONE
        val (left, right) = originalDeadzone() ?: return
        applyDeadzoneValues(left, right)
        ui.leftStep = DeadzoneSteps.toStep(left)
        ui.rightStep = DeadzoneSteps.toStep(right)
    }

    // ---------- Sensitivity + Trigger (Reiter Sticks, Schritt S) ----------

    // Was bei Sensitivity und Trigger verschieden ist; alles Übrige (anzeigen, übernehmen, zurücksetzen) ist für
    // beide gleich. Die Deadzone bleibt auf ihrem eigenen Weg, weil Auto-Set und Messung daran hängen
    private inner class PairSetting(
        val ui: SettingPairUi,
        val steps: SettingSteps,
        val read: (OdinMapping.Side) -> Float?,                              // wirksamer Wert (Live-Weg)
        val apply: (OdinMapping.Side, Float) -> DeadzoneControl.Result,      // setzen + nachlesen
        val original: (OdinMapping.Side) -> Float?,                          // Wert von vor StickLab 2
        val valueText: (Float) -> String,                                    // Wert für Anzeige und Nachfrage
        @param:StringRes val nowText: Int,
        @param:StringRes val restoreValues: Int,
        val restoreDialog: MainDialog,
    ) {
        // Original beider Seiten, null = noch nicht für beide gemerkt
        fun originalBoth(): LeftRight<Float>? {
            val left = original(OdinMapping.Side.LEFT) ?: return null
            val right = original(OdinMapping.Side.RIGHT) ?: return null
            return LeftRight(left, right)
        }

        // Wert oder "–", wenn er unbekannt ist
        fun textOrUnknown(value: Float?): String = value?.let(valueText) ?: getString(R.string.deadzone_unknown)
    }

    // Die beiden Teile mit ihren Wegen zum Odin (TriggerControl: live + System; SensitivityControl: nur live,
    // bleibt nach Neustart)
    private fun createPairSettings(): Map<StickSetting, PairSetting> =
        mapOf(
            StickSetting.SENSITIVITY to PairSetting(
                ui = ui.sensitivity,
                steps = SettingSteps.SENSITIVITY,
                read = mapping::getSensitivity,
                apply = sensitivityControl::apply,
                original = originalValues::sensitivity,
                valueText = { getString(R.string.sensitivity_value, it) },
                nowText = R.string.sensitivity_now,
                restoreValues = R.string.sensitivity_restore_values,
                restoreDialog = MainDialog.SENSITIVITY_RESTORE,
            ),
            StickSetting.TRIGGER to PairSetting(
                ui = ui.trigger,
                steps = SettingSteps.TRIGGER,
                read = mapping::getTriggerDeadzone,
                apply = triggerControl::apply,
                original = originalValues::triggerDeadzone,
                valueText = { getString(R.string.trigger_percent, it * 100f) },
                nowText = R.string.trigger_now,
                restoreValues = R.string.trigger_restore_values,
                restoreDialog = MainDialog.TRIGGER_RESTORE,
            ),
        )

    // Umschalter oben in der Regler-Karte: nur anzeigen, gesetzt wird nichts
    override fun onStickSettingChosen(setting: StickSetting) {
        ui.stickSetting = setting
    }

    // Anzeige "… now" mit dem wirksamen Wert (Aufruf 10 bzw. 15). Solange nichts Eigenes gewählt ist,
    // folgen die Regler diesem Wert (z. B. nach einer Änderung im Odin-Menü)
    private fun showPairNow(pair: PairSetting) {
        val left = pair.read(OdinMapping.Side.LEFT)
        val right = pair.read(OdinMapping.Side.RIGHT)
        val hadOwnChoice = pair.ui.isPending
        pair.ui.nowText = getString(pair.nowText, pair.textOrUnknown(left), pair.textOrUnknown(right))
        pair.ui.applied = if (left != null && right != null) LeftRight(pair.steps.toStep(left), pair.steps.toStep(right)) else null
        if (!hadOwnChoice) pair.ui.applied?.let { pair.ui.chosen = it }
    }

    private fun showAllPairsNow() = pairs.values.forEach(::showPairNow)

    // Regler bewegt: nur merken, gesetzt wird erst mit "Apply" (wie bei der Deadzone)
    override fun onPairStepChanged(setting: StickSetting, side: OdinMapping.Side, step: Int) {
        val pair = pairs[setting] ?: return
        pair.ui.chosen = when (side) {
            OdinMapping.Side.LEFT -> pair.ui.chosen.copy(left = step)
            OdinMapping.Side.RIGHT -> pair.ui.chosen.copy(right = step)
        }
        showPendingStatus()
    }

    // Knopf "Apply": setzt nur den angezeigten Teil (beide Seiten). Bei Erfolg kurz ein Haken am Knopf
    override fun onApplyPair(setting: StickSetting) {
        val pair = pairs[setting] ?: return
        val chosen = pair.ui.chosen
        val result = applyPairValues(pair, pair.steps.toValue(chosen.left), pair.steps.toValue(chosen.right))
        if (result != DeadzoneControl.Result.FAILED) pair.ui.lastApplySuccessAt = SystemClock.uptimeMillis()
    }

    // Knopf "Restore original": Nachfrage mit Original und jetzigem Wert (wie bei der Deadzone, R-3)
    override fun onRestorePairClicked(setting: StickSetting) {
        val pair = pairs[setting] ?: return
        val original = pair.originalBoth() ?: return
        ui.restoreMessage = getString(
            pair.restoreValues,
            pair.valueText(original.left), pair.valueText(original.right),
            pair.textOrUnknown(pair.read(OdinMapping.Side.LEFT)), pair.textOrUnknown(pair.read(OdinMapping.Side.RIGHT)),
        )
        ui.dialog = pair.restoreDialog
    }

    // OK in der Nachfrage: Original setzen und die Regler darauf stellen
    override fun onRestorePairConfirmed(setting: StickSetting) {
        ui.dialog = MainDialog.NONE
        val pair = pairs[setting] ?: return
        val original = pair.originalBoth() ?: return
        pair.ui.chosen = LeftRight(pair.steps.toStep(original.left), pair.steps.toStep(original.right))
        applyPairValues(pair, original.left, original.right)
    }

    // Setzt beide Seiten, zeigt danach den wirksamen Wert und meldet das schlechtere Ergebnis.
    // Die Live-Karte vergisst den alten Stick-Wert (er stimmt nach einer Änderung nicht mehr, wie bei der Deadzone)
    private fun applyPairValues(pair: PairSetting, left: Float, right: Float): DeadzoneControl.Result {
        val worst = maxOf(pair.apply(OdinMapping.Side.LEFT, left), pair.apply(OdinMapping.Side.RIGHT, right))
        ui.statusText = statusTextOf(worst)
        showPairNow(pair)
        forgetLiveInput()
        profiles.refreshOdinValues()
        return worst
    }

    // Statuszeile "Not applied yet", solange irgendwo (Deadzone, Sensitivity, Trigger, M1/M2) eine Wahl
    // noch nicht übernommen ist, sonst "Ready"
    private fun showPendingStatus() {
        val pending = ui.deadzonePending || pairs.values.any { it.ui.isPending } || buttonsPending()
        ui.statusText = if (pending) R.string.status_not_applied else R.string.status_ready
    }

    // ---------- M1/M2 (Reiter Buttons, Phase 4) ----------

    // Liest Layout und Belegung neu. Eine gewählte, noch nicht übernommene Belegung bleibt stehen;
    // sonst zeigt die Auswahl, was gerade eingestellt ist
    private fun loadButtonMapping() {
        ui.controllerLayout = buttonMapping.layout()
        for (button in BackButton.entries) {
            val pending = ui.chosenTargets[button] != ui.appliedTargets[button]
            putOrRemove(ui.appliedTargets, button, buttonMapping.read(button))
            if (!pending) putOrRemove(ui.chosenTargets, button, ui.appliedTargets[button])
        }
        ui.buttonsRestoreEnabled = originalButtonTargets() != null
    }

    // Pfeil ‹ oder ›: vorheriger bzw. nächster Eintrag. Ist die Belegung unbekannt, beginnt die Auswahl am
    // Anfang bzw. Ende der Liste
    override fun onButtonTargetStep(button: BackButton, delta: Int) {
        val current = ui.chosenTargets[button]
        onButtonTargetChosen(button, current?.stepped(delta) ?: if (delta > 0) ButtonTarget.entries.first() else ButtonTarget.entries.last())
    }

    // Eintrag gewählt (Pfeil oder Kachel-Übersicht): nur merken und anzeigen, gesetzt wird erst mit "Apply" (B3)
    override fun onButtonTargetChosen(button: BackButton, target: ButtonTarget) {
        ui.chosenTargets[button] = target
        showPendingStatus()
    }

    // Knopf "Apply": schreibt beide gewählten Belegungen (wie "Apply" bei der Deadzone beide Seiten setzt)
    override fun onApplyButtons() {
        applyButtonTargets(ui.chosenTargets.toMap())
    }

    // Knopf "Restore original": Nachfrage mit den gemerkten Werten, nur M1/M2 (Entscheidung B4)
    override fun onRestoreButtonsClicked() {
        val original = originalButtonTargets() ?: return
        val layout = ui.controllerLayout
        ui.restoreMessage = getString(
            R.string.buttons_restore_values,
            ButtonLayoutMap.printedName(original.getValue(BackButton.M1), layout),
            ButtonLayoutMap.printedName(original.getValue(BackButton.M2), layout),
        )
        ui.dialog = MainDialog.BUTTONS_RESTORE
    }

    // OK in der Nachfrage: Original für M1 und M2 setzen, Auswahl darauf stellen
    override fun onRestoreButtonsConfirmed() {
        ui.dialog = MainDialog.NONE
        val original = originalButtonTargets() ?: return
        original.forEach { (button, target) -> ui.chosenTargets[button] = target }
        applyButtonTargets(original)
    }

    // Setzt die Belegungen, liest danach neu und meldet das Ergebnis in der Statuszeile.
    // Bei Erfolg zeigt "Apply" kurz einen Haken
    private fun applyButtonTargets(targets: Map<BackButton, ButtonTarget>) {
        val results = targets.map { (button, target) -> buttonMapping.apply(button, target) }
        loadButtonMapping()
        val saved = results.isNotEmpty() && results.all { it == ButtonMappingControl.Result.SAVED }
        ui.statusText = if (saved) R.string.status_buttons_saved else R.string.status_buttons_failed
        if (saved) ui.lastButtonsApplyAt = SystemClock.uptimeMillis()
        profiles.refreshOdinValues()
    }

    // Gemerktes Original von M1 und M2 (seit 15.10.2026 in original_values), null = nicht für beide da
    private fun originalButtonTargets(): Map<BackButton, ButtonTarget>? =
        BackButton.entries.associateWith { originalValues.buttonTarget(it) ?: return null }

    // Weicht die Auswahl irgendwo von der eingestellten Belegung ab?
    private fun buttonsPending(): Boolean =
        BackButton.entries.any { ui.chosenTargets[it] != ui.appliedTargets[it] }

    // Steuerkreuz für die Gedrückt-Anzeige: kommt als Achse mit jeder Controller-Bewegung
    private fun showPressedDpad(event: MotionEvent) {
        val parts = ButtonLayoutMap.dpadParts(
            event.getAxisValue(MotionEvent.AXIS_HAT_X),
            event.getAxisValue(MotionEvent.AXIS_HAT_Y),
        )
        if (parts == pressedDpadParts) return
        pressedDpadParts = parts
        showPressedParts()
    }

    // Tasten + Steuerkreuz zusammen an die Anzeige geben (nur bei Änderung, spart Neuzeichnen)
    private fun showPressedParts() {
        val all = pressedKeyParts + pressedDpadParts
        if (all != ui.pressedParts) ui.pressedParts = all
    }

    // Wert in die Anzeige-Tabelle legen, null = Eintrag entfernen (Anzeige "–")
    private fun <K, V : Any> putOrRemove(map: MutableMap<K, V>, key: K, value: V?) {
        if (value == null) map.remove(key) else map[key] = value
    }

    // ---------- Profile (Reiter Profiles, Phase 5a/5b-1): nur weiterreichen, Abläufe in ProfilesController ----------

    // Profile setzen ihre Werte über dieselben Wege wie "Apply" in Sticks und Buttons (OdinControlsSetter);
    // "Use" wechselt über ProfileSwitcher, genau wie die Kachel (5d)
    private fun createProfilesController(): ProfilesController {
        val applier = ProfileApplier(OdinControlsSetter(deadzoneControl, triggerControl, sensitivityControl, buttonMapping))
        return ProfilesController(
            context = this,
            ui = ui,
            snapshot = ProfileSnapshot(mapping, buttonMapping, CurveStore(this)::load),
            applier = applier,
            switcher = ProfileSwitcher(this, applier),
            originals = originalValues,
            mapping = mapping,
            onOdinValuesSet = ::showValuesAfterProfile,
        )
    }

    // Wechsel über die Kachel (5d) seit dem letzten Blick: Profile und Werte neu laden. Das Schnellmenü pausiert die
    // App nicht (R133), darum auch beim Zurückbekommen des Fokus, nicht nur in onResume
    private fun showTileSwitchIfAny() {
        val switchedAt = ProfileTileService.lastSwitchAt
        if (switchedAt == seenTileSwitchAt) return
        seenTileSwitchAt = switchedAt
        profiles.load()
        showValuesAfterProfile()
        profiles.refreshOdinValues()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        showTileSwitchIfAny()
        loadTileAdded()
    }

    // Nach "Use" bzw. "Restore everything": Regler und Anzeigen in Sticks und Buttons zeigen die neuen Werte.
    // Eine dort gewählte, noch nicht übernommene Stellung wird verworfen – sie passte zum alten Stand (Plan 5b-1)
    private fun showValuesAfterProfile() {
        loadDeadzoneSliders()
        showDeadzoneNow()
        pairs.values.forEach { pair ->
            showPairNow(pair)
            pair.ui.applied?.let { pair.ui.chosen = it }
        }
        loadButtonMapping()
        ui.chosenTargets.clear()
        ui.chosenTargets.putAll(ui.appliedTargets)
        curves.load()
        forgetLiveInput()
    }

    override fun onProfilesShown() = profiles.refreshOdinValues()
    override fun onProfileSlotSelected(slot: Int) = profiles.select(slot)
    override fun onProfileUse() {
        profiles.use()
    }
    override fun onProfileEdit() = profiles.edit()
    override fun onProfileRestoreEverything() = profiles.restoreEverything()
    override fun onProfileRestoreEverythingConfirmed() = profiles.restoreEverythingConfirmed()
    override fun onProfileSave() = profiles.save()
    override fun onProfileSaveConfirmed() = profiles.saveConfirmed()
    override fun onProfileRename() = profiles.rename()
    override fun onProfileRenamed(name: String) = profiles.renamed(name)
    override fun onProfileColor() = profiles.color()
    override fun onProfileColorChosen(color: ProfileColor) = profiles.colorChosen(color)
    override fun onProfileCopy() = profiles.copy()
    override fun onProfileDelete() = profiles.delete()
    override fun onProfileDeleteConfirmed() = profiles.deleteConfirmed()
    override fun onProfileSaveToActive() = profiles.saveToActive()
    override fun onProfilesHelp() = profiles.showHelp()
    override fun onProfilesHelpClosed() = profiles.helpClosed()

    // ---------- Ambilight und Licht-Werte (Reiter Lights) ----------

    // Knopf Ambilight: läuft es, ausschalten, sonst Android um das Mitlesen des Bildschirms bitten
    override fun onAmbilightClicked() {
        when {
            AmbilightService.running -> stopAmbilight()
            // Ohne die Kachel "Joystick light" bleiben die LEDs dunkel. StickLab schaltet das Licht nie selbst ein (Plan 5c),
            // darum nur der Hinweis
            LightFlash.isLightOff(this) -> ui.dialog = MainDialog.AMBILIGHT_LIGHT_OFF
            else -> requestScreenCapture()
        }
    }

    // OK im Hinweis: Trotzdem starten, das Licht lässt sich auch danach noch einschalten
    override fun onAmbilightLightOffConfirmed() {
        ui.dialog = MainDialog.NONE
        requestScreenCapture()
    }

    // Fragt Android nach der Erlaubnis, den Bildschirm mitzulesen (Antwort kommt im projectionLauncher)
    private fun requestScreenCapture() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val intent = if (Build.VERSION.SDK_INT >= 34) {
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            mpm.createScreenCaptureIntent()
        }
        projectionLauncher.launch(intent)
    }

    // Startet den Ambilight-Dienst mit der Erlaubnis von Android
    private fun startAmbilight(resultCode: Int, data: Intent) {
        val intent = Intent(this, AmbilightService::class.java)
            .putExtra(AmbilightService.EXTRA_CODE, resultCode)
            .putExtra(AmbilightService.EXTRA_DATA, data)
        startForegroundService(intent)
        ui.ambilightRunning = true
        ui.statusText = R.string.status_ambi_running
    }

    // Beendet den Ambilight-Dienst (er setzt die LED-Farbe dabei selbst zurück)
    private fun stopAmbilight() {
        stopService(Intent(this, AmbilightService::class.java))
        ui.ambilightRunning = false
        ui.statusText = R.string.status_ambi_off
    }

    // Lädt Schalter und Licht-Regler aus der Datei "light"
    private fun loadLightSettings() {
        LightSettings.load(this)
        ui.skipPrompt = LightSettings.skipPrompt
        showLightValues()
    }

    // Überträgt die sechs Licht-Werte aus LightSettings in die Anzeige
    private fun showLightValues() {
        for (slider in LightSlider.entries) {
            ui.lightValues[slider] = slider.read()
        }
    }

    // Licht-Regler bewegt: sofort übernehmen und speichern (das laufende Ambilight liest LightSettings)
    override fun onLightChanged(slider: LightSlider, value: Int) {
        slider.write(value)
        LightSettings.save(this)
        ui.lightValues[slider] = slider.read()
    }

    // Knopf "Defaults": nur die Licht-Regler zurück, nicht der Schalter
    override fun onLightDefaults() {
        LightSettings.reset()
        LightSettings.save(this)
        showLightValues()
    }

    // Schalter "Don't ask again": Android-Abfrage zur Bildschirmaufnahme überspringen oder wieder zeigen
    override fun onSkipPromptChanged(checked: Boolean) {
        LightSettings.skipPrompt = checked
        LightSettings.save(this)
        OdinService.setScreenCaptureAllowed(packageName, checked)
        ui.skipPrompt = checked
    }

    // ---------- Reiter Settings: Kachel ins Schnellmenü (5d-2) ----------

    // Knopf "Add tile": Android fragt selbst "Add tile to Quick Settings?" (ab Android 13). Ohne diese Funktion
    // bleibt der Weg von Hand (rechte Karte im Reiter)
    override fun onAddTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            ui.statusText = R.string.status_tile_add_failed
            return
        }
        val statusBar = getSystemService(StatusBarManager::class.java)
        statusBar.requestAddTileService(
            ComponentName(this, ProfileTileService::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_tile_profiles),
            mainExecutor,
        ) { result -> showAddTileResult(result) }
    }

    // Antwort von Android: hinzugefügt / war schon drin → Merker setzen (sonst erst beim nächsten Öffnen des
    // Schnellmenüs), abgelehnt → nur Meldung, Fehler → Hinweis auf den Weg von Hand
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun showAddTileResult(result: Int) {
        val inQuickSettings = result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
            result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
        if (inQuickSettings) appPrefs().edit { putBoolean(AppStorage.AppKeys.TILE_ADDED, true) }
        ui.tileAdded = inQuickSettings || ui.tileAdded
        ui.statusText = when (result) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> R.string.status_tile_added
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.status_tile_already
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> R.string.status_tile_not_added
            else -> R.string.status_tile_add_failed
        }
        Log.i(TAG, "Kachel hinzufügen: Antwort von Android $result")
    }

    // Stand "Kachel im Schnellmenü" aus dem Merker der Kachel (beim Öffnen und Zurückkehren in die App)
    private fun loadTileAdded() {
        ui.tileAdded = appPrefs().getBoolean(AppStorage.AppKeys.TILE_ADDED, false)
    }

    // Hintergrund gewählt (Reiter Settings): wirkt sofort und bleibt gespeichert, kein "Apply", weil es nur Optik ist
    override fun onBackgroundChosen(background: AppBackground) {
        ui.background = background
        appPrefs().edit { putString(AppStorage.AppKeys.BACKGROUND, background.id) }
    }

    // ---------- Hinweise beim Start ----------

    // "Nicht unterstützt" hat Vorrang vor der Begrüßung; die Begrüßung nur, bis einmal OK gedrückt wurde;
    // danach einmal die Erklärung der Profile (31. Sitzung)
    private fun startDialog(): MainDialog = when {
        !OdinService.isAvailable() -> MainDialog.UNSUPPORTED
        !appPrefs().getBoolean(AppStorage.AppKeys.INTRO_SHOWN, false) -> MainDialog.INTRO
        profiles.helpPending -> MainDialog.PROFILES_HELP
        else -> MainDialog.NONE
    }

    // OK in der Begrüßung: nicht mehr zeigen. Danach kommt einmal die Erklärung der Profile
    override fun onIntroConfirmed() {
        appPrefs().edit { putBoolean(AppStorage.AppKeys.INTRO_SHOWN, true) }
        ui.dialog = if (profiles.helpPending) MainDialog.PROFILES_HELP else MainDialog.NONE
    }

    // Nachfrage ohne Wirkung schließen (Abbrechen, daneben tippen, "Zurück")
    override fun onDialogDismissed() {
        ui.dialog = MainDialog.NONE
    }

    // Allgemeine App-Einstellungen (Datei "app", gibt es seit v1.0; Namen in AppStorage – nie ändern,
    // sonst erscheint die Begrüßung bei allen Nutzern erneut)
    private fun appPrefs() = AppStorage.open(this, AppStorage.AppFile.APP)

    private companion object {
        // Name im Protokoll (logcat)
        const val TAG = "StickLab"

        // Takt des Auto-Sets: Ein Feld wird höchstens 0,1 s nach Ende der 300-ms-Stille (DriftMeter) grün,
        // die Uhr endet höchstens 0,1 s zu spät. Zehnmal pro Sekunde kostet praktisch nichts
        const val AUTO_SET_TICK_MS = 100L

        // Tasten, die auch im Reiter Buttons immer durchgehen: die Lautstärke-Wippe hängt am selben Gerät wie der
        // Controller (event8 meldet KEY_VOLUMEDOWN/UP, Abschnitt 2 "Eingebauter Controller")
        val KEYS_ALWAYS_PASSED = setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)
    }
}
private const val NOTIFICATION_REQUEST = 7
