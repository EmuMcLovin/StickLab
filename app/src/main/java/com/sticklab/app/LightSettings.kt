package com.sticklab.app

import android.content.Context

// Die Einstellwerte für das Ambilight, gemeinsam genutzt von App und Hintergrund-Teil
object LightSettings {

    @Volatile var bright = 100
    @Volatile var boost = 70
    @Volatile var white = 40
    @Volatile var green = 75
    @Volatile var blue = 85
    @Volatile var speed = 5

    // Schalter "Nicht mehr fragen"
    @Volatile var skipPrompt = false

    // Aus dem Tempo-Regler (1 bis 10) werden die beiden Nachzieh-Werte berechnet
    val slow: Float get() = 0.05f + speed * 0.04f
    val fast: Float get() = minOf(0.9f, slow * 2.4f)

    fun load(c: Context) {
        val p = c.getSharedPreferences("light", Context.MODE_PRIVATE)
        bright = p.getInt("bright", 100)
        boost = p.getInt("boost", 70)
        white = p.getInt("white", 40)
        green = p.getInt("green", 75)
        blue = p.getInt("blue", 85)
        speed = p.getInt("speed", 5)
        skipPrompt = p.getBoolean("skipPrompt", false)
    }

    fun save(c: Context) {
        c.getSharedPreferences("light", Context.MODE_PRIVATE).edit()
            .putInt("bright", bright)
            .putInt("boost", boost)
            .putInt("white", white)
            .putInt("green", green)
            .putInt("blue", blue)
            .putInt("speed", speed)
            .putBoolean("skipPrompt", skipPrompt)
            .apply()
    }

    // Setzt nur die Licht-Regler zurück, nicht den Schalter
    fun reset() {
        bright = 100
        boost = 70
        white = 40
        green = 75
        blue = 85
        speed = 5
    }
}