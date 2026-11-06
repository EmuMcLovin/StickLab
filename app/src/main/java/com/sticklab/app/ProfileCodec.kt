package com.sticklab.app

// Wandelt ein Profil in einfache Schlüssel/Wert-Paare um und zurück, damit ProfileStore es in den
// App-Einstellungen ablegen kann. Ohne Android-Teile, darum auf dem Laptop prüfbar (ProfileCodecTest).
// Beim Zurücklesen wird jeder Wert geprüft: fehlt er oder ist er kaputt, gilt der Standardwert;
// Zahlen werden auf den erlaubten Bereich begrenzt (Plan Regel 7), Farbe und M1/M2 kommen nur aus den festen Listen
object ProfileCodec {

    // Schlüsselnamen. Nie umbenennen, sonst findet eine neue App-Version alte Profile nicht mehr
    private const val NAME = "name"
    private const val COLOR = "color"
    private const val DEADZONE = "deadzone"
    private const val SENSITIVITY = "sensitivity"
    private const val TRIGGER_DEADZONE = "triggerDeadzone"
    private const val CURVE = "curve"
    private const val ANTI_DEADZONE = "antiDeadzone"
    private const val OUTER_DEADZONE = "outerDeadzone"
    private const val CUSTOM_CURVE = "customCurve"
    private const val TRIGGER_FULL_OUTPUT = "triggerFullOutput"
    private const val M1 = "m1"
    private const val M2 = "m2"
    private const val LIGHT_MODE = "lightMode"
    private const val LEFT = ".left"
    private const val RIGHT = ".right"

    // Profil → Schlüssel/Wert-Paare (Werte sind String, Int oder Float)
    fun encode(profile: Profile): Map<String, Any> = buildMap {
        put(NAME, profile.name)
        put(COLOR, profile.color.argb)
        putPair(DEADZONE, profile.deadzone)
        putPair(SENSITIVITY, profile.sensitivity)
        putPair(TRIGGER_DEADZONE, profile.triggerDeadzone)
        putAll(encodeCurves(profile.curves))
        put(M1, profile.m1.keyCode)
        put(M2, profile.m2.keyCode)
        put(LIGHT_MODE, profile.lightMode.name)
    }

    // Schlüssel/Wert-Paare → Profil. fallbackName gilt, wenn kein brauchbarer Name gespeichert ist
    fun decode(values: Map<String, Any?>, fallbackName: String): Profile {
        val reader = Reader(values)
        return Profile(
            name = reader.name(fallbackName),
            color = ProfileColor.fromArgb(reader.int(COLOR)) ?: Profile.DEFAULT_COLOR,
            deadzone = reader.floatPair(DEADZONE, Profile.DEFAULT_DEADZONE, SettingLimits.DEADZONE),
            sensitivity = reader.floatPair(SENSITIVITY, Profile.DEFAULT_SENSITIVITY, SettingLimits.SENSITIVITY),
            triggerDeadzone = reader.floatPair(
                TRIGGER_DEADZONE, Profile.DEFAULT_TRIGGER_DEADZONE, SettingLimits.TRIGGER_DEADZONE
            ),
            curves = reader.curves(),
            m1 = reader.buttonTarget(M1, Profile.DEFAULT_M1),
            m2 = reader.buttonTarget(M2, Profile.DEFAULT_M2),
            lightMode = reader.enum(LIGHT_MODE, Profile.DEFAULT_LIGHT_MODE),
        )
    }

    // Kurven beider Sticks → Schlüssel/Wert-Paare. Auch für die Datei "curves" (CurveStore), mit denselben Namen wie im Profil
    fun encodeCurves(curves: LeftRight<CurveSettings>): Map<String, Any> = buildMap {
        putPair(CURVE, LeftRight(curves.left.curve.name, curves.right.curve.name))
        putPair(OUTER_DEADZONE, LeftRight(curves.left.outerDeadzone, curves.right.outerDeadzone))
        putPair(ANTI_DEADZONE, LeftRight(curves.left.antiDeadzone, curves.right.antiDeadzone))
        putPair(CUSTOM_CURVE, LeftRight(curves.left.custom.encode(), curves.right.custom.encode()))
        putPair(TRIGGER_FULL_OUTPUT, LeftRight(curves.left.triggerFullOutput, curves.right.triggerFullOutput))
    }

    // Schlüssel/Wert-Paare → Kurven. Profile ohne diese Felder (vor Phase 6 gespeichert) bekommen die Standardwerte
    fun decodeCurves(values: Map<String, Any?>): LeftRight<CurveSettings> = Reader(values).curves()

    private fun MutableMap<String, Any>.putPair(key: String, pair: LeftRight<out Any>) {
        put(key + LEFT, pair.left)
        put(key + RIGHT, pair.right)
    }

    // Liest einzelne Werte und ersetzt Fehlendes oder Falsches durch den Standardwert
    private class Reader(private val values: Map<String, Any?>) {

        fun name(fallback: String): String {
            val stored = (values[NAME] as? String)?.trim()?.take(Profile.MAX_NAME_LENGTH)
            return if (stored.isNullOrEmpty()) fallback else stored
        }

        fun int(key: String): Int? = values[key] as? Int

        // M1/M2: nur Zahlen aus der Menü-Liste ButtonTarget (dort ist auch "None" = 0 erlaubt). Jede andere Zahl
        // gilt als kaputt und wird zum Standardwert (Fund F2, 30. Sitzung: vorher wurde "None" still zu A)
        fun buttonTarget(key: String, default: ButtonTarget): ButtonTarget =
            ButtonTarget.fromKeyCode(int(key)) ?: default

        fun curves() = LeftRight(curveSettings(LEFT), curveSettings(RIGHT))

        // Eine Seite: jedes Feld für sich geprüft (begrenzt bzw. Standard), kaputte Punkte → Gerade
        private fun curveSettings(side: String) = CurveSettings(
            curve = enum(CURVE + side, Profile.DEFAULT_CURVE),
            outerDeadzone = float(OUTER_DEADZONE + side, CurveSettings.NO_OUTER_DEADZONE, SettingLimits.OUTER_DEADZONE),
            antiDeadzone = float(ANTI_DEADZONE + side, Profile.DEFAULT_ANTI_DEADZONE, SettingLimits.ANTI_DEADZONE),
            custom = CustomCurve.decode(values[CUSTOM_CURVE + side] as? String) ?: CustomCurve.DEFAULT,
            triggerFullOutput = float(TRIGGER_FULL_OUTPUT + side, CurveSettings.NO_TRIGGER_LIMIT, SettingLimits.TRIGGER_FULL_OUTPUT),
        )

        fun floatPair(key: String, default: Float, range: ClosedFloatingPointRange<Float>) = LeftRight(
            float(key + LEFT, default, range),
            float(key + RIGHT, default, range),
        )

        private fun float(key: String, default: Float, range: ClosedFloatingPointRange<Float>): Float {
            val stored = values[key] as? Float ?: return default
            return SettingLimits.limit(stored, range) ?: default
        }

        inline fun <reified E : Enum<E>> enum(key: String, default: E): E {
            val stored = values[key] as? String ?: return default
            return enumValues<E>().firstOrNull { it.name == stored } ?: default
        }
    }
}
