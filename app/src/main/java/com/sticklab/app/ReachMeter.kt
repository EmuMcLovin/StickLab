package com.sticklab.app

import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt

// Auto-Set mit 8 Richtungen (Phase 3c): Jeder Stick wird in 8 Richtungen bis zum Anschlag gedrückt und
// losgelassen. Diese Datei zählt mit, welche Richtung schon erledigt ist, wie weit der Stick dort kam
// (für die äußere Deadzone, Z10) und wo er nach dem Loslassen liegen blieb (DriftMeter, für die Deadzone).
// Ohne Android-Teile, damit die Regeln auf dem Laptop geprüft werden können (ReachMeterTest)

// Die 8 Richtungen, im Uhrzeigersinn ab oben. Die Reihenfolge ist auch die der Felder im Kreis
enum class StickDirection {
    UP, UP_RIGHT, RIGHT, DOWN_RIGHT, DOWN, DOWN_LEFT, LEFT, UP_LEFT;

    companion object {
        // Jede Richtung deckt 45° um ihre Mitte ab (oben = −22,5° bis +22,5°)
        private val DEGREES_PER_DIRECTION = 360.0 / entries.size

        // Richtung einer Stellung. Ein Winkel genau auf der Grenze gehört zur nächsten Richtung im
        // Uhrzeigersinn (roundToInt rundet ,5 nach oben)
        fun of(position: StickPosition): StickDirection {
            // Winkel ab oben im Uhrzeigersinn. y zeigt in StickPosition nach unten, darum −y
            val degrees = Math.toDegrees(atan2(position.x.toDouble(), -position.y.toDouble()))
            val index = (degrees / DEGREES_PER_DIRECTION).roundToInt().mod(entries.size)
            return entries[index]
        }
    }
}

// Stand eines Felds: offen, Anschlag erreicht (aber noch nicht losgelassen), erledigt
enum class FieldState { OPEN, REACHED, DONE }

// Ein Feld: größter Ausschlag in dieser Richtung (null = noch nie dorthin gedrückt) und sein Stand
data class ReachField(val peakPercent: Float?, val state: FieldState)

// Momentaufnahme eines Sticks für die Anzeige: 8 Felder (Reihenfolge wie StickDirection) und die Ruhepunkte
data class StickReach(val fields: List<ReachField>, val rest: DriftResult) {

    val doneCount: Int get() = fields.count { it.state == FieldState.DONE }

    val allDone: Boolean get() = doneCount == fields.size

    fun field(direction: StickDirection): ReachField = fields[direction.ordinal]
}

// Misst einen Stick
class ReachMeter {

    private val drift = DriftMeter()
    private val peaks = MutableList<Float?>(StickDirection.entries.size) { null }
    private val states = MutableList(StickDirection.entries.size) { FieldState.OPEN }

    // Richtung, in der der Stick zuletzt am Anschlag war (seit dem letzten Ruhepunkt), null = seitdem nicht.
    // Nur dieses Feld wird beim nächsten Loslassen erledigt (3c-3, Entscheidung F4a vom 17.10.2026)
    private var lastEdgeDirection: StickDirection? = null

    // Neuer Wert vom Controller. Zuerst prüfen, ob der vorige Wert ein Ruhepunkt war (dann ist das Feld
    // erledigt, aus dem losgelassen wurde), danach den neuen Wert eintragen
    fun add(timeMs: Long, position: StickPosition) {
        if (drift.add(timeMs, position)) markReleasedFieldAsDone()
        record(position)
    }

    // Ohne neue Meldung nachsehen, ob der Stick inzwischen liegen geblieben ist (MainActivity, alle 0,1 s)
    fun settle(nowMs: Long) {
        if (drift.settle(nowMs)) markReleasedFieldAsDone()
    }

    // Stand für die Anzeige
    fun snapshot(): StickReach =
        StickReach(peaks.indices.map { ReachField(peaks[it], states[it]) }, drift.resultSoFar())

    // Ergebnis zum Ende der Messung (der letzte Wert zählt als Ruhepunkt, wenn er lange genug still lag)
    fun result(endTimeMs: Long): StickReach {
        settle(endTimeMs)
        return snapshot()
    }

    // Größten Ausschlag der Richtung merken; ab REACH_PERCENT gilt das Feld als "Anschlag erreicht" und die
    // Richtung als die, aus der gerade losgelassen werden kann.
    // Werte nahe der Mitte zählen für keine Richtung: Das ist Rest/Drift, kein Drücken
    private fun record(position: StickPosition) {
        val percent = position.distancePercent()
        if (percent < MIN_PUSH_PERCENT) return
        val direction = StickDirection.of(position)
        val index = direction.ordinal
        peaks[index] = max(peaks[index] ?: 0f, percent)
        if (percent < REACH_PERCENT) return
        lastEdgeDirection = direction
        if (states[index] == FieldState.OPEN) states[index] = FieldState.REACHED
    }

    // Losgelassen: Nur das Feld, in dem der Stick zuletzt am Anschlag war, ist jetzt erledigt.
    // Früher wurden alle Felder mit erreichtem Anschlag auf einmal erledigt – einmal im Kreis am Rand entlang
    // und einmal loslassen ergab so 8 grüne Felder mit nur einem Rest (Fund F4, 25. Sitzung).
    // So braucht jeder Stick mindestens 8 Loslass-Punkte, in der Messung und in der Kontrolle
    private fun markReleasedFieldAsDone() {
        val direction = lastEdgeDirection ?: return
        lastEdgeDirection = null
        if (states[direction.ordinal] == FieldState.REACHED) states[direction.ordinal] = FieldState.DONE
    }

    companion object {
        // Ab diesem Ausschlag gilt eine Richtung als "bis zum Anschlag gedrückt" (Plan 3c, 17.10.2026).
        // Der Stick kam geradeaus auf ca. 95–97 % (17. Sitzung), 80 % lässt Luft für Sticks mit weniger Weg
        const val REACH_PERCENT = 80f

        // Darunter zählt ein Wert für keine Richtung: dieselbe Grenze wie für Ruhepunkte (DriftMeter)
        const val MIN_PUSH_PERCENT = DriftMeter.REST_MAX_PERCENT
    }
}

// Auto-Set für beide Sticks auf einmal (Entscheidung R1: gleichzeitig, Reihenfolge egal)
class ReachSession {
    private val left = ReachMeter()
    private val right = ReachMeter()

    // Neue Meldung vom Controller (beide Sticks)
    fun add(timeMs: Long, input: LiveInput) {
        left.add(timeMs, input.left)
        right.add(timeMs, input.right)
    }

    fun settle(nowMs: Long) {
        left.settle(nowMs)
        right.settle(nowMs)
    }

    fun snapshot() = LeftRight(left.snapshot(), right.snapshot())

    fun result(endTimeMs: Long) = LeftRight(left.result(endTimeMs), right.result(endTimeMs))
}
