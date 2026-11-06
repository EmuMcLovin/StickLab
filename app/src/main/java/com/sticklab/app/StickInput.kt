package com.sticklab.app

import android.view.InputDevice
import android.view.MotionEvent

// Liest aus einer Bewegung des Controllers, wo beide Sticks stehen und wie weit L2/R2 gedrückt sind
// (Phase 3a Live-Anzeige). Liest nur, ändert nichts am Odin.
// Achsen (gesichert, PROJEKT_DOKU Abschnitt 2 "Tastenbelegung" und Test A):
// linker Stick X/Y, rechter Stick Z/RZ, L2 = LTRIGGER (Kernel ABS_BRAKE), R2 = RTRIGGER (Kernel ABS_GAS).
// Ob die App die Trigger genau unter LTRIGGER/RTRIGGER bekommt, ist noch ungeprüft (Test 3a)
object StickInput {

    // Die neueste Stellung einer Controller-Bewegung, oder null, wenn das Ereignis nicht vom Controller kommt
    // bzw. keine Bewegung ist (dann lässt die Anzeige ihren alten Stand stehen)
    fun read(event: MotionEvent): LiveInput? {
        if (!isStickMove(event)) return null
        return inputOf { axis -> event.getAxisValue(axis) }
    }

    // Alle Stellungen einer Bewegung mit ihrer Zeit, die älteste zuerst, die neueste zuletzt.
    // Android fasst schnelle Bewegungen pro Bildschirmbild zu einem Ereignis zusammen; die Zwischenwerte
    // stecken als "History" darin. Für die Messung (3c) zählen auch sie: In Lauf 2 (17.10.2026) fehlte sonst
    // ein kurzer Spitzenwert (getevent 97,6 %, App 97 %). Für die Anzeige reicht read()
    fun readAll(event: MotionEvent): List<TimedInput> {
        if (!isStickMove(event)) return emptyList()
        val older = (0 until event.historySize).map { position ->
            TimedInput(event.getHistoricalEventTime(position), inputOf { axis -> event.getHistoricalAxisValue(axis, position) })
        }
        return older + TimedInput(event.eventTime, inputOf { axis -> event.getAxisValue(axis) })
    }

    // Bewegt Android mit dieser Meldung die Auswahl nicht mehr? Für die Schutzregel beim Verschlucken
    // (InputSwallowGate, R76): Android bewegt sie mit dem linken Stick und dem Steuerkreuz (HAT)
    fun isNavigationNeutral(event: MotionEvent): Boolean = InputSwallowGate.isNeutral(
        x = event.getAxisValue(MotionEvent.AXIS_X),
        y = event.getAxisValue(MotionEvent.AXIS_Y),
        hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X),
        hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y),
    )

    // Ist das Steuerkreuz (HAT) gedrückt? Der Reiter Sticks lässt es durch, obwohl er die Sticks verschluckt
    fun isDpadPressed(event: MotionEvent): Boolean =
        event.getAxisValue(MotionEvent.AXIS_HAT_X) != 0f || event.getAxisValue(MotionEvent.AXIS_HAT_Y) != 0f

    // Nur Bewegungen des Controllers (nicht Touch, nicht Tasten)
    private fun isStickMove(event: MotionEvent): Boolean =
        event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.actionMasked == MotionEvent.ACTION_MOVE

    // Beide Sticks und Trigger aus einer Achsen-Quelle (neuester Wert oder ein Zwischenwert)
    private fun inputOf(axisValue: (Int) -> Float) = LiveInput(
        left = StickPosition.fromAxes(axisValue(MotionEvent.AXIS_X), axisValue(MotionEvent.AXIS_Y)),
        right = StickPosition.fromAxes(axisValue(MotionEvent.AXIS_Z), axisValue(MotionEvent.AXIS_RZ)),
        l2Percent = TriggerPressure.percentOf(axisValue(MotionEvent.AXIS_LTRIGGER)),
        r2Percent = TriggerPressure.percentOf(axisValue(MotionEvent.AXIS_RTRIGGER)),
    )
}

// Eine Stellung mit ihrer Zeit (ms, SystemClock.uptimeMillis wie MotionEvent.eventTime)
data class TimedInput(val timeMs: Long, val input: LiveInput)
