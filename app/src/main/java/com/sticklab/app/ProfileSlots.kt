package com.sticklab.app

import kotlin.math.abs

// Regeln des Reiters Profiles (Phase 5a), ohne Android-Teile, darum auf dem Laptop prüfbar (ProfileSlotsTest)

// Die Werte eines Profils, so wie sie gerade gelten (gelesen von ProfileSnapshot). curves liegt nicht am Odin, sondern
// in der eigenen Datei "curves" (CurveStore): Kurve und Anti-Deadzone kann der Odin nicht, sie wirken erst mit Phase 6c
data class OdinValues(
    val deadzone: LeftRight<Float>,
    val sensitivity: LeftRight<Float>,
    val triggerDeadzone: LeftRight<Float>,
    val m1: ButtonTarget,
    val m2: ButtonTarget,
    val curves: LeftRight<CurveSettings> = Profile.DEFAULT_CURVES,
) {
    // Passt das Profil noch zu diesen Werten? Sonst bekommt das aktive Profil die Marke "changed" (P9).
    // Seit Phase 6 zählt auch die Kurve: Eine ungespeicherte Änderung darf beim Profilwechsel nicht still verloren gehen
    fun matches(profile: Profile): Boolean =
        sameNumbers(deadzone, profile.deadzone) &&
            sameNumbers(sensitivity, profile.sensitivity) &&
            sameNumbers(triggerDeadzone, profile.triggerDeadzone) &&
            m1 == profile.m1 && m2 == profile.m2 &&
            curves == profile.curves

    // Profil mit diesen Werten ("Save current", P1). Licht wird in Phase 5 nicht umgeschaltet (P7)
    fun toProfile(name: String, color: ProfileColor): Profile =
        Profile.withDefaults(name).copy(
            color = color,
            deadzone = deadzone,
            sensitivity = sensitivity,
            triggerDeadzone = triggerDeadzone,
            curves = curves,
            m1 = m1,
            m2 = m2,
        )

    private fun sameNumbers(a: LeftRight<Float>, b: LeftRight<Float>) =
        abs(a.left - b.left) <= MATCH_TOLERANCE && abs(a.right - b.right) <= MATCH_TOLERANCE

    private companion object {
        // Gleich, wenn höchstens so weit auseinander: die Hälfte des feinsten Schritts überhaupt (Deadzone-Regler
        // 0,1 % = 0.001). Gelesene Kommazahlen weichen sonst nur in der 7. Stelle ab
        const val MATCH_TOLERANCE = 0.0005f
    }
}

// Platz-Regeln: freie Plätze, freie Farben, Namen
object ProfileSlots {

    // Entscheidung D (31. Sitzung): Beim ersten Öffnen ohne Profil legt StickLab selbst "Standard" an.
    // Nur einmal (alreadyDone = Merker in der Datei "app"); gibt es schon ein Profil, ist es nicht nötig
    fun needsStandardProfile(alreadyDone: Boolean, slots: List<Profile?>): Boolean =
        !alreadyDone && slots.all { it == null }

    // Erster leerer Platz, null = alle 4 belegt (dann geht "Copy" nicht)
    fun firstFreeSlot(slots: List<Profile?>): Int? = slots.indexOfFirst { it == null }.takeIf { it >= 0 }

    // Kachel (Phase 5d): das nächste gespeicherte Profil im Kreis nach dem aktiven (Platz 1→2→3→4→1), leere Plätze
    // übersprungen. Kein Profil aktiv → das erste gespeicherte. Nur ein Profil → wieder dieses (setzt es neu).
    // null = gar kein Profil gespeichert
    fun nextSlot(slots: List<Profile?>, activeSlot: Int?): Int? {
        val filled = slots.indices.filter { slots[it] != null }
        if (filled.isEmpty()) return null
        if (activeSlot == null) return filled.first()
        return filled.firstOrNull { it > activeSlot } ?: filled.first()
    }

    // Farbe für ein neues Profil: die erste der 8 festen Farben, die noch kein Profil hat (Entwurf)
    fun nextFreeColor(slots: List<Profile?>): ProfileColor =
        ProfileColor.entries.firstOrNull { color -> slots.none { it?.color == color } } ?: Profile.DEFAULT_COLOR

    // Name aus dem Umbenennen-Fenster: ohne Steuerzeichen (z. B. Zeilenumbruch), ohne Leerzeichen am Rand,
    // höchstens Profile.MAX_NAME_LENGTH Zeichen. null = nichts Brauchbares übrig → alter Name bleibt.
    // Der Name wird nur gespeichert und angezeigt, nie Teil eines Befehls (Plan Regel 7)
    fun cleanName(input: String): String? =
        input.filterNot { it.isISOControl() }.trim().take(Profile.MAX_NAME_LENGTH).dropHalfEmoji().trim().ifEmpty { null }

    // Kürzen kann ein Zeichen aus zwei Teilen (z. B. ein Emoji) halbieren – dann den halben Rest weglassen
    private fun String.dropHalfEmoji(): String = if (isNotEmpty() && last().isHighSurrogate()) dropLast(1) else this

    // Name einer Kopie, z. B. "Shooter copy" (copyPattern aus den Texten), auf die Höchstlänge gekürzt
    fun copyName(name: String, copyPattern: (String) -> String): String =
        cleanName(copyPattern(name)) ?: name

    // Marke an einem Platz: aktiv und passend, aktiv aber am Odin geändert, oder keine.
    // odin = null (Werte gerade nicht lesbar): das aktive Profil gilt weiter als passend
    fun badgeOf(slot: Int, profile: Profile?, activeSlot: Int?, odin: OdinValues?): SlotBadge = when {
        profile == null || slot != activeSlot -> SlotBadge.NONE
        odin == null || odin.matches(profile) -> SlotBadge.ACTIVE
        else -> SlotBadge.CHANGED
    }
}

// Marke oben in einem Platz (Entwurf: "● active" grün, "● changed" orange)
enum class SlotBadge { NONE, ACTIVE, CHANGED }
