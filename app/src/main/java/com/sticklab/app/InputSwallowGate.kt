package com.sticklab.app

import kotlin.math.abs

// Entscheidet, ob die App eine Controller-Bewegung verschluckt, statt sie an die Oberfläche weiterzugeben
// (Phase 3b, Entscheidung M3: während der Loslass-Messung springt die Auswahl nicht mit).
// Schutzregel R76: Android bewegt die Auswahl mit Stick/Steuerkreuz und wiederholt das, solange es die Achse
// für ausgelenkt hält. Würde die App die Meldung "losgelassen" verschlucken, könnte die Wiederholung hängen
// bleiben (Vermutung). Darum wird erst verschluckt, wenn die zuletzt weitergegebene Meldung die Auswahl nicht
// mehr bewegt (isNeutral). Sonst wird weitergegeben, bis so eine Meldung durchging.
// Ohne Android-Teile, damit die Regel auf dem Laptop geprüft werden kann (InputSwallowGateTest)
class InputSwallowGate {

    // Zuletzt weitergegebene Meldung war "alles in der Mitte". Am Anfang wahr: noch nichts ausgelenkt
    private var lastPassedNeutral = true

    // Messung läuft
    private var active = false

    // Verschlucken hat begonnen (erst ab einer neutralen weitergegebenen Meldung)
    private var swallowing = false

    // Messung beginnt: sofort verschlucken, wenn zuletzt alles in der Mitte war
    fun begin() {
        active = true
        swallowing = lastPassedNeutral
    }

    // Messung endet: ab jetzt wieder alles weitergeben
    fun end() {
        active = false
        swallowing = false
    }

    // Soll gerade verschluckt werden? Für den Aufrufer, der das bei jeder Meldung aus seinem Zustand ableitet:
    // Wechselt nur, wenn sich der Wunsch ändert, damit ein laufendes Warten auf "alles in der Mitte" bestehen bleibt
    fun update(wanted: Boolean) {
        if (wanted == active) return
        if (wanted) begin() else end()
    }

    // Diese Meldung verschlucken?
    fun shouldSwallow(): Boolean = active && swallowing

    // Eine Meldung wurde weitergegeben. neutral = Android bewegt die Auswahl damit nicht mehr (isNeutral).
    // Nicht neutral: Das Verschlucken pausiert, bis wieder "alles in der Mitte" durchging (R76). Das braucht der Kurven-Editor,
    // der das Steuerkreuz trotz Verschlucken durchlässt: Sonst ginge dessen Meldung "losgelassen" verloren
    fun passed(neutral: Boolean) {
        lastPassedNeutral = neutral
        if (active) swallowing = neutral
    }

    companion object {
        // Ab diesem Ausschlag des linken Sticks bewegt Android die Auswahl. Laut Android-Quelltext 0,5 (ViewRootImpl, aus dem Gedächtnis,
        // nicht am Odin gelesen), hier mit Abstand darunter: Lieber kurz länger warten, als eine laufende Wiederholung abzuschneiden (R76)
        private const val NAVIGATION_THRESHOLD = 0.25f

        // Bewegt Android mit dieser Stellung die Auswahl nicht mehr? Steuerkreuz los und linker Stick unter der Schwelle.
        // Nicht "genau 0": Ein Stick mit Drift-Rest knapp über der Deadzone (am Odin des Besitzers links 0,1 %) meldete sonst nie
        // "neutral", und das Verschlucken lief nach einem Steuerkreuz-Druck nie wieder an (Fund Besitzer, 06.11.2026).
        // Der rechte Stick zählt nicht, er bewegt keine Auswahl
        fun isNeutral(x: Float, y: Float, hatX: Float, hatY: Float): Boolean =
            hatX == 0f && hatY == 0f && abs(x) < NAVIGATION_THRESHOLD && abs(y) < NAVIGATION_THRESHOLD
    }
}
