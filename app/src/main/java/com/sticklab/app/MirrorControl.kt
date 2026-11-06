package com.sticklab.app

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.security.MessageDigest

// Test C Stufe 5 (PROJEKT_DOKU Abschnitt 11 "Stufe 5 – genauer Plan"), seit Phase 2 / E mit mitgeliefertem Programm.
// Startet und stoppt den virtuellen Controller als root über PServerBinder (seit Phase 6e auch in der echten App). Seit Phase 6b (B2)
// immer im Modus curve: wie serve, Sticks mit Kurve, wenn files/curve_table.bin gültig ist, sonst 1:1 wie serve.
// Einzige Stelle für diese Fälle, benutzt von MirrorControlActivity (Knöpfe), MirrorService (Start über den
// Vordergrund-Dienst und Stopp aus der Benachrichtigung, Phase 2 / D) und PServerCaseReceiver (ADB):
//   CHECK "mirror_pruefen": prüft alles wie START, schreibt s5.sh, schickt aber nichts (Trockenprobe)
//   PROBE "mirror_probe":   harmloser root-Test (E-E4): Prüfsumme als root, Programm OHNE Modus starten
//                           (gibt nur "Aufruf: …" aus und endet sofort mit Code 2, öffnet nichts)
//   START "mirror_start":   Prüfsumme, Doppelstart-Sperre, s5.sh schreiben + zurücklesen, kurzer Startbefehl
//   STOP  "mirror_stop":    fester Befehl pkill -x libslmirror.so (Programm räumt auf, s5.sh legt event8 an)
//   KILL  "mirror_kill":    harter Stopp (Z-1), fester Befehl pkill -KILL -x libslmirror.so. Beendet das Programm
//                           sofort, auch wenn es hängt; der Kernel gibt event8 frei (gemessen 3c), s5.sh legt event8 an
// Das Programm wird mit der App installiert (E-E1): app/src/<debug|release>/jniLibs/arm64-v8a/libslmirror.so, Android packt es
// in den nativeLibraryDir aus (gehört system, App kann es nicht ändern). Der Ordnername ist bei jeder Installation
// anders, darum wird er bei jedem Fall neu von Android geholt und streng geprüft. Das Programm muss genau
// PROGRAM_SHA256 haben, die Skripte prüfen dieselbe Prüfsumme noch einmal als root direkt vor dem Start.
// Kein Befehl aus freiem Text, kein Befehl länger als PServerLink.MAX_COMMAND (R14).
// Neben OdinService die einzige Stelle, die Befehle für PServerBinder baut; geschickt wird über PServerLink
internal object MirrorControl {

    const val CHECK = "mirror_pruefen"
    const val PROBE = "mirror_probe"
    const val START = "mirror_start"
    const val STOP = "mirror_stop"
    const val KILL = "mirror_kill"
    val CASES = setOf(CHECK, PROBE, START, STOP, KILL)

    private const val TAG = "MirrorControl"
    // Privater Ordner der App (geprüft 12.10.2026 mit run-as). Hier liegen nur noch die Skripte
    private const val FILES_DIR = "/data/user/0/${MirrorBuild.PACKAGE}/files"
    // Höchstens 15 Zeichen, sonst kürzt Linux den Prozessnamen und pkill -x findet ihn nicht (gemessen 13.10.2026)
    private const val PROGRAM_NAME = "libslmirror.so"
    // nativeLibraryDir, z. B. /data/app/~~<zufall>==/com.sticklab.app.debug-<zufall>==/lib/arm64
    private val LIB_DIR_PATTERN =
        Regex("^/data/app/~~[A-Za-z0-9_=-]+/" + Regex.escape(MirrorBuild.PACKAGE) + "-[A-Za-z0-9_=-]+/lib/arm64$")
    private const val SCRIPT_NAME = "s5.sh"
    private const val SCRIPT_PATH = "$FILES_DIR/$SCRIPT_NAME"
    private const val PROBE_SCRIPT_NAME = "e_probe.sh"
    private const val PROBE_SCRIPT_PATH = "$FILES_DIR/$PROBE_SCRIPT_NAME"
    // SHA-256 des Programms dieser Version (MirrorBuild, je Debug und echte App eigenes)
    private const val PROGRAM_SHA256 = MirrorBuild.PROGRAM_SHA256
    // Doppelstart-Sperre in der App: zweiter Start frühestens nach 10 s (s5.sh braucht bis zum Löschen ca. 3 s,
    // danach bricht ein zweites s5.sh ohnehin ab, weil event8 fehlt bzw. das Programm läuft)
    private const val START_GAP_MS = 10_000L
    // Pause in s5.sh nach einem harten Stopp, bevor event8 wieder angelegt wird. Gleich lang wie die
    // R20-Pause, die das Programm bei normalem Ende selbst macht (ID_GAP_MS 300 in sticklab_mirror.c)
    private const val KILL_PAUSE_S = "0.3"

