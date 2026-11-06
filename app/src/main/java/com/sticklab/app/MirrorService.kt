package com.sticklab.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Phase 2 / D (PROJEKT_DOKU Abschnitt 11 "Phase 2 / D", Entscheidungen D-1–D-3). Seit Phase 6e auch in der echten App.
//
// Was: Vordergrund-Dienst (Typ specialUse), der läuft, solange der virtuelle Controller läuft.
// Warum: Android behandelt die App dann als wichtig und räumt sie nicht weg (R32), sonst würde der Wächter
//        im Programm den virtuellen Controller mitten im Spiel beenden (R28). Dazu eine Benachrichtigung mit Stopp.
//
// Ablauf:
//   1. Schalter Curve mode → ACTION_START (App sichtbar = Android erlaubt den Start)
//   2. Benachrichtigung zeigen, dann MirrorControl.run(START) – Root-Befehle schickt weiter nur MirrorControl
//   3. Androids Eingabegeräte beobachten (ohne root): "StickLab Controller" erscheint = Programm läuft,
//      verschwindet = Programm zu Ende (Stopp, Wächter, Fehler – egal wie) → Dienst endet.
//      Kein Zeitlimit mehr (Z-1): Der Dienst läuft, solange das Programm läuft
//   4. Stopp (Benachrichtigung, Schalter Curve mode oder "Restore everything") → ACTION_STOP → MirrorControl.run(STOP).
//      Pad nach STOP_TIMEOUT_MS noch da → harter Stopp (MirrorControl.run(KILL)),
//      nach weiteren KILL_TIMEOUT_MS noch da → Dienst endet mit Meldung (dann App beenden = Wächter, oder Neustart)
// Alle Wege enden in finish(). Endet der Dienst, während das Pad noch da ist, schickt er Stopp (R44).
// START_NOT_STICKY: Android startet den Dienst nie von selbst neu, auch nicht beim Hochfahren (Plan Regel 7)
class MirrorService : Service(), InputManager.InputDeviceListener {

    companion object {
        const val ACTION_START = "com.sticklab.app.MIRROR_START"
        const val ACTION_STOP = "com.sticklab.app.MIRROR_STOP"

        private const val TAG = "MirrorService"
        private const val CHANNEL_ID = "mirror"
        private const val NOTIFICATION_ID = 2            // 1 gehört AmbilightService
        const val PAD_NAME = "StickLab Controller"       // Name des virtuellen Controllers (sticklab_mirror.c VIRT_NAME)

        // Wie lange auf das Pad warten: s5.sh wartet bis 3 s auf "abgegriffen", das Programm bis 8 s
        // auf das Löschen von event8, dann 0,3 s Pause → spätestens nach ca. 11,5 s da
        private const val PAD_APPEAR_TIMEOUT_MS = 15_000L
        // Nach Stopp so lange auf das Verschwinden warten (gemessen ca. 0,3–0,46 s, E.4), sonst harter Stopp
        private const val STOP_TIMEOUT_MS = 5_000L
        // Nach hartem Stopp so lange warten (Kernel gibt sofort frei, gemessen 12 ms in 3c, dazu 0,3 s Pause in s5.sh)
        private const val KILL_TIMEOUT_MS = 5_000L
    }

    // Alle Rückrufe (Zeitgeber, Eingabegeräte, Ergebnis von MirrorControl) laufen im Haupt-Thread → kein Sperren nötig
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var inputManager: InputManager

    private var running = false     // Start angenommen, Dienst beobachtet das Pad
    private var finished = false    // finish() ist gelaufen, Dienst endet gerade (kein neuer Start mehr)
    private var padId = -1          // Android-Nummer des Pads, -1 = noch nicht gesehen
    private var startedAtMs = 0L    // Uhrzeit des Starts, für den Text der Benachrichtigung
    private var killSent = false    // harter Stopp schon geschickt (nur einmal pro Lauf)

