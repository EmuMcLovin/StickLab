package com.sticklab.app

// Loslass-Messung (Phase 3b, seit 3c auch während der Messung): findet die Stellen, an denen ein Stick nach
// dem Loslassen liegen bleibt.
// Warum "nach dem Loslassen": In Ruhe schickt der Controller gar keine Meldungen (Test A, 10.10.2026).
// Ein Ruhepunkt ist darum der letzte Wert vor einer Pause ohne neue Meldung (Stille).
// Seit 3c wird schon während der Messung erkannt, wann ein Ruhepunkt feststeht (add/settle), damit das
// Auto-Set ein Feld sofort grün machen kann. Die Regel ist dieselbe wie in 3b (mit getevent geprüft, 23. Sitzung).
// Ohne Android-Teile, damit die Regel auf dem Laptop geprüft werden kann (DriftMeterTest)
class DriftMeter {

    // Ein Messwert: Zeit (ms, SystemClock.uptimeMillis wie MotionEvent.eventTime) und Stellung
    private data class Sample(val timeMs: Long, val position: StickPosition)

    // Der zuletzt gesehene Wert und ob über ihn schon entschieden ist (Ruhepunkt oder festgehalten)
    private var last: Sample? = null
    private var lastDecided = false

    private val restPoints = mutableListOf<StickPosition>()

    // Neuer Wert vom Controller. Gleiche Stellung wie zuletzt wird übersprungen: Jede Meldung enthält
    // beide Sticks, auch wenn sich nur der andere bewegt hat – das wäre keine neue Bewegung dieses Sticks.
    // Vorher wird der bisherige letzte Wert beurteilt. true = er ist gerade zum Ruhepunkt geworden
    fun add(timeMs: Long, position: StickPosition): Boolean {
        if (last?.position == position) return false
        val becameRestPoint = settle(timeMs)
        last = Sample(timeMs, position)
        lastDecided = false
        return becameRestPoint
    }

    // Zum Zeitpunkt nowMs nachsehen, ob der letzte Wert inzwischen lange genug still liegt.
    // Ruft MainActivity auch ohne neue Meldung regelmäßig auf (in Ruhe kommt ja nichts mehr).
    // true = gerade ein neuer Ruhepunkt dazugekommen
    fun settle(nowMs: Long): Boolean {
        val sample = last ?: return false
        if (lastDecided || nowMs - sample.timeMs < QUIET_MS) return false
        lastDecided = true
        // Weit weg von der Mitte = Stick wird noch festgehalten, kein Ruhepunkt
        if (sample.position.distancePercent() >= REST_MAX_PERCENT) return false
        restPoints += sample.position
        return true
    }

    // Ruhepunkte bis jetzt (ohne den letzten Wert zu beurteilen)
    fun resultSoFar(): DriftResult = DriftResult(restPoints.toList())

    // Ergebnis zum Zeitpunkt endTimeMs (Ende der Messung): der letzte Wert zählt nur,
    // wenn er bis dahin schon QUIET_MS still lag
    fun result(endTimeMs: Long): DriftResult {
        settle(endTimeMs)
        return resultSoFar()
    }

    companion object {
        // Stille, ab der ein Stick als "liegen geblieben" gilt. Vorschlag aus dem Plan 3b (16.10.2026):
        // Android meldet Bewegungen etwa jedes Bild (wenige ms), 300 ms Pause heißt "keine Bewegung mehr"
        const val QUIET_MS = 300L

        // Größter Abstand zur Mitte, der noch als Ruhepunkt zählt. Drift lag in Test A bei höchstens
        // ca. 2,2 %; 10 % lässt Luft und schließt einen festgehaltenen Stick aus (Plan 3b)
        const val REST_MAX_PERCENT = 10f
    }
}

// Ergebnis der Loslass-Messung für einen Stick
data class DriftResult(val restPoints: List<StickPosition>) {

    // Wie oft der Stick liegen geblieben ist
    val count: Int get() = restPoints.size

    // Größter Rest nach dem Loslassen in Prozent, null = kein einziges Mal losgelassen
    val maxPercent: Float? get() = restPoints.maxOfOrNull { it.distancePercent() }

    // Liegt der größte Rest innerhalb der Deadzone (Bruchteil, 2,5 % = 0.025)? null = keine Daten.
    // Vermutung (R73): Prozent in der App und in der Firmware bedeuten dasselbe – prüft erst 3c-2
    fun fitsInside(deadzoneFraction: Float): Boolean? =
        maxPercent?.let { it <= deadzoneFraction * PERCENT }

    private companion object {
        const val PERCENT = 100f
    }
}
