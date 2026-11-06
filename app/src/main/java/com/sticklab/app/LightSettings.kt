package com.sticklab.app

import android.content.Context
import androidx.core.content.edit
import com.sticklab.app.AppStorage.LightKeys

// Die Einstellwerte für das Ambilight, gemeinsam genutzt von App und Hintergrund-Teil.
// Gespeichert in der Datei "light" (AppStorage); das laufende Ambilight liest nur dieses Objekt
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
        val p = AppStorage.open(c, AppStorage.AppFile.LIGHT)
        bright = p.getInt(LightKeys.BRIGHT, 100)
        boost = p.getInt(LightKeys.BOOST, 70)
        white = p.getInt(LightKeys.WHITE, 40)
        green = p.getInt(LightKeys.GREEN, 75)
        blue = p.getInt(LightKeys.BLUE, 85)
        speed = p.getInt(LightKeys.SPEED, 5)
        skipPrompt = p.getBoolean(LightKeys.SKIP_PROMPT, false)
    }

    fun save(c: Context) {
        AppStorage.open(c, AppStorage.AppFile.LIGHT).edit {
            putInt(LightKeys.BRIGHT, bright)
            putInt(LightKeys.BOOST, boost)
            putInt(LightKeys.WHITE, white)
            putInt(LightKeys.GREEN, green)
            putInt(LightKeys.BLUE, blue)
            putInt(LightKeys.SPEED, speed)
            putBoolean(LightKeys.SKIP_PROMPT, skipPrompt)
        }
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
