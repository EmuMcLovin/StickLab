package com.sticklab.app

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// Was die Regler-Karte für einen Teil zeigt (Deadzone, Sensitivity oder Trigger, Schritt S):
// "… now" mit dem wirksamen Wert, zwei Regler (Stellungen 0 … maxStep, valueText macht daraus die Beschriftung),
// "Apply" und "Restore original" nur für diesen Teil
class SliderPairCard(
    val nowText: String,
    @param:StringRes val leftName: Int,
    @param:StringRes val rightName: Int,
    val leftStep: Int,
    val rightStep: Int,
    val maxStep: Int,
    val valueText: @Composable (step: Int) -> String,
    val restoreEnabled: Boolean,
    val lastApplySuccessAt: Long,
    val onLeftStepChange: (Int) -> Unit,
    val onRightStepChange: (Int) -> Unit,
    val onApply: () -> Unit,
    val onRestore: () -> Unit,
)

// Reiter "Sticks": links die Regler-Karte mit Umschalter Deadzone · Sensitivity · Trigger (Schritt S),
// rechts daneben die Karte "Live" (Phase 3a, Entscheidung L1: nebeneinander, damit man die Wirkung von
// "Apply" sofort an den Sticks sieht, ohne zu scrollen).
// Während und nach einem Auto-Set steht hier stattdessen die große Auto-Set-Ansicht (Phase 3c-1, U1),
// bis "Done" gedrückt wird. Beim Teil "Curve" (Phase 6d) stehen hier statt beider Karten die Kurven-Karten;
// card ist dann null
@Composable
fun SticksTab(
    setting: StickSetting,
    pendingSettings: Set<StickSetting>,
    onSettingChosen: (StickSetting) -> Unit,
    card: SliderPairCard?,
    curve: CurveUi,
    curveActions: CurveActions,
    liveInput: () -> LiveInput?,
    autoSet: AutoSetUi,
    onAutoSetStart: () -> Unit,
    onAutoSetStop: () -> Unit,
    onAutoSetDone: () -> Unit,
    onAutoSetCheck: () -> Unit,
    onAutoSetApply: () -> Unit,
) {
    if (autoSet != AutoSetUi.Idle) {
        AutoSetPanel(
            autoSet = autoSet,
            liveInput = liveInput,
            onStop = onAutoSetStop,
            onAgain = onAutoSetStart,
            onCheck = onAutoSetCheck,
            onApply = onAutoSetApply,
            onDone = onAutoSetDone,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    if (card == null) {
        if (curve.editorOpen) CurveEditorPanel(curve, curveActions, liveInput)
        else CurvePanel(pendingSettings, onSettingChosen, curve, curveActions, liveInput)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(StickLabDimens.ColumnGap)) {
        SliderCard(
            setting = setting,
            pendingSettings = pendingSettings,
            onSettingChosen = onSettingChosen,
            card = card,
            modifier = Modifier.weight(1f),
        )
        StickLiveCard(
            liveInput = liveInput,
            onAutoSet = onAutoSetStart,
            highlight = liveHighlightOf(setting),
            modifier = Modifier.width(StickLabDimens.LiveCardWidth),
        )
    }
}

// Die Regler-Karte.
// Oben der Umschalter (statt der Überschrift "Deadzone"), darunter der Wert, der gerade wirklich wirkt,
// und zwei Regler. Schieben ändert nur die Anzeige, gesetzt wird erst mit "Apply" (Wunsch Besitzer
// 15.10.2026). "Restore original" daneben (A2: eine Zeile, damit die Karte ohne Scrollen passt)
@Composable
private fun SliderCard(
    setting: StickSetting,
    pendingSettings: Set<StickSetting>,
    onSettingChosen: (StickSetting) -> Unit,
    card: SliderPairCard,
    modifier: Modifier,
) {
    SettingsCard(modifier = modifier) {
        StickSettingSwitch(selected = setting, pending = pendingSettings, onSelect = onSettingChosen)
        Text(
            text = card.nowText,
            fontSize = StickLabDimens.LabelSize,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(top = NOW_TEXT_GAP)
                .alpha(NOW_TEXT_ALPHA)
                .popOnChange(card.nowText),
        )
        // key: Beim Wechsel des Teils neue Regler, damit der Griff nicht vom alten Wert herübergleitet
        key(setting) {
            PairSlider(card.leftName, card.leftStep, card, card.onLeftStepChange, Modifier.enterStaggered(0))
            PairSlider(card.rightName, card.rightStep, card, card.onRightStepChange, Modifier.enterStaggered(1))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(StickLabDimens.ButtonGap),
            modifier = Modifier.padding(top = StickLabDimens.ButtonGap).enterStaggered(2),
        ) {
            val confirmed = isRecent(card.lastApplySuccessAt, APPLY_CONFIRM_MS)
            StickLabButton(
                text = stringResource(if (confirmed) R.string.applied else R.string.apply),
                onClick = card.onApply,
                primary = true,
                confirmed = confirmed,
                modifier = Modifier.weight(1f),
            )
            StickLabButton(
                text = stringResource(R.string.restore_original),
                onClick = card.onRestore,
                enabled = card.restoreEnabled,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// Regler für eine Seite. Ein Druck aufs Steuerkreuz links/rechts bewegt ihn genau einen Schritt
// (Deadzone 0,1 %, Sensitivity 0,05, Trigger 1 %)
@Composable
private fun PairSlider(
    @StringRes name: Int,
    step: Int,
    card: SliderPairCard,
    onStepChange: (Int) -> Unit,
    modifier: Modifier,
) {
    SettingSlider(
        label = stringResource(name),
        valueText = card.valueText(step),
        value = step.toFloat(),
        valueRange = 0f..card.maxStep.toFloat(),
        steps = card.maxStep - 1,
        onValueChange = { onStepChange(it.roundToInt()) },
        modifier = modifier,
    )
}

// In der Live-Karte leuchtet, woran man den gewählten Teil sieht: Sensitivity an den Sticks, Trigger an L2/R2
private fun liveHighlightOf(setting: StickSetting): LiveHighlight = when (setting) {
    StickSetting.DEADZONE, StickSetting.CURVE -> LiveHighlight.NONE
    StickSetting.SENSITIVITY -> LiveHighlight.STICKS
    StickSetting.TRIGGER -> LiveHighlight.TRIGGERS
}

// Wahr, solange seit "at" (SystemClock.uptimeMillis) weniger als durationMs vergangen sind.
// Danach wird von selbst wieder neu gezeichnet. Für den Haken nach "Apply", auch nach Reiterwechsel richtig.
// Auch vom Reiter Buttons benutzt (Phase 4)
@Composable
fun isRecent(at: Long, durationMs: Long): Boolean {
    var recent by remember(at) { mutableStateOf(at > 0 && SystemClock.uptimeMillis() - at < durationMs) }
    LaunchedEffect(at) {
        val left = at + durationMs - SystemClock.uptimeMillis()
        if (at > 0 && left > 0) {
            recent = true
            delay(left)
        }
        recent = false
    }
    return recent
}

// Entwurf A2: Haken bleibt 1,6 s (auch im Reiter Buttons), Anzeige "… now" etwas blasser (opacity .8).
// Abstand Umschalter → "… now" wie im Entwurf S
const val APPLY_CONFIRM_MS = 1600L
private const val NOW_TEXT_ALPHA = 0.8f
private val NOW_TEXT_GAP = 6.dp

// Vorschau in Android Studio mit den Werten des Besitzers (Deadzone 1,5 % / 2,5 %)
@Preview(widthDp = 716)
@Composable
private fun SticksTabPreview() {
    StickLabTheme {
        SticksTab(
            setting = StickSetting.DEADZONE,
            pendingSettings = setOf(StickSetting.SENSITIVITY),
            onSettingChosen = {},
            card = SliderPairCard(
                nowText = "Deadzone now: Left 1.5 % · Right 2.5 %",
                leftName = R.string.left_stick,
                rightName = R.string.right_stick,
                leftStep = 15,
                rightStep = 25,
                maxStep = DeadzoneSteps.MAX_STEP,
                valueText = { stringResource(R.string.deadzone_percent, DeadzoneSteps.toPercent(it)) },
                restoreEnabled = true,
                lastApplySuccessAt = 0L,
                onLeftStepChange = {},
                onRightStepChange = {},
                onApply = {},
                onRestore = {},
            ),
            curve = CurveUi(),
            curveActions = PreviewCurveActions,
            liveInput = { null },
            autoSet = AutoSetUi.Idle,
            onAutoSetStart = {},
            onAutoSetStop = {},
            onAutoSetDone = {},
            onAutoSetCheck = {},
            onAutoSetApply = {},
        )
    }
}
