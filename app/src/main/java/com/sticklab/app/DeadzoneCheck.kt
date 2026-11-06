package com.sticklab.app

import kotlin.math.roundToInt

// Kontrollmessung des Auto-Sets (Phase 3c-2, Entscheidungen K2a, N1, E1, Ü3 vom 17.10.2026):
// Der Vorschlag wird nur live gesetzt, dann wird jede Richtung noch einmal bis zum Anschlag gedrückt und
// losgelassen. Hier steht die Regel, ob ein Stick damit bestanden hat und wie es danach weitergeht.
// Ohne Android-Teile, damit die Regel auf dem Laptop geprüft werden kann (DeadzoneCheckTest)

// Urteil einer Kontrollmessung für einen Stick
enum class CheckVerdict {
    // Alle 8 Felder erledigt und nach jedem Loslassen genau 0
    PASSED,

    // Mindestens einmal nach dem Loslassen nicht genau 0 → Deadzone zu klein
    FAILED,

    // Nicht alle 8 Felder erledigt, aber auch kein Rest gesehen → kein Urteil, noch einmal prüfen
    INCOMPLETE,
}

// Vorschlag einer Seite und was die Kontrollmessungen dazu ergeben haben.
// step        = Reglerstellung (DeadzoneSteps), die als Nächstes geprüft bzw. übernommen wird
// firstStep   = erster Vorschlag aus der Messung (Grenze für die Erhöhungen)
// checkedStep = zuletzt geprüfte Reglerstellung, null = noch nie geprüft
// verdict     = Urteil dieser letzten Prüfung
data class SideProposal(
    val step: Int,
    val firstStep: Int,
    val checkedStep: Int? = null,
    val verdict: CheckVerdict? = null,
) {
    // Der jetzige Vorschlag hat die Kontrolle bestanden → darf übernommen werden (Ü3)
    val passed: Boolean get() = verdict == CheckVerdict.PASSED && checkedStep == step

    // Nicht bestanden und keine Erhöhung mehr erlaubt → kein passender Wert gefunden (R82: stoppen)
    val exhausted: Boolean get() = verdict == CheckVerdict.FAILED && checkedStep == step

    // Muss (noch einmal) geprüft werden
    val needsCheck: Boolean get() = !passed && !exhausted
}

// Ergebnis eines Auto-Sets. Bleibt über alle Kontrollmessungen hinweg stehen, bis "Again", "Done" oder Übernehmen.
// deadzoneBefore = wirksame Deadzone vor der Messung (Bruchteile, zum Vergleich "now" und für die Nachfrage)
// measured       = 8 Felder + Ruhepunkte der Messung mit Deadzone 0 (Vorschläge, äußere Deadzone)
// proposals      = Vorschlag + Urteil je Stick, null = Stick nie losgelassen (kein Vorschlag)
// lastCheck      = Stand der letzten Kontrollmessung (für die Kreise), null = noch keine
data class AutoSetResult(
    val deadzoneBefore: LeftRight<Float>,
    val measured: LeftRight<StickReach>,
    val proposals: LeftRight<SideProposal?>,
    val lastCheck: LeftRight<StickReach>? = null,
) {
    private val sides: List<SideProposal> get() = listOfNotNull(proposals.left, proposals.right)

    // Knopf "Check": Mindestens eine Seite muss (noch einmal) geprüft werden
    val anyNeedsCheck: Boolean get() = sides.any { it.needsCheck }

    // Knopf "Apply": Mindestens eine Seite hat bestanden (Ü3: nur nach bestandener Kontrolle)
    val anyPassed: Boolean get() = sides.any { it.passed }

    // Werte für die Kontrollmessung (nur live): je Seite ihr Vorschlag, null = Seite bleibt, wie sie ist
    fun checkDeadzone(): LeftRight<Float?> = LeftRight(
        proposals.left?.let { DeadzoneSteps.toFraction(it.step) },
        proposals.right?.let { DeadzoneSteps.toFraction(it.step) },
    )

    // Werte zum Übernehmen: nur bestandene Seiten (E1), null = Seite bleibt, wie sie ist
    fun applicableDeadzone(): LeftRight<Float?> = LeftRight(
        proposals.left?.takeIf { it.passed }?.let { DeadzoneSteps.toFraction(it.step) },
        proposals.right?.takeIf { it.passed }?.let { DeadzoneSteps.toFraction(it.step) },
    )

    // Neuer Stand nach einer Kontrollmessung mit dem Ergebnis "checked"
    fun withCheck(checked: LeftRight<StickReach>): AutoSetResult = copy(
        proposals = LeftRight(
            proposals.left?.let { DeadzoneCheck.afterCheck(it, checked.left) },
            proposals.right?.let { DeadzoneCheck.afterCheck(it, checked.right) },
        ),
        lastCheck = checked,
    )

    companion object {
        // Ergebnis direkt nach der Messung mit Deadzone 0: erste Vorschläge, noch nichts geprüft
        fun ofMeasurement(deadzoneBefore: LeftRight<Float>, measured: LeftRight<StickReach>) = AutoSetResult(
            deadzoneBefore = deadzoneBefore,
            measured = measured,
            proposals = LeftRight(DeadzoneCheck.proposalOf(measured.left), DeadzoneCheck.proposalOf(measured.right)),
        )
    }
}

