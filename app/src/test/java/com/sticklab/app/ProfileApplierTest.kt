package com.sticklab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Prüft "Use" / "Restore everything" (Phase 5b-1) ohne Odin: Reihenfolge, alle 5 Werte (U1),
// weitermachen nach einem Fehler (U2), nicht angefasste Teile und der Bericht für die Statuszeile
class ProfileApplierTest {

    // Merkt sich jeden Aufruf; failing = Teile, die scheitern sollen; afterRestart = Teile, deren Live-Weg fehlt
    private class RecordingSetter(
        private val failing: Set<String> = emptySet(),
        private val afterRestart: Set<String> = emptySet(),
    ) : OdinSetter {
        val calls = mutableListOf<String>()

        private fun record(name: String): DeadzoneControl.Result {
            calls += name
            return when (name) {
                in failing -> DeadzoneControl.Result.FAILED
                in afterRestart -> DeadzoneControl.Result.AFTER_RESTART
                else -> DeadzoneControl.Result.ACTIVE_NOW
            }
        }

        override fun setDeadzone(side: OdinMapping.Side, value: Float) = record("deadzone ${side.name} $value")
        override fun setTriggerDeadzone(side: OdinMapping.Side, value: Float) = record("trigger ${side.name} $value")
        override fun setSensitivity(side: OdinMapping.Side, value: Float) = record("sensitivity ${side.name} $value")
        override fun setButton(button: BackButton, target: ButtonTarget) =
            record("button ${button.name} ${target.name}") != DeadzoneControl.Result.FAILED
    }

    // Ein zweites Profil wie im geplanten Lauf: DZ 3,0/3,0 %, L2 10 %, Sens 1,10, M1 = Y (Android-Name)
    private val shooter = Profile.withDefaults("Shooter").copy(
        deadzone = LeftRight(0.03f, 0.03f),
        triggerDeadzone = LeftRight(0.10f, 0.05f),
        sensitivity = LeftRight(1.10f, 1.0f),
        m1 = ButtonTarget.Y,
        m2 = ButtonTarget.B,
    )

    @Test
    fun useSetsAllFiveValuesInOrder() {
        val setter = RecordingSetter()
        val report = ProfileApplier(setter).apply(ProfileTargets.of(shooter))
        assertEquals(
            listOf(
                "deadzone LEFT 0.03", "deadzone RIGHT 0.03",
                "trigger LEFT 0.1", "trigger RIGHT 0.05",
                "sensitivity LEFT 1.1", "sensitivity RIGHT 1.0",
                "button M1 Y", "button M2 B",
            ),
            setter.calls,
        )
        assertNull(report.firstFailed)
        assertFalse(report.needsRestart)
    }

    @Test
    fun failureDoesNotStopTheRest() {
        // Linke Deadzone und M1 scheitern: trotzdem werden alle 8 Werte versucht (U2)
        val setter = RecordingSetter(failing = setOf("deadzone LEFT 0.03", "button M1 Y"))
        val report = ProfileApplier(setter).apply(ProfileTargets.of(shooter))
        assertEquals(8, setter.calls.size)
        assertEquals(ProfilePart.DEADZONE, report.firstFailed)
        assertEquals(DeadzoneControl.Result.FAILED, report.results[ProfilePart.BUTTONS])
        assertEquals(DeadzoneControl.Result.ACTIVE_NOW, report.results[ProfilePart.SENSITIVITY])
    }

    @Test
    fun liveWayMissingMeansAfterRestart() {
        val setter = RecordingSetter(afterRestart = setOf("trigger RIGHT 0.05"))
        val report = ProfileApplier(setter).apply(ProfileTargets.of(shooter))
        assertNull(report.firstFailed)
        assertTrue(report.needsRestart)
    }

    @Test
    fun partsWithoutTargetAreLeftAlone() {
        // Restore everything ohne gemerkte Sensitivity: die bleibt, wie sie ist
        val setter = RecordingSetter()
        val report = ProfileApplier(setter).apply(
            ProfileTargets(
                deadzone = LeftRight(0.015f, 0.025f),
                triggerDeadzone = null,
                sensitivity = null,
                buttons = mapOf(BackButton.M1 to ButtonTarget.A, BackButton.M2 to ButtonTarget.B),
            )
        )
        assertTrue(setter.calls.none { it.startsWith("trigger") || it.startsWith("sensitivity") })
        assertEquals(setOf(ProfilePart.DEADZONE, ProfilePart.BUTTONS), report.results.keys)
    }
}
