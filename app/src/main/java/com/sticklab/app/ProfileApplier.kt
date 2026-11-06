package com.sticklab.app

// Setzt die Werte eines Profils am Odin – für "Use" und "Restore everything" im Reiter Profiles (Phase 5b-1,
// Entscheidungen U1/U2 vom 26.10.2026). Ohne Android-Teile, darum auf dem Laptop prüfbar (ProfileApplierTest).
// Wie gesetzt wird, weiß OdinSetter (in der App: OdinControlsSetter mit den Wegen aus Sticks und Buttons).
// Regel U2: Klappt ein Teil nicht, werden die übrigen trotzdem gesetzt – der Bericht nennt, was fehlte

// Die Teile, die ein Profil am Odin setzt, in der Reihenfolge, in der sie gesetzt werden
enum class ProfilePart { DEADZONE, TRIGGER, SENSITIVITY, BUTTONS }

// Was gesetzt werden soll. null = diesen Teil nicht anfassen (z. B. Original noch nicht gemerkt)
data class ProfileTargets(
    val deadzone: LeftRight<Float>?,
    val triggerDeadzone: LeftRight<Float>?,
    val sensitivity: LeftRight<Float>?,
    val buttons: Map<BackButton, ButtonTarget>?,
) {
    companion object {
        // Alle 5 Werte eines Profils (U1). Kurve und Anti-Deadzone gibt es am Odin nicht (erst Phase 6),
        // das Licht bleibt beim Wechsel, wie es ist (P7)
        fun of(profile: Profile) = ProfileTargets(
            deadzone = profile.deadzone,
            triggerDeadzone = profile.triggerDeadzone,
            sensitivity = profile.sensitivity,
            buttons = mapOf(BackButton.M1 to profile.m1, BackButton.M2 to profile.m2),
        )
    }
}

// Die Wege zum Odin, je Teil und Seite. Jeder setzt, liest nach und meldet, ob der Wert sitzt
interface OdinSetter {
    fun setDeadzone(side: OdinMapping.Side, value: Float): DeadzoneControl.Result
    fun setTriggerDeadzone(side: OdinMapping.Side, value: Float): DeadzoneControl.Result
    fun setSensitivity(side: OdinMapping.Side, value: Float): DeadzoneControl.Result
    fun setButton(button: BackButton, target: ButtonTarget): Boolean
}

// Ergebnis je gesetztem Teil (nicht angefasste Teile fehlen)
data class ApplyReport(val results: Map<ProfilePart, DeadzoneControl.Result>) {

    // Erster Teil (in Setz-Reihenfolge), der nicht klappte – für die Statuszeile. null = alles gesetzt
    val firstFailed: ProfilePart?
        get() = ProfilePart.entries.firstOrNull { results[it] == DeadzoneControl.Result.FAILED }

    // Gilt etwas erst nach einem Neustart? (Live-Weg fehlte, Systemwert ist gespeichert)
    val needsRestart: Boolean
        get() = results.values.any { it == DeadzoneControl.Result.AFTER_RESTART }
}

class ProfileApplier(private val odin: OdinSetter) {

    // Setzt alle Teile, die in targets stehen, der Reihe nach (beide Seiten bzw. M1 und M2 je Teil)
    fun apply(targets: ProfileTargets): ApplyReport {
        val results = buildMap {
            targets.deadzone?.let { put(ProfilePart.DEADZONE, bothSides(it, odin::setDeadzone)) }
            targets.triggerDeadzone?.let { put(ProfilePart.TRIGGER, bothSides(it, odin::setTriggerDeadzone)) }
            targets.sensitivity?.let { put(ProfilePart.SENSITIVITY, bothSides(it, odin::setSensitivity)) }
            targets.buttons?.let { put(ProfilePart.BUTTONS, buttons(it)) }
        }
        return ApplyReport(results)
    }

    // Beide Seiten setzen (auch wenn die linke scheitert, U2) und das schlechtere Ergebnis melden
    private fun bothSides(
        pair: LeftRight<Float>,
        set: (OdinMapping.Side, Float) -> DeadzoneControl.Result,
    ): DeadzoneControl.Result {
        val left = set(OdinMapping.Side.LEFT, pair.left)
        val right = set(OdinMapping.Side.RIGHT, pair.right)
        return maxOf(left, right)
    }

    // M1 und M2 setzen (jede Taste für sich, auch wenn eine scheitert). Belegungen wirken sofort (R105 widerlegt),
    // "nach Neustart" gibt es hier nicht
    private fun buttons(targets: Map<BackButton, ButtonTarget>): DeadzoneControl.Result {
        val saved = targets.map { (button, target) -> odin.setButton(button, target) }
        return if (saved.all { it }) DeadzoneControl.Result.ACTIVE_NOW else DeadzoneControl.Result.FAILED
    }
}
