package com.sticklab.app

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

// Der ganze Bildschirm im Aussehen A2: bewegter Farbverlauf als Hintergrund, links die Leiste mit Symbolen,
// rechts oben die Profil-Zeile (ProfileBar, ersetzt seit der 31. Sitzung den App-Namen), darunter der Inhalt des
// Reiters (passt ohne Scrollen, quer) und fest unten die Statuszeile.
// Darüber ggf. eine Nachfrage
@Composable
fun MainScreen(state: MainUiState, actions: MainActions) {
    Surface(modifier = Modifier.fillMaxSize(), color = StickLabColors.Night, contentColor = StickLabColors.Ink) {
        GradientBackground(state.background)
        Row(
            horizontalArrangement = Arrangement.spacedBy(StickLabDimens.ColumnGap),
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(StickLabDimens.ScreenPadding),
        ) {
            // zIndex: die Namensblase der Leiste ragt in den Inhalt und muss darüber liegen
            NavRail(
                selected = state.selectedTab,
                onSelect = { state.selectedTab = it },
                modifier = Modifier.zIndex(1f),
            )
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                ProfileBar(state.profiles, actions::onProfileSaveToActive, actions::onProfilesHelp)
                // weight(1f): der Inhalt nimmt den Platz zwischen Kopf und Statuszeile,
                // so bleibt die Statuszeile immer sichtbar
                TabContent(state, actions, Modifier.weight(1f).fillMaxWidth())
                StatusLine(state.statusText)
            }
        }
        MainDialogs(state, actions)
    }
}

// Inhalt des gewählten Reiters. Beim Wechsel gleitet der alte Inhalt weg und der neue fährt aus der
// Richtung des Symbols ein (weiter unten in der Leiste → von unten). Kein Reiter scrollt (25.10.2026)
@Composable
private fun TabContent(state: MainUiState, actions: MainActions, modifier: Modifier) {
    val enterDistance = with(LocalDensity.current) { TAB_ENTER_DISTANCE.roundToPx() }
    val exitDistance = with(LocalDensity.current) { TAB_EXIT_DISTANCE.roundToPx() }
    AnimatedContent(
        targetState = state.selectedTab,
        transitionSpec = { tabTransition(targetState.ordinal > initialState.ordinal, enterDistance, exitDistance) },
        label = "tabContent",
        modifier = modifier,
    ) { tab ->
        // Kein Scrollen (Besitzer 25.10.2026): jeder Reiter ist so gebaut, dass er quer ganz auf den
        // Bildschirm passt. Was trotzdem nicht passt, wird unten abgeschnitten – darum nach jeder Änderung
        // am Odin mit Foto prüfen
        Column(modifier = Modifier.fillMaxSize()) {
            TabPage(tab, state, actions)
        }
    }
}

// Übergang zwischen zwei Reitern (Entwurf A2: neuer Inhalt 36 dp mit Federung, alter 28 dp in 180 ms weg)
private fun tabTransition(forward: Boolean, enterDistance: Int, exitDistance: Int): ContentTransform {
    val direction = if (forward) 1 else -1
    val enter = slideInVertically(StickLabMotion.bouncy()) { direction * enterDistance } + fadeIn(tween(TAB_FADE_IN_MS))
    val exit = slideOutVertically(tween(TAB_EXIT_MS)) { -direction * exitDistance } +
        fadeOut(tween(TAB_EXIT_MS)) + scaleOut(tween(TAB_EXIT_MS), targetScale = TAB_EXIT_SCALE)
    return enter togetherWith exit
}

private val TAB_ENTER_DISTANCE = 36.dp
private val TAB_EXIT_DISTANCE = 28.dp
private const val TAB_FADE_IN_MS = 250
private const val TAB_EXIT_MS = 180
private const val TAB_EXIT_SCALE = 0.98f

