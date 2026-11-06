package com.sticklab.app

import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

// Wegwerf-Testbildschirm für Phase 1, Test A. Liegt nur in der Debug-Version.
// Zeigt die Stick-Werte so, wie Android sie an Apps und Spiele gibt,
// und schreibt jede Änderung ins Log (Tag "StickTest")
class StickTestActivity : AppCompatActivity() {

    private lateinit var text: TextView

    private val axes = intArrayOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bildschirm bleibt während der Messung an
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        text = TextView(this).apply {
            textSize = 24f
            setPadding(48, 48, 48, 48)
            text = "Stick-Test: Stick bewegen"
        }
        setContentView(text)
        logRanges()
    }

    // Achsenbereiche aller Controller einmal ins Log schreiben (flat = Androids eigene Deadzone)
    private fun logRanges() {
        for (id in InputDevice.getDeviceIds()) {
            val dev = InputDevice.getDevice(id) ?: continue
            if (!dev.supportsSource(InputDevice.SOURCE_JOYSTICK)) continue
            for (r in dev.motionRanges) {
                if (r.axis !in axes) continue
                Log.i(TAG, "device=${dev.name} axis=${MotionEvent.axisToString(r.axis)} flat=${r.flat} fuzz=${r.fuzz}")
            }
        }
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (!ev.isFromSource(InputDevice.SOURCE_JOYSTICK) || ev.action != MotionEvent.ACTION_MOVE) {
            return super.dispatchGenericMotionEvent(ev)
        }
        // Android bündelt schnelle Bewegungen, darum auch die Zwischenwerte ausgeben
        for (h in 0 until ev.historySize) {
            Log.i(TAG, line(ev.getHistoricalEventTime(h), axes.map { ev.getHistoricalAxisValue(it, h) }))
        }
        val now = axes.map { ev.getAxisValue(it) }
        Log.i(TAG, line(ev.eventTime, now))
        text.text = String.format(
            Locale.US, "Links  X %+.5f  Y %+.5f\nRechts Z %+.5f  RZ %+.5f",
            now[0], now[1], now[2], now[3]
        )
        return true
    }

    private fun line(time: Long, v: List<Float>) =
        String.format(Locale.US, "t=%d X=%.5f Y=%.5f Z=%.5f RZ=%.5f", time, v[0], v[1], v[2], v[3])

    companion object {
        private const val TAG = "StickTest"
    }
}
