package com.sticklab.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot

// Kurven-Editor (Plan Phase 7, Z4): ersetzt beide Karten des Teils "Curve", damit das Diagramm groß genug für den Finger ist.
// Links das Diagramm mit 3 bis 5 verschiebbaren Punkten und dem Live-Punkt des Sticks, rechts Stick-Wahl, Werte des gewählten
// Punkts und die Knöpfe. Ändert nur die Wahl: Gespeichert wird weiter mit "Apply" auf der Kurven-Karte.
// Bedienung: Finger zieht den nächsten Punkt. Controller: links/rechts wählt einen Punkt, die Bestätigen-Taste packt ihn,
// das Steuerkreuz verschiebt ihn dann in 1-%-Schritten, die Bestätigen-Taste lässt ihn wieder los.
// Solange der Editor offen ist, bewegt der linke Stick nicht die Auswahl, damit man ihn auf der Kurve sehen kann (MainActivity)
@Composable
fun CurveEditorPanel(curve: CurveUi, actions: CurveActions, liveInput: () -> LiveInput?, modifier: Modifier = Modifier) {
    val settings = curve.chosen.of(curve.side)
    val selected = curve.selectedPoint.coerceIn(settings.custom.points.indices)
    var grabbed by remember { mutableStateOf(false) }
    BackHandler(onBack = actions::onCurveEditorClose)
    SettingsCard(maxWidth = Dp.Unspecified, modifier = modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(StickLabDimens.ColumnGap), modifier = Modifier.fillMaxSize()) {
            EditableChart(
                settings = settings,
                selected = selected,
                grabbed = grabbed,
                onGrabbedChange = { grabbed = it },
                actions = actions,
                // Im Curve mode kommt schon die umgerechnete Stellung an: Ein Punkt auf der Kurve säße an der falschen Stelle
                reach = { if (curve.modeRunning) null else liveInput()?.stickOf(curve.side)?.reach() },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            EditorSide(curve, settings, selected, grabbed, actions, Modifier.width(SIDE_WIDTH).fillMaxHeight())
        }
    }
}

// Das Diagramm mit Griffen. Bekommt Fokus wie ein Knopf (Ring), die Tasten siehe oben
@Composable
private fun EditableChart(
    settings: CurveSettings,
    selected: Int,
    grabbed: Boolean,
    onGrabbedChange: (Boolean) -> Unit,
    actions: CurveActions,
    reach: () -> Float?,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val points by rememberUpdatedState(settings.custom.points)
    val insetPx = with(LocalDensity.current) { EDITOR_INSET.toPx() }
    val moveOnKey = pointKeyHandler(points, selected, grabbed, onGrabbedChange, actions)
    Box(
        modifier = modifier
            .focusRing(visible = focused, shape = RoundedCornerShape(CHART_CORNER))
            .onPreviewKeyEvent { event -> moveOnKey(event.key, event.type) }
            .focusable(interactionSource = interaction)
            .pointerInput(insetPx) {
                fun plot(size: IntSize) = ChartPlot(insetPx, size.width - 2 * insetPx, size.height - 2 * insetPx)
                detectTapGestures { tap -> actions.onCurvePointSelected(nearestPoint(points, plot(size), tap)) }
            }
            .pointerInput(insetPx) {
                fun plot(size: IntSize) = ChartPlot(insetPx, size.width - 2 * insetPx, size.height - 2 * insetPx)
                var dragged = 0
                detectDragGestures(
                    onDragStart = { start ->
                        dragged = nearestPoint(points, plot(size), start)
                        actions.onCurvePointSelected(dragged)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val at = plot(size)
                        actions.onCurvePointMoved(dragged, at.valueOfX(change.position.x), at.valueOfY(change.position.y))
                    },
                )
            },
    ) {
        CurveChart(settings, reach, Modifier.fillMaxSize(), inset = EDITOR_INSET, glide = false) { plot ->
            points.forEachIndexed { index, point ->
                val center = Offset(plot.xOf(point.x), plot.yOf(point.y))
                val isSelected = index == selected
                val radius = (if (isSelected) HANDLE_RADIUS_SELECTED else HANDLE_RADIUS).toPx()
                drawCircle(StickLabColors.InkOnLight, radius = radius, center = center)
                drawCircle(
                    if (isSelected && grabbed) StickLabColors.Warning else StickLabColors.Ink,
                    radius = radius,
                    center = center,
                    style = Stroke(HANDLE_STROKE.toPx()),
                )
            }
        }
    }
}