// Die Karte eines Reiters
@Composable
private fun TabPage(tab: MainTab, state: MainUiState, actions: MainActions) {
    when (tab) {
        MainTab.STICKS -> SticksTab(
            setting = state.stickSetting,
            pendingSettings = pendingStickSettings(state),
            onSettingChosen = actions::onStickSettingChosen,
            card = sliderCardOf(state, actions),
            curve = state.curve,
            curveActions = actions.curveActions,
            liveInput = { state.liveInput },
            autoSet = state.autoSet,
            onAutoSetStart = actions::onAutoSetStart,
            onAutoSetStop = actions::onAutoSetStop,
            onAutoSetDone = actions::onAutoSetDone,
            onAutoSetCheck = actions::onAutoSetCheck,
            onAutoSetApply = actions::onAutoSetApply,
        )
        MainTab.BUTTONS -> ButtonsTab(
            layout = state.controllerLayout,
            applied = state.appliedTargets,
            chosen = state.chosenTargets,
            pressedParts = { state.pressedParts },
            restoreEnabled = state.buttonsRestoreEnabled,
            lastApplySuccessAt = state.lastButtonsApplyAt,
            onStep = actions::onButtonTargetStep,
            onChoose = actions::onButtonTargetChosen,
            onApply = actions::onApplyButtons,
            onRestore = actions::onRestoreButtonsClicked,
        )
        MainTab.LIGHTS -> LightsTab(
            ambilightRunning = state.ambilightRunning,
            skipPrompt = state.skipPrompt,
            lightValues = state.lightValues,
            onAmbilightClick = actions::onAmbilightClicked,
            onSkipPromptChange = actions::onSkipPromptChanged,
            onLightChange = actions::onLightChanged,
            onDefaults = actions::onLightDefaults,
        )
        MainTab.PROFILES -> ProfilesTab(state.profiles, state.controllerLayout, actions)
        MainTab.SETTINGS -> SettingsTab(
            activeProfileName = state.profiles.activeSlot?.let { state.profiles.slots.getOrNull(it)?.name },
            tileAdded = state.tileAdded,
            onAddTile = actions::onAddTile,
            background = state.background,
            onBackgroundChosen = actions::onBackgroundChosen,
        )
    }
}

// Inhalt der Regler-Karte im Reiter Sticks für den gewählten Teil (Schritt S). Die Deadzone benutzt ihre
// bisherigen Werte und Aktionen (Auto-Set hängt daran), Sensitivity und Trigger die gemeinsamen
private fun sliderCardOf(state: MainUiState, actions: MainActions): SliderPairCard? = when (state.stickSetting) {
    StickSetting.CURVE -> null
    StickSetting.DEADZONE -> SliderPairCard(
        nowText = state.deadzoneNowText,
        leftName = R.string.left_stick,
        rightName = R.string.right_stick,
        leftStep = state.leftStep,
        rightStep = state.rightStep,
        maxStep = DeadzoneSteps.MAX_STEP,
        valueText = { stringResource(R.string.deadzone_percent, DeadzoneSteps.toPercent(it)) },
        restoreEnabled = state.restoreEnabled,
        lastApplySuccessAt = state.lastApplySuccessAt,
        onLeftStepChange = { actions.onDeadzoneStepChanged(OdinMapping.Side.LEFT, it) },
        onRightStepChange = { actions.onDeadzoneStepChanged(OdinMapping.Side.RIGHT, it) },
        onApply = actions::onApplyDeadzone,
        onRestore = actions::onRestoreClicked,
    )
    StickSetting.SENSITIVITY -> pairCardOf(
        StickSetting.SENSITIVITY, state.sensitivity, SettingSteps.SENSITIVITY,
        R.string.left_stick, R.string.right_stick, actions,
    ) { stringResource(R.string.sensitivity_value, SettingSteps.SENSITIVITY.toValue(it)) }
    StickSetting.TRIGGER -> pairCardOf(
        StickSetting.TRIGGER, state.trigger, SettingSteps.TRIGGER,
        R.string.live_l2, R.string.live_r2, actions,
    ) { stringResource(R.string.trigger_percent, SettingSteps.TRIGGER.toValue(it) * 100f) }
}

// Regler-Karte für Sensitivity bzw. Trigger
private fun pairCardOf(
    setting: StickSetting,
    pair: SettingPairUi,
    steps: SettingSteps,
    @StringRes leftName: Int,
    @StringRes rightName: Int,
    actions: MainActions,
    valueText: @Composable (Int) -> String,
) = SliderPairCard(
    nowText = pair.nowText,
    leftName = leftName,
    rightName = rightName,
    leftStep = pair.chosen.left,
    rightStep = pair.chosen.right,
    maxStep = steps.maxStep,
    valueText = valueText,
    restoreEnabled = pair.restoreEnabled,
    lastApplySuccessAt = pair.lastApplySuccessAt,
    onLeftStepChange = { actions.onPairStepChanged(setting, OdinMapping.Side.LEFT, it) },
    onRightStepChange = { actions.onPairStepChanged(setting, OdinMapping.Side.RIGHT, it) },
    onApply = { actions.onApplyPair(setting) },
    onRestore = { actions.onRestorePairClicked(setting) },
)

