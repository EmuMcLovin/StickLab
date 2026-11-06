package com.sticklab.app

import kotlin.math.pow

// Einstellungen der Kurven-Engine für einen Stick (Phase 6, Z3/Z8/Z10). Werte als Bruchteile wie überall:
// outerDeadzone 0.95 = ab 95 % Ausschlag gilt der Stick als ganz gedrückt (1.0 = aus),
// antiDeadzone 0.10 = die Ausgabe beginnt bei 10 % statt bei 0
data class CurveSettings(
    val curve: Curve = Curve.LINEAR,
    val outerDeadzone: Float = NO_OUTER_DEADZONE,
    val antiDeadzone: Float = Profile.DEFAULT_ANTI_DEADZONE,
    val custom: CustomCurve = CustomCurve.DEFAULT,       // Punkte der eigenen Kurve, zählen nur bei Curve.CUSTOM (Z4)
    val triggerFullOutput: Float = NO_TRIGGER_LIMIT,     // Trigger dieser Seite (links = L2, rechts = R2) voll ab (Z11)
) {
    // Braucht diese Seite den Curve mode, um zu wirken? Sonst verhält sie sich ohnehin wie ohne Kurve
    val needsCurveMode: Boolean
        get() = curve != Curve.LINEAR || outerDeadzone < NO_OUTER_DEADZONE ||
            antiDeadzone > Profile.DEFAULT_ANTI_DEADZONE || triggerFullOutput < NO_TRIGGER_LIMIT

    companion object {
        // Äußere Deadzone aus: erst bei 100 % Ausschlag ist der Stick ganz gedrückt
        const val NO_OUTER_DEADZONE = 1.0f

        // Keine Trigger-Grenze: erst am Ende des Wegs volle Ausgabe
        const val NO_TRIGGER_LIMIT = 1.0f
    }
}

// Rechnung der Kurven-Engine (Phase 6, Schritt 6a; Entscheidungen K6-1–K6-4 vom 27.10.2026). Ohne Android-Teile,
// auf dem Laptop geprüft (CurveTableTest).
// Gerechnet wird mit der Länge des Stick-Ausschlags (radial, 0 = Mitte, 1 = Rand), nicht je Achse – die Richtung
// bleibt, das macht das Hilfsprogramm (K6-4: auf den Kreis begrenzt). Die Firmware-Deadzone ist dabei schon
// abgezogen (A40), darum gibt es hier keine zweite Deadzone.
// Reihenfolge (Plan Phase 6): äußere Deadzone → Kurve → Anti-Deadzone → begrenzen.
// Das Hilfsprogramm rechnet nicht selbst (K6-2): Die App baut daraus eine Tabelle mit 256 Punkten, das Programm
// schlägt nur nach und verbindet zwei Punkte gerade. isValid ist die Prüfregel, die das Programm genauso anwendet
object CurveTable {

    // 256 Stützpunkte von Ausschlag 0 bis 1 (Plan Phase 6: "Tabelle mit 256 Werten")
    const val SIZE = 256

    // Größter Achsenwert des Odin-Controllers (event8: −32767 bis 32767, gelesen 10.10.2026)
    const val MAX_OUTPUT = 32767

    // Anti-Deadzone erst ab diesem Ausschlag, darunter Ausgabe 0 (K6-3): sonst würde ein Drift-Rest knapp über
    // der Firmware-Deadzone (gemessen 0,05–0,3 %) im Spiel zur Bewegung (R137)
    const val ANTI_DEADZONE_THRESHOLD = 0.01f

    // Kurvenformen (Plan Phase 6 "Fertige Kurven"; Shooter am Gerät abgestimmt, siehe SHOOTER_BASE)
    private const val PRECISION_EXPONENT = 1.8f   // sanfter Anfang
    private const val FAST_EXPONENT = 0.6f        // schneller Anfang
    // Shooter: fein zielen in der Mitte, zum Rand hin schnell, ohne Knick. Anteil der Geraden und Hochzahl des steilen Teils.
    // Erste Fassung (flach bis 80 %, dann gerade steil) war dem Besitzer im Spiel zu extrem (CoD3, 05.11.2026): erst sehr
    // langsam, dann abrupter Sprung. Diese Werte: 25/50/75/90 % Ausschlag → 18/36/60/81 % Ausgabe, noch am Gerät zu prüfen
    private const val SHOOTER_BASE = 0.70f
    private const val SHOOTER_POWER = 5f

