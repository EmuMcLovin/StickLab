package com.sticklab.app

// Belegung der Rückseiten-Tasten M1/M2 (Plan Phase 4, Ziel Z2). Ohne Android-Teile, darum auf dem Laptop
// prüfbar (ButtonTargetTest). Alle Zahlen am Odin gemessen (PROJEKT_DOKU Abschnitt 2 "M1/M2"):
// Test B (10.10.2026), Liste des Odin-Menüs und Zahl je Eintrag (4.0/4.0c, 26.10.2026),
// Tasten im Odin-Layout (4.0b, 26.10.2026)

// Die zwei Tasten hinten an den Griffen (M1 links, M2 rechts) mit ihrer Systemeinstellung.
// Der Wert der Einstellung ist eine Android-Tastennummer; das Odin-Menü schreibt genau diese Einstellung
enum class BackButton(val settingKey: String) {
    M1("remap_custom_to_m1_value"),
    M2("remap_custom_to_m2_value"),
}

// Was M1/M2 senden können: genau die 22 Einträge des Odin-Menüs "M1 key mapping", in derselben Reihenfolge
// (Entscheidung B1). menuName = Name im Odin-Menü, keyCode = Zahl, die das Menü dafür schreibt.
// Andere Zahlen schreibt StickLab nie (Plan Regel 7)
enum class ButtonTarget(val menuName: String, val keyCode: Int) {
    DEFAULT("Default", 98),          // = Android KEYCODE_BUTTON_C; was M1 damit sendet, ist ungeprüft
    MODE("Mode", 110),
    APP_SWITCH("APP_SWITCH", 187),
    HOME("HOME", 3),
    SELECT("SELECT", 109),
    START("START", 108),
    BACK("BACK", 4),
    A("A", 96),
    B("B", 97),
    X("X", 99),
    Y("Y", 100),
    L1("L1", 102),
    L2("L2", 104),
    L3("L3", 106),
    R1("R1", 103),
    R2("R2", 105),
    R3("R3", 107),
    DOWN("DOWN", 20),
    UP("UP", 19),
    LEFT("LEFT", 21),
    RIGHT("RIGHT", 22),
    NONE("None", 0);                 // vermutlich: M1 sendet nichts (ungeprüft)

    // Eine der vier Tasten A/B/X/Y? Nur deren Namen hängen vom Layout ab
    val isFaceButton: Boolean get() = this == A || this == B || this == X || this == Y

    // Nächster bzw. vorheriger Eintrag der Liste, am Ende geht es von vorne weiter (Steuerkreuz links/rechts)
    fun stepped(delta: Int): ButtonTarget {
        val count = entries.size
        return entries[((ordinal + delta) % count + count) % count]
    }

    companion object {
        // Eintrag zu einer gelesenen Zahl, null = keine Zahl aus der Liste
        fun fromKeyCode(keyCode: Int?): ButtonTarget? = entries.firstOrNull { it.keyCode == keyCode }

        // Eintrag zum Text einer Systemeinstellung ("96" → A), null = fehlt oder unbekannt
        fun fromSettingText(text: String?): ButtonTarget? = fromKeyCode(text?.trim()?.toIntOrNull())
    }
}

// Das Controller-Layout aus dem Odin-Menü. Es entscheidet, welche Taste am Gerät welche Android-Taste sendet
enum class ControllerLayout {
    XBOX,     // flip_button_layout = 1: A↔B und X↔Y gegenüber dem Aufdruck vertauscht (Test B, Stufe 4.0/4.2)
    ODIN,     // flip_button_layout = 0: jede Taste sendet, was auf ihr steht (4.0b)
    UNKNOWN;  // Einstellung fehlt oder hat einen anderen Wert

    companion object {
        // Name der Systemeinstellung, die das Odin-Menü beim Layout-Wechsel umstellt (gelesen 26.10.2026)
        const val FLIP_SETTING_KEY = "flip_button_layout"

        fun fromFlipSetting(text: String?): ControllerLayout = when (text?.trim()) {
            "1" -> XBOX
            "0" -> ODIN
            else -> UNKNOWN
        }
    }
}

// Die Teile im Bild des Odin (Reiter Buttons). FACE_A … FACE_Y = Taste mit diesem Aufdruck
enum class OdinPart {
    FACE_A, FACE_B, FACE_X, FACE_Y,
    L1, L2, R1, R2, L3, R3,
    DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
    SELECT, START, HOME, BACK,
    M1, M2,
}

// Umrechnung zwischen Android-Taste, Aufdruck und Teil im Bild
object ButtonLayoutMap {

