package com.sticklab.app

import org.junit.Assert.assertEquals
import org.junit.Test

// Namensschutz (Plan Phase 2, S-A, Risiko R60): Die Datei- und Schlüsselnamen der App-Einstellungen
// dürfen sich nie ändern. Sonst findet eine neue App-Version die gespeicherten Werte nicht mehr –
// beim Original würde sie dann die heutigen (evtl. schon veränderten) Werte als "Original" merken.
// Die erwarteten Namen sind hier bewusst noch einmal als Text ausgeschrieben (Stand am Odin 16.10.2026)
class AppStorageNamesTest {

    @Test
    fun fileNamesNeverChange() {
        val names = AppStorage.AppFile.entries.map { it.fileName }
        assertEquals(listOf("app", "light", "original_values", "profiles", "curves"), names)
    }

    @Test
    fun appKeyNeverChanges() {
        assertEquals("intro_shown", AppStorage.AppKeys.INTRO_SHOWN)
    }

    // Merker der Loslass-Messung (seit 16.10.2026): sonst fände eine neue Version nach einem Absturz
    // den Wert zum Zurückstellen nicht mehr
    @Test
    fun driftRestoreKeysNeverChange() {
        assertEquals("drift_restore_left", AppStorage.AppKeys.DRIFT_RESTORE_LEFT)
        assertEquals("drift_restore_right", AppStorage.AppKeys.DRIFT_RESTORE_RIGHT)
    }

    // Merker des Lichtblitzes (seit 27.10.2026): sonst fände eine neue Version nach einem Absturz mitten im Blitz
    // die alte LED-Farbe nicht mehr
    @Test
    fun ledFlashRestoreKeyNeverChanges() {
        assertEquals("led_flash_restore", AppStorage.AppKeys.LED_FLASH_RESTORE)
    }

    // Merker der Kachel (seit 27.10.2026): sonst zeigte der Reiter Settings nach einem Update "noch nicht drin"
    @Test
    fun tileAddedKeyNeverChanges() {
        assertEquals("tile_added", AppStorage.AppKeys.TILE_ADDED)
        assertEquals("curve_mode_accepted", AppStorage.AppKeys.CURVE_MODE_ACCEPTED)
    }

    @Test
    fun lightKeysNeverChange() {
        val keys = with(AppStorage.LightKeys) {
            listOf(BRIGHT, BOOST, WHITE, GREEN, BLUE, SPEED, SKIP_PROMPT)
        }
        assertEquals(listOf("bright", "boost", "white", "green", "blue", "speed", "skipPrompt"), keys)
    }

    // Profile (seit 5a, 26.10.2026): Platz-Vorsilbe "p", aktives Profil "active"
    @Test
    fun profileKeysNeverChange() {
        assertEquals("p", ProfileStore.PROFILE_PREFIX)
        assertEquals("active", ProfileStore.KEY_ACTIVE)
        // Merker in der Datei "app": sonst käme die Erklärung bzw. "Standard" nach einem Update erneut
        assertEquals("profiles_help_shown", AppStorage.AppKeys.PROFILES_HELP_SHOWN)
        assertEquals("standard_profile_done", AppStorage.AppKeys.STANDARD_PROFILE_DONE)
    }

    @Test
    fun originalKeysNeverChange() {
        val keys = OriginalField.entries.map { it.key }
        val expected = listOf(
            "deadzone.left", "deadzone.right",
            "triggerDeadzone.l2", "triggerDeadzone.r2",
            "sensitivity.left", "sensitivity.right",
            "m1", "m2",
        )
        assertEquals(expected, keys)
    }
}
