package com.sticklab.app

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

// Wegwerf-Testbildschirm für Phase 1 (A26, A4, E10). Liegt nur in der Debug-Version.
// Führt genau einen Testfall aus der festen Liste TEST_CASES aus (Auswahl per Extra
// "case", Standard: deadzone_r_0). Ablauf: alten Wert lesen, prüfen, ob er plausibel
// ist, Testwert setzen, beide Seiten nachlesen. Nur die Aufrufe aus der Liste, Werte
// fest im Code. Der Wert wird nur live gesetzt, nie als Systemeinstellung.
// Zwei Arten von Testfällen:
// - "zurückstellen" (restoreOnExit = true): beim Verlassen alten Wert zurück und nachlesen
// - "stehen lassen" (restoreOnExit = false, Phase 2 "Vorher prüfen", 15.10.2026): der Wert
//   bleibt, damit man sehen kann, was nach einem Neustart gilt. Rückweg sind die festen
//   Fälle "…_back" (Werte des Besitzers aus original_values.xml)
// Rückweg, falls das Zurücksetzen ausfällt: Deadzone und Trigger per Neustart
// (Systemwert), Sensitivity nur über das Odin-Menü (bleibt nach Neustart erhalten).
// Stick-Werte rechts gehen ins Log (Tag "MappingSet")
class MappingSetTestActivity : AppCompatActivity() {

    // Ein Testfall: Lese- und Setz-Nummer, Seite (0 = links/L2, 1 = rechts/R2),
    // Testwert, der Bereich, in dem alter Wert und Testwert liegen müssen, und ob der
    // alte Wert beim Verlassen des Bildschirms zurückgestellt wird
    private data class TestCase(
        val label: String,
        val getCode: Int,
        val setCode: Int,
        val side: Int,
        val testValue: Float,
        val min: Float,
        val max: Float,
        val restoreOnExit: Boolean = true,
    )

    private lateinit var text: TextView
    private lateinit var case: TestCase
    private var service: IBinder? = null
    private var bound = false
    private var oldValue: Float? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = binder
            val before = read(binder, case.side)
            log("${case.label}: vorher ${bothSides(binder)}")
            if (before == null || before !in case.min..case.max) {
                show("${case.label}: alter Wert nicht plausibel ($before), es wird nichts gesetzt")
                return
            }
            // Nur Fälle zum Zurückstellen merken sich den alten Wert (restore() prüft das)
            if (case.restoreOnExit) oldValue = before
            write(binder, case.testValue)
            val after = bothSides(binder)
            log("${case.label}: gesetzt ${case.testValue}, nachgelesen $after")
            val howBack = if (case.restoreOnExit) {
                "Zurück-Taste stellt $before wieder her"
            } else {
                "Bleibt stehen (nur live, kein Systemwert)"
            }
            show("${case.label}\nGesetzt: ${case.testValue} (vorher $before)\nNachgelesen: $after\n$howBack")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            log("Verbindung getrennt")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        text = TextView(this).apply {
            textSize = 22f
            setPadding(48, 48, 48, 48)
            text = "Setz-Test: verbinde …"
        }
        setContentView(text)

        // Nur Namen aus der festen Liste sind erlaubt, unbekannte Namen setzen nichts
        val caseName = intent.getStringExtra(EXTRA_CASE) ?: DEFAULT_CASE
        case = TEST_CASES[caseName] ?: run {
            show("Unbekannter Testfall: $caseName")
            log("Unbekannter Testfall: $caseName")
            return
        }
        log("Testfall $caseName")

