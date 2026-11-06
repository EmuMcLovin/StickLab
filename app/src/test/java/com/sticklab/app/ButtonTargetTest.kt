package com.sticklab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Prüft die M1/M2-Liste und die Umrechnung Android-Taste ↔ Aufdruck (Plan Phase 4, Messungen 26.10.2026)
class ButtonTargetTest {

    @Test
    fun listMatchesTheOdinMenuInOrderAndNumbers() {
        // Reihenfolge und Zahlen wie gemessen in 4.0/4.0c (PROJEKT_DOKU Abschnitt 2 "M1/M2")
        val measured = listOf(
            "Default" to 98, "Mode" to 110, "APP_SWITCH" to 187, "HOME" to 3, "SELECT" to 109, "START" to 108,
            "BACK" to 4, "A" to 96, "B" to 97, "X" to 99, "Y" to 100, "L1" to 102, "L2" to 104, "L3" to 106,
            "R1" to 103, "R2" to 105, "R3" to 107, "DOWN" to 20, "UP" to 19, "LEFT" to 21, "RIGHT" to 22, "None" to 0,
        )
        assertEquals(measured, ButtonTarget.entries.map { it.menuName to it.keyCode })
    }

    @Test
    fun settingTextIsReadOnlyForNumbersFromTheList() {
        assertEquals(ButtonTarget.A, ButtonTarget.fromSettingText("96"))
        assertEquals(ButtonTarget.NONE, ButtonTarget.fromSettingText("0"))
        assertNull(ButtonTarget.fromSettingText("95"))
        assertNull(ButtonTarget.fromSettingText("null"))
        assertNull(ButtonTarget.fromSettingText(null))
    }

    @Test
    fun steppingWrapsAroundAtBothEnds() {
        assertEquals(ButtonTarget.MODE, ButtonTarget.DEFAULT.stepped(1))
        assertEquals(ButtonTarget.NONE, ButtonTarget.DEFAULT.stepped(-1))
        assertEquals(ButtonTarget.DEFAULT, ButtonTarget.NONE.stepped(1))
    }

    @Test
    fun xboxLayoutSwapsFaceButtonsOdinLayoutDoesNot() {
        // Xbox (gemessen Test B/Stufe 4.0): Aufdruck B sendet A, Aufdruck A sendet B, Aufdruck X sendet Y
        assertEquals(ButtonTarget.B, ButtonLayoutMap.printedFace(ButtonTarget.A, ControllerLayout.XBOX))
        assertEquals(ButtonTarget.A, ButtonLayoutMap.printedFace(ButtonTarget.B, ControllerLayout.XBOX))
        assertEquals(ButtonTarget.X, ButtonLayoutMap.printedFace(ButtonTarget.Y, ControllerLayout.XBOX))
        // Odin (gemessen 4.0b): jede Taste sendet ihren Aufdruck
        assertEquals(ButtonTarget.A, ButtonLayoutMap.printedFace(ButtonTarget.A, ControllerLayout.ODIN))
        // Andere Tasten haben keinen Aufdruck-Tausch
        assertEquals(ButtonTarget.L1, ButtonLayoutMap.printedFace(ButtonTarget.L1, ControllerLayout.XBOX))
    }

    @Test
    fun m1WithTheOwnersSettingLightsTheButtonPrintedB() {
        // Besitzer: M1 = 96 im Xbox-Layout ahmt die Taste mit Aufdruck B nach (4.0b/Test B)
        assertEquals(OdinPart.FACE_B, ButtonLayoutMap.partOf(ButtonTarget.A, ControllerLayout.XBOX))
        assertEquals(OdinPart.FACE_A, ButtonLayoutMap.partOf(ButtonTarget.A, ControllerLayout.ODIN))
        // Unbekanntes Layout: lieber gar nichts markieren als die falsche Taste
        assertNull(ButtonLayoutMap.partOf(ButtonTarget.A, ControllerLayout.UNKNOWN))
        // Ohne eigene Taste am Gerät
        assertNull(ButtonLayoutMap.partOf(ButtonTarget.APP_SWITCH, ControllerLayout.XBOX))
        assertNull(ButtonLayoutMap.partOf(ButtonTarget.NONE, ControllerLayout.XBOX))
    }

    @Test
    fun pressedKeysIgnoreOnlyDirectionsThatAndroidMadeItself() {
        assertEquals(OdinPart.FACE_B, ButtonLayoutMap.partOfPressedKey(96, ControllerLayout.XBOX, generatedByAndroid = false))
        assertEquals(OdinPart.L3, ButtonLayoutMap.partOfPressedKey(106, ControllerLayout.XBOX, generatedByAndroid = false))
        // DPAD_UP aus dem linken Stick bzw. der HAT-Achse: nicht zeigen (das Steuerkreuz kommt über dpadParts)
        assertNull(ButtonLayoutMap.partOfPressedKey(19, ControllerLayout.XBOX, generatedByAndroid = true))
        // DPAD_UP als echte Taste (z. B. M1 auf UP): zeigen
        assertEquals(OdinPart.DPAD_UP, ButtonLayoutMap.partOfPressedKey(19, ControllerLayout.XBOX, generatedByAndroid = false))
        assertNull(ButtonLayoutMap.partOfPressedKey(0, ControllerLayout.XBOX, generatedByAndroid = false))    // keine Taste
        assertNull(ButtonLayoutMap.partOfPressedKey(66, ControllerLayout.XBOX, generatedByAndroid = false))   // ENTER, nicht in der Liste
    }

    @Test
    fun dpadComesFromTheHatAxes() {
        assertEquals(setOf(OdinPart.DPAD_UP), ButtonLayoutMap.dpadParts(0f, -1f))
        assertEquals(setOf(OdinPart.DPAD_RIGHT, OdinPart.DPAD_DOWN), ButtonLayoutMap.dpadParts(1f, 1f))
        assertEquals(emptySet<OdinPart>(), ButtonLayoutMap.dpadParts(0f, 0f))
    }

    @Test
    fun layoutComesFromTheFlipSetting() {
        assertEquals(ControllerLayout.XBOX, ControllerLayout.fromFlipSetting("1"))
        assertEquals(ControllerLayout.ODIN, ControllerLayout.fromFlipSetting("0"))
        assertEquals(ControllerLayout.UNKNOWN, ControllerLayout.fromFlipSetting(null))
        assertEquals(ControllerLayout.UNKNOWN, ControllerLayout.fromFlipSetting("2"))
    }
}
