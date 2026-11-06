package com.sticklab.app

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit

// Abläufe des Reiters Profiles (Phase 5a "verwalten", Entwurf PROFILE_ENTWURF.html, Entscheidungen P1–P12 und
// F1/F2 vom 26.10.2026). Eigene Klasse, damit MainActivity nicht noch länger wird; MainActivity reicht die
// Aktionen des Bildschirms nur weiter.
// "Save current" liest nur (ProfileSnapshot) und schreibt nur die eigene Datei "profiles" (ProfileStore).
// Am Odin setzen nur "Use", "Edit" und "Restore everything" (Phase 5b-1) – über ProfileApplier mit denselben Wegen
// wie "Apply" in Sticks und Buttons. Danach ruft der Controller onOdinValuesSet, damit MainActivity Regler und
// Anzeigen auf die neuen Werte stellt
class ProfilesController(
    private val context: Context,
    private val ui: MainUiState,
    private val snapshot: ProfileSnapshot,
    private val applier: ProfileApplier,
    private val switcher: ProfileSwitcher,
    private val originals: OriginalValues,
    private val mapping: OdinMapping,
    private val onOdinValuesSet: () -> Unit,
) {
    private val store = ProfileStore(context)
    private val curveStore = CurveStore(context)
    private val appPrefs = AppStorage.open(context, AppStorage.AppFile.APP)
    private val profiles get() = ui.profiles
    private val text get() = ProfileText(context.resources)

    // Werte, die die offene Nachfrage "Save current" zeigt – genau die werden bei OK gespeichert
    private var pendingValues: OdinValues? = null

    // Plätze und aktives Profil aus der Datei laden (beim Öffnen der App).
    // Ein "aktiv" auf einem leeren Platz gilt als "kein Profil aktiv"
    fun load() {
        val slots = store.loadSlots(::defaultName)
        slots.forEachIndexed { slot, profile -> profiles.slots[slot] = profile }
        profiles.activeSlot = store.activeSlot?.takeIf { slots[it] != null }
    }

    // Werte am Odin neu lesen, nur für die Marke "changed" (beim Öffnen des Reiters, Zurückkehren in die App,
    // sobald der Live-Weg steht, nach jedem Apply/Restore und Ende des Auto-Sets – damit die Profil-Zeile oben in
    // jedem Reiter stimmt). Nicht lesbar → unbekannt, das aktive Profil gilt dann weiter als passend
    fun refreshOdinValues() {
        profiles.odinValues = (snapshot.read() as? ProfileSnapshot.Result.Read)?.values
    }

    // Platz antippen: nur wählen (für die Knöpfe darunter)
    fun select(slot: Int) {
        if (slot in profiles.slots.indices) profiles.selectedSlot = slot
    }

    // "Use" (ohne Nachfrage, P6): setzt alle 5 Werte des gewählten Profils am Odin (U1) und macht es aktiv – auch
    // wenn ein Teil nicht klappte (U2: die Statuszeile nennt ihn, die Marke zeigt dann "changed").
    // Ergebnis: true = gesetzt (für "Edit"), false = nichts getan (leerer Platz, Auto-Set läuft)
    fun use(): Boolean {
        val profile = profiles.selectedProfile ?: return false
        if (refuseDuringAutoSet()) return false
        // Setzen, aktiv machen und Lichtblitz: derselbe Weg wie die Kachel (ProfileSwitcher, 5d)
        val outcome = switcher.switchTo(profiles.selectedSlot, profile)
        load()
        afterOdinChanged()
        ui.statusText = statusAfterApply(outcome.report, stored = outcome.stored, okText = R.string.status_profile_used)
        if (outcome.allSet) profiles.lastUseAt = SystemClock.uptimeMillis()
        return true
    }

    // "Edit" (Entscheidung C, U3): am aktiven Profil nur in den Reiter Sticks springen – Änderungen, die dort schon
    // gemacht und noch nicht gespeichert sind, bleiben. An einem anderen Profil erst "Use", dann springen
    fun edit() {
        if (profiles.selectedProfile == null) return
        val alreadyActive = profiles.selectedSlot == profiles.activeSlot
        if (!alreadyActive && !use()) return
        ui.selectedTab = MainTab.STICKS
    }

    // "Restore everything" (P5, Plan Regel 7): erst die Nachfrage mit allen Werten "jetzt → vor StickLab"
    fun restoreEverything() {
        if (refuseDuringAutoSet()) return
        val targets = originalTargets()
        if (targets.isEmpty()) {
            ui.statusText = R.string.status_profile_restore_unknown
            return
        }
        refreshOdinValues()
        ui.restoreMessage = restoreAllMessage(targets)
        ui.dialog = MainDialog.PROFILE_RESTORE_ALL
    }

    // OK in der Nachfrage: alle Werte aufs Original. Danach ist kein Profil aktiv (U4) – die Werte sind wieder die
    // von vor StickLab
    fun restoreEverythingConfirmed() {
        ui.dialog = MainDialog.NONE
        if (refuseDuringAutoSet()) return
        val report = applier.apply(originalTargets())
        // Vor StickLab gab es keine Kurven: zurück auf Linear (nur eigene Dateien), und der echte Controller kommt zurück
        val curvesReset = curveStore.save(Profile.DEFAULT_CURVES)
        if (CurveModeEngine.isRunning()) CurveModeEngine.stop(context)
        // Eintrag auf der Schwarzen Liste wieder heraus: Auch er kam erst durch den Curve mode dazu
        mapping.forbidVirtualPad()
        val cleared = store.setActiveSlot(null) && curvesReset
        load()
        afterOdinChanged()
        ui.statusText = statusAfterApply(report, stored = cleared, okText = R.string.status_profile_restored_all)
    }

    // Während des Auto-Sets wirkt gerade eine Mess-Deadzone, und das Auto-Set stellt am Ende seinen Wert zurück –
    // ein Profil dazwischen würde überschrieben. Darum dann nichts setzen
    private fun refuseDuringAutoSet(): Boolean {
        if (ui.autoSet !is AutoSetUi.Running) return false
        ui.statusText = R.string.status_profile_apply_autoset_running
        return true
    }

    // Nach dem Setzen: Werte neu lesen (Marke) und MainActivity Regler + Anzeigen nachziehen lassen
    private fun afterOdinChanged() {
        refreshOdinValues()
        onOdinValuesSet()
    }

    // Statuszeile nach "Use" bzw. "Restore everything": zuerst ein Teil, der nicht klappte (U2), dann ein Fehler beim
    // Speichern von "aktiv", dann "wirkt nach Neustart", sonst okText
    private fun statusAfterApply(report: ApplyReport, stored: Boolean, okText: Int): Int {
        val failed = report.firstFailed
        return when {
            failed != null -> failedText(failed)
            !stored -> R.string.status_profile_store_failed
            report.needsRestart -> R.string.status_profile_after_restart
            else -> okText
        }
    }

    private fun failedText(part: ProfilePart): Int = when (part) {
        ProfilePart.DEADZONE -> R.string.status_profile_failed_deadzone
        ProfilePart.TRIGGER -> R.string.status_profile_failed_trigger
        ProfilePart.SENSITIVITY -> R.string.status_profile_failed_sensitivity
        ProfilePart.BUTTONS -> R.string.status_profile_failed_buttons
    }

    // Die gemerkten Werte von vor StickLab 2 (OriginalValues). Ein Teil ohne Original (für eine Seite nicht
    // gemerkt) bleibt, wie er ist
    private fun originalTargets(): ProfileTargets {
        val m1 = originals.buttonTarget(BackButton.M1)
        val m2 = originals.buttonTarget(BackButton.M2)
        return ProfileTargets(
            deadzone = bothSides(originals::deadzone),
            triggerDeadzone = bothSides(originals::triggerDeadzone),
            sensitivity = bothSides(originals::sensitivity),
            buttons = if (m1 != null && m2 != null) mapOf(BackButton.M1 to m1, BackButton.M2 to m2) else null,
        )
    }

    private fun bothSides(read: (OdinMapping.Side) -> Float?): LeftRight<Float>? {
        val left = read(OdinMapping.Side.LEFT) ?: return null
        val right = read(OdinMapping.Side.RIGHT) ?: return null
        return LeftRight(left, right)
    }

    private fun ProfileTargets.isEmpty() =
        deadzone == null && triggerDeadzone == null && sensitivity == null && buttons == null

    // Text der Nachfrage "Restore everything": je Teil "jetzt → vor StickLab" (Sens z. B. 1.00 → 0.90, R122).
    // Liegt ein Original außerhalb der StickLab-Bereiche, wird es begrenzt – das steht dann darunter
    private fun restoreAllMessage(targets: ProfileTargets): String {
        val t = text
        val now = profiles.odinValues
        val layout = ui.controllerLayout
        val rows = listOf(
            restoreRow(R.string.profile_label_deadzone, now?.deadzone?.let(t::deadzone), targets.deadzone?.let(t::deadzone)),
            restoreRow(R.string.profile_label_trigger, now?.triggerDeadzone?.let(t::trigger), targets.triggerDeadzone?.let(t::trigger)),
            restoreRow(R.string.profile_label_sensitivity, now?.sensitivity?.let(t::sensitivity), targets.sensitivity?.let(t::sensitivity)),
            restoreRow(
                R.string.profile_label_buttons,
                now?.let { t.buttons(it.m1, it.m2, layout) },
                targets.buttons?.let { t.buttons(it.getValue(BackButton.M1), it.getValue(BackButton.M2), layout) },
            ),
            restoreRow(R.string.profile_label_curve, t.curve(curveStore.load()), t.curve(Profile.DEFAULT_CURVES)),
        )
        var message = context.getString(R.string.profile_restore_all_text) + "\n\n" + rows.joinToString("\n")
        if (isCapped(targets)) message += "\n\n" + context.getString(R.string.profile_restore_all_capped)
        return message
    }

    // Eine Zeile "Teil: jetzt → Original"; unbekannt jetzt = "–", Original fehlt = "bleibt"
    private fun restoreRow(label: Int, now: String?, original: String?): String = row(
        label,
        now ?: context.getString(R.string.deadzone_unknown),
        original ?: context.getString(R.string.profile_restore_all_unknown),
    )

    // Liegt ein Original außerhalb der Bereiche, die StickLab setzt (SettingLimits)?
    private fun isCapped(targets: ProfileTargets): Boolean {
        fun outside(pair: LeftRight<Float>?, range: ClosedFloatingPointRange<Float>) =
            pair != null && (pair.left !in range || pair.right !in range)
        return outside(targets.deadzone, SettingLimits.DEADZONE) ||
            outside(targets.triggerDeadzone, SettingLimits.TRIGGER_DEADZONE) ||
            outside(targets.sensitivity, SettingLimits.SENSITIVITY)
    }

    // "Save current": Werte vom Odin holen. Leerer Platz → neues Profil "Profile N" mit der nächsten freien Farbe;
    // belegter Platz → erst Nachfrage alt → neu (Entwurf). Fehlt ein Wert, wird nichts gespeichert (R119).
    // Während des Auto-Sets wirkt gerade eine Mess-Deadzone (0 bzw. Vorschlag) – die gehört in kein Profil
    fun save() {
        if (ui.autoSet is AutoSetUi.Running) {
            ui.statusText = R.string.status_profile_autoset_running
            return
        }
        val values = when (val result = snapshot.read()) {
            is ProfileSnapshot.Result.Read -> result.values
            is ProfileSnapshot.Result.Missing -> {
                ui.statusText = missingText(result.part)
                return
            }
        }
        profiles.odinValues = values
        val slot = profiles.selectedSlot
        val existing = profiles.selectedProfile
        if (existing == null) {
            storeCurrent(slot, values.toProfile(defaultName(slot), ProfileSlots.nextFreeColor(profiles.slots)))
            return
        }
        pendingValues = values
        profiles.replaceMessage = replaceMessage(existing, values)
        ui.dialog = MainDialog.PROFILE_REPLACE
    }

    // "Save to profile" in der Profil-Zeile oben (jeder Reiter): wie "Save current" für das aktive Profil – also
    // erst die Nachfrage alt → neu. Der Platz des aktiven Profils wird dafür im Reiter Profiles gewählt
    fun saveToActive() {
        val slot = profiles.activeSlot ?: return
        profiles.selectedSlot = slot
        save()
    }

    // Entscheidung D: Gibt es noch kein Profil (erstes Öffnen), "Standard" mit den Werten am Odin anlegen und aktiv
    // machen – nur einmal. Läuft, sobald der Live-Weg steht; fehlt ein Wert, beim nächsten Mal wieder versuchen.
    // Am Odin wird dabei nichts gesetzt, nur die eigene Datei geschrieben
    fun createStandardIfNeeded() {
        val alreadyDone = appPrefs.getBoolean(AppStorage.AppKeys.STANDARD_PROFILE_DONE, false)
        if (!ProfileSlots.needsStandardProfile(alreadyDone, profiles.slots)) {
            if (!alreadyDone) markStandardDone()
            return
        }
        if (ui.autoSet is AutoSetUi.Running) return
        val values = (snapshot.read() as? ProfileSnapshot.Result.Read)?.values ?: return
        val standard = values.toProfile(context.getString(R.string.profile_standard_name), Profile.DEFAULT_COLOR)
        if (store.saveSlot(STANDARD_SLOT, standard) && store.setActiveSlot(STANDARD_SLOT)) {
            markStandardDone()
            profiles.selectedSlot = STANDARD_SLOT
            ui.statusText = R.string.status_profile_standard_created
        }
        load()
    }

    private fun markStandardDone() {
        appPrefs.edit { putBoolean(AppStorage.AppKeys.STANDARD_PROFILE_DONE, true) }
    }

    // Erklärung "How profiles work": einmal von selbst (MainActivity fragt helpPending), sonst über "?"
    val helpPending: Boolean get() = !appPrefs.getBoolean(AppStorage.AppKeys.PROFILES_HELP_SHOWN, false)

    fun showHelp() {
        ui.dialog = MainDialog.PROFILES_HELP
    }

    // Erklärung geschlossen (OK, daneben, Zurück): gilt als gezeigt
    fun helpClosed() {
        ui.dialog = MainDialog.NONE
        appPrefs.edit { putBoolean(AppStorage.AppKeys.PROFILES_HELP_SHOWN, true) }
    }

    // OK in der Nachfrage: die gezeigten Werte ins Profil, Name und Farbe bleiben
    fun saveConfirmed() {
        ui.dialog = MainDialog.NONE
        val values = pendingValues ?: return
        pendingValues = null
        val existing = profiles.selectedProfile ?: return
        storeCurrent(profiles.selectedSlot, values.toProfile(existing.name, existing.color))
    }

    // Speichert ein Profil mit den Werten vom Odin und macht es aktiv (Entscheidung F2, 31. Sitzung:
    // in diesem Moment sind die Werte am Odin genau die des Profils)
    private fun storeCurrent(slot: Int, profile: Profile) {
        val saved = store.saveSlot(slot, profile) && store.setActiveSlot(slot)
        load()
        if (saved) {
            ui.statusText = R.string.status_profile_saved
            profiles.lastSaveAt = SystemClock.uptimeMillis()
        } else {
            ui.statusText = R.string.status_profile_store_failed
        }
    }

    // "Rename": Fenster mit Textfeld öffnen (nur bei belegtem Platz)
    fun rename() {
        if (profiles.selectedProfile != null) ui.dialog = MainDialog.PROFILE_RENAME
    }

    // OK im Fenster: neuer Name (bereinigt, ProfileSlots.cleanName). Leer → alter Name bleibt
    fun renamed(input: String) {
        ui.dialog = MainDialog.NONE
        val profile = profiles.selectedProfile ?: return
        val name = ProfileSlots.cleanName(input) ?: return
        if (name != profile.name) updateSlot(profiles.selectedSlot, profile.copy(name = name), R.string.status_profile_renamed)
    }

    // "Color": Fenster mit den 8 festen Farben öffnen (P8)
    fun color() {
        if (profiles.selectedProfile != null) ui.dialog = MainDialog.PROFILE_COLOR
    }

    fun colorChosen(color: ProfileColor) {
        ui.dialog = MainDialog.NONE
        val profile = profiles.selectedProfile ?: return
        if (color != profile.color) updateSlot(profiles.selectedSlot, profile.copy(color = color), R.string.status_profile_color)
    }

    // "Copy": Kopie auf den ersten freien Platz, mit der nächsten freien Farbe; die Kopie ist danach gewählt,
    // aber nicht aktiv
    fun copy() {
        val source = profiles.selectedProfile ?: return
        val free = ProfileSlots.firstFreeSlot(profiles.slots) ?: return
        val copy = source.copy(
            name = ProfileSlots.copyName(source.name) { context.getString(R.string.profile_copy_name, it) },
            color = ProfileSlots.nextFreeColor(profiles.slots),
        )
        if (updateSlot(free, copy, R.string.status_profile_copied)) profiles.selectedSlot = free
    }

    // "Delete": erst Nachfrage (Odin bleibt unverändert)
    fun delete() {
        if (profiles.selectedProfile != null) ui.dialog = MainDialog.PROFILE_DELETE
    }

    // OK in der Nachfrage: Platz leeren; war es das aktive Profil, ist danach keins aktiv
    fun deleteConfirmed() {
        ui.dialog = MainDialog.NONE
        val slot = profiles.selectedSlot
        if (profiles.slots[slot] == null) return
        val wasActive = profiles.activeSlot == slot
        if (updateSlot(slot, null, R.string.status_profile_deleted) && wasActive) {
            if (!store.setActiveSlot(null)) ui.statusText = R.string.status_profile_store_failed
            load()
        }
    }

    // Einen Platz speichern (Profil oder leer), neu laden und melden. Ergebnis: true = gespeichert
    private fun updateSlot(slot: Int, profile: Profile?, okText: Int): Boolean {
        val saved = store.saveSlot(slot, profile)
        load()
        ui.statusText = if (saved) okText else R.string.status_profile_store_failed
        return saved
    }

    // Text der Nachfrage "Save current" über ein Profil: je Zeile alt → neu, auch die Kurve
    private fun replaceMessage(old: Profile, values: OdinValues): String {
        val new = values.toProfile(old.name, old.color)
        val t = text
        val layout = ui.controllerLayout
        val rows = listOf(
            row(R.string.profile_label_deadzone, t.deadzone(old.deadzone), t.deadzone(new.deadzone)),
            row(R.string.profile_label_trigger, t.trigger(old.triggerDeadzone), t.trigger(new.triggerDeadzone)),
            row(R.string.profile_label_sensitivity, t.sensitivity(old.sensitivity), t.sensitivity(new.sensitivity)),
            row(
                R.string.profile_label_curve,
                t.curve(old.curves),
                t.curve(new.curves),
            ),
            row(R.string.profile_label_buttons, t.buttons(old.m1, old.m2, layout), t.buttons(new.m1, new.m2, layout)),
        )
        return context.getString(R.string.profile_replace_text) + "\n\n" + rows.joinToString("\n")
    }

    private fun row(label: Int, old: String, new: String): String =
        context.getString(R.string.profile_replace_row, context.getString(label), old, new)

    // Name eines neuen Profils bzw. Ersatzname: "Profile 1" … "Profile 4" (Platznummer)
    private fun defaultName(slot: Int): String = context.getString(R.string.profile_default_name, slot + 1)

    // Meldung, welcher Wert fehlte (R119)
    private fun missingText(part: ProfileSnapshot.Part): Int = when (part) {
        ProfileSnapshot.Part.DEADZONE -> R.string.status_profile_missing_deadzone
        ProfileSnapshot.Part.SENSITIVITY -> R.string.status_profile_missing_sensitivity
        ProfileSnapshot.Part.TRIGGER -> R.string.status_profile_missing_trigger
        ProfileSnapshot.Part.BUTTONS -> R.string.status_profile_missing_buttons
    }

    private companion object {
        // "Standard" liegt auf dem ersten Platz (beim ersten Öffnen sind alle leer)
        const val STANDARD_SLOT = 0
    }
}
