package com.sticklab.app

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import java.util.Locale

// Wegwerf-Testbildschirm für Phase 3 "Prozent App = Firmware?" (PROJEKT_DOKU Abschnitt 11, Entscheidungen
// S1/W2/R1/E1 vom 19.10.2026). Liegt nur in der Debug-Version, Start per ADB.
// Ablauf ("Deadzone-Treppe"), ROUNDS Durchgänge:
// - MOVE_MS lang Deadzone 0 und der Text "JETZT drücken + loslassen": Der Besitzer drückt beide Sticks bis
//   zum Rand und lässt los, der Stick bleibt mit etwas Rest liegen
// - dann "NICHT ANFASSEN": die Deadzone beider Sticks steigt nur live durch SWEEP_PERCENT, je STEP_MS.
//   Der Controller meldet bei jeder Änderung den neu gerechneten Wert für dieselbe Stick-Stellung
//   (25. Sitzung gesehen) – getevent zeigt so, was jede Stufe wirklich abschneidet
// Rückweg wie beim Auto-Set (DriftMeasurement): vorher Merker in der Datei "app", am Ende und bei onPause
// alter Wert live zurück (nachgelesen); fällt das aus, stellt StickLab beim nächsten Öffnen zurück.
// Die Systemeinstellung wird nie geschrieben, ein Neustart bringt immer den gespeicherten Wert.
// Jede Stufe geht mit Zeit ins Log (Tag "DzSweep", in logcat -v monotonic dieselbe Uhr wie getevent)
class DeadzoneSweepActivity : AppCompatActivity() {

    // Ein Schritt des festen Ablaufs: Deadzone (Prozent) für beide Sticks, Text auf dem Schirm, Dauer
    private data class Step(val round: Int, val percent: Float, val hint: String, val durationMs: Long)

    private lateinit var text: TextView
    private lateinit var mapping: OdinMapping
    private lateinit var deadzone: DeadzoneControl
    private lateinit var measurement: DriftMeasurement
    private val handler = Handler(Looper.getMainLooper())

    private val steps = buildSteps()
    private var nextIndex = 0
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        text = TextView(this).apply {
            textSize = 40f
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        setContentView(text)
        // Anlegen verbindet noch nichts (onDestroy braucht mapping auch im Fall unten)
        mapping = OdinMapping(this)
        deadzone = DeadzoneControl(mapping)
        measurement = DriftMeasurement(this, mapping, deadzone)

        // Von Android neu aufgebaut (savedInstanceState da): nie von selbst eine neue Treppe beginnen,
        // nur ein Start per ADB zählt (19.10.2026: Neuaufbau startete ungewollt neue Läufe)
        if (savedInstanceState != null) {
            log("Neu aufgebaut – kein Start")
            show("Neu aufgebaut – nichts gestartet.\nTreppe nur per ADB neu starten.", Color.GRAY)
            return
        }
        show("Verbinde …", Color.GRAY)
        // Erst starten, wenn der Live-Weg steht; fällt er weg, sofort beenden (Rückweg)
        mapping.onConnectionChanged = { connected -> if (connected) startSweep() else stopSweep("Verbindung weg") }
        if (!mapping.connect()) show("com.odin.mapping nicht erreichbar – nichts verändert", Color.RED)
    }

    // Verlassen (Home, Zurück, Bildschirm aus) beendet die Treppe sofort und stellt zurück
    override fun onPause() {
        super.onPause()
        stopSweep("onPause")
    }

    override fun onDestroy() {
        stopSweep("onDestroy")
        mapping.disconnect()
        super.onDestroy()
    }

    // Merker schreiben und Deadzone auf 0 (DriftMeasurement), dann den Ablauf Schritt für Schritt abspielen
    // Nur einmal und nur, solange der Bildschirm vorne ist (Verbindung kann erst nach onPause kommen)
    private fun startSweep() {
        if (running || nextIndex > 0) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (!measurement.start(LeftRight(0f, 0f))) {
            log("Start abgelehnt (Merker da oder Live-Weg nicht bereit) – nichts verändert")
            show("Start nicht möglich – nichts verändert.\nStickLab einmal öffnen, dann neu starten.", Color.RED)
            return
        }
        running = true
        log("Start, vorher ${measurement.savedDeadzone}")
        runNextStep()
    }