    private var lastStartMs = -START_GAP_MS

    // Läuft als root im Hintergrund (ca. 3 s bis zum Löschen, dann bis das Programm endet – ohne Zeitlimit, Z-1).
    // Reihenfolge wie 4.5b/4.5d, nur dass s5.sh das Programm selbst startet und auf "abgegriffen" wartet:
    // Doppelstart? → event8 prüfen → Prüfsumme → Programm starten → "abgegriffen"? → event8 löschen →
    // warten, bis das Programm endet → event8 genau wie vorher wieder anlegen (13:72, root:input, 0666).
    // Nach einem harten Stopp (KILL) fehlt die Zeile "Ende:" und damit die R20-Pause des Programms →
    // s5.sh wartet dann selbst KILL_PAUSE_S, damit Android nie zwei Geräte mit derselben Kennung offen hat
    private fun script(programPath: String) = listOf(
        "S=/sys/class/input/event8; N=/dev/input/event8",
        "P=$programPath; L=/data/local/tmp/sticklab_5_mirror.txt",
        "pruef() { [ \"\$(cat \$S/device/name)\" = \"Xbox Wireless Controller\" ] && " +
            "[ \"\$(cat \$S/device/id/vendor)\" = 2020 ] && " +
            "[ \"\$(cat \$S/device/id/product)\" = 0112 ] && " +
            "[ \"\$(cat \$S/dev)\" = 13:72 ]; }",
        "stand() { echo \"\$1 \$(date +%T.%N) \$(ls -lZ \$N 2>&1)\"; }",
        "if pidof $PROGRAM_NAME > /dev/null; then stand ABBRUCH_laeuft_schon_nichts_gestartet; exit 1; fi",
        "if ! pruef || [ ! -c \$N ]; then stand ABBRUCH_event8_nichts_gestartet; exit 1; fi",
        "set -- \$(sha256sum \$P 2>&1)",
        "if [ \"\$1\" != $PROGRAM_SHA256 ]; then echo \"ABBRUCH_pruefsumme \$1\"; exit 1; fi",
        "stand vorher",
        "\$P curve < /dev/null > \$L 2>&1 &",
        "i=0; while [ \$i -lt 15 ] && ! grep -q 'abgegriffen, warte' \$L; do sleep 0.2; i=\$((i+1)); done",
        // Nur löschen, wenn das Programm event8 sicher hat UND noch läuft (z. B. nicht schon per Stopp beendet)
        "if ! grep -q 'abgegriffen, warte' \$L || ! pidof $PROGRAM_NAME > /dev/null; then " +
            "echo \"ABBRUCH_nicht_abgegriffen \$(date +%T.%N)\"; " +
            "pkill -x $PROGRAM_NAME; wait; stand ende_nichts_geloescht; exit 1; fi",
        "rm \$N; echo \"rm rc=\$? \$(date +%T.%N)\"",
        "wait; echo \"mirror_ende \$(date +%T.%N)\"",
        "i=0; while [ \$i -lt 100 ] && pidof $PROGRAM_NAME > /dev/null; do sleep 0.2; i=\$((i+1)); done",
        "if ! grep -q '^Ende:' \$L; then sleep $KILL_PAUSE_S; echo \"hart_beendet_pause \$(date +%T.%N)\"; fi",
        "if [ -e \$N ]; then stand schon_wieder_da",
        "elif pruef; then mknod -Z u:object_r:input_device:s0 -m 0666 \$N c 13 72; " +
            "echo \"mknod rc=\$?\"; chown root:input \$N; chmod 0666 \$N; stand neu_angelegt",
        "else stand sys_geaendert_nichts_angelegt; fi",
        "if [ ! -c \$N ] && [ -e \$S/uevent ]; then echo add > \$S/uevent; sleep 1; stand nach_uevent; fi",
        "stand ende",
    ).joinToString("\n", postfix = "\n")

