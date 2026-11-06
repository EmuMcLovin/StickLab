package com.sticklab.app

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

// Wegwerf-Testbildschirm für Test C Stufe 2. Nur Debug-Version.
// Ohne Extra "case" wird PServerBinder nur gesucht, es wird nichts geschickt.
// Mit einem Namen aus CASES wird genau einmal der dazu fest eingetragene Befehl geschickt
// und gemessen, wie lange transact dauert (Teil 1, 12.10.2026: wartet bis zum Ende des Befehls).
//   "sleep3":            Teil 1 (A28), wartet 3 s
//   "uinput_testpad":    Teil 2 (A5, A18), legt per eingebautem uinput das Testgerät aus
//                        /data/local/tmp/sticklab_c2.json an (Datei legt ADB vorher hin),
//                        hart begrenzt durch timeout (nach 20 s TERM, nach 22 s KILL)
//   "uinput_testpad_bg": Teil 2b, wie Teil 2, aber im Hintergrund (&) mit eigener Datei
//                        sticklab_c2b.json. Ein- und Ausgabe umgeleitet, damit nichts
//                        die Verbindung zu pservice offen hält. Kommt transact sofort zurück?
//   "sleep3_parallel":   Teil 2b, zweimal "sleep 3" gleichzeitig. Je ca. 3 s = pservice
//                        arbeitet parallel, einmal ca. 6 s = der zweite Aufruf muss warten
//   "hide_event8_5s":    Stufe 4.5a (PROJEKT_DOKU Abschnitt 11), im Hintergrund: nach 3 s die
//                        Gerätedatei /dev/input/event8 löschen (nur wenn Name, 2020/0112 und
//                        13:72 stimmen), nach 5 s genau gleich wieder anlegen. Kein Abgreifen,
//                        kein virtuelles Gerät. Protokoll /data/local/tmp/sticklab_45a_log.txt.
//                        Der Ablauf ist zu lang für einen Befehl (R14) und steht deshalb als
//                        Skript im privaten Ordner der App (Weg A, Besitzer 12.10.2026)
//   "hide_event8_mirror": Stufe 4.5b, Skript s45b.sh im Hintergrund: nur wenn sticklab_mirror
//                        (Modus mirror_hide, über ADB gestartet) läuft und Name, 2020/0112, 13:72
//                        stimmen, /dev/input/event8 löschen. Dann warten, bis sticklab_mirror
//                        beendet ist (höchstens 30 s), danach die Datei genau gleich wieder
//                        anlegen. Protokoll /data/local/tmp/sticklab_45b_log.txt
//   "probe_multiline":   A38, zweizeiliger Befehl, jede Zeile schreibt nur ein Wort (Z1, Z2) in
//                        /data/local/tmp/sticklab_probe_ml.txt. Kommt Z2 an, gehen mehrere Zeilen
//                        (gemessen 12.10.2026: ja). Der Gegenstück-Fall "probe_long" (842 Zeichen)
//                        ist entfernt: pservice führt nur ca. die ersten 183–256 Zeichen aus (A38)
// Aufrufformat wie OdinService (Code 0, Textfeld [Befehl, "0"]). Kein Befehl aus freiem Text.
// Kein Befehl länger als PServerLink.MAX_COMMAND (R14), sonst wird nichts geschickt. Geschickt wird über PServerLink.
// Läuft höchstens einmal pro Start, im Hintergrund (nicht im Bildschirm-Thread)
// Ergebnis im Log (Tag "PServerSleep") und auf dem Bildschirm
class PServerSleepTestActivity : AppCompatActivity() {

    private lateinit var text: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = TextView(this).apply {
            textSize = 22f
            setPadding(48, 48, 48, 48)
            text = "PServerBinder-Test: suche Dienst …"
        }
        setContentView(text)

        // Nach einem Neuaufbau des Fensters (z. B. Drehen) nichts erneut schicken
        val caseName = if (savedInstanceState == null) intent.getStringExtra(EXTRA_CASE) else null
        if (caseName != null && caseName !in CASES) {
            report("Unbekannter Testfall: $caseName, es wird nichts geschickt")
            return
        }
        log("Testfall ${caseName ?: "nur suchen"}")

        if (!PServerLink.isAvailable()) {
            report("PServerBinder nicht gefunden, es wird nichts geschickt")
            return
        }
        if (caseName == null) {
            report("PServerBinder gefunden, nichts geschickt")
            return
        }

