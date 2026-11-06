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

// Wegwerf-Testbildschirm für Test C (Schwarze Liste von com.odin.mapping). Nur Debug-Version.
// Ohne Extra "case" wird nur gelesen (Aufruf 37). Mit "add_testpad" bzw. "remove_testpad"
// wird genau der feste Name TEST_NAME eingetragen bzw. entfernt (Aufruf 38), sonst nichts.
// Sicherungen: vorher lesen; eintragen nur, wenn der Name fehlt (38 prüft selbst nicht auf
// Doppel); entfernen nur, wenn er da ist; danach nachlesen und prüfen, dass sich nur dieser
// Name geändert hat. Läuft höchstens einmal pro Start. Beim Verlassen wird nichts gesetzt.
// Rückweg: "remove_testpad". Nie die App-Daten von com.odin.mapping löschen
// Ergebnis im Log (Tag "BlackListTest") und auf dem Bildschirm
class BlackListTestActivity : AppCompatActivity() {

    private lateinit var text: TextView
    private var bound = false
    private var done = false
    private var caseName: String? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            // Verbindet sich der Dienst neu (z. B. nach Neustart von com.odin.mapping), nichts wiederholen
            if (done) return
            done = true
            run(service)
            unbind()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            log("Verbindung getrennt")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = TextView(this).apply {
            textSize = 22f
            setPadding(48, 48, 48, 48)
            text = "Schwarze-Liste-Test: verbinde …"
        }
        setContentView(text)

        // Nach einem Neuaufbau des Fensters (z. B. Drehen) nur noch lesen, nichts erneut ausführen
        caseName = if (savedInstanceState == null) intent.getStringExtra(EXTRA_CASE) else null
        if (caseName != null && caseName !in CASES) {
            show("Unbekannter Testfall: $caseName, es wird nichts geändert")
            log("Unbekannter Testfall: $caseName")
            return
        }
        log("Testfall ${caseName ?: "nur lesen"}")

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

    private fun run(service: IBinder) {
        val before = readList(service)
        if (before == null) {
            report("Liste nicht lesbar, es wird nichts geändert")
            return
        }
        log("vorher: ${format(before)}")

        val mode = when (caseName) {
            null -> {
                report("Nur gelesen: ${format(before)}")
                return
            }
            CASE_ADD -> {
                if (TEST_NAME in before) {
                    report("\"$TEST_NAME\" steht schon drauf, nichts eingetragen\nListe: ${format(before)}")
                    return
                }
                MODE_ADD
            }
            else -> {
                if (TEST_NAME !in before) {
                    report("\"$TEST_NAME\" steht nicht drauf, nichts entfernt\nListe: ${format(before)}")
                    return
                }
                MODE_REMOVE
            }
        }

        if (!writeName(service, mode)) {
            report("Aufruf $SET_BLACK_LIST fehlgeschlagen (siehe Log)\nvorher: ${format(before)}")
            return
        }

        val after = readList(service)
        val expected = if (mode == MODE_ADD) before + TEST_NAME else before - TEST_NAME
        val ok = after == expected
        report(
            (if (mode == MODE_ADD) "Eingetragen" else "Entfernt") + ": \"$TEST_NAME\"\n" +
                "vorher: ${format(before)}\nnachher: ${after?.let { format(it) } ?: "nicht lesbar"}\n" +
                (if (ok) "Prüfung OK" else "ACHTUNG: unerwartete Liste, bitte melden")
        )
    }

    // Aufruf 37: nur Schnittstellen-Kennung, Antwort ist die Namensliste
    private fun readList(service: IBinder): List<String>? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            if (!service.transact(GET_BLACK_LIST, data, reply, 0)) {
                log("Aufruf $GET_BLACK_LIST: transact = false")
                return null
            }
            reply.readException()
            reply.createStringArrayList()
        } catch (e: Exception) {
            Log.e(TAG, "Aufruf $GET_BLACK_LIST fehlgeschlagen", e)
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    // Aufruf 38: Modus (1 = eintragen, sonst entfernen), dann der feste Name
    private fun writeName(service: IBinder, mode: Int): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(mode)
            data.writeString(TEST_NAME)
            if (!service.transact(SET_BLACK_LIST, data, reply, 0)) {
                log("Aufruf $SET_BLACK_LIST: transact = false")
                return false
            }
            reply.readException()
            log("Aufruf $SET_BLACK_LIST Modus $mode \"$TEST_NAME\" gesendet")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Aufruf $SET_BLACK_LIST fehlgeschlagen", e)
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun format(list: List<String>) =
        if (list.isEmpty()) "leer" else list.joinToString(", ") { "\"$it\"" }

    private fun report(msg: String) {
        msg.lines().forEach { log(it) }
        show(msg)
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

    private fun show(msg: String) {
        text.text = msg
    }

    private fun log(msg: String) {
        Log.i(TAG, msg)
    }

    companion object {
        private const val TAG = "BlackListTest"
        private const val PACKAGE = "com.odin.mapping"
        private const val ACTION = "com.ro.mapping.action.MAPPING_SERVICE"
        private const val DESCRIPTOR = "com.ro.mapping.sdk.IServerApi"
        private const val EXTRA_CASE = "case"

        // Aufrufnummern siehe PROJEKT_DOKU.md, "Übernahme fremder Controller"
        private const val GET_BLACK_LIST = 37
        private const val SET_BLACK_LIST = 38
        private const val MODE_ADD = 1
        private const val MODE_REMOVE = 0

        // Einziger Name, der eingetragen werden darf: der des virtuellen Controllers
        private const val TEST_NAME = MirrorService.PAD_NAME

        private const val CASE_ADD = "add_testpad"
        private const val CASE_REMOVE = "remove_testpad"
        private val CASES = setOf(CASE_ADD, CASE_REMOVE)
    }
}
