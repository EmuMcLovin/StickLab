package com.sticklab.app

import android.content.Context
import android.content.SharedPreferences

// Ein Speicher für alle Einstellungen der App (Plan Phase 2, Entscheidung S-A vom 16.10.2026):
// die einzige Stelle, die App-Einstellungsdateien öffnet. Jede Datei liegt im privaten Ordner der App
// (nur diese App kann sie lesen). Die Dateien bleiben getrennt wie bisher – es zieht nichts um,
// damit vor allem das gemerkte Original ("original_values") nie verloren geht.
// Alle Datei- und Schlüsselnamen hier nie ändern: Eine neue App-Version fände die alten Werte sonst nicht
// mehr (geprüft auf dem Laptop: AppStorageNamesTest)
object AppStorage {

    // Die App-Einstellungsdateien
    enum class AppFile(val fileName: String) {
        // Allgemeines, seit v1.0 (Schlüssel: AppKeys)
        APP("app"),

        // Licht-Regler und Schalter "Don't ask again", seit v1.0 (Schlüssel: LightKeys)
        LIGHT("light"),

        // Werte von vor StickLab 2, seit 15.10.2026 (Schlüssel: OriginalField in OriginalCapture.kt)
        ORIGINAL_VALUES("original_values"),

        // Profile, seit 15.10.2026 (Schlüssel: ProfileStore + ProfileCodec), benutzt seit Phase 5a (26.10.2026)
        PROFILES("profiles"),

        // Gerade eingestellte Kurven, seit 05.11.2026 (Schlüssel: ProfileCodec.encodeCurves, CurveStore)
        CURVES("curves"),
    }

    // Schlüssel in der Datei "app", seit v1.0
    object AppKeys {
        // Begrüßung schon mit OK geschlossen
        const val INTRO_SHOWN = "intro_shown"

        // Merker der Loslass-Messung (Phase 3b, seit 16.10.2026): die Deadzone, die vor der Messung wirkte.
        // Steht nur da, solange eine Messung läuft oder nicht zurückgestellt werden konnte –
        // dann stellt die App sie beim nächsten Öffnen zurück (Rückweg nach Absturz, Risiko R70)
        const val DRIFT_RESTORE_LEFT = "drift_restore_left"
        const val DRIFT_RESTORE_RIGHT = "drift_restore_right"

        // Reiter Profiles (seit 5a, 31. Sitzung): Erklärung "How profiles work" schon einmal gezeigt;
        // Profil "Standard" schon einmal von selbst angelegt bzw. nicht mehr nötig (Entscheidung D) – danach legt
        // StickLab nie wieder selbst eins an, auch wenn man alle Profile löscht
        const val PROFILES_HELP_SHOWN = "profiles_help_shown"
        const val STANDARD_PROFILE_DONE = "standard_profile_done"

        // Merker des Lichtblitzes (Phase 5c, seit 27.10.2026): die LED-Farbe von vor dem Blitz ("#aarrggbb,#aarrggbb").
        // Steht nur da, solange ein Blitz läuft oder die Farbe nicht zurückgestellt werden konnte –
        // dann stellt die App sie beim nächsten Öffnen zurück (Rückweg nach Absturz, Entscheidung L4, R112)
        const val LED_FLASH_RESTORE = "led_flash_restore"

        // Kachel (5d-2, seit 27.10.2026): true, sobald die StickLab-Kachel im Schnellmenü erscheint; weg, wenn man sie
        // entfernt (Android meldet das der Kachel). Nur für die Anzeige im Reiter Settings (E2)
        const val TILE_ADDED = "tile_added"

        // Curve mode (6e, seit 05.11.2026): Hinweis beim ersten Einschalten schon mit OK bestätigt
        const val CURVE_MODE_ACCEPTED = "curve_mode_accepted"

        // Gewählter Hintergrund (seit 06.11.2026): AppBackground.id. Nur Optik, gehört nicht zum Profil
        const val BACKGROUND = "background"
    }

    // Schlüssel in der Datei "light", seit v1.0 (so stehen sie am Odin in light.xml, gelesen 16.10.2026)
    object LightKeys {
        const val BRIGHT = "bright"
        const val BOOST = "boost"
        const val WHITE = "white"
        const val GREEN = "green"
        const val BLUE = "blue"
        const val SPEED = "speed"
        const val SKIP_PROMPT = "skipPrompt"
    }

    // Öffnet eine App-Einstellungsdatei (nur diese App kann sie lesen)
    fun open(context: Context, file: AppFile): SharedPreferences =
        context.getSharedPreferences(file.fileName, Context.MODE_PRIVATE)
}