        val case = CASES.getValue(caseName)
        if (case.command.length > PServerLink.MAX_COMMAND) {
            report("Befehl hat ${case.command.length} Zeichen (erlaubt ${PServerLink.MAX_COMMAND}), es wird nichts geschickt")
            return
        }
        if (case.script != null && !writeScript(case.script)) {
            report("Skript nicht geschrieben, es wird nichts geschickt")
            return
        }
        show("Schicke ${case.parallel}× \"${case.command}\" …")
        for (i in 1..case.parallel) {
            val label = if (case.parallel == 1) caseName else "$caseName #$i"
            Thread { send(label, case.command) }.start()
        }
    }

    // Schickt den festen Befehl über PServerLink, Ergebnis auf den Bildschirm und ins Log
    private fun send(caseName: String, command: String) {
        log("sende \"$command\"")
        when (val result = PServerLink.send(command)) {
            is PServerLink.SendResult.Sent -> report(
                "$caseName: transact = ${result.accepted} nach ${result.durationMs} ms\n" +
                    "Antwort: ${result.replySize} Bytes ${result.replyHex}"
            )
            is PServerLink.SendResult.Failed -> {
                Log.e(TAG, "transact fehlgeschlagen", result.error)
                report("transact fehlgeschlagen: ${result.error.javaClass.simpleName} (siehe Log)")
            }
            is PServerLink.SendResult.NotFound -> report("PServerBinder nicht gefunden, es wird nichts geschickt")
            is PServerLink.SendResult.TooLong ->
                report("Befehl hat ${result.length} Zeichen (erlaubt ${PServerLink.MAX_COMMAND}), es wird nichts geschickt")
        }
    }

    // Schreibt das feste Skript frisch in den privaten Ordner (nur App und root kommen hinein)
    // und liest es zurück. Weicht der Pfad oder der Inhalt ab, wird nichts geschickt
    private fun writeScript(script: Script): Boolean = try {
        val file = File(filesDir, script.name)
        if (file.absolutePath != script.path) {
            log("Skript-Pfad unerwartet: ${file.absolutePath}")
            false
        } else {
            file.writeText(script.content)
            val same = file.readText() == script.content
            log("Skript geschrieben: ${file.absolutePath}, ${script.content.length} Zeichen, gleich = $same")
            same
        }
    } catch (t: Throwable) {
        Log.e(TAG, "Skript schreiben fehlgeschlagen", t)
        false
    }

    // Hängt an, statt zu ersetzen, damit bei zwei Aufrufen beide Ergebnisse sichtbar bleiben
    private fun report(msg: String) {
        msg.lines().forEach { log(it) }
        runOnUiThread { text.append("\n$msg") }
    }

    private fun show(msg: String) {
        text.text = msg
    }

    private fun log(msg: String) {
        Log.i(TAG, msg)
    }

    companion object {
        private const val TAG = "PServerSleep"
        private const val EXTRA_CASE = "case"
        // Privater Ordner der Debug-App (geprüft 12.10.2026 mit run-as). Ordner darüber drwx------
        private const val SCRIPT_DIR = "/data/user/0/com.sticklab.app.debug/files"
        private const val S45A_PATH = "$SCRIPT_DIR/s45a.sh"
        private const val S45B_PATH = "$SCRIPT_DIR/s45b.sh"

        // Festes Skript: wird vor dem Senden genau so nach path geschrieben
        internal class Script(val name: String, val content: String) {
            val path get() = "$SCRIPT_DIR/$name"
        }

        // parallel = wie viele gleiche Aufrufe gleichzeitig (je ein eigener Thread)
        // script = wird vor dem Senden geschrieben (nur wenn gesetzt)
        internal class TestCase(val command: String, val parallel: Int = 1, val script: Script? = null)

        // Stufe 4.5a. Läuft im Hintergrund (ca. 9 s), endet von selbst, keine Schleife.
        // pruef: ist /sys/class/input/event8 noch der eingebaute Controller? Sonst wird nichts
        // gelöscht bzw. nichts angelegt. Neu anlegen mit genau dem Stand vorher (gelesen 12.10.2026):
        // Zeichengerät 13:72, root:input, 0666, SELinux input_device. Ersatzweg uevent nur, wenn
        // die Datei danach immer noch fehlt
        private val HIDE_EVENT8_5S_SCRIPT = listOf(
            "sleep 3",
            "S=/sys/class/input/event8; N=/dev/input/event8",
            "pruef() { [ \"\$(cat \$S/device/name)\" = \"Xbox Wireless Controller\" ] && " +
                "[ \"\$(cat \$S/device/id/vendor)\" = 2020 ] && " +
                "[ \"\$(cat \$S/device/id/product)\" = 0112 ] && " +
                "[ \"\$(cat \$S/dev)\" = 13:72 ]; }",
            "stand() { echo \"\$1 \$(date +%T.%N) \$(ls -lZ \$N 2>&1)\"; }",
            "if ! pruef || [ ! -c \$N ]; then stand ABBRUCH_nichts_geloescht; exit 1; fi",
            "stand vorher",
            "rm \$N; echo \"rm rc=\$? \$(date +%T.%N)\"",
            "sleep 5",
            "if [ -e \$N ]; then stand schon_wieder_da",
            "elif pruef; then mknod -Z u:object_r:input_device:s0 -m 0666 \$N c 13 72; " +
                "echo \"mknod rc=\$?\"; chown root:input \$N; chmod 0666 \$N; stand neu_angelegt",
            "else stand sys_geaendert_nichts_angelegt; fi",
            "if [ ! -c \$N ] && [ -e \$S/uevent ]; then echo add > \$S/uevent; sleep 1; stand nach_uevent; fi",
            "stand ende",
        ).joinToString("\n", postfix = "\n")

        // Kurzer Befehl, der nur das Skript im Hintergrund startet. umask 022: Protokoll für ADB lesbar
        private const val HIDE_EVENT8_5S = "umask 022; sh $S45A_PATH < /dev/null " +
            "> /data/local/tmp/sticklab_45a_log.txt 2>&1 &"

        // Stufe 4.5b. Löscht nur, wenn sticklab_mirror läuft (sonst gäbe es niemanden, der den
        // Controller weitergibt). Legt die Datei wieder an, sobald sticklab_mirror endet – egal
        // wie (Zeitlimit, Fehler, Strg+C, timeout). Warten höchstens 150 × 0,2 s = 30 s
        private val HIDE_EVENT8_MIRROR_SCRIPT = listOf(
            "sleep 3",
            "S=/sys/class/input/event8; N=/dev/input/event8",
            "pruef() { [ \"\$(cat \$S/device/name)\" = \"Xbox Wireless Controller\" ] && " +
                "[ \"\$(cat \$S/device/id/vendor)\" = 2020 ] && " +
                "[ \"\$(cat \$S/device/id/product)\" = 0112 ] && " +
                "[ \"\$(cat \$S/dev)\" = 13:72 ]; }",
            "stand() { echo \"\$1 \$(date +%T.%N) \$(ls -lZ \$N 2>&1)\"; }",
            "if ! pidof sticklab_mirror > /dev/null; then stand ABBRUCH_kein_mirror_nichts_geloescht; exit 1; fi",
            "if ! pruef || [ ! -c \$N ]; then stand ABBRUCH_nichts_geloescht; exit 1; fi",
            "stand vorher",
            "rm \$N; echo \"rm rc=\$? \$(date +%T.%N)\"",
            "i=0; while [ \$i -lt 150 ] && pidof sticklab_mirror > /dev/null; do sleep 0.2; i=\$((i+1)); done",
            "echo \"mirror_ende i=\$i \$(date +%T.%N)\"",
            "if [ -e \$N ]; then stand schon_wieder_da",
            "elif pruef; then mknod -Z u:object_r:input_device:s0 -m 0666 \$N c 13 72; " +
                "echo \"mknod rc=\$?\"; chown root:input \$N; chmod 0666 \$N; stand neu_angelegt",
            "else stand sys_geaendert_nichts_angelegt; fi",
            "if [ ! -c \$N ] && [ -e \$S/uevent ]; then echo add > \$S/uevent; sleep 1; stand nach_uevent; fi",
            "stand ende",
        ).joinToString("\n", postfix = "\n")

        private const val HIDE_EVENT8_MIRROR = "umask 022; sh $S45B_PATH < /dev/null " +
            "> /data/local/tmp/sticklab_45b_log.txt 2>&1 &"

        // A38: Führt pservice mehrere Zeilen aus? Fasst nichts außer den eigenen Dateien an.
        // cd zuerst: würde der Befehl irgendwo abgeschnitten, entstünde eine Datei höchstens in
        // /data/local/tmp. umask 022: Datei für ADB lesbar
        private val PROBE_MULTILINE = listOf(
            "umask 022; cd /data/local/tmp || exit; echo Z1 > sticklab_probe_ml.txt",
            "echo Z2 >> sticklab_probe_ml.txt",
        ).joinToString("\n")

        // Die einzigen Befehle, die dieser Bildschirm schickt. Alle enden von selbst.
        // PServerCaseReceiver darf daraus nur "hide_event8_mirror" schicken (eigene Liste dort)
        internal val CASES = mapOf(
            "sleep3" to TestCase("sleep 3"),
            // umask 022: Protokoll-Datei für ADB lesbar (root legt sonst nur für sich lesbare Dateien an)
            "uinput_testpad" to TestCase(
                "umask 022; /system/bin/timeout -k 2 20 /system/bin/uinput " +
                    "/data/local/tmp/sticklab_c2.json > /data/local/tmp/sticklab_c2_log.txt 2>&1"
            ),
            // Das & gilt nur für timeout … (nicht für umask). < /dev/null und > Datei 2>&1:
            // der Hintergrund-Prozess erbt keine Ein-/Ausgabe von sh bzw. pservice
            "uinput_testpad_bg" to TestCase(
                "umask 022; /system/bin/timeout -k 2 20 /system/bin/uinput " +
                    "/data/local/tmp/sticklab_c2b.json < /dev/null " +
                    "> /data/local/tmp/sticklab_c2b_log.txt 2>&1 &"
            ),
            "sleep3_parallel" to TestCase("sleep 3", parallel = 2),
            "hide_event8_5s" to TestCase(HIDE_EVENT8_5S, script = Script("s45a.sh", HIDE_EVENT8_5S_SCRIPT)),
            "hide_event8_mirror" to TestCase(
                HIDE_EVENT8_MIRROR, script = Script("s45b.sh", HIDE_EVENT8_MIRROR_SCRIPT)
            ),
            "probe_multiline" to TestCase(PROBE_MULTILINE),
        )
    }
}
