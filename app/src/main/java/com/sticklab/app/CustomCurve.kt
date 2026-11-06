package com.sticklab.app

import java.util.Locale

// Ein Punkt der eigenen Kurve: x = Stick-Ausschlag, y = Ausgabe, beide von 0 bis 1
data class CurvePoint(val x: Float, val y: Float)

// Die eigene Kurve aus dem Kurven-Editor (Plan Phase 7, Z4). Ohne Android-Teile, auf dem Laptop geprüft
// (CustomCurveTest). Man verschiebt 3 bis 5 Punkte, Anfang (0/0) und Ende (1/1) stehen fest.
// Die Punkte werden so verbunden, dass die Kurve nie fällt: Sonst liefe der Stick stellenweise rückwärts (R136).
// Jeder Weg, der Punkte ändert, hält die Regeln ein: Punkte steigen von links nach rechts, mit Mindestabstand
data class CustomCurve(val points: List<CurvePoint>) {

    // Ausgabe (0 bis 1) für einen Ausschlag (0 bis 1). Stückweise kubisch nach Fritsch und Butland: Die Steigung an
    // einem Punkt ist höchstens das Dreifache der kleineren Nachbar-Steigung, das garantiert "fällt nie"
    fun outputAt(x: Float): Float {
        val knots = listOf(START) + points + END
        val position = x.coerceIn(0f, 1f)
        val segment = (0 until knots.size - 1).first { position <= knots[it + 1].x }
        val left = knots[segment]
        val right = knots[segment + 1]
        val width = right.x - left.x
        val t = (position - left.x) / width
        val leftSlope = slopeAt(knots, segment)
        val rightSlope = slopeAt(knots, segment + 1)
        val t2 = t * t
        val t3 = t2 * t
        val output = (2 * t3 - 3 * t2 + 1) * left.y +
            (t3 - 2 * t2 + t) * width * leftSlope +
            (-2 * t3 + 3 * t2) * right.y +
            (t3 - t2) * width * rightSlope
        return output.coerceIn(left.y, right.y)
    }

    // Verschiebt einen Punkt, begrenzt auf den Platz zwischen seinen Nachbarn (Mindestabstand waagrecht, nie tiefer
    // als der linke oder höher als der rechte Nachbar)
    fun withPointMoved(index: Int, x: Float, y: Float): CustomCurve {
        if (index !in points.indices || x.isNaN() || y.isNaN()) return this
        val left = points.getOrNull(index - 1) ?: START
        val right = points.getOrNull(index + 1) ?: END
        val smallestX = left.x + MIN_GAP
        // maxOf: Nach dem Runden beim Speichern können die Nachbarn minimal enger stehen als zwei Mindestabstände
        val moved = CurvePoint(x.coerceIn(smallestX, maxOf(smallestX, right.x - MIN_GAP)), y.coerceIn(left.y, right.y))
        return copy(points = points.toMutableList().also { it[index] = moved })
    }

    // Neuer Punkt mitten in der breitesten Lücke, genau auf der Kurve (ändert ihre Form kaum). Bei MAX_POINTS: unverändert
    fun withPointAdded(): CustomCurve {
        if (points.size >= MAX_POINTS) return this
        val knots = listOf(START) + points + END
        val widest = (0 until knots.size - 1).maxBy { knots[it + 1].x - knots[it].x }
        val x = (knots[widest].x + knots[widest + 1].x) / 2f
        val added = CurvePoint(x, outputAt(x))
        return copy(points = points.toMutableList().also { it.add(widest, added) })
    }

    // Entfernt einen Punkt. Bei MIN_POINTS: unverändert
    fun withPointRemoved(index: Int): CustomCurve {
        if (points.size <= MIN_POINTS || index !in points.indices) return this
        return copy(points = points.toMutableList().also { it.removeAt(index) })
    }

    // Stimmen alle Regeln? (Gespeichertes wird so geprüft, bevor es benutzt wird)
    fun isValid(): Boolean {
        if (points.size !in MIN_POINTS..MAX_POINTS) return false
        val knots = listOf(START) + points + END
        return points.all { it.x in 0f..1f && it.y in 0f..1f } &&
            (0 until knots.size - 1).all { knots[it + 1].x - knots[it].x >= MIN_GAP - GAP_TOLERANCE && knots[it + 1].y >= knots[it].y }
    }

    // Text für die Einstellungs-Datei: "0.25:0.31;0.5:0.5;0.75:0.8"
    fun encode(): String = points.joinToString(POINT_SEPARATOR) { "%.4f$VALUE_SEPARATOR%.4f".format(Locale.ROOT, it.x, it.y) }

    // Steigung der Kurve an Stützpunkt i (Fritsch und Butland): an den Enden die des Stücks daneben,
    // innen ein gewichteter Mittelwert der beiden Stücke, 0 wenn eines davon flach ist
    private fun slopeAt(knots: List<CurvePoint>, i: Int): Float {
        fun piece(a: Int) = (knots[a + 1].y - knots[a].y) / (knots[a + 1].x - knots[a].x)
        if (i == 0) return piece(0)
        if (i == knots.size - 1) return piece(knots.size - 2)
        val before = piece(i - 1)
        val after = piece(i)
        if (before <= 0f || after <= 0f) return 0f
        val widthBefore = knots[i].x - knots[i - 1].x
        val widthAfter = knots[i + 1].x - knots[i].x
        return 3f * (widthBefore + widthAfter) /
            ((2f * widthAfter + widthBefore) / before + (widthAfter + 2f * widthBefore) / after)
    }

    companion object {
        // Plan Phase 7: "3 bis 5 Punkte"
        const val MIN_POINTS = 3
        const val MAX_POINTS = 5

        // Waagrechter Mindestabstand 2 % des Ausschlags: Punkt-Griffe bleiben unterscheidbar, keine Teilung durch fast 0
        private const val MIN_GAP = 0.02f

        // Gespeichert wird auf 4 Stellen gerundet, darum kann ein Abstand beim Zurücklesen knapp kleiner sein
        private const val GAP_TOLERANCE = 0.0002f

        private const val POINT_SEPARATOR = ";"
        private const val VALUE_SEPARATOR = ":"

        private val START = CurvePoint(0f, 0f)
        private val END = CurvePoint(1f, 1f)

        // Anfang des Editors: drei gleichmäßig verteilte Punkte auf der Geraden
        val DEFAULT = CustomCurve(listOf(CurvePoint(0.25f, 0.25f), CurvePoint(0.5f, 0.5f), CurvePoint(0.75f, 0.75f)))

        // Text aus der Einstellungs-Datei → Kurve. Kaputt oder regelwidrig → null (dann gilt DEFAULT)
        fun decode(text: String?): CustomCurve? {
            if (text.isNullOrBlank()) return null
            val points = text.split(POINT_SEPARATOR).map { part ->
                val values = part.split(VALUE_SEPARATOR)
                if (values.size != 2) return null
                val x = values[0].toFloatOrNull() ?: return null
                val y = values[1].toFloatOrNull() ?: return null
                CurvePoint(x, y)
            }
            return CustomCurve(points).takeIf { it.isValid() }
        }

        // Startpunkt des Editors aus einer fertigen Kurve: drei Punkte auf ihr, die eigene Kurve sieht dann ähnlich aus
        fun sampledFrom(curve: Curve): CustomCurve =
            CustomCurve(DEFAULT.points.map { CurvePoint(it.x, CurveTable.shape(curve, it.x)) })
    }
}
