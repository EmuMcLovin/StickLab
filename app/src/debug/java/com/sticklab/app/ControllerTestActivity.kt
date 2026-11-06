package com.sticklab.app

import android.hardware.input.InputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

// Wegwerf-Testbildschirm für Test C Stufe 4. Liegt nur in der Debug-Version.
// Zeigt, von welchem Controller (Gerät, Spielernummer) Sticks, Trigger, D-Pad und Tasten
// in einer normalen App ankommen, und schreibt alles ins Log (Tag "ControllerTest").
// Liest nur mit: setzt nichts, kein PServerBinder, keine Aufrufe an com.odin.mapping
class ControllerTestActivity : AppCompatActivity(), InputManager.InputDeviceListener {

    private lateinit var text: TextView
    private lateinit var inputManager: InputManager

    private val axes = intArrayOf(
        MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
        MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y
    )

    // Zähler je Gerät (id → Bewegungen, Tasten), für die Zusammenfassung beim Verlassen
    private val motionCount = HashMap<Int, Int>()
    private val keyCount = HashMap<Int, Int>()
    private var lastKey = "-"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bildschirm bleibt während der Messung an
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        text = TextView(this).apply {
            textSize = 20f
            setPadding(48, 48, 48, 48)
        }
        setContentView(text)
        inputManager = getSystemService(InputManager::class.java)
        inputManager.registerInputDeviceListener(this, Handler(Looper.getMainLooper()))
        logDevices("Start")
        text.text = "Controller-Test: Sticks bewegen, Tasten drücken\n\n${deviceList()}"
    }

    override fun onDestroy() {
        inputManager.unregisterInputDeviceListener(this)
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        val ids = (motionCount.keys + keyCount.keys).toSortedSet()
        for (id in ids) {
            Log.i(TAG, "Zusammenfassung id=$id ${InputDevice.getDevice(id)?.name ?: "(weg)"}: " +
                "Bewegungen=${motionCount[id] ?: 0} Tasten=${keyCount[id] ?: 0}")
        }
    }

    // Geräte-Änderungen: z. B. "StickLab Controller" kommt dazu oder verschwindet
    override fun onInputDeviceAdded(deviceId: Int) = logDevices("hinzugefügt id=$deviceId")
    override fun onInputDeviceRemoved(deviceId: Int) = logDevices("entfernt id=$deviceId")
    override fun onInputDeviceChanged(deviceId: Int) = logDevices("geändert id=$deviceId")

    private fun isController(dev: InputDevice) =
        dev.supportsSource(InputDevice.SOURCE_GAMEPAD) || dev.supportsSource(InputDevice.SOURCE_JOYSTICK)

    // Alle Controller ins Log: id, Name, Kennung, Spielernummer, Quellen, extern
    private fun logDevices(reason: String) {
        Log.i(TAG, "Geräte ($reason):")
        for (id in InputDevice.getDeviceIds()) {
            val dev = InputDevice.getDevice(id) ?: continue
            if (!isController(dev)) continue
            Log.i(TAG, String.format(
                Locale.US, "  id=%d name=\"%s\" vendor=%04x product=%04x spieler=%d sources=0x%08x extern=%b",
                dev.id, dev.name, dev.vendorId, dev.productId, dev.controllerNumber, dev.sources, dev.isExternal
            ))
        }
        if (::text.isInitialized) text.text = "Geräte geändert ($reason)\n\n${deviceList()}"
    }

    private fun deviceList(): String {
        val sb = StringBuilder("Controller:\n")
        for (id in InputDevice.getDeviceIds()) {
            val dev = InputDevice.getDevice(id) ?: continue
            if (!isController(dev)) continue
            sb.append("  id ${dev.id}  ${dev.name}  Spieler ${dev.controllerNumber}\n")
        }
        return sb.toString()
    }

    private fun who(ev: android.view.InputEvent): String {
        val dev = ev.device
        return "id=${ev.deviceId} name=\"${dev?.name}\" spieler=${dev?.controllerNumber}"
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (!ev.isFromSource(InputDevice.SOURCE_JOYSTICK) || ev.action != MotionEvent.ACTION_MOVE) {
            return super.dispatchGenericMotionEvent(ev)
        }
        motionCount[ev.deviceId] = (motionCount[ev.deviceId] ?: 0) + 1
        // Android bündelt schnelle Bewegungen, darum auch die Zwischenwerte ausgeben
        for (h in 0 until ev.historySize) {
            Log.i(TAG, "BEW ${who(ev)} " + values(ev.getHistoricalEventTime(h), axes.map { ev.getHistoricalAxisValue(it, h) }))
        }
        val now = axes.map { ev.getAxisValue(it) }
        Log.i(TAG, "BEW ${who(ev)} " + values(ev.eventTime, now))
        text.text = String.format(
            Locale.US,
            "Letztes Gerät: %s (id %d)\nSpieler %d\n\nLinks  X %+.3f  Y %+.3f\nRechts Z %+.3f  RZ %+.3f\n" +
                "L2 %.3f  R2 %.3f\nD-Pad X %+.0f  Y %+.0f\n\nLetzte Taste: %s\n\n%s",
            ev.device?.name, ev.deviceId, ev.device?.controllerNumber ?: -1,
            now[0], now[1], now[2], now[3], now[4], now[5], now[6], now[7], lastKey, deviceList()
        )
        return true
    }

    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        val action = when (ev.action) {
            KeyEvent.ACTION_DOWN -> "DOWN"
            KeyEvent.ACTION_UP -> "UP"
            else -> "action=${ev.action}"
        }
        keyCount[ev.deviceId] = (keyCount[ev.deviceId] ?: 0) + 1
        lastKey = "${KeyEvent.keyCodeToString(ev.keyCode)} $action (${ev.device?.name})"
        Log.i(TAG, "TASTE ${who(ev)} ${KeyEvent.keyCodeToString(ev.keyCode)} scan=0x${Integer.toHexString(ev.scanCode)} " +
            "$action repeat=${ev.repeatCount} t=${ev.eventTime}")
        text.text = "Letzte Taste: $lastKey\nSpieler ${ev.device?.controllerNumber}\n\n${deviceList()}"
        // Gamepad-Tasten (A, B, X, Y, L1 …) hier behalten: sonst macht Android aus B ein "Zurück"
        // und der Testbildschirm würde mitten in der Messung zugehen. Alle anderen Tasten
        // (Back, Lautstärke, MOVE_HOME …) gehen normal an Android weiter
        if (KeyEvent.isGamepadButton(ev.keyCode)) return true
        return super.dispatchKeyEvent(ev)
    }

    private fun values(time: Long, v: List<Float>) = String.format(
        Locale.US, "t=%d X=%.5f Y=%.5f Z=%.5f RZ=%.5f LT=%.5f RT=%.5f HX=%.0f HY=%.0f",
        time, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]
    )

    companion object {
        private const val TAG = "ControllerTest"
    }
}