    private fun runNextStep() {
        if (!running) return
        val step = steps.getOrNull(nextIndex++) ?: return stopSweep("fertig")
        if (!setBothLive(step.percent)) return stopSweep("Setzen fehlgeschlagen bei ${step.percent} %")
        log(String.format(Locale.US, "r%d dz=%.1f", step.round, step.percent))
        show("${step.hint}\n\nDurchgang ${step.round}/$ROUNDS · Deadzone ${percentText(step.percent)}", colorOf(step))
        handler.postDelayed(::runNextStep, step.durationMs)
    }

    // Beide Sticks nur live setzen (Aufruf 12, nachgelesen mit 13). true = beide wirken
    private fun setBothLive(percent: Float): Boolean {
        val fraction = percent / PERCENT
        val left = deadzone.setLiveOnly(OdinMapping.Side.LEFT, fraction)
        val right = deadzone.setLiveOnly(OdinMapping.Side.RIGHT, fraction)
        return left && right
    }

    // Beendet die Treppe (einmalig) und stellt den alten Wert zurück. Schlägt das fehl, bleibt der Merker
    // stehen und StickLab stellt beim nächsten Öffnen zurück
    private fun stopSweep(reason: String) {
        if (!running) return
        running = false
        handler.removeCallbacksAndMessages(null)
        val restored = measurement.finish()
        log("Ende ($reason), zurückgestellt=$restored")
        val message = if (restored) "Fertig ($reason).\nDeadzone ist zurückgestellt." else
            "Ende ($reason).\nZurückstellen fehlgeschlagen – StickLab öffnen!"
        show(message, if (restored) Color.GREEN else Color.RED)
    }

    private fun show(message: String, color: Int) {
        text.text = message
        text.setTextColor(color)
    }

    private fun colorOf(step: Step): Int = if (step.hint == HINT_MOVE) Color.YELLOW else Color.CYAN

    private fun percentText(percent: Float) = String.format(Locale.US, "%.1f %%", percent)

    private fun log(message: String) = Log.i(TAG, "${SystemClock.uptimeMillis()} $message")

    companion object {
        private const val TAG = "DzSweep"
        private const val PERCENT = 100f

        // Drei Durchgänge = drei verschiedene Ruhepunkte je Stick (Entscheidung R1); ein Rest von 0 sagt nichts
        private const val ROUNDS = 3

        // Zeit zum Drücken und Loslassen, Deadzone 0 (Plan: 6 s)
        private const val MOVE_MS = 6_000L

        // Zeit je Stufe. 0,8 s: der Controller meldet sofort neu (25. Sitzung), der Abstand schont
        // com.odin.mapping (Risiko R97, ca. 12 Aufrufe je Durchgang statt bisher 2)
        private const val STEP_MS = 800L

        // Stufen in Prozent (Entscheidung W2: 0,5er-Schritte bis 4 %, dazu 5/6/8 %, falls der Rest größer ist).
        // Höchstens 8 %: weit unter der Grenze 20 % (SettingLimits.DEADZONE), der Odin nahm bis 30 % an
        private val SWEEP_PERCENT = listOf(0f, 0.5f, 1f, 1.5f, 2f, 2.5f, 3f, 3.5f, 4f, 5f, 6f, 8f)

        private const val HINT_MOVE = "JETZT: beide Sticks bis zum Rand\ndrücken und loslassen"
        private const val HINT_HANDS_OFF = "NICHT ANFASSEN"

        // Der ganze feste Ablauf: je Durchgang erst Drücken/Loslassen bei 0, dann die Treppe
        private fun buildSteps(): List<Step> = (1..ROUNDS).flatMap { round ->
            listOf(Step(round, 0f, HINT_MOVE, MOVE_MS)) +
                SWEEP_PERCENT.map { Step(round, it, HINT_HANDS_OFF, STEP_MS) }
        }
    }
}
