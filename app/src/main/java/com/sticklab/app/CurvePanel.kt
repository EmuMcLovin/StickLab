package com.sticklab.app

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// Teil "Curve" im Reiter Sticks (Plan Phase 6d, kein HTML-Entwurf, siehe Logbuch "Entscheidungen zum Überprüfen").
// Links die Karte mit dem Umschalter, den Kacheln der Kurven (je mit Vorschaubild, Plan Phase 7) und den Reglern für
// äußere und Anti-Deadzone. Rechts die Karte des gewählten Sticks: Diagramm mit Live-Punkt, darunter die Knöpfe.
// Regler und Kacheln gelten für einen Stick, der wird rechts gewählt. Quer ohne Scrollen, passt in 317 dp Höhe
@Composable
fun CurvePanel(
    pending: Set<StickSetting>,
    onSettingChosen: (StickSetting) -> Unit,
    curve: CurveUi,
    actions: CurveActions,
    liveInput: () -> LiveInput?,
) {
    val settings = curve.chosen.of(curve.side)
    Row(horizontalArrangement = Arrangement.spacedBy(StickLabDimens.ColumnGap)) {
        SettingsCard(modifier = Modifier.weight(1f)) {
            StickSettingSwitch(selected = StickSetting.CURVE, pending = pending, onSelect = onSettingChosen)
            if (curve.showTriggers) {
                TriggerSliders(curve.chosen, actions, Modifier.enterStaggered(0))
            } else {
                CurveTiles(settings, actions::onCurveChosen, Modifier.padding(top = TILES_TOP_GAP).enterStaggered(0))
                CurveSliders(settings, actions, Modifier.enterStaggered(1))
            }
            Text(
                text = stringResource(curveHint(curve)),
                fontSize = StickLabDimens.HintSize,
                lineHeight = HINT_LINE_HEIGHT,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp).alpha(StickLabDimens.DIMMED_ALPHA),
            )
        }
        SettingsCard(modifier = Modifier.width(StickLabDimens.LiveCardWidth)) {
            CurveSideSwitch(curve, actions)
            val chartHeight = if (curve.modeAvailable) CHART_HEIGHT_WITH_MODE else CHART_HEIGHT
            val chartModifier = Modifier.padding(top = StickLabDimens.SmallGap).fillMaxWidth().height(chartHeight).enterStaggered(0)
            // Im Curve mode kommt schon die umgerechnete Stellung an: Ein Punkt auf der Kurve säße dann an der falschen Stelle
            val input = if (curve.modeRunning) ({ null }) else liveInput
            if (curve.showTriggers) {
                TriggerChart(curve.chosen, input, chartModifier)
            } else {
                CurveChart(
                    settings = settings,
                    reach = { input()?.stickOf(curve.side)?.reach() },
                    modifier = chartModifier,
                )
            }
            if (curve.modeAvailable) {
                SwitchRow(
                    text = stringResource(if (curve.modeBusy) R.string.curve_mode_working else R.string.curve_mode),
                    checked = curve.modeRunning,
                    onCheckedChange = { actions.onCurveModeToggled() },
                    modifier = Modifier.padding(top = StickLabDimens.SmallGap),
                )
            }
            val confirmed = isRecent(curve.lastApplySuccessAt, APPLY_CONFIRM_MS)
            Row(
                horizontalArrangement = Arrangement.spacedBy(StickLabDimens.SmallGap),
                modifier = Modifier.padding(top = StickLabDimens.SmallGap).enterStaggered(1),
            ) {
                StickLabButton(
                    text = stringResource(if (confirmed) R.string.applied else R.string.apply),
                    onClick = actions::onApplyCurves,
                    primary = true,
                    confirmed = confirmed,
                    modifier = Modifier.weight(1f),
                )
                // Der Editor gilt für Stick-Kurven, bei den Triggern gibt es nur die Grenze
                if (!curve.showTriggers) {
                    StickLabButton(
                        text = stringResource(R.string.curve_edit),
                        onClick = actions::onCurveEditorOpen,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// Hinweis unten in der linken Karte: was mit den Kurven gerade geschieht
@StringRes
private fun curveHint(curve: CurveUi): Int = when {
    !curve.modeAvailable -> R.string.curve_mode_firmware
    curve.modeRunning -> R.string.curve_mode_running_hint
    else -> R.string.curve_mode_off_hint
}

// Linker oder rechter Stick einer Live-Stellung
fun LiveInput.stickOf(side: OdinMapping.Side): StickPosition = if (side == OdinMapping.Side.LEFT) left else right

// Umschalter "Left stick · Right stick · L2 / R2", sieht aus wie der große Umschalter darüber
@Composable
private fun CurveSideSwitch(curve: CurveUi, actions: CurveActions) {
    val stickSide = curve.side.takeUnless { curve.showTriggers }
    SwitchBar {
        SwitchSegment(stringResource(R.string.left_stick), stickSide == OdinMapping.Side.LEFT, pending = false) {
            actions.onCurveSideChosen(OdinMapping.Side.LEFT)
        }
        SwitchSegment(stringResource(R.string.right_stick), stickSide == OdinMapping.Side.RIGHT, pending = false) {
            actions.onCurveSideChosen(OdinMapping.Side.RIGHT)
        }
        SwitchSegment(stringResource(R.string.profile_label_trigger), curve.showTriggers, pending = false, onClick = actions::onCurveTriggersShown)
    }
}

// Trigger voll ab (Z11, Wunsch Besitzer 05.11.2026): wenig Weg bis zur vollen Ausgabe, z. B. für schnelles Tapfire
@Composable
private fun TriggerSliders(chosen: LeftRight<CurveSettings>, actions: CurveActions, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.curve_trigger_hint),
            fontSize = StickLabDimens.HintSize,
            lineHeight = HINT_LINE_HEIGHT,
            modifier = Modifier.padding(top = TILES_TOP_GAP).alpha(StickLabDimens.DIMMED_ALPHA),
        )
        TriggerSlider(R.string.curve_trigger_l2, chosen.left) { actions.onTriggerFullOutputStepChanged(OdinMapping.Side.LEFT, it) }
        TriggerSlider(R.string.curve_trigger_r2, chosen.right) { actions.onTriggerFullOutputStepChanged(OdinMapping.Side.RIGHT, it) }
    }
}

@Composable
private fun TriggerSlider(@StringRes label: Int, settings: CurveSettings, onChange: (Int) -> Unit) {
    val steps = SettingSteps.TRIGGER_FULL_OUTPUT
    CurveSlider(
        label = label,
        step = steps.toStep(settings.triggerFullOutput),
        maxStep = steps.maxStep,
        offStep = steps.maxStep,
        percent = settings.triggerFullOutput,
        onChange = onChange,
    )
}

// Diagramm der Trigger: L2 als Kurve im Farbverlauf, R2 gestrichelt türkis darüber (bei gleicher Grenze liegen sie
// aufeinander), dazu je ein Punkt, wo der Trigger gerade steht
@Composable
private fun TriggerChart(chosen: LeftRight<CurveSettings>, liveInput: () -> LiveInput?, modifier: Modifier) {
    CurveChart(
        settings = triggerChartSettings(chosen.left),
        reach = { liveInput()?.let { it.l2Percent / PERCENT } },
        modifier = modifier,
        xLabel = R.string.curve_axis_trigger,
    ) { plot ->
        val r2 = triggerChartSettings(chosen.right)
        val path = Path().apply {
            moveTo(plot.xOf(0f), plot.yOf(0f))
            lineTo(plot.xOf(r2.outerDeadzone), plot.yOf(1f))
            lineTo(plot.xOf(1f), plot.yOf(1f))
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(R2_DASH.toPx(), R2_DASH.toPx()))
        drawPath(path, StickLabColors.Teal, style = Stroke(R2_LINE.toPx(), pathEffect = dash))
        liveInput()?.let { input ->
            val reach = input.r2Percent / PERCENT
            drawCircle(StickLabColors.Teal, radius = R2_POINT.toPx(), center = Offset(plot.xOf(reach), plot.yOf(CurveTable.outputFor(r2, reach))))
        }
    }
}

// Ein Trigger verhält sich im Diagramm wie eine Gerade mit äußerer Deadzone: bis zur Grenze gestreckt, danach voll
private fun triggerChartSettings(settings: CurveSettings) = CurveSettings(outerDeadzone = settings.triggerFullOutput)

// Die Kacheln der Kurven nebeneinander (alle gleich breit)
@Composable
private fun CurveTiles(settings: CurveSettings, onChoose: (Curve) -> Unit, modifier: Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP), modifier = modifier.fillMaxWidth()) {
        for (curve in Curve.entries) {
            CurveTile(curve, settings.custom, curve == settings.curve, { onChoose(curve) }, Modifier.weight(1f))
        }
    }
}

