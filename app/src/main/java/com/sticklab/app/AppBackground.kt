package com.sticklab.app

import androidx.annotation.StringRes

// Die wählbaren Hintergründe der App (Wunsch Besitzer 06.11.2026, Reiter Settings). Nur Optik: gilt für alle Profile und
// ändert nichts am Odin. Ohne Android- und Compose-Teile, damit die Regeln auf dem Laptop geprüft werden können
// (AppBackgroundTest). Die Farben stehen in GradientBackground.
// id steht in der Datei "app" (AppStorage.AppKeys.BACKGROUND) und darf sich nie ändern, sonst fiele die Wahl nach einem
// Update auf den Standard zurück
enum class AppBackground(val id: String, @param:StringRes val nameRes: Int) {
    NEON("neon", R.string.background_neon),
    OCEAN("ocean", R.string.background_ocean),
    EMBER("ember", R.string.background_ember),
    GRAPHITE("graphite", R.string.background_graphite);

    companion object {
        // Der Hintergrund seit dem Aussehen A2, passend zum App-Symbol (Plan Phase 8)
        val DEFAULT = NEON

        // Gespeicherten Wert lesen. Fehlt er oder ist unbekannt (z. B. nach einem Wechsel auf eine ältere Version): Standard
        fun fromId(id: String?): AppBackground = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
