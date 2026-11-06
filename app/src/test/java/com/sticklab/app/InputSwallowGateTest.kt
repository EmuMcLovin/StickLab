package com.sticklab.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Prüft die Schutzregel R76 (Phase 3b): Verschluckt wird nur während der Messung und erst,
// nachdem eine Meldung "alles in der Mitte" weitergegeben wurde
class InputSwallowGateTest {

    // Kurven-Editor: Das Steuerkreuz wird durchgelassen. Danach muss auch seine Meldung "losgelassen" durchkommen (R76)
    @Test
    fun passedDeflectionPausesSwallowingUntilCentered() {
        val gate = InputSwallowGate()
        gate.begin()
        assertTrue(gate.shouldSwallow())
        gate.passed(neutral = false)
        assertFalse(gate.shouldSwallow())
        gate.passed(neutral = true)
        assertTrue(gate.shouldSwallow())
    }

    @Test
    fun nothingIsSwallowedWithoutMeasurement() {
        val gate = InputSwallowGate()
        assertFalse(gate.shouldSwallow())
        gate.passed(neutral = true)
        assertFalse(gate.shouldSwallow())
    }

    @Test
    fun swallowsAtOnceWhenEverythingWasCentered() {
        val gate = InputSwallowGate()
        gate.passed(neutral = true)
        gate.begin()
        assertTrue(gate.shouldSwallow())
    }

    @Test
    fun waitsForACenteredMessageWhenAStickWasDeflected() {
        val gate = InputSwallowGate()
        gate.passed(neutral = false)                 // Stick war beim Start noch ausgelenkt
        gate.begin()
        assertFalse(gate.shouldSwallow())            // weitergeben ...
        gate.passed(neutral = false)
        assertFalse(gate.shouldSwallow())
        gate.passed(neutral = true)                  // ... bis "losgelassen" durchging
        assertTrue(gate.shouldSwallow())
    }

    // Reiter Sticks: Die App fragt bei jeder Meldung. Gleicher Wunsch darf das Warten auf "losgelassen" nicht aufheben (R76)
    @Test
    fun repeatedUpdateKeepsWaitingForCentered() {
        val gate = InputSwallowGate()
        gate.passed(neutral = false)                 // Stick beim Wechsel in den Reiter noch ausgelenkt
        gate.update(wanted = true)
        assertFalse(gate.shouldSwallow())
        gate.update(wanted = true)
        assertFalse(gate.shouldSwallow())
        gate.passed(neutral = true)
        gate.update(wanted = true)
        assertTrue(gate.shouldSwallow())
        gate.update(wanted = false)                  // anderer Reiter: wieder alles weitergeben
        assertFalse(gate.shouldSwallow())
    }

    // Fund Besitzer 06.11.2026: Ein Stick mit Drift-Rest steht nie genau auf 0. Nach einem Steuerkreuz-Druck lief das Verschlucken darum
    // nie wieder an, und der linke Stick wanderte wieder durch die Knöpfe
    @Test
    fun smallRestOfTheStickCountsAsNeutral() {
        assertTrue(InputSwallowGate.isNeutral(x = 0.001f, y = -0.001f, hatX = 0f, hatY = 0f))
        assertTrue(InputSwallowGate.isNeutral(x = 0f, y = 0f, hatX = 0f, hatY = 0f))
    }

    @Test
    fun swallowingResumesAfterDpadDespiteStickRest() {
        val gate = InputSwallowGate()
        gate.update(wanted = true)
        assertTrue(gate.shouldSwallow())
        gate.passed(InputSwallowGate.isNeutral(x = 0.001f, y = 0f, hatX = 1f, hatY = 0f))   // Steuerkreuz gedrückt
        assertFalse(gate.shouldSwallow())
        gate.passed(InputSwallowGate.isNeutral(x = 0.001f, y = 0f, hatX = 0f, hatY = 0f))   // losgelassen, Rest bleibt
        assertTrue(gate.shouldSwallow())
    }

    // Was die Auswahl noch bewegen kann, zählt nicht als neutral: Sonst bliebe eine Wiederholung hängen (R76)
    @Test
    fun dpadOrDeflectedLeftStickIsNotNeutral() {
        assertFalse(InputSwallowGate.isNeutral(x = 0f, y = 0f, hatX = 1f, hatY = 0f))
        assertFalse(InputSwallowGate.isNeutral(x = 0f, y = 0f, hatX = 0f, hatY = -1f))
        assertFalse(InputSwallowGate.isNeutral(x = 0.6f, y = 0f, hatX = 0f, hatY = 0f))
        assertFalse(InputSwallowGate.isNeutral(x = 0f, y = -0.6f, hatX = 0f, hatY = 0f))
    }

    @Test
    fun endStopsSwallowing() {
        val gate = InputSwallowGate()
        gate.begin()
        assertTrue(gate.shouldSwallow())
        gate.end()
        assertFalse(gate.shouldSwallow())
    }
}
