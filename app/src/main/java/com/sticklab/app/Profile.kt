package com.sticklab.app

// Ein Wert für links und rechts (Sticks) bzw. L2 und R2 (Trigger)
data class LeftRight<T>(val left: T, val right: T)

// Stick-Kurven: fünf fertige (Plan Phase 6, Z3) und die eigene aus dem Kurven-Editor (Plan Phase 7, Z4).
// Wirken erst mit der Kurven-Engine, bis dahin nur gespeichert
enum class Curve { LINEAR, PRECISION, FAST, S_CURVE, SHOOTER, CUSTOM }

// Was die Stick-LEDs zeigen (Plan Phase 5). In Phase 5 noch nicht benutzt: Ein Profil schaltet das Licht nicht
// um (Entscheidung P7 vom 26.10.2026)
enum class LightMode { OFF, AMBILIGHT, PROFILE_COLOR }

// Die 8 festen Profilfarben (Entscheidung P8 vom 26.10.2026, Farben aus PROFILE_ENTWURF.html).
// Reihenfolge = Vorschlag für neue Profile (die erste, die noch frei ist). argb = Farbe für Anzeige und Lichtblitz (Z6)
enum class ProfileColor(val argb: Int) {
    PINK(0xFFFF2D95.toInt()),
    VIOLET(0xFFBF5AF2.toInt()),
    BLUE(0xFF0A84FF.toInt()),
    TURQUOISE(0xFF00C7BE.toInt()),
    GREEN(0xFF30D158.toInt()),
    YELLOW(0xFFFFD60A.toInt()),
    ORANGE(0xFFFF9F0A.toInt()),
    RED(0xFFFF453A.toInt());

    companion object {
        // Farbe zu einem gespeicherten Wert, null = keine der 8 Farben
        fun fromArgb(argb: Int?): ProfileColor? = entries.firstOrNull { it.argb == argb }
    }
}

// Ein Profil = alle Einstellungen, die StickLab auf einmal setzen kann (Plan Phase 2, Ziele Z1–Z8).
// Zahlen sind Bruchteile wie in den Odin-Einstellungen (2,5 % = 0.025).
// M1/M2 sind Einträge der Menü-Liste ButtonTarget, nie freie Zahlen (Fund F2, 30. Sitzung)
data class Profile(
    val name: String,
    val color: ProfileColor,                 // Profilfarbe für Anzeige und Lichtblitz (Z6)
    val deadzone: LeftRight<Float>,          // Stick-Deadzone links/rechts
    val sensitivity: LeftRight<Float>,       // Stick-Sensitivity links/rechts
    val triggerDeadzone: LeftRight<Float>,   // Trigger-Deadzone L2/R2
    val curves: LeftRight<CurveSettings>,    // Kurve, äußere und Anti-Deadzone links/rechts (Z3, Z4, Z8, Z10)
    val m1: ButtonTarget,                    // Belegung von M1 (Z2)
    val m2: ButtonTarget,                    // Belegung von M2 (Z2)
    val lightMode: LightMode,
) {
    // Wirkt ein Teil dieses Profils nur im Curve mode? (Kurve, äußere und Anti-Deadzone, Trigger-Grenze)
    val needsCurveMode: Boolean get() = curves.left.needsCurveMode || curves.right.needsCurveMode

    companion object {
        // Höchstens 4 Profile (Ziel Z5) = 4 feste Plätze im Reiter Profiles
        const val MAX_PROFILES = 4

        // Längster Name: Vorschlag, damit er in die Schnellkachel passt (Z7)
        const val MAX_NAME_LENGTH = 24

        // Standardwerte für fehlende Angaben. Woher sie kommen:
        // Deadzone 2 %: Ersatzwert von v1.0, wenn der Systemwert nicht lesbar ist
        const val DEFAULT_DEADZONE = 0.02f

        // Sensitivity 0.9: am Odin des Besitzers gemessen (10.10.2026), Werkswert unbekannt
        const val DEFAULT_SENSITIVITY = 0.9f

        // Trigger 5 %: am Odin gemessen, kleinster Wert im Odin-Menü
        const val DEFAULT_TRIGGER_DEADZONE = 0.05f

        // Keine Anti-Deadzone
        const val DEFAULT_ANTI_DEADZONE = 0.0f

        // M1 = A, M2 = B: am Odin gemessen (remap_custom_to_m1/m2_value = 96/97)
        val DEFAULT_M1 = ButtonTarget.A
        val DEFAULT_M2 = ButtonTarget.B

        // Erste der 8 festen Farben
        val DEFAULT_COLOR = ProfileColor.entries.first()

        val DEFAULT_CURVE = Curve.LINEAR
        val DEFAULT_LIGHT_MODE = LightMode.OFF

        // Beide Sticks Linear, ohne äußere und Anti-Deadzone: Der Stick verhält sich wie ohne Kurve
        val DEFAULT_CURVES = LeftRight(CurveSettings(), CurveSettings())

        // Profil mit allen Standardwerten
        fun withDefaults(name: String) = Profile(
            name = name,
            color = DEFAULT_COLOR,
            deadzone = LeftRight(DEFAULT_DEADZONE, DEFAULT_DEADZONE),
            sensitivity = LeftRight(DEFAULT_SENSITIVITY, DEFAULT_SENSITIVITY),
            triggerDeadzone = LeftRight(DEFAULT_TRIGGER_DEADZONE, DEFAULT_TRIGGER_DEADZONE),
            curves = DEFAULT_CURVES,
            m1 = DEFAULT_M1,
            m2 = DEFAULT_M2,
            lightMode = DEFAULT_LIGHT_MODE,
        )
    }
}
