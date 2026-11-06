package com.sticklab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Prüft die Regel der Kontrollmessung (Phase 3c-2, Entscheidungen K2a, N1, E1, Ü3)
class DeadzoneCheckTest {

    // Ein Stick nach der Kontrollmessung: "done" Felder erledigt, Ruhepunkte wie angegeben
    private fun reachOf(done: Int, vararg rest: StickPosition): StickReach {
        val fields = List(StickDirection.entries.size) { i ->
            if (i < done) ReachField(96f, FieldState.DONE) else ReachField(null, FieldState.OPEN)
        }
        return StickReach(fields, DriftResult(rest.toList()))
    }

    private val zero = StickPosition.CENTER
    private val smallRest = StickPosition(0.003f, 0f)       // 0,3 % – mit Deadzone hätte das 0 sein müssen

    @Test
    fun allFieldsAndOnlyZerosPasses() {
        assertEquals(CheckVerdict.PASSED, DeadzoneCheck.verdict(reachOf(8, zero, zero, zero)))
    }

    @Test
    fun anyRestNotZeroFailsEvenIfIncomplete() {
        assertEquals(CheckVerdict.FAILED, DeadzoneCheck.verdict(reachOf(8, zero, smallRest)))
        assertEquals(CheckVerdict.FAILED, DeadzoneCheck.verdict(reachOf(3, smallRest)))
    }

    @Test
    fun notAllFieldsWithoutRestIsIncomplete() {
        assertEquals(CheckVerdict.INCOMPLETE, DeadzoneCheck.verdict(reachOf(7, zero, zero)))
        assertEquals(CheckVerdict.INCOMPLETE, DeadzoneCheck.verdict(reachOf(0)))
    }

    @Test
    fun firstProposalComesFromTheMeasurement() {
        // 3c-1 Lauf 3 links: Rest 3,73 % + 0,3 = 4,03 → 4,1 % = Schritt 41 (0,1-Raster)
        val proposal = DeadzoneCheck.proposalOf(reachOf(8, StickPosition(0.0373f, 0f)))!!
        assertEquals(41, proposal.step)
        assertEquals(41, proposal.firstStep)
        assertTrue(proposal.needsCheck)
        assertFalse(proposal.passed)
        assertNull(DeadzoneCheck.proposalOf(reachOf(0)))       // nie losgelassen → kein Vorschlag
    }

    @Test
    fun passingMakesTheProposalApplicable() {
        val after = DeadzoneCheck.afterCheck(SideProposal(9, 9), reachOf(8, zero))
        assertEquals(9, after.step)
        assertTrue(after.passed)
        assertFalse(after.needsCheck)
    }

    @Test
    fun failingRaisesByHalfAPercentAtMostTwice() {
        var proposal = SideProposal(35, 35)
        proposal = DeadzoneCheck.afterCheck(proposal, reachOf(8, smallRest))
        assertEquals(40, proposal.step)                        // 3,5 → 4,0 % (E1: +0,5 %)
        assertEquals(35, proposal.checkedStep)
        assertTrue(proposal.needsCheck)
        proposal = DeadzoneCheck.afterCheck(proposal, reachOf(8, smallRest))
        assertEquals(45, proposal.step)                        // 4,0 → 4,5 %
        assertTrue(proposal.needsCheck)
        proposal = DeadzoneCheck.afterCheck(proposal, reachOf(8, smallRest))
        assertEquals(45, proposal.step)                        // keine dritte Erhöhung
        assertTrue(proposal.exhausted)
        assertFalse(proposal.passed)
        assertFalse(proposal.needsCheck)
    }

    @Test
    fun neverAboveTheLimit() {
        val top = DeadzoneSteps.MAX_STEP
        val after = DeadzoneCheck.afterCheck(SideProposal(top, top), reachOf(8, smallRest))
        assertEquals(top, after.step)
        assertTrue(after.exhausted)
    }

    @Test
    fun raiseNearTheLimitStopsAtTwentyPercent() {
        // 19,8 % + 0,5 % wären 20,3 % → wird auf 20 % begrenzt, danach keine Erhöhung mehr
        val top = DeadzoneSteps.MAX_STEP
        val raised = DeadzoneCheck.afterCheck(SideProposal(top - 2, top - 2), reachOf(8, smallRest))
        assertEquals(top, raised.step)
        assertTrue(raised.needsCheck)
        val again = DeadzoneCheck.afterCheck(raised, reachOf(8, smallRest))
        assertEquals(top, again.step)
        assertTrue(again.exhausted)
    }

    @Test
    fun incompleteKeepsAnEarlierPass() {
        val passed = DeadzoneCheck.afterCheck(SideProposal(9, 9), reachOf(8, zero))
        // Zweite Runde: nur der andere Stick wurde bewegt → bleibt bestanden
        assertEquals(passed, DeadzoneCheck.afterCheck(passed, reachOf(0)))
        // Ein Rest ≠ 0 in einer späteren Runde zählt aber
        val failedLater = DeadzoneCheck.afterCheck(passed, reachOf(2, smallRest))
        assertEquals(14, failedLater.step)                      // 0,9 → 1,4 %
        assertFalse(failedLater.passed)
    }

    @Test
    fun resultAppliesOnlyPassedSides() {
        // Messung: links Rest 3,73 % (3c-1 Lauf 3) → 4,1 %, rechts nie losgelassen → kein Vorschlag
        val measured = LeftRight(reachOf(8, StickPosition(0.0373f, 0f)), reachOf(2))
        val result = AutoSetResult.ofMeasurement(LeftRight(0.015f, 0.025f), measured)
        assertNull(result.proposals.right)
        assertTrue(result.anyNeedsCheck)
        assertFalse(result.anyPassed)
        // Kontrolle: links mit 4,1 % live, rechts bleibt
        assertEquals(0.041f, result.checkDeadzone().left!!, 0.0001f)
        assertNull(result.checkDeadzone().right)
        // Vor der Kontrolle darf nichts übernommen werden (Ü3)
        assertNull(result.applicableDeadzone().left)

        val checked = result.withCheck(LeftRight(reachOf(8, zero), reachOf(0)))
        assertTrue(checked.anyPassed)
        assertFalse(checked.anyNeedsCheck)
        assertEquals(0.041f, checked.applicableDeadzone().left!!, 0.0001f)
        assertNull(checked.applicableDeadzone().right)
    }

    @Test
    fun oneSideFailsTheOtherPasses() {
        // 3c-1 Lauf 1b: links 3,18 % → 3,5 %, rechts 2,75 % → 3,1 %
        val measured = LeftRight(reachOf(8, StickPosition(0.0318f, 0f)), reachOf(8, StickPosition(0.0275f, 0f)))
        val result = AutoSetResult.ofMeasurement(LeftRight(0.015f, 0.025f), measured)
            .withCheck(LeftRight(reachOf(8, zero), reachOf(8, zero, smallRest)))
        assertTrue(result.proposals.left!!.passed)
        assertEquals(36, result.proposals.right!!.step)         // 3,1 → 3,6 %
        assertTrue(result.anyNeedsCheck)                        // rechts noch einmal prüfen
        assertNull(result.applicableDeadzone().right)           // rechts noch nicht übernehmbar
    }

    @Test
    fun incompleteWithoutEarlierPassNeedsAnotherCheck() {
        val after = DeadzoneCheck.afterCheck(SideProposal(9, 9), reachOf(5, zero))
        assertEquals(CheckVerdict.INCOMPLETE, after.verdict)
        assertEquals(9, after.step)
        assertTrue(after.needsCheck)
    }
}
