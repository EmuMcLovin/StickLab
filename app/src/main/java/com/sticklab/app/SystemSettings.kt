package com.sticklab.app

import android.content.ContentResolver
import android.provider.Settings

// Einzige Stelle der App, die Android-Systemeinstellungen (settings system) liest.
// Lesen geht ohne root und ohne Berechtigung (seit v1.0 für die Deadzone so benutzt).
// Geschrieben wird nie hier, sondern nur über OdinService (feste Befehle, Plan Regel 7)
object SystemSettings {

    // Wert als Text, null = nicht vorhanden oder von Android verweigert
    fun readText(resolver: ContentResolver, key: String): String? =
        try {
            Settings.System.getString(resolver, key)
        } catch (t: Throwable) {
            null
        }

    // Wert als Zahl, null = nicht vorhanden, verweigert oder keine Zahl
    fun readFloat(resolver: ContentResolver, key: String): Float? =
        readText(resolver, key)?.toFloatOrNull()
}
