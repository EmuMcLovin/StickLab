package com.sticklab.app

import androidx.annotation.StringRes

// Die sechs Licht-Regler des Ambilights mit Beschriftung, Einheit und erlaubtem Bereich.
// Bereiche wie in v1.0; die Werte selbst liegen weiter in LightSettings (Datei "light")
enum class LightSlider(@param:StringRes val label: Int, val unit: String, val min: Int, val max: Int) {
    BRIGHTNESS(R.string.brightness, " %", 20, 200),
    COLOR_BOOST(R.string.color_boost, " %", 0, 100),
    WHITE_LEVEL(R.string.white_level, " %", 0, 100),
    SPEED(R.string.speed, "", 1, 10),
    GREEN_LEVEL(R.string.green_level, " %", 40, 100),
    BLUE_LEVEL(R.string.blue_level, " %", 40, 100);

    // Liest den aktuellen Wert dieses Reglers aus LightSettings
    fun read(): Int = when (this) {
        BRIGHTNESS -> LightSettings.bright
        COLOR_BOOST -> LightSettings.boost
        WHITE_LEVEL -> LightSettings.white
        SPEED -> LightSettings.speed
        GREEN_LEVEL -> LightSettings.green
        BLUE_LEVEL -> LightSettings.blue
    }

    // Schreibt einen neuen Wert in LightSettings (gespeichert wird von außen mit LightSettings.save)
    fun write(value: Int) {
        val limited = value.coerceIn(min, max)
        when (this) {
            BRIGHTNESS -> LightSettings.bright = limited
            COLOR_BOOST -> LightSettings.boost = limited
            WHITE_LEVEL -> LightSettings.white = limited
            SPEED -> LightSettings.speed = limited
            GREEN_LEVEL -> LightSettings.green = limited
            BLUE_LEVEL -> LightSettings.blue = limited
        }
    }
}