// Tasten im Diagramm. Ergebnis true = verbraucht. Nicht gepackt: links/rechts wählt, am Rand und bei hoch/runter wandert der
// Fokus normal weiter (sonst käme man mit dem Controller nicht mehr aus dem Diagramm heraus). Gepackt: alle vier Richtungen
// verschieben, nichts verlässt das Diagramm
private fun pointKeyHandler(
    points: List<CurvePoint>,
    selected: Int,
    grabbed: Boolean,
    onGrabbedChange: (Boolean) -> Unit,
    actions: CurveActions,
): (Key, KeyEventType) -> Boolean = handler@{ key, type ->
    val isConfirm = key == Key.ButtonA || key == Key.DirectionCenter || key == Key.Enter
    if (isConfirm) {
        if (type == KeyEventType.KeyDown) onGrabbedChange(!grabbed)
        return@handler true
    }
    val dx = when (key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> 0 }
    val dy = when (key) { Key.DirectionDown -> -1; Key.DirectionUp -> 1; else -> 0 }
    if (dx == 0 && dy == 0) return@handler false
    if (grabbed) {
        if (type == KeyEventType.KeyDown) {
            val point = points[selected]
            actions.onCurvePointMoved(selected, point.x + dx * KEY_STEP, point.y + dy * KEY_STEP)
        }
        return@handler true
    }
    val next = selected + dx
    if (dx == 0 || next !in points.indices) return@handler false
    if (type == KeyEventType.KeyDown) actions.onCurvePointSelected(next)
    true
}

// Punkt, der einer Stelle im Diagramm am nächsten liegt (der Finger muss ihn nicht genau treffen)
private fun nearestPoint(points: List<CurvePoint>, plot: ChartPlot, at: Offset): Int =
    points.indices.minBy { index ->
        hypot(plot.xOf(points[index].x) - at.x, plot.yOf(points[index].y) - at.y)
    }

// Rechte Spalte: Stick-Wahl, Name und Regeln, Werte des gewählten Punkts, Knöpfe
@Composable
private fun EditorSide(
    curve: CurveUi,
    settings: CurveSettings,
    selected: Int,
    grabbed: Boolean,
    actions: CurveActions,
    modifier: Modifier,
) {
    val points = settings.custom.points
    Column(modifier = modifier) {
        SwitchBar {
            SwitchSegment(stringResource(R.string.live_left), curve.side == OdinMapping.Side.LEFT, pending = false) {
                actions.onCurveSideChosen(OdinMapping.Side.LEFT)
            }
            SwitchSegment(stringResource(R.string.live_right), curve.side == OdinMapping.Side.RIGHT, pending = false) {
                actions.onCurveSideChosen(OdinMapping.Side.RIGHT)
            }
        }
        val point = points[selected]
        Text(
            text = stringResource(R.string.curve_point_value, point.x * PERCENT, point.y * PERCENT),
            fontSize = StickLabDimens.BodySize,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = StickLabDimens.SectionGap).popOnChange(point),
        )
        Text(
            text = stringResource(if (grabbed) R.string.curve_editor_grabbed else R.string.curve_editor_hint),
            fontSize = StickLabDimens.HintSize,
            lineHeight = HINT_LINE_HEIGHT,
            modifier = Modifier.padding(top = 4.dp).alpha(StickLabDimens.DIMMED_ALPHA),
        )
        Text(
            text = stringResource(R.string.curve_double_hint),
            fontSize = StickLabDimens.HintSize,
            lineHeight = HINT_LINE_HEIGHT,
            modifier = Modifier.padding(top = 6.dp).alpha(StickLabDimens.DIMMED_ALPHA),
        )
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(StickLabDimens.SmallGap)) {
            StickLabButton(
                text = stringResource(R.string.curve_point_add),
                onClick = actions::onCurvePointAdd,
                enabled = points.size < CustomCurve.MAX_POINTS,
                compact = true,
                modifier = Modifier.weight(1f),
            )
            StickLabButton(
                text = stringResource(R.string.curve_point_remove),
                onClick = actions::onCurvePointRemove,
                enabled = points.size > CustomCurve.MIN_POINTS,
                compact = true,
                modifier = Modifier.weight(1f),
            )
        }
        StickLabButton(
            text = stringResource(R.string.curve_editor_done),
            onClick = actions::onCurveEditorClose,
            primary = true,
            modifier = Modifier.fillMaxWidth().padding(top = StickLabDimens.SmallGap),
        )
    }
}

private const val PERCENT = 100f

// Ein Druck aufs Steuerkreuz verschiebt den Punkt um 1 % (wie die Regler daneben)
private const val KEY_STEP = 0.01f

// Griffe gut sichtbar, gewählter größer; Rand im Diagramm, damit auch Punkte am Rand ganz zu sehen sind
private val HANDLE_RADIUS = 8.dp
private val HANDLE_RADIUS_SELECTED = 11.dp
private val HANDLE_STROKE = 2.5.dp
private val EDITOR_INSET = 12.dp
private val CHART_CORNER = 12.dp
private val SIDE_WIDTH = 216.dp
private val HINT_LINE_HEIGHT = 15.5.sp
