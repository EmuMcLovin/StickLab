package com.sticklab.app

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

// Wegwerf-Testbildschirm für Phase 1 (A26). Liegt nur in der Debug-Version.
// Verbindet sich mit dem Dienst von com.odin.mapping (über den auch das Odin-Menü
// die Deadzone setzt) und ruft NUR Lese-Nummern auf. Nummern stehen fest im Code,
// nichts wird gesetzt. Seit Test C auch die Schwarze Liste (Aufruf 37).
// Ergebnis im Log (Tag "MappingProbe") und auf dem Bildschirm
class MappingProbeActivity : AppCompatActivity() {

    private lateinit var text: TextView
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val lines = mutableListOf("Verbunden mit $name")
            for ((code, label) in READ_CALLS) {
                for (side in 0..1) {
                    val result = readFloat(service, code, side)
                    lines += "$label ${if (side == 0) "links" else "rechts"}: $result"
                }
            }
            lines += readBlackList(service)
            lines.forEach { Log.i(TAG, it) }
            text.text = lines.joinToString("\n")
            unbind()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.i(TAG, "Verbindung getrennt")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = TextView(this).apply {
            textSize = 22f
            setPadding(48, 48, 48, 48)
            text = "Mapping-Test: verbinde …"
        }
        setContentView(text)

        val intent = Intent(ACTION).setPackage(PACKAGE)
        // Flags 0: nur an den schon laufenden Dienst anhängen, ihn nicht selbst starten
        bound = try {
            bindService(intent, connection, 0)
        } catch (e: SecurityException) {
            Log.e(TAG, "Verbindung verweigert", e)
            false
        }
        Log.i(TAG, "bindService = $bound")
        if (!bound) text.text = "Verbindung nicht möglich (siehe Log)"
    }

    // Ein Lese-Aufruf: Schnittstellen-Kennung + Seite (0 = links, 1 = rechts), Antwort ist eine Zahl
    private fun readFloat(service: IBinder, code: Int, side: Int): String {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(side)
            if (!service.transact(code, data, reply, 0)) return "transact = false"
            reply.readException()
            String.format(Locale.US, "%.4f", reply.readFloat())
        } catch (e: Exception) {
            Log.e(TAG, "Aufruf $code Seite $side fehlgeschlagen", e)
            "Fehler: ${e.javaClass.simpleName}"
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    // Schwarze Liste lesen (Aufruf 37): nur Schnittstellen-Kennung, keine Daten.
    // Antwort ist eine Liste von Gerätenamen, die com.odin.mapping nicht übernimmt
    private fun readBlackList(service: IBinder): List<String> {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            if (!service.transact(GET_BLACK_LIST, data, reply, 0)) return listOf("Schwarze Liste: transact = false")
            reply.readException()
            val names = reply.createStringArrayList()
            when {
                names == null -> listOf("Schwarze Liste: keine Liste (null)")
                names.isEmpty() -> listOf("Schwarze Liste: 0 Namen")
                else -> listOf("Schwarze Liste: ${names.size} Namen") +
                    names.mapIndexed { i, n -> "  ${i + 1}. \"$n\"" }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Aufruf $GET_BLACK_LIST fehlgeschlagen", e)
            listOf("Schwarze Liste: Fehler ${e.javaClass.simpleName}")
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun unbind() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }

    override fun onDestroy() {
        unbind()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MappingProbe"
        private const val PACKAGE = "com.odin.mapping"
        private const val ACTION = "com.ro.mapping.action.MAPPING_SERVICE"
        private const val DESCRIPTOR = "com.ro.mapping.sdk.IServerApi"

        // Nur Lese-Aufrufe (Liste in PROJEKT_DOKU.md, "Code der Odin-Apps")
        private val READ_CALLS = listOf(
            13 to "Deadzone",
            10 to "Sensitivity",
            15 to "Trigger-Deadzone",
        )

        // Schwarze Liste nur lesen (Test C, Besitzer 12.10.2026)
        private const val GET_BLACK_LIST = 37
    }
}
