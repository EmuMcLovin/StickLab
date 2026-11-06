package com.sticklab.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

// Wegwerf-Empfänger für Test C Stufe 4.5c (PROJEKT_DOKU Abschnitt 11, Nachtrag 23:25). Nur Debug-Version.
// Schickt einen festen Fall von PServerSleepTestActivity, aber OHNE Bildschirm: Das Spiel bleibt vorne,
// GameNative hält es nicht an (Fokus-Probe 12.10.2026: Testbildschirm vorne → SIGSTOP).
// Auslösen nur von der ADB-Shell (Manifest: android:permission="android.permission.DUMP"):
//   am broadcast -n com.sticklab.app.debug/com.sticklab.app.PServerCaseReceiver --es case <fall>
// Erlaubte Fälle (alles andere wird abgelehnt, es wird nichts geschickt):
//   "nur_suchen":         sucht PServerBinder, schickt nichts (Schritt 2b, ohne root)
//   "hide_event8_mirror": genau der Befehl und das Skript aus PServerSleepTestActivity (Stufe 4.5b/4.5d)
//   "mirror_pruefen" / "mirror_probe" / "mirror_start" / "mirror_stop": Stufe 5 und Phase 2 / E,
//   "mirror_kill" (harter Stopp, Z-1): alles in MirrorControl (Prüfsumme, Sperren, feste Befehle)
//   "curve_linear" / "curve_precision" / "curve_off" (Phase 6b): Tabellen-Datei schreiben/löschen, ohne root
//   (CurveTestCases)
// Gleiche Prüfungen wie im Testbildschirm: feste Liste, Sperre PServerLink.MAX_COMMAND (R14), Skript schreiben +
// zurücklesen. Kein Befehl aus freiem Text. Ergebnis im Log (Tag "PServerCase")
class PServerCaseReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val caseName = intent.getStringExtra(EXTRA_CASE)
        log("Empfangen: Fall ${caseName ?: "(keiner)"}")
        if (caseName !in ALLOWED) {
            log("Fall nicht erlaubt, es wird nichts geschickt")
            return
        }
        // Skript schreiben und transact nicht im Haupt-Thread. goAsync hält den Prozess am Leben,
        // bis finish() kommt (Android erlaubt dafür ca. 10 s, wir brauchen Millisekunden)
        val pending = goAsync()
        val appContext = context.applicationContext
        val filesDir = context.filesDir
        Thread {
            try {
                if (caseName in MirrorControl.CASES) MirrorControl.run(appContext, caseName!!)
                else if (caseName in CurveTestCases.CASES) CurveTestCases.run(appContext, caseName!!)
                else handle(caseName!!, filesDir)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun handle(caseName: String, filesDir: File) {
        if (!PServerLink.isAvailable()) {
            log("PServerBinder nicht gefunden, es wird nichts geschickt")
            return
        }
        if (caseName == CASE_ONLY_FIND) {
            log("PServerBinder gefunden, nichts geschickt")
            return
        }
        val case = PServerSleepTestActivity.CASES.getValue(caseName)
        if (case.command.length > PServerLink.MAX_COMMAND) {
            log("Befehl hat ${case.command.length} Zeichen (erlaubt ${PServerLink.MAX_COMMAND}), es wird nichts geschickt")
            return
        }
        val script = case.script
        if (script != null && !writeScript(script, filesDir)) {
            log("Skript nicht geschrieben, es wird nichts geschickt")
            return
        }
        send(caseName, case.command)
    }

    // Schickt den festen Befehl des Falls über PServerLink, Ergebnis ins Log
    private fun send(caseName: String, command: String) {
        log("sende \"$command\"")
        when (val result = PServerLink.send(command)) {
            is PServerLink.SendResult.Sent ->
                log("$caseName: transact = ${result.accepted} nach ${result.durationMs} ms, Antwort ${result.replySize} Bytes")
            is PServerLink.SendResult.Failed -> Log.e(TAG, "transact fehlgeschlagen", result.error)
            is PServerLink.SendResult.NotFound -> log("PServerBinder nicht gefunden, es wird nichts geschickt")
            is PServerLink.SendResult.TooLong ->
                log("Befehl hat ${result.length} Zeichen (erlaubt ${PServerLink.MAX_COMMAND}), es wird nichts geschickt")
        }
    }

    // Wie PServerSleepTestActivity.writeScript: Pfad und Inhalt müssen genau stimmen
    private fun writeScript(script: PServerSleepTestActivity.Companion.Script, filesDir: File): Boolean = try {
        val file = File(filesDir, script.name)
        if (file.absolutePath != script.path) {
            log("Skript-Pfad unerwartet: ${file.absolutePath}")
            false
        } else {
            file.writeText(script.content)
            val same = file.readText() == script.content
            log("Skript geschrieben: ${file.absolutePath}, ${script.content.length} Zeichen, gleich = $same")
            same
        }
    } catch (t: Throwable) {
        Log.e(TAG, "Skript schreiben fehlgeschlagen", t)
        false
    }


    private fun log(msg: String) {
        Log.i(TAG, msg)
    }

    companion object {
        private const val TAG = "PServerCase"
        private const val EXTRA_CASE = "case"
        private const val CASE_ONLY_FIND = "nur_suchen"
        // Bewusst nur diese. Weitere Fälle nur nach Entscheidung des Besitzers (Stufe 5: 13.10.2026;
        // Kurven-Datei ohne root: Phase 6b, 27.10.2026)
        private val ALLOWED = setOf(CASE_ONLY_FIND, "hide_event8_mirror") + MirrorControl.CASES + CurveTestCases.CASES
    }
}