object DeadzoneCheck {

    // Höchstens so viele Erhöhungen über den ersten Vorschlag (Plan 3c Schritt 4, Risiko R80)
    const val MAX_RAISES = 2

    // Eine Erhöhung = 0,5 %, auch mit dem feinen Regler (Entscheidung E1 vom 19.10.2026:
    // 3c-3 Lauf B brauchte +1,0 %, feinere Erhöhungen hieße mehr Check-Runden)
    private const val RAISE_FRACTION = 0.005f
    private val RAISE_STEPS: Int = (RAISE_FRACTION / DeadzoneSteps.STEP_FRACTION).roundToInt()

    // Urteil aus dem Stand eines Sticks am Ende der Kontrollmessung.
    // Ein Rest ≠ 0 zählt sofort als "nicht bestanden", auch wenn nicht alle Felder erledigt sind
    fun verdict(reach: StickReach): CheckVerdict = when {
        reach.rest.restPoints.any { !it.isExactlyCenter() } -> CheckVerdict.FAILED
        reach.allDone -> CheckVerdict.PASSED
        else -> CheckVerdict.INCOMPLETE
    }

    // Erster Vorschlag einer Seite nach der Messung, null = der Stick wurde nie losgelassen
    fun proposalOf(measured: StickReach): SideProposal? =
        DeadzoneSuggestion.deadzoneStep(measured.rest)?.let { SideProposal(step = it, firstStep = it) }

    // Neuer Stand einer Seite nach einer Kontrollmessung (Entscheidung N1):
    // - nicht bestanden → eine Erhöhung (0,5 %) höher, wenn noch erlaubt (sonst bleibt der Schritt = exhausted)
    // - unvollständig nach einer schon bestandenen Prüfung desselben Werts → bleibt bestanden
    //   (es wurde nur nichts Neues gesehen, z. B. weil nur der andere Stick noch einmal geprüft wurde)
    fun afterCheck(proposal: SideProposal, reach: StickReach): SideProposal {
        val verdict = verdict(reach)
        if (verdict == CheckVerdict.INCOMPLETE && proposal.passed) return proposal
        val nextStep = if (verdict == CheckVerdict.FAILED) raisedStep(proposal) ?: proposal.step else proposal.step
        return proposal.copy(step = nextStep, checkedStep = proposal.step, verdict = verdict)
    }

    // Eine Erhöhung (RAISE_STEPS) höher, oben auf 20 % begrenzt.
    // null = nicht mehr erlaubt (2 Erhöhungen erreicht oder schon bei 20 %)
    private fun raisedStep(proposal: SideProposal): Int? {
        if (proposal.step >= DeadzoneSteps.MAX_STEP) return null
        val next = (proposal.step + RAISE_STEPS).coerceAtMost(DeadzoneSteps.MAX_STEP)
        if (next - proposal.firstStep > MAX_RAISES * RAISE_STEPS) return null
        return next
    }
}
