package com.sticklab.app

import java.util.Locale

// Die einzige Stelle der echten App, die Befehle für den eingebauten Odin-Dienst PServerBinder baut.
// PServerBinder führt Befehle als root aus. Darum gibt es hier nur feste Befehle mit
// geprüften Werten, nie Text von außen (Plan Regel 7, CLAUDE.md "Sicherheit").
// Geschickt wird über PServerLink (einzige Leitung, Grenze 180 Zeichen)
object OdinService {

    // Systemeinstellungen, die StickLab als Zahl schreiben darf, mit erlaubtem Bereich
    enum class FloatSetting(val key: String, val range: ClosedFloatingPointRange<Float>) {
        LEFT_DEADZONE("left_joystick_dead_zone_radius", SettingLimits.DEADZONE),
        RIGHT_DEADZONE("right_joystick_dead_zone_radius", SettingLimits.DEADZONE),
        L2_DEADZONE("l2_dead_zone_radius", SettingLimits.TRIGGER_DEADZONE),
        R2_DEADZONE("r2_dead_zone_radius", SettingLimits.TRIGGER_DEADZONE),
    }

    // Systemeinstellung mit der Farbe der Stick-LEDs (Ambilight)
    const val LED_COLOR_KEY = "joystick_led_light_picker_color"

    // Zwei Farben "#aarrggbb,#aarrggbb" (links, rechts), so steht der Wert am Odin (gelesen 15.10.2026)
    private val LED_COLORS_FORMAT = Regex("#[0-9a-fA-F]{8},#[0-9a-fA-F]{8}")

    // Paketnamen bestehen nur aus Buchstaben, Ziffern, Punkt und Unterstrich
    private val PACKAGE_NAME_FORMAT = Regex("[A-Za-z0-9_.]+")

    // Schreibt eine Zahl in eine Systemeinstellung, vorher auf den erlaubten Bereich begrenzt.
    // Ergebnis: true, wenn PServerBinder den Befehl angenommen hat
    fun putSystemFloat(setting: FloatSetting, value: Float): Boolean =
        sendCommand(systemFloatCommand(setting, value))

    // Belegt M1 oder M2 (Plan Phase 4). Nur Einträge aus der festen Liste ButtonTarget (= Odin-Menü)
    fun putButtonTarget(button: BackButton, target: ButtonTarget): Boolean =
        sendCommand(buttonTargetCommand(button, target))

    // Setzt die Farbe beider Stick-LEDs. Nur Werte im festen Format werden geschickt
    fun putLedColors(colors: String): Boolean = sendCommand(ledColorsCommand(colors))

    // Schalter "Nicht mehr fragen": Android überspringt die Abfrage zur Bildschirmaufnahme
    fun setScreenCaptureAllowed(packageName: String, allowed: Boolean): Boolean =
        sendCommand(screenCaptureCommand(packageName, allowed))

    // Prüft, ob der Dienst auf diesem Gerät vorhanden ist
    fun isAvailable(): Boolean = PServerLink.isAvailable()

    // ---------- Die festen Befehle (ohne Android-Teile, auf dem Laptop geprüft: OdinServiceCommandsTest) ----------
    // Jede Funktion liefert null, wenn der Wert nicht erlaubt ist – dann wird nichts geschickt

    // "settings put system <key> <wert>", Wert auf den Bereich der Einstellung begrenzt
    internal fun systemFloatCommand(setting: FloatSetting, value: Float): String? {
        val safe = SettingLimits.limit(value, setting.range) ?: return null
        return "settings put system ${setting.key} ${formatFraction(safe)}"
    }

    // "settings put system remap_custom_to_m1_value <Zahl>". Name und Zahl kommen nur aus den festen Listen
    // BackButton/ButtonTarget, darum gibt es hier keinen unerlaubten Wert
    internal fun buttonTargetCommand(button: BackButton, target: ButtonTarget): String =
        "settings put system ${button.settingKey} ${target.keyCode}"

    // "settings put system joystick_led_light_picker_color '<links>,<rechts>'"
    internal fun ledColorsCommand(colors: String): String? {
        if (!LED_COLORS_FORMAT.matches(colors)) return null
        return "settings put system $LED_COLOR_KEY '$colors'"
    }

    // "appops set <paket> PROJECT_MEDIA allow|default"
    internal fun screenCaptureCommand(packageName: String, allowed: Boolean): String? {
        if (!PACKAGE_NAME_FORMAT.matches(packageName)) return null
        val mode = if (allowed) "allow" else "default"
        return "appops set $packageName PROJECT_MEDIA $mode"
    }

    // 3 Nachkommastellen wie seit v1.0 (0,1-%-Reglerschritte = 0.001 bleiben damit genau)
    private fun formatFraction(value: Float): String = String.format(Locale.US, "%.3f", value)

    // Schickt einen fertigen Befehl. Ergebnis: true, wenn er angenommen wurde (nicht: gelungen, A11)
    private fun sendCommand(command: String?): Boolean {
        if (command == null) return false
        val result = PServerLink.send(command)
        return result is PServerLink.SendResult.Sent && result.accepted
    }
}
