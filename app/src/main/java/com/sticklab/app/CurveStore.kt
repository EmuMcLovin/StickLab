package com.sticklab.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

// Merkt sich die gerade eingestellten Kurven beider Sticks (Plan Phase 6): die App-Einstellungsdatei "curves", nur diese
// App kann sie lesen. Sie entspricht den Werten am Odin bei Deadzone und Co.: "Apply" schreibt hierher, "Use" lädt aus dem
// Profil hierher, und "Save to profile" nimmt sie von hier mit. Felder wie im Profil (ProfileCodec).
// Seit Phase 6c schreibt jedes Speichern auch die Tabellen-Datei für das Hilfsprogramm (CurveTableFile), so gilt eine neue
// Wahl im laufenden Curve mode nach höchstens 1 s, egal ob sie von "Apply", "Use" oder der Kachel kommt. Ohne Curve mode
// liest die Datei niemand, die Firmware-Werte am Odin ändert das nie
class CurveStore(private val context: Context) {

    private val prefs: SharedPreferences = AppStorage.open(context, AppStorage.AppFile.CURVES)

    // Ohne gespeicherte Werte oder bei kaputten Werten: Standard je Feld (Linear, keine äußere und Anti-Deadzone)
    fun load(): LeftRight<CurveSettings> = ProfileCodec.decodeCurves(prefs.all)

    // Tabellen-Datei auf den gespeicherten Stand bringen, vor dem Start des Curve mode. Ergebnis true = Datei stimmt
    fun writeTable(): Boolean {
        val curves = load()
        return CurveTableFile.write(
            context.filesDir,
            CurveTable.build(curves.left),
            CurveTable.build(curves.right),
            LeftRight(triggerLimit(curves.left), triggerLimit(curves.right)),
        )
    }

    private fun triggerLimit(settings: CurveSettings): Int =
        CurveTableFile.triggerLimitOf(SettingLimits.limit(settings.triggerFullOutput, SettingLimits.TRIGGER_FULL_OUTPUT) ?: CurveSettings.NO_TRIGGER_LIMIT)

    // Sofort auf den Speicher (commit), Ergebnis true = gespeichert und Tabellen-Datei geschrieben (wie ProfileStore).
    // Bewusst nicht die kurze Form edit { } (Lint-Hinweis UseKtx): die verschluckt das Ergebnis von commit()
    @SuppressLint("UseKtx")
    fun save(curves: LeftRight<CurveSettings>): Boolean {
        val editor = prefs.edit().clear()
        for ((key, value) in ProfileCodec.encodeCurves(curves)) {
            when (value) {
                is String -> editor.putString(key, value)
                is Float -> editor.putFloat(key, value)
                else -> error("Unerwartete Art für $key: ${value::class.java.simpleName}")
            }
        }
        return editor.commit() && writeTable()
    }
}