    // Harmloser root-Test (E-E4): wer bin ich, wie sieht die Datei aus, Prüfsumme, Programm ohne Modus
    // (argc != 2 → "Aufruf: …", Code 2, öffnet weder event8 noch /dev/uinput). timeout als Sicherheitsnetz
    private fun probeScript(programPath: String) = listOf(
        "P=$programPath",
        "echo \"start \$(date +%T.%N) uid=\$(id -u) \$(cat /proc/self/attr/current 2>&1)\"",
        "ls -lZ \$P 2>&1",
        "set -- \$(sha256sum \$P 2>&1)",
        "if [ \"\$1\" != $PROGRAM_SHA256 ]; then echo \"ABBRUCH_pruefsumme \$1\"; exit 1; fi",
        "echo pruefsumme_ok",
        "/system/bin/timeout -k 1 5 \$P < /dev/null; echo \"programm_rc=\$?\"",
        "echo \"laeuft_noch=[\$(pidof $PROGRAM_NAME)]\"",
        "echo \"ende \$(date +%T.%N)\"",
    ).joinToString("\n", postfix = "\n")

    // Kurze Befehle an PServerBinder. umask 022: Protokoll für ADB lesbar. < /dev/null, > Datei, &:
    // der Hintergrund-Prozess hält die Verbindung zu pservice nicht offen (Stufe 2b).
    // Die Probe läuft im Vordergrund (dauert Millisekunden, pservice wartet bis zum Ende, A1/A28)
    private const val START_COMMAND = "umask 022; sh $SCRIPT_PATH < /dev/null " +
        "> /data/local/tmp/sticklab_5_log.txt 2>&1 &"
    private const val PROBE_COMMAND = "umask 022; sh $PROBE_SCRIPT_PATH < /dev/null " +
        "> /data/local/tmp/sticklab_e_probe.txt 2>&1"
    private const val STOP_COMMAND = "pkill -x $PROGRAM_NAME"
    private const val KILL_COMMAND = "pkill -KILL -x $PROGRAM_NAME"

