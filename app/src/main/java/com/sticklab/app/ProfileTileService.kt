package com.sticklab.app

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.content.edit

// Kachel in den Schnelleinstellungen (Phase 5d, Ziel Z7; Entscheidungen K1–K4 und Nachfragen vom 27.10.2026).
// Zeigt das aktive Profil (Untertitel "changed", wenn die Werte am Odin nicht passen, K4) und wechselt beim Tippen
// zum nächsten gespeicherten Profil – mit genau denselben Wegen wie "Use" in der App (ProfileSwitcher), inkl.
// Lichtblitz (K2). Nicht gespeicherte Änderungen am Odin werden dabei überschrieben, wie bei "Use".
// Langes Drücken öffnet StickLab (Eintrag QS_TILE_PREFERENCES an MainActivity im Manifest).
// Nur Android selbst kann diesen Dienst ansprechen (BIND_QUICK_SETTINGS_TILE im Manifest)
class ProfileTileService : TileService() {

    companion object {
        private const val TAG = "ProfileTile"

        // So lange auf die Verbindung zum Live-Weg warten, wenn vor dem Verbinden getippt wurde (Plan 5d, H2).
        // Ohne Live-Weg würde die Sensitivity scheitern – dann lieber gar nichts setzen
        private const val CONNECT_TIMEOUT_MS = 2000L

        // Zeitpunkt des letzten Wechsels über die Kachel. Die App lädt danach Profile und Werte neu (R133)
        @Volatile
        var lastSwitchAt = 0L
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private val connectTimeout = Runnable { onConnectTimeout() }

    // Live-Weg, verbunden solange die Schnelleinstellungen offen sind (H2)
    private var mapping: OdinMapping? = null

    // Getippt, bevor die Verbindung stand: nach dem Verbinden wechseln
    private var switchWhenConnected = false

    private val store by lazy { ProfileStore(this) }

    // Schnelleinstellungen geöffnet: Live-Weg verbinden, Kachel beschriften (zuerst ohne "changed", nach dem
    // Verbinden mit gelesenen Werten). Ein unterbrochener Lichtblitz wird auch hier zurückgestellt (R112)
    override fun onStartListening() {
        super.onStartListening()
        rememberTileAdded(true)
        LightFlash.restoreInterrupted(this)
        connectMapping()
        showTile(odin = null)
    }

    // Kachel ins Schnellmenü gelegt bzw. herausgenommen: Merker für den Reiter Settings (5d-2, E2).
    // Eine schon vorher hinzugefügte Kachel meldet sich beim nächsten Öffnen des Menüs (onStartListening)
    override fun onTileAdded() {
        super.onTileAdded()
        rememberTileAdded(true)
    }

    override fun onTileRemoved() {
        rememberTileAdded(false)
        super.onTileRemoved()
    }

    private fun rememberTileAdded(added: Boolean) {
        val prefs = AppStorage.open(this, AppStorage.AppFile.APP)
        if (prefs.getBoolean(AppStorage.AppKeys.TILE_ADDED, false) == added) return
        prefs.edit {
            if (added) putBoolean(AppStorage.AppKeys.TILE_ADDED, true) else remove(AppStorage.AppKeys.TILE_ADDED)
        }
    }

    // Schnelleinstellungen geschlossen: Verbindung lösen
    override fun onStopListening() {
        handler.removeCallbacks(connectTimeout)
        switchWhenConnected = false
        mapping?.disconnect()
        mapping = null
        super.onStopListening()
    }

    // Tippen: nächstes Profil. Läuft in der App das Auto-Set, nichts setzen (R134)
    override fun onClick() {
        super.onClick()
        if (autoSetRunning()) {
            showTile(odin = null)
            return
        }
        val live = mapping ?: connectMapping()
        if (live.isConnected) {
            switchToNext(live)
        } else {
            switchWhenConnected = true
            handler.removeCallbacks(connectTimeout)
            handler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
        }
    }

    private fun connectMapping(): OdinMapping {
        val live = OdinMapping(this)
        live.onConnectionChanged = { connected -> onMappingChanged(live, connected) }
        mapping = live
        live.connect()
        return live
    }

    // Verbindung steht (oder fiel weg). Steht sie: wartendes Tippen ausführen, sonst nur die Kachel neu beschriften
    private fun onMappingChanged(live: OdinMapping, connected: Boolean) {
        if (!connected || mapping !== live) return
        handler.removeCallbacks(connectTimeout)
        if (switchWhenConnected) {
            switchWhenConnected = false
            switchToNext(live)
        } else {
            showTile(odin = readOdin(live))
        }
    }

    // Keine Verbindung zum Live-Weg in der Wartezeit: nichts gesetzt, Hinweis in der Kachel
    private fun onConnectTimeout() {
        switchWhenConnected = false
        Log.w(TAG, "Kein Live-Weg nach $CONNECT_TIMEOUT_MS ms – nichts gesetzt")
        showTile(odin = null, note = R.string.tile_no_connection)
    }

    // Wechsel auf das nächste gespeicherte Profil (ProfileSlots.nextSlot), dann Kachel mit den neuen Werten
    private fun switchToNext(live: OdinMapping) {
        val slots = loadSlots()
        val target = ProfileSlots.nextSlot(slots, activeSlot(slots)) ?: return showTile(odin = null)
        val profile = slots[target] ?: return
        val outcome = ProfileSwitcher(this, applierFor(live)).switchTo(target, profile)
        lastSwitchAt = System.currentTimeMillis()
        Log.i(TAG, "Wechsel auf Platz ${target + 1} \"${profile.name}\": alles gesetzt=${outcome.allSet}, " +
            "erster Fehler=${outcome.report.firstFailed}, aktiv gespeichert=${outcome.stored}")
        showTile(odin = readOdin(live), note = if (outcome.allSet) null else R.string.tile_not_all_set)
    }

    // Dieselben Wege wie "Use" in der App (MainActivity.createProfilesController)
    private fun applierFor(live: OdinMapping) = ProfileApplier(
        OdinControlsSetter(
            DeadzoneControl(live),
            TriggerControl(live),
            SensitivityControl(live),
            ButtonMappingControl(contentResolver),
        ),
    )

    // Werte am Odin lesen (nur lesen, wie die Profil-Zeile der App). null = nicht lesbar.
    // Steht im Protokoll: zeigt, ob der Live-Weg ohne offene App geht (Vorher-Test R113)
    private fun readOdin(live: OdinMapping): OdinValues? {
        val result = ProfileSnapshot(live, ButtonMappingControl(contentResolver), CurveStore(this)::load).read()
        Log.i(TAG, "Live-Weg verbunden=${live.isConnected}, gelesen: $result")
        return (result as? ProfileSnapshot.Result.Read)?.values
    }

    // Beschriftung: Name des aktiven Profils, darunter Hinweis (note), "Auto-Set running", "changed" oder "StickLab".
    // Kachel an = ein Profil ist aktiv; ohne gespeicherte Profile nicht antippbar
    private fun showTile(odin: OdinValues?, note: Int? = null) {
        val tile = qsTile ?: return
        val slots = loadSlots()
        val active = activeSlot(slots)
        val profile = active?.let { slots[it] }
        val noProfiles = slots.all { it == null }
        tile.label = profile?.name ?: getString(if (noProfiles) R.string.tile_no_profiles else R.string.tile_no_profile_active)
        tile.subtitle = getString(
            note ?: when {
                autoSetRunning() -> R.string.tile_autoset_running
                active != null && ProfileSlots.badgeOf(active, profile, active, odin) == SlotBadge.CHANGED -> R.string.tile_changed
                curvesWithoutCurveMode(profile) -> R.string.tile_curve_mode_off
                else -> R.string.tile_subtitle
            },
        )
        tile.state = when {
            noProfiles -> Tile.STATE_UNAVAILABLE
            profile != null -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    // Profil hat eine Kurve oder Trigger-Grenze, aber der Curve mode läuft nicht: Dann wirken sie nicht, das soll man sehen
    private fun curvesWithoutCurveMode(profile: Profile?): Boolean =
        profile != null && profile.needsCurveMode && !CurveModeEngine.isRunning()

    private fun loadSlots(): List<Profile?> =
        store.loadSlots { slot -> getString(R.string.profile_default_name, slot + 1) }

    // Aktiver Platz; "aktiv" auf einem leeren Platz gilt als kein Profil aktiv (wie ProfilesController.load)
    private fun activeSlot(slots: List<Profile?>): Int? = store.activeSlot?.takeIf { slots.getOrNull(it) != null }

    // Während des Auto-Sets steht der Merker der Messung in der Datei "app" (DriftMeasurement, R70) – dann wirkt
    // gerade eine Mess-Deadzone, die das Auto-Set danach zurückstellt (R134)
    private fun autoSetRunning(): Boolean {
        val prefs = AppStorage.open(this, AppStorage.AppFile.APP)
        return prefs.contains(AppStorage.AppKeys.DRIFT_RESTORE_LEFT) || prefs.contains(AppStorage.AppKeys.DRIFT_RESTORE_RIGHT)
    }
}
