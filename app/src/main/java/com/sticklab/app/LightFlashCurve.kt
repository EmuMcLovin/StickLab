package com.sticklab.app

// Die Farben beider Stick-LEDs, so wie sie am Odin in der Systemeinstellung stehen: "#aarrggbb,#aarrggbb"
// (links, rechts; Format gelesen 15.10.2026, OdinService.LED_COLOR_KEY). Ohne Android-Teile, auf dem Laptop
// geprüft (LightFlashCurveTest)
data class LedColors(val left: Int, val right: Int) {

    // Text für die Systemeinstellung, z. B. "#ff0a84ff,#ff0a84ff"
    fun toSetting(): String = "${hex(left)},${hex(right)}"

    companion object {
        // Eine Farbe: "#" und 8 Hex-Ziffern (Alpha, Rot, Grün, Blau)
        private val ONE_COLOR = Regex("#[0-9a-fA-F]{8}")

        // Liest den Text der Systemeinstellung. null = fehlt oder unbekanntes Format (dann blitzt nichts,
        // weil die Farbe von vorher nicht sicher zurückgestellt werden könnte)
        fun parse(text: String?): LedColors? {
            val parts = text?.split(",") ?: return null
            if (parts.size != 2 || parts.any { !ONE_COLOR.matches(it) }) return null
            return LedColors(argbOf(parts[0]), argbOf(parts[1]))
        }

        // Beide Seiten in derselben Farbe (Profilfarbe)
        fun both(argb: Int) = LedColors(argb, argb)

        private fun argbOf(part: String): Int = part.substring(1).toLong(16).toInt()

        private fun hex(argb: Int): String = "#%08x".format(argb)
    }
}

// Verlauf des Lichtblitzes beim Profilwechsel (Phase 5c, Ziel Z6): weich in die Profilfarbe, kurz halten, weich
// zurück zur Farbe von vorher – nicht blinken (Plan Phase 5). Rechnet nach vergangener Zeit, nicht nach Schritten:
// Dauert ein LED-Befehl länger, gibt es weniger Schritte, der Blitz bleibt gleich lang (Plan 5c, G4)
object LightFlashCurve {

    // Zeiten (Entscheidung L2 vom 27.10.2026: 1,0 s wie im Plan Phase 5 vom 26.10.2026)
    const val FADE_IN_MS = 250L
    const val HOLD_MS = 400L
    const val FADE_OUT_MS = 350L
    const val TOTAL_MS = FADE_IN_MS + HOLD_MS + FADE_OUT_MS

    // Abstand der Schritte: ca. 20 pro Sekunde (Plan 5c). Das Ambilight schickt seit v1.0 bis zu 40 pro Sekunde
    const val STEP_MS = 50L

    // Alle Farbteile beim Blitz voll deckend, wie beim Ambilight ("#ff…")
    private const val OPAQUE = 0xFF

    // Anteil der Profilfarbe zum Zeitpunkt elapsedMs nach dem Start: 0 = Farbe von vorher, 1 = Profilfarbe
    fun mix(elapsedMs: Long): Float = when {
        elapsedMs <= 0L -> 0f
        elapsedMs < FADE_IN_MS -> smooth(elapsedMs.toFloat() / FADE_IN_MS)
        elapsedMs < FADE_IN_MS + HOLD_MS -> 1f
        elapsedMs < TOTAL_MS -> 1f - smooth((elapsedMs - FADE_IN_MS - HOLD_MS).toFloat() / FADE_OUT_MS)
        else -> 0f
    }

    // Ist der Blitz zu diesem Zeitpunkt vorbei? Dann wird genau die Farbe von vorher geschickt
    fun isFinished(elapsedMs: Long): Boolean = elapsedMs >= TOTAL_MS

    // Farben beider LEDs zum Zeitpunkt elapsedMs: zwischen der Farbe von vorher und der Profilfarbe gemischt
    fun colorsAt(elapsedMs: Long, before: LedColors, flashArgb: Int): LedColors {
        val share = mix(elapsedMs)
        return LedColors(blend(before.left, flashArgb, share), blend(before.right, flashArgb, share))
    }

    // Weicher Anfang und weiches Ende eines Übergangs (Smoothstep), x von 0 bis 1
    private fun smooth(x: Float): Float = x * x * (3f - 2f * x)

    // Mischt Rot, Grün und Blau zweier Farben; share = Anteil der zweiten
    private fun blend(from: Int, to: Int, share: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return Math.round(a + (b - a) * share).coerceIn(0, 0xFF)
        }
        return (OPAQUE shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