    // Die fertige Tabelle: Punkt i gehört zum Ausschlag i / 255, Wert 0 bis MAX_OUTPUT.
    // Werte außerhalb der erlaubten Bereiche werden vorher begrenzt (SettingLimits, Plan Regel 7)
    fun build(settings: CurveSettings): IntArray {
        val safe = limited(settings)
        return IntArray(SIZE) { index ->
            val reach = index.toFloat() / (SIZE - 1)
            Math.round(outputFor(safe, reach) * MAX_OUTPUT).coerceIn(0, MAX_OUTPUT)
        }
    }

    // Ausgabe (0 bis 1) für einen Ausschlag (0 bis 1). Ohne Begrenzen der Einstellungen – dafür build benutzen
    fun outputFor(settings: CurveSettings, reach: Float): Float {
        if (reach <= 0f) return 0f
        val stretched = (reach / settings.outerDeadzone).coerceAtMost(1f)
        val shaped = if (settings.curve == Curve.CUSTOM) settings.custom.outputAt(stretched) else shape(settings.curve, stretched)
        val anti = settings.antiDeadzone
        if (anti <= 0f) return shaped.coerceIn(0f, 1f)
        if (reach < ANTI_DEADZONE_THRESHOLD) return 0f
        return (anti + (1f - anti) * shaped).coerceIn(0f, 1f)
    }

    // Form einer fertigen Kurve für x von 0 bis 1; jede beginnt bei 0, endet bei 1 und fällt nie.
    // Die eigene Kurve hat ihre Form in den Punkten (CustomCurve), hier zählt sie wie Linear
    fun shape(curve: Curve, x: Float): Float = when (curve) {
        Curve.LINEAR, Curve.CUSTOM -> x
        Curve.PRECISION -> x.pow(PRECISION_EXPONENT)
        Curve.FAST -> x.pow(FAST_EXPONENT)
        Curve.S_CURVE -> x * x * (3f - 2f * x)
        Curve.SHOOTER -> shooter(x)
    }

    // Prüfregel für eine Tabelle (die gleiche prüft das Hilfsprogramm, bevor es sie benutzt; sonst 1:1):
    // richtige Länge, beginnt bei 0, endet bei MAX_OUTPUT, steigt nie ab (sonst liefe der Stick rückwärts, R136)
    fun isValid(table: IntArray): Boolean =
        table.size == SIZE &&
            table.first() == 0 &&
            table.last() == MAX_OUTPUT &&
            table.all { it in 0..MAX_OUTPUT } &&
            (1 until SIZE).all { table[it] >= table[it - 1] }

    // Shooter: gerader Anteil für feines Zielen, dazu ein Anteil, der erst zum Rand hin stark wächst. Beide Teile steigen immer,
    // zusammen also ohne Knick und ohne Rückwärtslauf
    private fun shooter(x: Float): Float = SHOOTER_BASE * x + (1f - SHOOTER_BASE) * x.pow(SHOOTER_POWER)

    // Einstellungen auf die erlaubten Bereiche (ungültige Zahl → Standard, regelwidrige eigene Kurve → Gerade)
    private fun limited(settings: CurveSettings) = settings.copy(
        custom = settings.custom.takeIf { it.isValid() } ?: CustomCurve.DEFAULT,
        outerDeadzone = SettingLimits.limit(settings.outerDeadzone, SettingLimits.OUTER_DEADZONE)
            ?: CurveSettings.NO_OUTER_DEADZONE,
        antiDeadzone = SettingLimits.limit(settings.antiDeadzone, SettingLimits.ANTI_DEADZONE)
            ?: Profile.DEFAULT_ANTI_DEADZONE,
    )
}
