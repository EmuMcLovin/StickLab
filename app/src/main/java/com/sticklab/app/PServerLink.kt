package com.sticklab.app

import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock

// Die einzige Leitung der App zum eingebauten Odin-Dienst PServerBinder (Plan Phase 2, Entscheidung P-A,
// 16.10.2026). PServerBinder führt jeden Befehl als root aus. Darum bauen Befehle nur zwei Stellen:
// OdinService (echte App) und MirrorControl (nur Debug-Version), beide nur aus festen Teilen und geprüften
// Werten, nie aus freiem Text (Plan Regel 7, Risiko R62). Die alten Debug-Tests schicken nur ihre festen Fälle.
// Aufrufformat nach ClusterTune (AurelioB) und O2P Tweaks (FeralAI): Code 0, Textfeld [Befehl, "0"].
// Bewusst ohne Sperre: Zwei Aufrufe dürfen gleichzeitig laufen (Stufe 2b, 12.10.2026), und ein langer Befehl
// (pservice wartet bis zu dessen Ende, A28) soll z. B. die LED-Befehle des Ambilights nicht aufhalten
internal object PServerLink {

    // pservice führt nur ca. die ersten 183–256 Zeichen eines Befehls aus (A38, R14), darum mit Abstand darunter
    const val MAX_COMMAND = 180

    // So viele Bytes der Antwort gibt es als Hex-Text fürs Protokoll (wie im PServerBinder-Test seit 12.10.2026)
    private const val REPLY_PREVIEW_BYTES = 64

    // Ergebnis eines Aufrufs
    sealed interface SendResult {
        // Dienst auf diesem Gerät nicht gefunden, nichts geschickt
        data object NotFound : SendResult

        // Befehl länger als MAX_COMMAND, nichts geschickt
        data class TooLong(val length: Int) : SendResult

        // Fehler beim Schicken (z. B. Dienst gerade neu gestartet)
        data class Failed(val error: Throwable) : SendResult

        // Geschickt. accepted = PServerBinder hat den Aufruf angenommen (heißt nicht: gelungen, A11).
        // Die Antwort wird nicht ausgewertet, nur Größe und Anfang fürs Protokoll geliefert
        data class Sent(
            val accepted: Boolean,
            val durationMs: Long,
            val replySize: Int,
            val replyHex: String,
        ) : SendResult
    }

    @Volatile
    private var binder: IBinder? = null

    // Prüft, ob der Dienst auf diesem Gerät vorhanden ist (schickt nichts)
    fun isAvailable(): Boolean = find() != null

    // Schickt einen Befehl und wartet auf die Antwort. Nicht im Bildschirm-Thread mit langen Befehlen aufrufen
    fun send(command: String): SendResult {
        if (command.length > MAX_COMMAND) return SendResult.TooLong(command.length)
        val service = find() ?: return SendResult.NotFound
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeStringArray(arrayOf(command, "0"))
            val start = SystemClock.elapsedRealtime()
            val accepted = service.transact(0, data, reply, 0)
            val durationMs = SystemClock.elapsedRealtime() - start
            SendResult.Sent(accepted, durationMs, reply.dataSize(), replyPreview(reply))
        } catch (t: Throwable) {
            // Beim nächsten Aufruf den Dienst neu suchen
            binder = null
            SendResult.Failed(t)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    // Anfang der Antwort als Hex-Text, nur zum Anzeigen (A11)
    private fun replyPreview(reply: Parcel): String = try {
        reply.marshall().take(REPLY_PREVIEW_BYTES).joinToString(" ") { "%02x".format(it) }
    } catch (t: Throwable) {
        "(nicht lesbar: ${t.javaClass.simpleName})"
    }

    // Holt den Dienst über die versteckte Android-Klasse ServiceManager und merkt ihn sich
    private fun find(): IBinder? {
        // Auf einem unbekannten Gerät gibt es diese Leitung nicht (Plan Regel 7, DeviceCheck)
        if (DeviceCheck.current == DeviceSupport.UNKNOWN_DEVICE) return null
        binder?.let { if (it.isBinderAlive) return it }
        binder = try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getDeclaredMethod("getService", String::class.java)
            getService.invoke(null, "PServerBinder") as? IBinder
        } catch (t: Throwable) {
            null
        }
        return binder
    }
}
