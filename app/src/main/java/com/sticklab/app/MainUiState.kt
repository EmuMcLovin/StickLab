package com.sticklab.app

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Die vier Reiter des Bildschirms (Aussehen A2: Leiste links nur mit Symbolen, Besitzer 16.10.2026).
// Reihenfolge hier = Reihenfolge in der Leiste. Profiles steht vorn und ist der Startbildschirm (Entscheidung A,
// 31. Sitzung): Wer die Reiter der Reihe nach durchgeht, sieht zuerst die Profile
enum class MainTab(@param:StringRes val title: Int) {
    PROFILES(R.string.tab_profiles),
    STICKS(R.string.tab_sticks),
    BUTTONS(R.string.tab_buttons),
    LIGHTS(R.string.tab_lights),

    // Reiter "Settings" (5d-2, Entwurf SETTINGS_ENTWURF.html): Zahnrad allein ganz unten in der Leiste (E1)
    SETTINGS(R.string.tab_settings),
}

// Die Teile des Reiters Sticks im Umschalter (Schritt S, Entscheidung S2 vom 26.10.2026; Curve seit Phase 6d)
enum class StickSetting { DEADZONE, SENSITIVITY, TRIGGER, CURVE }

// Welche Nachfrage gerade offen ist (höchstens eine gleichzeitig)
enum class MainDialog {
    NONE, UNSUPPORTED, INTRO, CURVE_MODE_INTRO, AMBILIGHT_LIGHT_OFF, RESTORE, AUTOSET_APPLY, BUTTONS_RESTORE, SENSITIVITY_RESTORE, TRIGGER_RESTORE,
    PROFILE_REPLACE, PROFILE_DELETE, PROFILE_RENAME, PROFILE_COLOR, PROFILES_HELP, PROFILE_RESTORE_ALL,
}

// Zustand des Reiters Profiles (Phase 5a): die 4 Plätze (null = leer), der gewählte Platz (für die Knöpfe),
// der Platz des aktiven Profils (null = keins), die zuletzt gelesenen Werte am Odin (null = nicht lesbar, für die
// Marke "changed"), Text der Nachfrage "Save current" über ein Profil (alt → neu), letzter Speicher-Haken und
// letzter Haken an "Use" (alle Werte gesetzt, 5b-1)
class ProfilesUi {
    val slots = mutableStateListOf<Profile?>().apply { repeat(Profile.MAX_PROFILES) { add(null) } }
    var selectedSlot by mutableIntStateOf(0)
    var activeSlot by mutableStateOf<Int?>(null)
    var odinValues by mutableStateOf<OdinValues?>(null)
    var replaceMessage by mutableStateOf("")
    var lastSaveAt by mutableLongStateOf(0L)
    var lastUseAt by mutableLongStateOf(0L)
    var curveModeRunning by mutableStateOf(false)   // für den Hinweis "Curve mode off", gesetzt von CurveController

    // Profil auf dem gewählten Platz, null = leerer Platz
    val selectedProfile: Profile? get() = slots.getOrNull(selectedSlot)
}

// Zustand eines Reglerpaars für Sensitivity bzw. Trigger (Schritt S): Text "… now" (der Wert, der gerade
// wirklich gilt), die gewählten Reglerstellungen (gesetzt wird erst mit "Apply"), die Stellungen des wirksamen
// Werts (null = unbekannt, z. B. Live-Weg noch nicht verbunden), "Restore original" frei, letzter Apply-Haken
class SettingPairUi {
    var nowText by mutableStateOf("")
    var chosen by mutableStateOf(LeftRight(0, 0))
    var applied by mutableStateOf<LeftRight<Int>?>(null)
    var restoreEnabled by mutableStateOf(false)
    var lastApplySuccessAt by mutableLongStateOf(0L)

    // Weicht die Wahl vom wirksamen Wert ab? (oranger Punkt im Umschalter, "Not applied yet")
    val isPending: Boolean get() = applied.let { it != null && it != chosen }
}

// Zustand des Teils "Curve" (Phase 6d): für welchen Stick die Karte gerade gilt, die gespeicherten Kurven (CurveStore),
// die gewählten (gespeichert wird erst mit "Apply"), der letzte Apply-Haken; dazu der Kurven-Editor (Phase 7): offen?
// Welcher Punkt ist gewählt?
class CurveUi {
    var side by mutableStateOf(OdinMapping.Side.LEFT)
    var showTriggers by mutableStateOf(false) // Karte zeigt L2/R2 statt eines Sticks (Z11)
    var applied by mutableStateOf(Profile.DEFAULT_CURVES)
    var chosen by mutableStateOf(Profile.DEFAULT_CURVES)
    var lastApplySuccessAt by mutableLongStateOf(0L)
    var editorOpen by mutableStateOf(false)
    var selectedPoint by mutableIntStateOf(0)

