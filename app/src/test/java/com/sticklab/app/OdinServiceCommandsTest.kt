package com.sticklab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Prüft die festen PServerBinder-Befehle der echten App (Plan Phase 2, P-A, Risiko R64):
// gleicher Aufbau wie bis 16.10.2026, nie länger als PServerLink.MAX_COMMAND, unerlaubte Werte → nichts schicken
class OdinServiceCommandsTest {

    // Längster Paketname, den StickLab heute hat
    private val debugPackage = "com.sticklab.app.debug"

    @Test
    fun systemFloatCommandLooksAsBefore() {
        assertEquals(
            "settings put system right_joystick_dead_zone_radius 0.025",
            OdinService.systemFloatCommand(OdinService.FloatSetting.RIGHT_DEADZONE, 0.025f),
        )
    }

    @Test
    fun systemFloatCommandLimitsValue() {
        // 50 % ist nicht erlaubt, die Deadzone wird auf 20 % begrenzt
        assertEquals(
            "settings put system left_joystick_dead_zone_radius 0.200",
            OdinService.systemFloatCommand(OdinService.FloatSetting.LEFT_DEADZONE, 0.5f),
        )
        assertNull(OdinService.systemFloatCommand(OdinService.FloatSetting.LEFT_DEADZONE, Float.NaN))
    }

    // Trigger-Deadzone (Schritt S): StickLab schreibt sie erstmals selbst – nur 5–50 % wie das Odin-Menü.
    // Das Menü schreibt "0.05", StickLab "0.050" (Risiko R121, am Odin mit Foto des Menüs prüfen)
    @Test
    fun triggerCommandUsesOdinMenuRange() {
        assertEquals(
            "settings put system l2_dead_zone_radius 0.100",
            OdinService.systemFloatCommand(OdinService.FloatSetting.L2_DEADZONE, 0.1f),
        )
        assertEquals(
            "settings put system r2_dead_zone_radius 0.050",
            OdinService.systemFloatCommand(OdinService.FloatSetting.R2_DEADZONE, 0.0f),
        )
        assertEquals(
            "settings put system r2_dead_zone_radius 0.500",
            OdinService.systemFloatCommand(OdinService.FloatSetting.R2_DEADZONE, 0.8f),
        )
    }

    @Test
    fun ledColorsCommandLooksAsBeforeAndRejectsOtherText() {
        assertEquals(
            "settings put system joystick_led_light_picker_color '#ff000000,#ff000000'",
            OdinService.ledColorsCommand("#ff000000,#ff000000"),
        )
        assertNull(OdinService.ledColorsCommand("#ff000000,#ff000000'; reboot; '"))
    }

    @Test
    fun screenCaptureCommandLooksAsBeforeAndRejectsOtherText() {
        assertEquals(
            "appops set com.sticklab.app.debug PROJECT_MEDIA allow",
            OdinService.screenCaptureCommand(debugPackage, true),
        )
        assertEquals(
            "appops set com.sticklab.app.debug PROJECT_MEDIA default",
            OdinService.screenCaptureCommand(debugPackage, false),
        )
        assertNull(OdinService.screenCaptureCommand("com.x; reboot", true))
    }

    @Test
    fun buttonTargetCommandWritesTheMenuNumber() {
        // Wie das Odin-Menü: M1 = X schreibt 99 (Test B), M2 = None schreibt 0 (4.0c)
        assertEquals(
            "settings put system remap_custom_to_m1_value 99",
            OdinService.buttonTargetCommand(BackButton.M1, ButtonTarget.X),
        )
        assertEquals(
            "settings put system remap_custom_to_m2_value 0",
            OdinService.buttonTargetCommand(BackButton.M2, ButtonTarget.NONE),
        )
    }

    @Test
    fun longestCommandsFitThroughTheLink() {
        val floatCommands = OdinService.FloatSetting.entries.map { setting ->
            OdinService.systemFloatCommand(setting, setting.range.endInclusive)
        }
        val buttonCommands = BackButton.entries.flatMap { button ->
            ButtonTarget.entries.map { target -> OdinService.buttonTargetCommand(button, target) }
        }
        val all = floatCommands + buttonCommands + listOf(
            OdinService.ledColorsCommand("#ffffffff,#ffffffff"),
            OdinService.screenCaptureCommand(debugPackage, false),
        )
        for (command in all) {
            assertTrue("zu lang: $command", command!!.length <= PServerLink.MAX_COMMAND)
        }
    }
}
