package com.sticklab.app

import org.junit.Assert.assertEquals
import org.junit.Test

// Der gespeicherte Hintergrund muss jedes Update überstehen, und ein kaputter Wert darf die App nicht stören
class AppBackgroundTest {

    // Bewusst noch einmal als Text ausgeschrieben, wie AppStorageNamesTest: Diese Namen stehen in den Einstellungen der Nutzer
    @Test
    fun idsNeverChange() {
        assertEquals(listOf("neon", "ocean", "ember", "graphite"), AppBackground.entries.map { it.id })
        assertEquals("background", AppStorage.AppKeys.BACKGROUND)
    }

    @Test
    fun everyIdLeadsBackToItsBackground() {
        for (background in AppBackground.entries) {
            assertEquals(background, AppBackground.fromId(background.id))
        }
    }

    @Test
    fun missingOrUnknownValueGivesTheDefault() {
        assertEquals(AppBackground.NEON, AppBackground.DEFAULT)
        assertEquals(AppBackground.DEFAULT, AppBackground.fromId(null))
        assertEquals(AppBackground.DEFAULT, AppBackground.fromId(""))
        assertEquals(AppBackground.DEFAULT, AppBackground.fromId("gibt-es-nicht"))
    }
}