    // Führt einen Fall aus und gibt eine kurze Meldung für Bildschirm/Log zurück. Nicht im Haupt-Thread aufrufen.
    // synchronized: Knopf und Empfänger laufen im selben Prozess, nie zwei Fälle gleichzeitig
    @Synchronized
    fun run(context: Context, caseName: String): String {
        log("Fall $caseName")
        if (caseName !in CASES) return report("Fall nicht erlaubt, es wird nichts geschickt")
        if (!PServerLink.isAvailable()) return report("PServerBinder nicht gefunden, es wird nichts geschickt")
        // Plan Regel 7: Auf unbekannter Firmware wird nichts gestartet. Stopp bleibt immer erlaubt (unten)
        if (caseName != STOP && caseName != KILL && DeviceCheck.current != DeviceSupport.KNOWN) {
            return report("Gerät oder Firmware nicht bekannt, es wird nichts geschickt")
        }
        // Stopp und harter Stopp brauchen keine Prüfungen: feste kurze Befehle, treffen nur unser Programm
        if (caseName == STOP) return send(caseName, STOP_COMMAND)
        if (caseName == KILL) return send(caseName, KILL_COMMAND)

        // CHECK, PROBE und START: alle Prüfungen
        if (context.filesDir.absolutePath != FILES_DIR) {
            return report("Privater Ordner unerwartet: ${context.filesDir.absolutePath}, es wird nichts geschickt")
        }
        val libDir = context.applicationInfo.nativeLibraryDir
        if (libDir == null || !LIB_DIR_PATTERN.matches(libDir)) {
            return report("Programm-Ordner unerwartet: $libDir, es wird nichts geschickt")
        }
        val programPath = "$libDir/$PROGRAM_NAME"
        val command = if (caseName == PROBE) PROBE_COMMAND else START_COMMAND
        if (command.length > PServerLink.MAX_COMMAND) {
            return report("Befehl hat ${command.length} Zeichen (erlaubt ${PServerLink.MAX_COMMAND}), es wird nichts geschickt")
        }
        val sha = sha256(File(programPath))
        if (sha != PROGRAM_SHA256) {
            return report("Prüfsumme falsch oder Programm fehlt in $programPath (${sha ?: "nicht lesbar"}), es wird nichts geschickt")
        }
        if (caseName == PROBE) {
            if (!writeScript(PROBE_SCRIPT_PATH, probeScript(programPath))) {
                return report("Probe-Skript nicht geschrieben, es wird nichts geschickt")
            }
            return send(caseName, PROBE_COMMAND)
        }
        val now = SystemClock.elapsedRealtime()
        if (caseName == START && now - lastStartMs < START_GAP_MS) {
            return report("Start vor ${(now - lastStartMs) / 1000} s schon geschickt, Doppelstart gesperrt, es wird nichts geschickt")
        }
        if (!writeScript(SCRIPT_PATH, script(programPath))) return report("Skript nicht geschrieben, es wird nichts geschickt")
        if (caseName == CHECK) {
            return report("Trockenprobe ok: Prüfsumme stimmt ($programPath), $SCRIPT_NAME geschrieben, Befehl ${START_COMMAND.length} Zeichen, nichts geschickt")
        }
        lastStartMs = now
        return send(caseName, START_COMMAND)
    }

    // Phase 2 / D: Hat run() den Fall wirklich an PServerBinder übergeben? (MirrorService entscheidet damit,
    // ob er auf das Pad wartet oder gleich endet). Passt genau zur Meldung aus send()
    fun sentOk(result: String, caseName: String) = result.startsWith("$caseName geschickt: transact = true ")

    // Schickt einen festen Befehl über PServerLink und meldet das Ergebnis.
    // Die Meldung "… geschickt: transact = …" nicht ändern, sentOk() und MirrorService hängen daran
    private fun send(caseName: String, command: String): String {
        log("sende \"$command\"")
        return when (val result = PServerLink.send(command)) {
            is PServerLink.SendResult.Sent ->
                report("$caseName geschickt: transact = ${result.accepted} nach ${result.durationMs} ms")
            is PServerLink.SendResult.Failed -> {
                Log.e(TAG, "transact fehlgeschlagen", result.error)
                report("$caseName: transact fehlgeschlagen (${result.error.javaClass.simpleName})")
            }
            is PServerLink.SendResult.NotFound ->
                report("PServerBinder nicht gefunden, es wird nichts geschickt")
            is PServerLink.SendResult.TooLong ->
                report("Befehl hat ${result.length} Zeichen (erlaubt ${PServerLink.MAX_COMMAND}), es wird nichts geschickt")
        }
    }

    // Wie PServerSleepTestActivity.writeScript: frisch schreiben, zurücklesen, muss genau gleich sein
    private fun writeScript(path: String, content: String): Boolean = try {
        val file = File(path)
        file.writeText(content)
        val same = file.readText() == content
        log("Skript geschrieben: $path, ${content.length} Zeichen, gleich = $same")
        same
    } catch (t: Throwable) {
        Log.e(TAG, "Skript schreiben fehlgeschlagen", t)
        false
    }

    private fun sha256(file: File): String? = try {
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    } catch (t: Throwable) {
        Log.e(TAG, "Prüfsumme nicht lesbar", t)
        null
    }

    private fun report(msg: String): String {
        log(msg)
        return msg
    }

    private fun log(msg: String) {
        Log.i(TAG, msg)
    }
}