        // Flags 0: nur an den schon laufenden Dienst anhängen, ihn nicht selbst starten
        bound = try {
            bindService(Intent(ACTION).setPackage(PACKAGE), connection, 0)
        } catch (e: SecurityException) {
            Log.e(TAG, "Verbindung verweigert", e)
            false
        }
        log("bindService = $bound")
        if (!bound) show("Verbindung nicht möglich (siehe Log)")
    }

    // Beim Verlassen des Bildschirms (Zurück, Home, Bildschirm aus) alten Wert zurück
    override fun onStop() {
        restore()
        super.onStop()
    }

    override fun onDestroy() {
        restore()
        if (bound) {
            unbindService(connection)
            bound = false
        }
        super.onDestroy()
    }

    private fun restore() {
        val old = oldValue ?: return
        val binder = service
        if (binder == null) {
            log("Zurücksetzen nicht möglich, keine Verbindung")
            return
        }
        write(binder, old)
        val check = bothSides(binder)
        log("${case.label}: zurückgesetzt auf $old, nachgelesen $check")
        oldValue = null
        show("${case.label}\nZurückgesetzt: $check")
    }

    private fun bothSides(binder: IBinder) = "links/L2 ${read(binder, 0)}, rechts/R2 ${read(binder, 1)}"

    private fun read(binder: IBinder, side: Int): Float? {
        var value: Float? = null
        call(binder, case.getCode, write = { it.writeInt(side) }) { value = it.readFloat() }
        return value
    }

    // Wert wird vor dem Setzen auf den erlaubten Bereich des Testfalls begrenzt
    private fun write(binder: IBinder, value: Float) {
        val safe = value.coerceIn(case.min, case.max)
        call(binder, case.setCode, write = {
            it.writeInt(case.side)
            it.writeFloat(safe)
        })
    }

    private fun call(binder: IBinder, code: Int, write: (Parcel) -> Unit, read: (Parcel) -> Unit = {}) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            write(data)
            if (!binder.transact(code, data, reply, 0)) {
                log("Aufruf $code: transact = false")
                return
            }
            reply.readException()
            read(reply)
        } catch (e: Exception) {
            Log.e(TAG, "Aufruf $code fehlgeschlagen", e)
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (!ev.isFromSource(InputDevice.SOURCE_JOYSTICK) || ev.action != MotionEvent.ACTION_MOVE) {
            return super.dispatchGenericMotionEvent(ev)
        }
        for (h in 0 until ev.historySize) {
            log(stick(ev.getHistoricalAxisValue(MotionEvent.AXIS_Z, h), ev.getHistoricalAxisValue(MotionEvent.AXIS_RZ, h)))
        }
        log(stick(ev.getAxisValue(MotionEvent.AXIS_Z), ev.getAxisValue(MotionEvent.AXIS_RZ)))
        return true
    }

    private fun stick(z: Float, rz: Float) = String.format(Locale.US, "Z=%.5f RZ=%.5f", z, rz)

    private fun show(msg: String) {
        text.text = msg
    }

    private fun log(msg: String) {
        Log.i(TAG, msg)
    }

    companion object {
        private const val TAG = "MappingSet"
        private const val PACKAGE = "com.odin.mapping"
        private const val ACTION = "com.ro.mapping.action.MAPPING_SERVICE"
        private const val DESCRIPTOR = "com.ro.mapping.sdk.IServerApi"
        private const val EXTRA_CASE = "case"
        private const val DEFAULT_CASE = "deadzone_r_0"

        // Aufrufnummern siehe PROJEKT_DOKU.md, "Code der Odin-Apps"
        private const val SET_SENSITIVITY = 9
        private const val GET_SENSITIVITY = 10
        private const val SET_DEADZONE = 12
        private const val GET_DEADZONE = 13
        private const val SET_TRIGGER = 14
        private const val GET_TRIGGER = 15

        // Seiten im Aufruf
        private const val LEFT = 0
        private const val RIGHT = 1

        // Erlaubte Bereiche: Stick- und Trigger-Deadzone 0–30 %, Sensitivity 0,5–1,5 (Menü zeigte 0.9 und 1.0)
        private const val ZONE_MIN = 0.0f
        private const val ZONE_MAX = 0.3f
        private const val SENSITIVITY_MIN = 0.5f
        private const val SENSITIVITY_MAX = 1.5f

        // Testwerte zum Stehenlassen (Phase 2 "Vorher prüfen"). Alle hat der Odin am 11.10.2026
        // schon angenommen. Deadzone links (nicht rechts), damit sie die Sensitivity-Messung
        // am rechten Stick nicht stört; 5 % liegt über dem gemessenen Drift (max. ca. 2,2 %)
        private const val KEEP_DEADZONE_LEFT = 0.05f
        private const val KEEP_TRIGGER_R2 = 0.10f
        private const val KEEP_SENSITIVITY_RIGHT = 1.0f

        // Rückweg: Werte des Besitzers vor StickLab 2 (original_values.xml vom 15.10.2026, 20:38)
        private const val ORIGINAL_DEADZONE_LEFT = 0.015f
        private const val ORIGINAL_TRIGGER_R2 = 0.05f
        private const val ORIGINAL_SENSITIVITY_RIGHT = 0.9f

        private val TEST_CASES = mapOf(
            // Zurückstellen beim Verlassen (Phase 1, 11.10.2026)
            "deadzone_r_0" to TestCase("Deadzone rechts 0 %", GET_DEADZONE, SET_DEADZONE, RIGHT, 0.0f, ZONE_MIN, ZONE_MAX),
            "deadzone_r_022" to TestCase("Deadzone rechts 2,2 %", GET_DEADZONE, SET_DEADZONE, RIGHT, 0.022f, ZONE_MIN, ZONE_MAX),
            "trigger_r2_10" to TestCase("Trigger-Deadzone R2 10 %", GET_TRIGGER, SET_TRIGGER, RIGHT, 0.10f, ZONE_MIN, ZONE_MAX),
            "sensitivity_r_10" to TestCase("Sensitivity rechts 1.0", GET_SENSITIVITY, SET_SENSITIVITY, RIGHT, 1.0f, SENSITIVITY_MIN, SENSITIVITY_MAX),

            // Stehen lassen bis Neustart bzw. Fall "…_back" (Phase 2 "Vorher prüfen", 15.10.2026)
            "deadzone_l_050_keep" to TestCase("Deadzone links 5 % (bleibt)", GET_DEADZONE, SET_DEADZONE, LEFT,
                KEEP_DEADZONE_LEFT, ZONE_MIN, ZONE_MAX, restoreOnExit = false),
            "trigger_r2_10_keep" to TestCase("Trigger-Deadzone R2 10 % (bleibt)", GET_TRIGGER, SET_TRIGGER, RIGHT,
                KEEP_TRIGGER_R2, ZONE_MIN, ZONE_MAX, restoreOnExit = false),
            "sensitivity_r_10_keep" to TestCase("Sensitivity rechts 1.0 (bleibt)", GET_SENSITIVITY, SET_SENSITIVITY, RIGHT,
                KEEP_SENSITIVITY_RIGHT, SENSITIVITY_MIN, SENSITIVITY_MAX, restoreOnExit = false),

            // Rückweg: zurück auf das Original
            "deadzone_l_back" to TestCase("Deadzone links zurück auf 1,5 %", GET_DEADZONE, SET_DEADZONE, LEFT,
                ORIGINAL_DEADZONE_LEFT, ZONE_MIN, ZONE_MAX, restoreOnExit = false),
            "trigger_r2_back" to TestCase("Trigger-Deadzone R2 zurück auf 5 %", GET_TRIGGER, SET_TRIGGER, RIGHT,
                ORIGINAL_TRIGGER_R2, ZONE_MIN, ZONE_MAX, restoreOnExit = false),
            "sensitivity_r_back" to TestCase("Sensitivity rechts zurück auf 0.9", GET_SENSITIVITY, SET_SENSITIVITY, RIGHT,
                ORIGINAL_SENSITIVITY_RIGHT, SENSITIVITY_MIN, SENSITIVITY_MAX, restoreOnExit = false),

            // Trigger unter 5 % (Wunsch Besitzer 05.11.2026): nur live und stehen lassen, ein Neustart stellt den
            // Systemwert wieder her (gesichert 17. Sitzung). Rückweg: Fälle "…_5" auf das Original 5 %
            "trigger_l2_3" to triggerCase("L2 3 %", LEFT, 0.03f),
            "trigger_r2_3" to triggerCase("R2 3 %", RIGHT, 0.03f),
            "trigger_l2_1" to triggerCase("L2 1 %", LEFT, 0.01f),
            "trigger_r2_1" to triggerCase("R2 1 %", RIGHT, 0.01f),
            "trigger_l2_5" to triggerCase("L2 zurück auf 5 %", LEFT, ORIGINAL_TRIGGER_R2),
            "trigger_r2_5" to triggerCase("R2 zurück auf 5 %", RIGHT, ORIGINAL_TRIGGER_R2),
        )

        // Fall für die Trigger-Deadzone einer Seite, der Wert bleibt stehen
        private fun triggerCase(label: String, side: Int, value: Float) =
            TestCase("Trigger-Deadzone $label (bleibt)", GET_TRIGGER, SET_TRIGGER, side, value, ZONE_MIN, ZONE_MAX, restoreOnExit = false)
    }
}