    // Curve mode (Phase 6c): verfügbar auf bekanntem Gerät mit bekannter Firmware (Plan Regel 7), läuft gerade, Schalter wartet
    // auf das Ergebnis
    val modeAvailable: Boolean = DeviceCheck.current == DeviceSupport.KNOWN
    var modeRunning by mutableStateOf(false)
    var modeBusy by mutableStateOf(false)

    // Weicht die Wahl vom Gespeicherten ab? (oranger Punkt im Umschalter)
    val isPending: Boolean get() = applied != chosen
}

// Alles, was der Bildschirm anzeigt. Gehört der MainActivity, darum bleibt z. B. eine noch nicht
// übernommene Reglerstellung beim Reiterwechsel erhalten (Risiko R54).
// Jede Änderung hier zeichnet die betroffenen Teile des Bildschirms automatisch neu (Compose-Zustand)
class MainUiState {

    // Welcher Reiter vorne ist (beim Öffnen der App: Profiles, Entscheidung A)
    var selectedTab by mutableStateOf(MainTab.PROFILES)

    // Reiter Sticks: Anzeige "Deadzone now", Reglerstellungen (Schritte, siehe DeadzoneSteps),
    // Knopf "Restore original" erst frei, wenn das Original für beide Sticks gemerkt ist
    var deadzoneNowText by mutableStateOf("")
    var leftStep by mutableIntStateOf(0)
    var rightStep by mutableIntStateOf(0)
    var restoreEnabled by mutableStateOf(false)

    // Zeitpunkt (SystemClock.uptimeMillis) des letzten erfolgreichen "Apply", 0 = noch nie.
    // Nur für die Rückmeldung: Der Knopf zeigt danach kurz einen grünen Haken
    var lastApplySuccessAt by mutableLongStateOf(0L)

    // Reiter Sticks, Schritt S: welcher Teil der Regler-Karte gezeigt wird; Reglerstellungen der wirksamen
    // Deadzone (null = unbekannt) für den Punkt "noch nicht übernommen"; Sensitivity und Trigger
    var stickSetting by mutableStateOf(StickSetting.DEADZONE)
    var deadzoneAppliedSteps by mutableStateOf<LeftRight<Int>?>(null)
    val sensitivity = SettingPairUi()
    val trigger = SettingPairUi()
    val curve = CurveUi()

    // Deadzone: weicht die Reglerstellung vom wirksamen Wert ab?
    val deadzonePending: Boolean
        get() = deadzoneAppliedSteps.let { it != null && it != LeftRight(leftStep, rightStep) }

    // Reiter Sticks, Karte "Live" (Phase 3a): letzte Stellung der Sticks und Trigger vom Controller.
    // null = seit dem Öffnen bzw. Zurückkehren in die App noch keine Bewegung gesehen (Anzeige "–")
    var liveInput by mutableStateOf<LiveInput?>(null)

    // Reiter Sticks: Stand des Auto-Sets (Phase 3c-1). Solange es nicht Idle ist, zeigt der Reiter
    // statt der beiden Karten die große Auto-Set-Ansicht (Entscheidung U1)
    var autoSet by mutableStateOf<AutoSetUi>(AutoSetUi.Idle)

    // Reiter Buttons (Phase 4): Belegung, die gerade eingestellt ist (gelesen), und die gewählte, die erst
    // "Apply" schreibt (Entscheidung B3). Fehlt ein Eintrag, ist der Wert unbekannt (Anzeige "–")
    val appliedTargets = mutableStateMapOf<BackButton, ButtonTarget>()
    val chosenTargets = mutableStateMapOf<BackButton, ButtonTarget>()

    // Layout aus dem Odin-Menü (bestimmt die Namen von A/B/X/Y) und die Tasten, die gerade gedrückt sind
    // (Gedrückt-Anzeige im Bild, nur ansehen)
    var controllerLayout by mutableStateOf(ControllerLayout.UNKNOWN)
    var pressedParts by mutableStateOf(emptySet<OdinPart>())

    // Reiter Buttons: "Restore original" erst frei, wenn das Original für M1 und M2 gemerkt ist;
    // Zeitpunkt des letzten erfolgreichen "Apply" (Haken am Knopf, wie bei der Deadzone)
    var buttonsRestoreEnabled by mutableStateOf(false)
    var lastButtonsApplyAt by mutableLongStateOf(0L)

