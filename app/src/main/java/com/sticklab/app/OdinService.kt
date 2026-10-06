package com.sticklab.app

import android.os.IBinder
import android.os.Parcel

// Verbindung zum eingebauten Odin-Dienst (PServerBinder).
// Aufrufformat nach ClusterTune (AurelioB) und O2P Tweaks (FeralAI).
object OdinService {

    @Volatile
    private var binder: IBinder? = null

    // Schickt einen Befehl an den Dienst. Ergebnis: true, wenn er angenommen wurde
    fun run(cmd: String): Boolean {
        val b = find() ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeStringArray(arrayOf(cmd, "0"))
            b.transact(0, data, reply, 0)
        } catch (t: Throwable) {
            binder = null
            false
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    // Prüft, ob der Dienst auf diesem Gerät vorhanden ist
    fun isAvailable(): Boolean = find() != null
    private fun find(): IBinder? {
        binder?.let { if (it.isBinderAlive) return it }
        binder = try {
            val sm = Class.forName("android.os.ServiceManager")
            val getService = sm.getDeclaredMethod("getService", String::class.java)
            getService.invoke(null, "PServerBinder") as? IBinder
        } catch (t: Throwable) {
            null
        }
        return binder
    }
}