// Teile mit verschobenem, noch nicht übernommenem Regler (oranger Punkt im Umschalter)
private fun pendingStickSettings(state: MainUiState): Set<StickSetting> = buildSet {
    if (state.deadzonePending) add(StickSetting.DEADZONE)
    if (state.sensitivity.isPending) add(StickSetting.SENSITIVITY)
    if (state.trigger.isPending) add(StickSetting.TRIGGER)
    if (state.curve.isPending) add(StickSetting.CURVE)
}

// Statuszeile unten, z. B. "Saved – active now". Bei neuem Text gleitet er ein und der Punkt pulsiert;
// der Punkt ist grün, bei Fehlern orange
@Composable
private fun StatusLine(@StringRes text: Int) {
    val dotColor by animateColorAsState(if (text in WARNING_TEXTS) StickLabColors.Warning else StickLabColors.Success, label = "statusDot")
    val pulse = remember { Animatable(1f) }
    var shownText by remember { mutableIntStateOf(text) }
    LaunchedEffect(text) {
        if (text == shownText) return@LaunchedEffect
        shownText = text
        pulse.animateTo(STATUS_PULSE_SCALE, tween(STATUS_PULSE_UP_MS))
        pulse.animateTo(1f, StickLabMotion.bouncy())
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(start = 4.dp, top = 8.dp).alpha(StickLabDimens.DIMMED_ALPHA),
    ) {
        Box(
            modifier = Modifier
                .size(STATUS_DOT_SIZE)
                .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
                .background(dotColor, CircleShape),
        )
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                (fadeIn(tween(STATUS_TEXT_MS)) + slideInVertically(tween(STATUS_TEXT_MS)) { it / 3 }) togetherWith
                    fadeOut(tween(STATUS_TEXT_MS / 2))
            },
            label = "statusText",
        ) { shown ->
            Text(text = stringResource(shown), fontSize = StickLabDimens.SubtitleSize)
        }
    }
}

// Meldungen, bei denen der Punkt orange wird
private val WARNING_TEXTS = setOf(
    R.string.status_error, R.string.status_read_failed, R.string.status_denied,
    // Messung mit Deadzone 0 (3b/3c Auto-Set): lief nicht an bzw. Deadzone noch nicht zurück
    R.string.status_drift_failed, R.string.status_drift_restore_failed,
    // Reiter Buttons: M1/M2 nicht gespeichert; gewählt, aber noch nicht übernommen (Entwurf: orange)
    R.string.status_buttons_failed, R.string.status_not_applied,
    // Reiter Profiles: nicht gespeichert (Wert fehlt, Auto-Set läuft, Datei nicht geschrieben)
    R.string.status_profile_missing_deadzone, R.string.status_profile_missing_sensitivity,
    R.string.status_profile_missing_trigger, R.string.status_profile_missing_buttons,
    R.string.status_profile_autoset_running, R.string.status_profile_store_failed,
    // Reiter Profiles, Use / Restore everything (5b-1): ein Teil nicht gesetzt, nichts gesetzt, Original unbekannt
    R.string.status_profile_failed_deadzone, R.string.status_profile_failed_trigger,
    R.string.status_profile_failed_sensitivity, R.string.status_profile_failed_buttons,
    R.string.status_profile_apply_autoset_running,
    R.string.status_profile_restore_unknown,
    R.string.status_tile_add_failed,
    R.string.status_curves_failed, R.string.status_curve_mode_failed, R.string.status_autoset_curve_mode,
)

// Entwurf A2: Punkt 7, pulsiert auf ×2,2, Text gleitet in 300 ms ein
private val STATUS_DOT_SIZE = 7.dp
private const val STATUS_PULSE_SCALE = 2.2f
private const val STATUS_PULSE_UP_MS = 150
private const val STATUS_TEXT_MS = 300