    // Aufdruck der Taste, die im Layout diese Android-Taste (A/B/X/Y) sendet. Bei UNKNOWN der Android-Name
    fun printedFace(target: ButtonTarget, layout: ControllerLayout): ButtonTarget =
        if (layout != ControllerLayout.XBOX) target
        else when (target) {
            ButtonTarget.A -> ButtonTarget.B
            ButtonTarget.B -> ButtonTarget.A
            ButtonTarget.X -> ButtonTarget.Y
            ButtonTarget.Y -> ButtonTarget.X
            else -> target
        }

    // Name einer Belegung, wie ihn StickLab zeigt: A/B/X/Y nach Aufdruck, alles andere wie im Odin-Menü
    fun printedName(target: ButtonTarget, layout: ControllerLayout): String = printedFace(target, layout).menuName

    // Teil im Bild, das eine Belegung nachahmt. null = keine eigene Taste am Gerät (Mode, APP_SWITCH,
    // Default, None) – oder A/B/X/Y bei unbekanntem Layout (dort wüssten wir nicht, welche Taste es ist)
    fun partOf(target: ButtonTarget, layout: ControllerLayout): OdinPart? {
        if (target.isFaceButton) {
            if (layout == ControllerLayout.UNKNOWN) return null
            return when (printedFace(target, layout)) {
                ButtonTarget.A -> OdinPart.FACE_A
                ButtonTarget.B -> OdinPart.FACE_B
                ButtonTarget.X -> OdinPart.FACE_X
                else -> OdinPart.FACE_Y
            }
        }
        return when (target) {
            ButtonTarget.L1 -> OdinPart.L1
            ButtonTarget.L2 -> OdinPart.L2
            ButtonTarget.L3 -> OdinPart.L3
            ButtonTarget.R1 -> OdinPart.R1
            ButtonTarget.R2 -> OdinPart.R2
            ButtonTarget.R3 -> OdinPart.R3
            ButtonTarget.UP -> OdinPart.DPAD_UP
            ButtonTarget.DOWN -> OdinPart.DPAD_DOWN
            ButtonTarget.LEFT -> OdinPart.DPAD_LEFT
            ButtonTarget.RIGHT -> OdinPart.DPAD_RIGHT
            ButtonTarget.SELECT -> OdinPart.SELECT
            ButtonTarget.START -> OdinPart.START
            ButtonTarget.HOME -> OdinPart.HOME
            ButtonTarget.BACK -> OdinPart.BACK
            else -> null
        }
    }

    // Teil im Bild zu einer Taste, die die App bekommt (Gedrückt-Anzeige). M1/M2 senden denselben Code wie
    // die Taste, die sie nachahmen (Test B) – sie leuchten darum als diese Taste.
    // generatedByAndroid = Android hat die Taste selbst aus einer Achse gemacht (linker Stick oder Steuerkreuz,
    // KeyEvent.FLAG_FALLBACK – Vermutung, im Test prüfen). Solche Richtungen zählen nicht: Das Steuerkreuz kommt
    // als Achse (HAT, Stufe 4.0) und wird über dpadParts gezeigt. Eine echte Richtungs-Taste (M1 auf DOWN/UP/…)
    // zählt dagegen
    fun partOfPressedKey(keyCode: Int, layout: ControllerLayout, generatedByAndroid: Boolean): OdinPart? {
        val target = ButtonTarget.fromKeyCode(keyCode) ?: return null
        if (target == ButtonTarget.NONE) return null
        if (target in DPAD_TARGETS && generatedByAndroid) return null
        return partOf(target, layout)
    }

    // Gedrückte Richtungen des Steuerkreuzes aus den Achsen HAT_X/HAT_Y (−1, 0, 1; gemessen Stufe 4.0:
    // HAT0X +1 = rechts, HAT0Y −1 = oben)
    fun dpadParts(hatX: Float, hatY: Float): Set<OdinPart> = buildSet {
        if (hatX > HAT_PRESSED) add(OdinPart.DPAD_RIGHT)
        if (hatX < -HAT_PRESSED) add(OdinPart.DPAD_LEFT)
        if (hatY > HAT_PRESSED) add(OdinPart.DPAD_DOWN)
        if (hatY < -HAT_PRESSED) add(OdinPart.DPAD_UP)
    }

    private val DPAD_TARGETS = setOf(ButtonTarget.UP, ButtonTarget.DOWN, ButtonTarget.LEFT, ButtonTarget.RIGHT)

    // Ab wann eine HAT-Achse als gedrückt gilt (sie springt nur zwischen −1, 0 und 1)
    private const val HAT_PRESSED = 0.5f
}
