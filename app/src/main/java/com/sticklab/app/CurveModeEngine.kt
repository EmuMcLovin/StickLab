package com.sticklab.app

import android.content.Context
import android.content.Intent
import android.view.InputDevice

// Schaltet den virtuellen Controller für den Schalter "Curve mode" (Phase 6c, Entscheidung K6-1 vom 27.10.2026): ein Zugang zum
// getesteten Weg MirrorService → MirrorControl (feste Befehle, Prüfsumme, Wächter). Startet nie von selbst, nur durch den
// Schalter (Plan Regel 7)
object CurveModeEngine {

    // Läuft das Programm? Sichtbar am virtuellen Gerät in Androids Liste, kein root nötig
    fun isRunning(): Boolean = InputDevice.getDeviceIds().any { InputDevice.getDevice(it)?.name == MirrorService.PAD_NAME }

    // Nur aufrufen, solange die App sichtbar ist: Android erlaubt den Dienst-Start sonst nicht
    fun start(context: Context) {
        context.startForegroundService(Intent(context, MirrorService::class.java).setAction(MirrorService.ACTION_START))
    }

    fun stop(context: Context) {
        context.startService(Intent(context, MirrorService::class.java).setAction(MirrorService.ACTION_STOP))
    }
}