// Die Nachfragen: "Gerät nicht unterstützt", Begrüßung, "Restore original?" (Deadzone bzw. M1/M2),
// Auto-Set übernehmen, Fenster des Reiters Profiles.
// Antippen daneben oder "Zurück" schließt sie ohne Wirkung
@Composable
private fun MainDialogs(state: MainUiState, actions: MainActions) {
    when (state.dialog) {
        MainDialog.NONE -> Unit
        MainDialog.UNSUPPORTED -> StickLabDialog(
            title = stringResource(R.string.unsupported_title),
            text = stringResource(R.string.unsupported_text),
            onConfirm = actions::onDialogDismissed,
            onDismiss = actions::onDialogDismissed,
        )
        // Curve mode beim ersten Einschalten: Was er tut. Nur OK startet ihn (Phase 6e), darum sichtbar "Abbrechen".
        // Breit, damit die vier Zeilen quer auf den Bildschirm passen
        MainDialog.CURVE_MODE_INTRO -> StickLabDialog(
            title = stringResource(R.string.curve_mode_intro_title),
            text = stringResource(R.string.curve_mode_intro_text),
            onConfirm = actions.curveActions::onCurveModeIntroConfirmed,
            onDismiss = actions::onDialogDismissed,
            showCancel = true,
            wide = true,
        )
        // Ambilight gestartet, aber die Kachel "Joystick light" ist aus: nur Hinweis, OK startet trotzdem
        MainDialog.AMBILIGHT_LIGHT_OFF -> StickLabDialog(
            title = stringResource(R.string.ambilight_light_off_title),
            text = stringResource(R.string.ambilight_light_off_text),
            onConfirm = actions::onAmbilightLightOffConfirmed,
            onDismiss = actions::onDialogDismissed,
            showCancel = true,
        )
        // Nur OK merkt sich "Begrüßung gezeigt" (wie bisher)
        MainDialog.INTRO -> StickLabDialog(
            title = stringResource(R.string.intro_title),
            text = stringResource(R.string.intro_text),
            onConfirm = actions::onIntroConfirmed,
            onDismiss = actions::onDialogDismissed,
        )
        // Entscheidung R-3: Nachfrage mit den Werten, erst OK setzt zurück
        MainDialog.RESTORE -> StickLabDialog(
            title = stringResource(R.string.restore_title),
            text = state.restoreMessage,
            onConfirm = actions::onRestoreConfirmed,
            onDismiss = actions::onDialogDismissed,
            showCancel = true,
        )
        // Auto-Set übernehmen (3c-2): alt → neu sichtbar, erst OK schreibt die Systemeinstellung (R81/R87)
        MainDialog.AUTOSET_APPLY -> StickLabDialog(
            title = stringResource(R.string.autoset_apply_title),
            text = state.autoSetApplyMessage,
            onConfirm = actions::onAutoSetApplyConfirmed,
            onDismiss = actions::onDialogDismissed,
            showCancel = true,
        )
        // Reiter Buttons (B4): nur M1/M2 zurück, die Deadzone bleibt
        MainDialog.BUTTONS_RESTORE -> StickLabDialog(
            title = stringResource(R.string.buttons_restore_title),
            text = state.restoreMessage,
            onConfirm = actions::onRestoreButtonsConfirmed,
            onDismiss = actions::onDialogDismissed,
            showCancel = true,
        )
        // Reiter Sticks, Schritt S: nur Sensitivity bzw. nur Trigger zurück
        MainDialog.SENSITIVITY_RESTORE -> StickLabDialog(
            title = stringResource(R.string.sensitivity_restore_title),
            text = state.restoreMessage,
            onConfirm = { actions.onRestorePairConfirmed(StickSetting.SENSITIVITY) },
            onDismiss = actions::onDialogDismissed,
            showCancel = true,
        )
        MainDialog.TRIGGER_RESTORE -> StickLabDialog(
            title = stringResource(R.string.trigger_restore_title),
            text = state.restoreMessage,
            onConfirm = { actions.onRestorePairConfirmed(StickSetting.TRIGGER) },
            onDismiss = actions::onDialogDismissed,
            showCancel = true,
        )
        // Erklärung "How profiles work": einmal von selbst, sonst über "?" in der Profil-Zeile
        MainDialog.PROFILES_HELP -> StickLabDialog(
            title = stringResource(R.string.profiles_help_title),
            text = stringResource(R.string.profiles_help_text),
            onConfirm = actions::onProfilesHelpClosed,
            onDismiss = actions::onProfilesHelpClosed,
            wide = true,
        )
        // Reiter Profiles (5b-1): alle Werte "jetzt → vor StickLab", erst OK setzt sie am Odin (P5, R122)
        MainDialog.PROFILE_RESTORE_ALL -> StickLabDialog(
            title = stringResource(R.string.profile_restore_all_title),
            text = state.restoreMessage,
            onConfirm = actions::onProfileRestoreEverythingConfirmed,
            onDismiss = actions::onDialogDismissed,
            showCancel = true,
        )
        // Übrige Fenster des Reiters Profiles: ändern nur die eigene Datei, nie etwas am Odin
        else -> ProfileDialogs(state, actions)
    }
}

