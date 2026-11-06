package com.sticklab.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

// Ein Speicher für alle Profile: die App-Einstellungsdatei "profiles" (AppStorage, nur diese App kann sie lesen).
// Es gibt 4 feste Plätze (Reiter Profiles, Entwurf PROFILE_ENTWURF.html). Ein Profil liegt unter "p<Platz>.<Feld>",
// z. B. "p0.deadzone.left" (Felder siehe ProfileCodec); ein Platz ohne solche Einträge ist leer. Löschen lässt eine
// Lücke, nichts rückt nach. Dazu "active" = Platz des aktiven Profils, fehlt = kein Profil aktiv (Fund F1).
// Format seit 5a (26.10.2026); vorher wurde die Datei nie angelegt, darum gibt es nichts umzuziehen
class ProfileStore(context: Context) {

    private val prefs = AppStorage.open(context, AppStorage.AppFile.PROFILES)

    // Alle Plätze in fester Reihenfolge (immer Profile.MAX_PROFILES Einträge), null = leerer Platz.
    // fallbackName(n) liefert den Namen für ein Profil ohne brauchbaren Namen
    fun loadSlots(fallbackName: (slot: Int) -> String): List<Profile?> {
        val all = prefs.all
        return (0 until Profile.MAX_PROFILES).map { slot ->
            val prefix = prefixOf(slot)
            val fields = all.filterKeys { it.startsWith(prefix) }.mapKeys { it.key.removePrefix(prefix) }
            if (fields.isEmpty()) null else ProfileCodec.decode(fields, fallbackName(slot))
        }
    }

    // Legt ein Profil auf einen Platz (ersetzt, was dort war), null = Platz leeren.
    // Gespeichert wird sofort auf den Speicher (commit), damit ein gemeldetes "Saved" auch ein sofortiges Beenden
    // der App übersteht. Ergebnis: true = gespeichert.
    // Bewusst nicht die kurze Form edit { } (Lint-Hinweis UseKtx): die verschluckt das Ergebnis von commit()
    @SuppressLint("UseKtx")
    fun saveSlot(slot: Int, profile: Profile?): Boolean {
        require(slot in 0 until Profile.MAX_PROFILES) { "Platz $slot gibt es nicht" }
        val prefix = prefixOf(slot)
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        if (profile != null) {
            for ((field, value) in ProfileCodec.encode(profile)) putValue(editor, prefix + field, value)
        }
        return editor.commit()
    }

    // Platz des aktiven Profils, null = kein Profil aktiv. Ein Platz außerhalb 0–3 gilt als "keins"
    val activeSlot: Int?
        get() = prefs.getInt(KEY_ACTIVE, NO_ACTIVE).takeIf { it in 0 until Profile.MAX_PROFILES }

    // Aktives Profil merken, null = keins. Ergebnis: true = gespeichert (commit wie bei saveSlot)
    @SuppressLint("UseKtx")
    fun setActiveSlot(slot: Int?): Boolean {
        val editor = prefs.edit()
        if (slot == null) editor.remove(KEY_ACTIVE) else editor.putInt(KEY_ACTIVE, slot)
        return editor.commit()
    }

    // Legt einen Wert passend zu seiner Art ab (ProfileCodec liefert nur String, Int, Float)
    private fun putValue(editor: SharedPreferences.Editor, key: String, value: Any) {
        when (value) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Float -> editor.putFloat(key, value)
            else -> error("Unerwartete Art für $key: ${value::class.java.simpleName}")
        }
    }

    private fun prefixOf(slot: Int) = "$PROFILE_PREFIX$slot."

    companion object {
        // Schlüsselnamen. Nie umbenennen, sonst findet eine neue App-Version die Profile nicht mehr (AppStorageNamesTest)
        const val PROFILE_PREFIX = "p"
        const val KEY_ACTIVE = "active"

        // Gespeicherter Wert für "kein Profil aktiv", falls ein älterer Stand -1 schreiben würde
        private const val NO_ACTIVE = -1
    }
}
