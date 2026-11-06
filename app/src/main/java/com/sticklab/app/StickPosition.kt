package com.sticklab.app

import kotlin.math.sqrt

// Stellung eines Sticks so, wie Android sie an Apps und Spiele gibt (Phase 3a Live-Anzeige).
// x und y je von -1 bis +1: Rohwert des Controllers geteilt durch 32767 (gemessen in Test A, 10.10.2026).
// x: links negativ, rechts positiv. y: oben negativ, unten positiv (wie die Bildschirm-Richtung).
// Ohne Android-Teile, damit die Rechnung auf dem Laptop geprüft werden kann (StickPositionTest)
data class StickPosition(val x: Float, val y: Float) {

    // Abstand zur Mitte in Prozent des vollen Ausschlags, gemessen als Ganzes (radial), nicht pro Achse.
    // Schräg in der Ecke kann das über 100 % liegen (bis ca. 141 %), wenn beide Achsen am Anschlag sind
    fun distancePercent(): Float = length() * PERCENT

    // Genau in der Mitte = das, was eine wirksame Deadzone in Ruhe liefern soll ("exakt 0")
    fun isExactlyCenter(): Boolean = x == 0f && y == 0f

    // Dieselbe Richtung, aber höchstens bis zum Kreisrand. Für den Punkt in der Anzeige,
    // damit er in der Ecke nicht aus dem Kreis wandert
    fun clampedToCircle(): StickPosition {
        val length = length()
        if (length <= 1f) return this
        return StickPosition(x / length, y / length)
    }

    private fun length(): Float = sqrt(x * x + y * y)

    companion object {
        val CENTER = StickPosition(0f, 0f)

        private const val PERCENT = 100f

        // Baut eine Stellung aus den zwei Achsen-Werten von Android. Werte außerhalb von -1..+1 werden
        // begrenzt; ungültige Zahlen (NaN, unendlich) werden 0. Beides liefert Android normalerweise nicht
        // (Vermutung, nie beobachtet) – so kann die Anzeige nie aus dem Ruder laufen
        fun fromAxes(x: Float, y: Float) = StickPosition(cleanAxis(x), cleanAxis(y))

        private fun cleanAxis(value: Float): Float =
            if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f
    }
}

// Druck auf einen Trigger (L2 oder R2) in Prozent, 0 = losgelassen, 100 = ganz gedrückt.
// Android gibt Trigger als 0 bis 1 (Rohwert 0–32767, Test C0 10.10.2026)
object TriggerPressure {

    private const val PERCENT = 100f

    // Wandelt den Achsen-Wert von Android in Prozent. Begrenzt auf 0–100 %, ungültige Zahlen → 0 %
    fun percentOf(axisValue: Float): Float =
        if (axisValue.isFinite()) axisValue.coerceIn(0f, 1f) * PERCENT else 0f
}

// Alles, was die Live-Anzeige auf einmal zeigt: beide Sticks und beide Trigger
data class LiveInput(
    val left: StickPosition,
    val right: StickPosition,
    val l2Percent: Float,
    val r2Percent: Float,
)