// Eine Kachel: kleines Bild der Kurve, darunter ihr Name. Gewählt = weiß, wie im Umschalter. Rückmeldung wie die Knöpfe:
// mit Fokus etwas größer und mit Ring
@Composable
private fun CurveTile(curve: Curve, custom: CustomCurve, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) StickLabMotion.FOCUS_SCALE else 1f, StickLabMotion.bouncy(), label = "curveTileScale")
    val shape = RoundedCornerShape(TILE_CORNER)
    val ink = if (selected) StickLabColors.InkOnLight else StickLabColors.Ink
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .focusRing(visible = focused, shape = shape)
            .clip(shape)
            .background(if (selected) Color.White else StickLabColors.RowFill)
            .border(1.dp, StickLabColors.GlassBorder, shape)
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 5.dp),
    ) {
        CurveThumbnail(curve, custom, ink, Modifier.width(TILE_PICTURE_WIDTH).height(TILE_PICTURE_HEIGHT))
        Text(
            text = stringResource(ProfileText.nameOf(curve)),
            color = ink,
            fontSize = TILE_TEXT_SIZE,
            lineHeight = TILE_LINE_HEIGHT,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

// Vorschaubild einer Kurve (Plan Phase 7): nur ihre Form, ohne äußere und Anti-Deadzone
@Composable
private fun CurveThumbnail(curve: Curve, custom: CustomCurve, color: Color, modifier: Modifier) {
    val settings = remember(curve, custom) { CurveSettings(curve, custom = custom) }
    Canvas(modifier = modifier) {
        val path = Path()
        for (i in 0..THUMBNAIL_SAMPLES) {
            val x = i.toFloat() / THUMBNAIL_SAMPLES
            val px = x * size.width
            val py = (1f - CurveTable.outputFor(settings, x)) * size.height
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawLine(color.copy(alpha = THUMBNAIL_AXIS_ALPHA), Offset(0f, size.height), Offset(size.width, size.height))
        drawPath(path, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
    }
}

// Die beiden Regler des gewählten Sticks. Ganz rechts bzw. ganz links steht "Off"
@Composable
private fun CurveSliders(settings: CurveSettings, actions: CurveActions, modifier: Modifier) {
    Column(modifier = modifier) {
        val outer = SettingSteps.OUTER_DEADZONE
        CurveSlider(
            label = R.string.curve_outer_label,
            step = outer.toStep(settings.outerDeadzone),
            maxStep = outer.maxStep,
            offStep = outer.maxStep,
            percent = settings.outerDeadzone,
            onChange = actions::onOuterStepChanged,
        )
        val anti = SettingSteps.ANTI_DEADZONE
        CurveSlider(
            label = R.string.curve_anti_label,
            step = anti.toStep(settings.antiDeadzone),
            maxStep = anti.maxStep,
            offStep = 0,
            percent = settings.antiDeadzone,
            onChange = actions::onAntiStepChanged,
        )
    }
}

// percent = aktueller Wert als Bruchteil. Bei offStep ist die Einstellung aus und zeigt "Off" statt einer Zahl
@Composable
private fun CurveSlider(
    @StringRes label: Int,
    step: Int,
    maxStep: Int,
    offStep: Int,
    percent: Float,
    onChange: (Int) -> Unit,
) {
    SettingSlider(
        label = stringResource(label),
        valueText = if (step == offStep) stringResource(R.string.curve_value_off)
        else stringResource(R.string.trigger_percent, percent * 100f),
        value = step.toFloat(),
        valueRange = 0f..maxStep.toFloat(),
        steps = maxStep - 1,
        onValueChange = { onChange(it.roundToInt()) },
    )
}

// Karte: Umschalter 28 + Kacheln ca. 66 + 2 Regler à 83 + Hinweis 18 + Rand 32 = 310 dp von 317
private val TILES_TOP_GAP = 8.dp
private val TILE_GAP = 4.dp
private val TILE_CORNER = 12.dp
private val TILE_PICTURE_WIDTH = 34.dp
private val TILE_PICTURE_HEIGHT = 28.dp
private val TILE_TEXT_SIZE = 10.sp
private val TILE_LINE_HEIGHT = 12.sp
private val HINT_LINE_HEIGHT = 16.sp
private const val PERCENT = 100f // Trigger-Druck kommt in Prozent (LiveInput)
private val R2_LINE = 2.dp
private val R2_DASH = 5.dp
private val R2_POINT = 4.5.dp
private const val THUMBNAIL_SAMPLES = 24
private const val THUMBNAIL_AXIS_ALPHA = 0.25f

// Rechte Karte: Umschalter 28 + Abstand 8 + Diagramm + Abstand 8 + Knopf 46 + Rand 32 = 317 dp
private val CHART_HEIGHT = 190.dp

// Mit Schalter "Curve mode" (ca. 52 dp + Abstand) bleibt für das Diagramm entsprechend weniger
private val CHART_HEIGHT_WITH_MODE = 130.dp

@Preview(widthDp = 716, heightDp = 325)
@Composable
private fun CurvePanelPreview() {
    StickLabTheme {
        CurvePanel(
            pending = setOf(StickSetting.CURVE),
            onSettingChosen = {},
            curve = CurveUi(),
            actions = PreviewCurveActions,
            liveInput = { null },
        )
    }
}

// Für die Vorschauen in Android Studio: Knöpfe tun nichts
object PreviewCurveActions : CurveActions {
    override fun onCurveSideChosen(side: OdinMapping.Side) = Unit
    override fun onCurveChosen(curve: Curve) = Unit
    override fun onOuterStepChanged(step: Int) = Unit
    override fun onAntiStepChanged(step: Int) = Unit
    override fun onApplyCurves() = Unit
    override fun onCurveEditorOpen() = Unit
    override fun onCurveEditorClose() = Unit
    override fun onCurvePointSelected(index: Int) = Unit
    override fun onCurvePointMoved(index: Int, x: Float, y: Float) = Unit
    override fun onCurvePointAdd() = Unit
    override fun onCurvePointRemove() = Unit
    override fun onCurveTriggersShown() = Unit
    override fun onTriggerFullOutputStepChanged(side: OdinMapping.Side, step: Int) = Unit
    override fun onCurveModeToggled() = Unit
    override fun onCurveModeIntroConfirmed() = Unit
}