    // Reiter Profiles (Phase 5a)
    val profiles = ProfilesUi()

    // Reiter Lights: läuft das Ambilight, Schalter "Don't ask again", Werte der sechs Regler
    var ambilightRunning by mutableStateOf(false)
    var skipPrompt by mutableStateOf(false)
    val lightValues = mutableStateMapOf<LightSlider, Int>()

    // Reiter Settings (5d-2): ist die StickLab-Kachel im Schnellmenü? (Merker der Kachel, E2)
    var tileAdded by mutableStateOf(false)

    // Gewählter Hintergrund (Reiter Settings, 06.11.2026)
    var background by mutableStateOf(AppBackground.DEFAULT)

    // Statuszeile unten (Text-Kennung aus strings.xml)
    var statusText by mutableIntStateOf(R.string.status_ready)

    // Offene Nachfrage, der Text der Restore-Nachfrage (mit den Originalwerten; Deadzone bzw. M1/M2) und der Text der
    // Übernehmen-Nachfrage des Auto-Sets (alt → neu, Phase 3c-2)
    var dialog by mutableStateOf(MainDialog.NONE)
    var restoreMessage by mutableStateOf("")
    var autoSetApplyMessage by mutableStateOf("")
}

// Stand des Auto-Sets (Phase 3c-1, Kontrollmessung seit 3c-2), wie ihn die große Ansicht zeigt
sealed interface AutoSetUi {
    // Kein Auto-Set: normale Karten im Reiter Sticks
    data object Idle : AutoSetUi

    // Messung läuft bis endsAt (SystemClock.uptimeMillis); reach = Stand der 8 Felder je Stick.
    // checking = null: Messung mit Deadzone 0; sonst Kontrollmessung der Vorschläge aus diesem Ergebnis
    data class Running(val endsAt: Long, val reach: LeftRight<StickReach>, val checking: AutoSetResult? = null) : AutoSetUi

    // Messung vorbei: Ergebnis, Vorschläge und Urteile bleiben stehen, bis "Done", "Again" oder Übernehmen
    data class Finished(val result: AutoSetResult) : AutoSetUi
}

// Was der Bildschirm auslösen kann. MainActivity führt es aus, denn nur sie spricht mit dem Odin
interface MainActions {
    val curveActions: CurveActions
    fun onAutoSetStart()
    fun onAutoSetStop()
    fun onAutoSetDone()
    fun onAutoSetCheck()
    fun onAutoSetApply()
    fun onAutoSetApplyConfirmed()
    fun onDeadzoneStepChanged(side: OdinMapping.Side, step: Int)
    fun onApplyDeadzone()
    fun onRestoreClicked()
    fun onRestoreConfirmed()
    fun onStickSettingChosen(setting: StickSetting)
    fun onPairStepChanged(setting: StickSetting, side: OdinMapping.Side, step: Int)
    fun onApplyPair(setting: StickSetting)
    fun onRestorePairClicked(setting: StickSetting)
    fun onRestorePairConfirmed(setting: StickSetting)
    fun onButtonTargetStep(button: BackButton, delta: Int)
    fun onButtonTargetChosen(button: BackButton, target: ButtonTarget)
    fun onApplyButtons()
    fun onRestoreButtonsClicked()
    fun onRestoreButtonsConfirmed()
    fun onProfilesShown()
    fun onProfileSlotSelected(slot: Int)
    fun onProfileUse()
    fun onProfileEdit()
    fun onProfileRestoreEverything()
    fun onProfileRestoreEverythingConfirmed()
    fun onProfileSave()
    fun onProfileSaveConfirmed()
    fun onProfileRename()
    fun onProfileRenamed(name: String)
    fun onProfileColor()
    fun onProfileColorChosen(color: ProfileColor)
    fun onProfileCopy()
    fun onProfileDelete()
    fun onProfileDeleteConfirmed()
    fun onProfileSaveToActive()
    fun onProfilesHelp()
    fun onProfilesHelpClosed()
    fun onAmbilightClicked()
    fun onAmbilightLightOffConfirmed()
    fun onSkipPromptChanged(checked: Boolean)
    fun onLightChanged(slider: LightSlider, value: Int)
    fun onLightDefaults()
    fun onAddTile()
    fun onBackgroundChosen(background: AppBackground)
    fun onIntroConfirmed()
    fun onDialogDismissed()
}
