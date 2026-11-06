package com.sticklab.app

import android.content.Context
import android.util.Log

// Phase 6b (PROJEKT_DOKU Abschnitt 11 "Plan 6b"). Nur Debug-Version. Schreibt oder löscht die Tabellen-Datei für das
// Hilfsprogramm, damit Test T1 und die Läufe A/B eine Kurve haben. Kein root, kein PServerBinder: nur eine Datei im
// privaten Ordner (das Programm im Modus curve merkt die Änderung innerhalb von 1 s, B1).
// Benutzt von MirrorControlActivity (Knöpfe) und PServerCaseReceiver (ADB, damit der Besitzer im Lauf nur die
// Sticks bewegt). Die Kurven der echten Profile verbindet erst 6c
internal object CurveTestCases {

    const val LINEAR = "curve_linear"
    const val PRECISION = "curve_precision"
    const val OFF = "curve_off"
    const val TRIGGER_50 = "curve_trigger50"
    val CASES = setOf(LINEAR, PRECISION, OFF, TRIGGER_50)

    private const val TAG = "CurveTest"

    // Testwert für die Trigger-Grenze (Z11): volle Ausgabe schon bei 50 % des Wegs (Wunsch Besitzer 05.11.2026)
    private const val TRIGGER_TEST_FULL_OUTPUT = 0.5f

    // Führt einen Fall aus und gibt eine kurze Meldung für Bildschirm/Log zurück. Nicht im Haupt-Thread aufrufen
    @Synchronized
    fun run(context: Context, caseName: String): String {
        val filesDir = context.filesDir
        val result = when (caseName) {
            LINEAR -> writeBoth(filesDir, Curve.LINEAR)
            PRECISION -> writeBoth(filesDir, Curve.PRECISION)
            TRIGGER_50 -> writeBoth(filesDir, Curve.LINEAR, TRIGGER_TEST_FULL_OUTPUT)
            OFF -> if (CurveTableFile.delete(filesDir)) "Kurve aus: Datei gelöscht" else "Kurve aus: Löschen gescheitert"
            else -> "Fall nicht erlaubt, nichts geändert"
        }
        Log.i(TAG, "$caseName: $result")
        return result
    }

    // Beide Sticks dieselbe Kurve, ohne äußere und Anti-Deadzone (nur zum Testen der Rechnung im Programm)
    private fun writeBoth(filesDir: java.io.File, curve: Curve, triggerFullOutput: Float = 1f): String {
        val table = CurveTable.build(CurveSettings(curve))
        val limit = CurveTableFile.triggerLimitOf(triggerFullOutput)
        return if (CurveTableFile.write(filesDir, table, table, LeftRight(limit, limit))) {
            "Kurve $curve geschrieben (${CurveTableFile.FILE_SIZE} Bytes, 25 % → ${percentAt(table, 25)} %, Trigger voll ab $limit)"
        } else {
            "Kurve $curve: Schreiben gescheitert"
        }
    }

    // Wert der Tabelle bei einem Ausschlag in Prozent (nächster Punkt), zum Vergleich mit dem Selbsttest im Programm
    private fun percentAt(table: IntArray, percent: Int): String {
        val index = Math.round(percent / 100f * (CurveTable.SIZE - 1))
        return "%.1f".format(java.util.Locale.ROOT, table[index] * 100f / CurveTable.MAX_OUTPUT)
    }
}