// Fenster des Reiters Profiles. Ohne gewähltes Profil (sollte nicht vorkommen) bleibt alles zu
@Composable
private fun ProfileDialogs(state: MainUiState, actions: MainActions) {
    val profile = state.profiles.selectedProfile ?: return
    when (state.dialog) {
        // "Save current" über ein vorhandenes Profil: alt → neu sichtbar, erst OK ersetzt (Entwurf)
        MainDialog.PROFILE_REPLACE -> StickLabDialog(
            title = stringResource(R.string.profile_replace_title, profile.name),
            text = state.profiles.replaceMessage,
            onConfirm = actions::onProfileSaveConfirmed,
            onDismiss = actions::onDialogDismissed,
            showCancel = true,
        )
        MainDialog.PROFILE_DELETE -> StickLabDialog(
            title = stringResource(R.string.profile_delete_title, profile.name),
            text = stringResource(R.string.profile_delete_text),
            onConfirm = actions::onProfileDeleteConfirmed,
            onDismiss = actions::onDialogDismissed,
            showCancel = true,
        )
        MainDialog.PROFILE_RENAME -> ProfileRenameDialog(
            currentName = profile.name,
            onConfirm = actions::onProfileRenamed,
            onDismiss = actions::onDialogDismissed,
        )
        MainDialog.PROFILE_COLOR -> ProfileColorDialog(
            current = profile.color,
            onChoose = actions::onProfileColorChosen,
            onDismiss = actions::onDialogDismissed,
        )
        else -> Unit
    }
}

// Eine Nachfrage im Aussehen A2 mit Titel, Text und OK.
// "OK" hat gleich den Fokus, damit man sie mit dem Steuerkreuz sofort bedienen kann.
// showCancel = zusätzlich "Abbrechen" (wirkt wie daneben tippen).
// wide = breites Fenster für längere Texte (Erklärung "How profiles work"), damit es quer nicht zu hoch wird
@Composable
private fun StickLabDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    showCancel: Boolean = false,
    wide: Boolean = false,
) {
    StickLabDialogFrame(onDismiss = onDismiss, wide = wide) {
        // Im Fenster, damit "OK" schon da ist, wenn der Fokus kommt
        val okFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { okFocus.requestFocus() }
        Text(text = title, fontSize = StickLabDimens.CardTitleSize, fontWeight = FontWeight.SemiBold)
        Text(
            text = text,
            fontSize = DIALOG_TEXT_SIZE,
            lineHeight = DIALOG_TEXT_LINE_HEIGHT,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp).alpha(0.75f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showCancel) {
                StickLabButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
            StickLabButton(
                text = stringResource(android.R.string.ok),
                onClick = onConfirm,
                primary = true,
                modifier = Modifier.weight(1f).focusRequester(okFocus),
            )
        }
    }
}

// Rahmen jeder Nachfrage im Aussehen A2: springt aus der Mitte auf (federt), abgerundet, dunkel.
// Antippen daneben oder "Zurück" ruft onDismiss. Auch für die Fenster des Reiters Profiles (ProfileDialogs).
// wide = 560 statt 340 dp breit; dafür die Android-Standardbreite für Fenster ausschalten (die würde begrenzen)
@Composable
fun StickLabDialogFrame(onDismiss: () -> Unit, wide: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = !wide)) {
        val appear = remember { Animatable(0f) }
        LaunchedEffect(Unit) { launch { appear.animateTo(1f, StickLabMotion.bouncy()) } }
        val shape = RoundedCornerShape(StickLabDimens.CardCornerRadius)
        Column(
            modifier = Modifier
                .graphicsLayer {
                    alpha = appear.value.coerceIn(0f, 1f)
                    val scale = DIALOG_START_SCALE + (1f - DIALOG_START_SCALE) * appear.value
                    scaleX = scale
                    scaleY = scale
                }
                .widthIn(max = if (wide) DIALOG_WIDE_WIDTH else DIALOG_MAX_WIDTH)
                .background(StickLabColors.DialogSurface, shape)
                .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                .padding(horizontal = 22.dp, vertical = 20.dp),
            content = content,
        )
    }
}

// Entwurf A2: Nachfrage 340 breit, springt von ×0,85 auf
private val DIALOG_MAX_WIDTH = 340.dp
private const val DIALOG_START_SCALE = 0.85f
val DIALOG_TEXT_SIZE = 13.5.sp

// Zeilenhöhe des Textes: ohne Angabe gibt Compose 24 sp (Fließtext) – die Erklärung war damit quer fast so hoch wie
// der Bildschirm und OK gequetscht (Foto 26.10.2026, 22:18). 19 sp ≈ Faktor 1,4 wie im Entwurf
private val DIALOG_TEXT_LINE_HEIGHT = 19.sp

// Breites Fenster (Erklärung): 560 dp lassen auf dem 832 dp breiten Bildschirm links und rechts Rand
private val DIALOG_WIDE_WIDTH = 560.dp