    private val padAppearTimeout = Runnable {
        if (padId < 0) finish("Pad nach ${PAD_APPEAR_TIMEOUT_MS / 1000} s nicht erschienen", sendStop = true)
    }
    // Normaler Stopp hat nicht gewirkt (Programm hängt?) → harter Stopp, dann noch einmal warten
    private val stopTimeout = Runnable {
        killSent = true
        sendKillInBackground("Pad ${STOP_TIMEOUT_MS / 1000} s nach Stopp noch da")
        mainHandler.postDelayed(killTimeout, KILL_TIMEOUT_MS)
    }
    private val killTimeout = Runnable {
        finish("Pad auch ${KILL_TIMEOUT_MS / 1000} s nach hartem Stopp noch da – App beenden oder neu starten", sendStop = false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        inputManager = getSystemService(InputManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("Auftrag ${intent?.action ?: "(keiner)"}, läuft = $running, endet = $finished")
        when (intent?.action) {
            ACTION_START -> onStartRequested()
            ACTION_STOP -> onStopRequested()
            else -> if (!running) stopSelf()
        }
        return START_NOT_STICKY
    }

    // ---- Start ----

    private fun onStartRequested() {
        // Nach startForegroundService verlangt Android sofort startForeground, auch wenn wir den Start ablehnen
        showForegroundNotification()
        if (finished) {
            log("Dienst endet gerade, Start ignoriert (bitte noch einmal tippen)")
            return
        }
        if (running) {
            log("läuft schon, Start ignoriert")
            return
        }
        running = true
        startedAtMs = System.currentTimeMillis()
        inputManager.registerInputDeviceListener(this, mainHandler)

        val existingPad = findPad()
        if (existingPad != null) {
            // Läuft schon (z. B. per ADB-Empfänger gestartet): nichts schicken, nur begleiten
            padId = existingPad
            log("Pad schon da (id=$existingPad), nichts geschickt, Dienst begleitet es")
            updateNotification()
            return
        }
        sendStartInBackground()
    }

    // MirrorControl.run nicht im Haupt-Thread (schreibt Dateien, spricht mit PServerBinder)
    private fun sendStartInBackground() {
        val appContext = applicationContext
        Thread {
            val result = MirrorControl.run(appContext, MirrorControl.START)
            mainHandler.post { onStartResult(result) }
        }.start()
    }

    private fun onStartResult(result: String) {
        if (finished) return
        if (MirrorControl.sentOk(result, MirrorControl.START)) {
            if (padId < 0) mainHandler.postDelayed(padAppearTimeout, PAD_APPEAR_TIMEOUT_MS)
        } else {
            finish("Start nicht geschickt: $result", sendStop = false)
        }
    }

    // ---- Stopp (Benachrichtigung, Schalter Curve mode oder "Restore everything") ----

    private fun onStopRequested() {
        if (!running || finished) {
            // Kein Lauf bekannt (z. B. alte Benachrichtigung): Stopp schadet nicht (pkill findet dann nichts)
            sendStopInBackground("Stopp ohne laufenden Dienst")
            if (!running) stopSelf()
            return
        }
        sendStopInBackground("Benachrichtigung, Schalter oder Restore everything")
        // Das Ende selbst kommt über onInputDeviceRemoved; falls nicht, nach STOP_TIMEOUT_MS harter Stopp.
        // Zweimal Stopp getippt: Zeitgeber neu starten; ist der harte Stopp schon geschickt, nicht noch einmal
        if (killSent) return
        mainHandler.removeCallbacks(stopTimeout)
        mainHandler.postDelayed(stopTimeout, STOP_TIMEOUT_MS)
    }

    private fun sendStopInBackground(reason: String) = sendInBackground(MirrorControl.STOP, "Stopp", reason)

    private fun sendKillInBackground(reason: String) = sendInBackground(MirrorControl.KILL, "harten Stopp", reason)

    // MirrorControl.run nicht im Haupt-Thread (spricht mit PServerBinder)
    private fun sendInBackground(caseName: String, label: String, reason: String) {
        log("schicke $label ($reason)")
        val appContext = applicationContext
        Thread { MirrorControl.run(appContext, caseName) }.start()
    }

    // ---- Pad beobachten (Androids Eingabegeräte, kein root) ----

    override fun onInputDeviceAdded(deviceId: Int) {
        if (padId >= 0 || InputDevice.getDevice(deviceId)?.name != PAD_NAME) return
        padId = deviceId
        mainHandler.removeCallbacks(padAppearTimeout)
        log("Pad erschienen (id=$deviceId)")
        updateNotification()
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        if (deviceId == padId) finish("Pad verschwunden (id=$deviceId), Programm zu Ende", sendStop = false)
    }

    override fun onInputDeviceChanged(deviceId: Int) {}

    private fun findPad(): Int? =
        InputDevice.getDeviceIds().firstOrNull { InputDevice.getDevice(it)?.name == PAD_NAME }

    // ---- Ende ----

    // Einziger geplanter Ausgang: Zeitgeber und Beobachtung ab, bei Bedarf Stopp, Benachrichtigung weg, Dienst endet
    private fun finish(reason: String, sendStop: Boolean) {
        if (finished) return
        finished = true
        log("Ende: $reason")
        stopWatching()
        if (sendStop) sendStopInBackground(reason)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopWatching() {
        mainHandler.removeCallbacksAndMessages(null)
        if (running) inputManager.unregisterInputDeviceListener(this)
    }

    // Unerwartetes Ende ohne finish() (z. B. Android beendet den Dienst): Pad noch da → Stopp (R44)
    override fun onDestroy() {
        if (!finished) {
            finished = true
            log("Dienst beendet ohne finish()")
            stopWatching()
            if (running && findPad() != null) sendStopInBackground("Dienst beendet, Pad noch da")
        }
        super.onDestroy()
    }

    // ---- Benachrichtigung ----

    private fun showForegroundNotification() {
        val notification = buildNotification(statusText())
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        if (finished) return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(statusText()))
    }

    private fun statusText(): String {
        if (padId < 0) return getString(R.string.curve_mode_starting)
        val clock = SimpleDateFormat("HH:mm", Locale.ROOT)
        return getString(R.string.curve_mode_running_since, clock.format(Date(startedAtMs)))
    }

    // Leise Benachrichtigung: Titel, Text, Knopf Stopp; Tippen öffnet StickLab
    private fun buildNotification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.curve_mode), NotificationManager.IMPORTANCE_LOW)
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, MirrorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.curve_mode_notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(Notification.Action.Builder(null as Icon?, getString(R.string.curve_mode_stop), stopIntent).build())
        // Ab Android 12 zeigt Android Dienst-Benachrichtigungen sonst evtl. erst nach 10 s
        if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        return builder.build()
    }

    private fun log(msg: String) {
        Log.i(TAG, msg)
    }
}
