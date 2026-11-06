package com.sticklab.app

// Erlaubte Bereiche für alle Werte, die StickLab an den Odin schickt oder in Profilen speichert.
// Plan Regel 7: "Jeder Wert wird vor dem Setzen auf einen erlaubten Bereich geprüft".
// Alle Werte sind Bruchteile wie in den Odin-Einstellungen (2,5 % = 0.025)
object SettingLimits {

    // Stick-Deadzone 0–20 %: Bereich des Reglers seit v1.0. Das Odin-Menü ging im Test bis 19 %
    val DEADZONE = 0.0f..0.20f

    // Trigger-Deadzone L2/R2 5–50 %: genau der Bereich des Odin-Menüs ("Analog trigger deadzone travel",
    // gemessen 26.10.2026). Was die Firmware außerhalb davon macht, ist nicht geprüft – darum nie mehr
    val TRIGGER_DEADZONE = 0.05f..0.50f

    // Sensitivity 0,50–2,00: genau der Bereich des Odin-Menüs ("Adjust joystick sensitivity" im Gamepad test,
    // gemessen 26.10.2026). Außerhalb davon ungeprüft – darum nie mehr
    val SENSITIVITY = 0.50f..2.00f

    // Anti-Deadzone 0–30 %: Vorschlag für Phase 6 (Z8), noch ohne Wirkung
    val ANTI_DEADZONE = 0.0f..0.30f

    // Äußere Deadzone (Z10): ab diesem Ausschlag gilt der Stick als ganz gedrückt; 1,00 = aus. Untergrenze 80 %
    // (Vermutung, Phase 6 Plan): gemessen erreicht ein Stick geradeaus 95–98 % (Auto-Set 17.10.2026), tiefer
    // würde ein großer Teil des Weges verschenkt
    val OUTER_DEADZONE = 0.80f..1.00f

    // Trigger voll ab (Z11, Wunsch Besitzer 05.11.2026): ab diesem Anteil des Wegs gibt L2/R2 schon voll aus; 1,00 = aus.
    // Untergrenze 10 % (Vorschlag): darunter wäre es kaum noch ein Analog-Trigger. Im Hilfsprogramm genauso (TRIGGER_LIMIT_MIN)
    val TRIGGER_FULL_OUTPUT = 0.10f..1.00f

    // Begrenzt einen Wert auf den Bereich. Ungültige Zahlen (NaN) ergeben null = nicht setzen
    fun limit(value: Float, range: ClosedFloatingPointRange<Float>): Float? {
        if (value.isNaN()) return null
        return value.coerceIn(range)
    }
}
