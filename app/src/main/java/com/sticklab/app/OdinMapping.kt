package com.sticklab.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.util.Log

// Einzige Stelle der App für den "Live-Weg": den Dienst ApiService der AYN-App com.odin.mapping.
// Über ihn setzt auch das Odin-Menü die Deadzone, die dann sofort wirkt (PROJEKT_DOKU A24, A26).
// Der Dienst kann mehr, als StickLab braucht.
// Darum sind hier nur die Aufrufe 9, 10, 12–15 und die Schwarze Liste erlaubt (Entscheidung E9), und jeder Wert
// wird vor dem Setzen begrenzt. Ist der Dienst nicht erreichbar, liefern alle Funktionen null
// bzw. false; der Aufrufer fällt dann auf "wirkt nach Neustart" zurück (Entscheidungen E7, E8)
class OdinMapping(private val context: Context) {

    // Seite im Aufruf: 0 = linker Stick bzw. L2, 1 = rechter Stick bzw. R2 (Setz-Tests 11.10.2026)
    enum class Side(val index: Int) { LEFT(0), RIGHT(1) }

    // Wird auf dem Haupt-Thread aufgerufen, sobald die Verbindung steht oder wegfällt
    var onConnectionChanged: ((connected: Boolean) -> Unit)? = null

    private var service: IBinder? = null
    private var bindRequested = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = binder
            onConnectionChanged?.invoke(true)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            onConnectionChanged?.invoke(false)
        }
    }

    // true, wenn der Live-Weg gerade benutzbar ist
    val isConnected: Boolean get() = service?.isBinderAlive == true

    // Verbindet sich mit dem schon laufenden Dienst (Flags 0: ihn nicht selbst starten).
    // Die Verbindung steht erst, wenn onConnectionChanged(true) kommt.
    // Ergebnis false: Dienst nicht gefunden oder Verbindung verweigert
    fun connect(): Boolean {
        // Auf einem unbekannten Gerät gibt es den Live-Weg nicht (Plan Regel 7, DeviceCheck)
        if (DeviceCheck.current == DeviceSupport.UNKNOWN_DEVICE) return false
        if (bindRequested) return true
        bindRequested = true
        return try {
            context.bindService(Intent(ACTION).setPackage(PACKAGE), connection, 0)
        } catch (e: SecurityException) {
            Log.w(TAG, "Verbindung verweigert", e)
            false
        }
    }

    // Löst die Verbindung. Laut Android-Doku auch nötig, wenn bindService false lieferte
    fun disconnect() {
        if (!bindRequested) return
        bindRequested = false
        service = null
        try {
            context.unbindService(connection)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "War nicht verbunden", e)
        }
    }

    // Wirksame Stick-Deadzone lesen (Aufruf 13) und live setzen (Aufruf 12)
    fun getDeadzone(side: Side): Float? = readFloat(GET_DEADZONE, side)
    fun setDeadzone(side: Side, value: Float): Boolean =
        writeFloat(SET_DEADZONE, side, value, SettingLimits.DEADZONE)

    // Sensitivity lesen (10) und setzen (9). Achtung: bleibt nach Neustart erhalten (kein Rückweg per Neustart)
    fun getSensitivity(side: Side): Float? = readFloat(GET_SENSITIVITY, side)
    fun setSensitivity(side: Side, value: Float): Boolean =
        writeFloat(SET_SENSITIVITY, side, value, SettingLimits.SENSITIVITY)

    // Trigger-Deadzone L2/R2 lesen (15) und setzen (14)
    fun getTriggerDeadzone(side: Side): Float? = readFloat(GET_TRIGGER_DEADZONE, side)
    fun setTriggerDeadzone(side: Side, value: Float): Boolean =
        writeFloat(SET_TRIGGER_DEADZONE, side, value, SettingLimits.TRIGGER_DEADZONE)

    // Schwarze Liste von com.odin.mapping (Phase 6e, Besitzer 05.11.2026): Steht der virtuelle Controller nicht darauf, übernimmt
    // ihn der AYN-Dienst, und die Eingaben kommen nirgends an (gemessen Test C Stufe 1b). Nur dieser eine feste Name. Beide Wege
    // lesen vorher und danach, denn der Dienst prüft nicht auf Doppel und entfernt immer nur den ersten Treffer (gelesen 12.10.2026).
    // Ergebnis true = der Name steht danach so da, wie gewünscht
    fun allowVirtualPad(): Boolean = setVirtualPadListed(wanted = true)

    fun forbidVirtualPad(): Boolean = setVirtualPadListed(wanted = false)

    private fun setVirtualPadListed(wanted: Boolean): Boolean {
        val before = readBlackList() ?: return false
        if ((VIRTUAL_PAD_NAME in before) == wanted) return true
        val sent = call(SET_BLACK_LIST, writeArgs = {
            it.writeInt(if (wanted) BLACK_LIST_ADD else BLACK_LIST_REMOVE)
            it.writeString(VIRTUAL_PAD_NAME)
        })
        return sent && readBlackList()?.contains(VIRTUAL_PAD_NAME) == wanted
    }

    private fun readBlackList(): List<String>? {
        var result: List<String>? = null
        call(GET_BLACK_LIST, writeArgs = {}, readReply = { result = it.createStringArrayList() })
        return result
    }

    // Lese-Aufruf: Seite hinein, Zahl heraus
    private fun readFloat(code: Int, side: Side): Float? {
        var result: Float? = null
        call(code, writeArgs = { it.writeInt(side.index) }, readReply = { result = it.readFloat() })
        return result
    }

    // Setz-Aufruf: Wert vorher auf den erlaubten Bereich begrenzen, dann Seite + Wert hinein
    private fun writeFloat(code: Int, side: Side, value: Float, range: ClosedFloatingPointRange<Float>): Boolean {
        val safe = SettingLimits.limit(value, range) ?: return false
        return call(code, writeArgs = {
            it.writeInt(side.index)
            it.writeFloat(safe)
        })
    }

    // Ein Aufruf beim Dienst. Nur Nummern aus ALLOWED_CALLS. Fehler werden geloggt, nie weitergeworfen
    private fun call(code: Int, writeArgs: (Parcel) -> Unit, readReply: (Parcel) -> Unit = {}): Boolean {
        if (code !in ALLOWED_CALLS) return false
        val binder = service ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            writeArgs(data)
            if (binder.transact(code, data, reply, 0)) {
                reply.readException()
                readReply(reply)
                true
            } else {
                Log.w(TAG, "Aufruf $code: transact = false")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Aufruf $code fehlgeschlagen", e)
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    companion object {
        private const val TAG = "OdinMapping"

        // Adresse des Dienstes (aus dem Code von OdinSettings gelesen, PROJEKT_DOKU "Code der Odin-Apps")
        private const val PACKAGE = "com.odin.mapping"
        private const val ACTION = "com.ro.mapping.action.MAPPING_SERVICE"
        private const val DESCRIPTOR = "com.ro.mapping.sdk.IServerApi"

        // Aufrufnummern (PROJEKT_DOKU "Code der Odin-Apps", alle am 10./11.10.2026 am Odin erprobt)
        private const val SET_SENSITIVITY = 9
        private const val GET_SENSITIVITY = 10
        private const val SET_DEADZONE = 12
        private const val GET_DEADZONE = 13
        private const val SET_TRIGGER_DEADZONE = 14
        private const val GET_TRIGGER_DEADZONE = 15

        // Schwarze Liste (am Odin erprobt 12.10.2026). 1 = eintragen, sonst entfernen
        private const val GET_BLACK_LIST = 37
        private const val SET_BLACK_LIST = 38
        private const val BLACK_LIST_ADD = 1
        private const val BLACK_LIST_REMOVE = 0

        // Name des virtuellen Controllers (sticklab_mirror.c VIRT_NAME), der einzige, den StickLab einträgt
        private const val VIRTUAL_PAD_NAME = MirrorService.PAD_NAME

        // Nur diese Nummern dürfen je geschickt werden (E9, 37/38 seit Phase 6e)
        private val ALLOWED_CALLS = setOf(
            SET_SENSITIVITY, GET_SENSITIVITY,
            SET_DEADZONE, GET_DEADZONE,
            SET_TRIGGER_DEADZONE, GET_TRIGGER_DEADZONE,
            GET_BLACK_LIST, SET_BLACK_LIST,
        )
    }
}
