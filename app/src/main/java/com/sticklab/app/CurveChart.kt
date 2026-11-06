package com.sticklab.app

import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Das Diagramm einer Kurve (Plan Phase 7): waagrecht Stick-Ausschlag, senkrecht Ausgabe, je von 0 bis 100 %.
// Gitter alle 25 %, dünn gestrichelt die Gerade (zum Vergleich), darüber die Kurve mit allen Einstellungen des Sticks.
// reach() = Ausschlag des Sticks von 0 bis 1 (null = noch keine Bewegung gesehen). Der Punkt zeigt, wo er auf der Kurve
// steht; erst beim Zeichnen gelesen, bei Bewegung wird also nur neu gemalt (wie die Karte "Live", R66).
// overlay malt zusätzlich etwas darüber (die Griffe im Editor)
@Composable
fun CurveChart(
    settings: CurveSettings,
    reach: () -> Float?,
    modifier: Modifier = Modifier,
    inset: Dp = CHART_INSET,
    glide: Boolean = true,
    @StringRes xLabel: Int = R.string.curve_axis_stick,
    overlay: DrawScope.(plot: ChartPlot) -> Unit = {},
) {
    val target = remember(settings) { sampleOutputs(settings) }
    val outputs = if (glide) glidingOutputs(target) else target
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val plot = ChartPlot(inset.toPx(), size.width - 2 * inset.toPx(), size.height - 2 * inset.toPx())
            drawGrid(plot)
            drawCurve(plot, outputs)
            drawLivePoint(plot, settings, reach())
            overlay(plot)
        }
        AxisLabel(R.string.curve_axis_output, Alignment.TopStart, inset)
        AxisLabel(xLabel, Alignment.BottomEnd, inset)
    }
}

// Der Zeichenbereich innerhalb des Rands: rechnet Kurven-Werte (0 bis 1) in Bildpunkte um und zurück
class ChartPlot(private val inset: Float, private val width: Float, private val height: Float) {
    fun xOf(value: Float) = inset + value * width
    fun yOf(value: Float) = inset + (1f - value) * height
    fun valueOfX(px: Float) = (px - inset) / width
    fun valueOfY(px: Float) = 1f - (px - inset) / height
}

// Die Kurve in gleichmäßigen Schritten, nur neu berechnet, wenn sich die Einstellungen ändern
private fun sampleOutputs(settings: CurveSettings): FloatArray =
    FloatArray(CHART_SAMPLES + 1) { CurveTable.outputFor(settings, it.toFloat() / CHART_SAMPLES) }

// Weicher Übergang (Plan Phase 8): Bei neuer Kurve gleitet die Linie von der alten zur neuen Form. Im Editor aus,
// dort muss sie dem Finger sofort folgen
@Composable
private fun glidingOutputs(target: FloatArray): FloatArray {
    var shown by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        val start = shown
        animate(0f, 1f, animationSpec = tween(CHART_GLIDE_MS, easing = LinearOutSlowInEasing)) { progress, _ ->
            shown = FloatArray(target.size) { start[it] + (target[it] - start[it]) * progress }
        }
    }
    return shown
}

private fun DrawScope.drawGrid(plot: ChartPlot) {
    for (i in 0..GRID_DIVISIONS) {
        val value = i.toFloat() / GRID_DIVISIONS
        drawLine(StickLabColors.GlassBorder, Offset(plot.xOf(value), plot.yOf(0f)), Offset(plot.xOf(value), plot.yOf(1f)))
        drawLine(StickLabColors.GlassBorder, Offset(plot.xOf(0f), plot.yOf(value)), Offset(plot.xOf(1f), plot.yOf(value)))
    }
    drawLine(
        StickLabColors.Track,
        Offset(plot.xOf(0f), plot.yOf(0f)),
        Offset(plot.xOf(1f), plot.yOf(1f)),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
    )
}

// Erst breit und blass (Leuchten), darüber schmal im Farbverlauf des App-Symbols
private fun DrawScope.drawCurve(plot: ChartPlot, outputs: FloatArray) {
    val path = Path()
    outputs.forEachIndexed { index, output ->
        val x = plot.xOf(index.toFloat() / CHART_SAMPLES)
        val y = plot.yOf(output)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    val brush = Brush.linearGradient(listOf(StickLabColors.Pink, StickLabColors.SwitchViolet, StickLabColors.Teal))
    drawPath(path, brush, alpha = CURVE_GLOW_ALPHA, style = Stroke(CURVE_GLOW_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, brush, style = Stroke(CURVE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawLivePoint(plot: ChartPlot, settings: CurveSettings, reach: Float?) {
    if (reach == null) return
    val center = Offset(plot.xOf(reach), plot.yOf(CurveTable.outputFor(settings, reach)))
    drawCircle(StickLabColors.Glow, radius = LIVE_GLOW_RADIUS.toPx(), center = center)
    drawCircle(StickLabColors.Ink, radius = LIVE_POINT_RADIUS.toPx(), center = center)
}

// Kleine Achsen-Beschriftung in einer Ecke des Diagramms
@Composable
private fun androidx.compose.foundation.layout.BoxScope.AxisLabel(text: Int, corner: Alignment, inset: Dp) {
    Text(
        text = stringResource(text),
        fontSize = AXIS_TEXT_SIZE,
        lineHeight = AXIS_LINE_HEIGHT,
        modifier = Modifier
            .align(corner)
            .padding(horizontal = inset + AXIS_GAP, vertical = inset + AXIS_GAP_V)
            .alpha(StickLabDimens.DIMMED_ALPHA),
    )
}

// Ausschlag einer Stick-Stellung von 0 bis 1, als Ganzes gemessen. Schräg in der Ecke kann er über 100 % liegen, das
// Hilfsprogramm begrenzt dort auf den Kreis (K6-4)
fun StickPosition.reach(): Float = (distancePercent() / PERCENT).coerceIn(0f, 1f)

private const val PERCENT = 100f

// 96 Schritte reichen für eine glatte Linie auf dem kleinen Diagramm (Abstand ca. 3 dp)
private const val CHART_SAMPLES = 96
private const val GRID_DIVISIONS = 4 // alle 25 %
private const val CHART_GLIDE_MS = 280
private const val CURVE_GLOW_ALPHA = 0.28f
private val CHART_INSET = 4.dp
private val CURVE_WIDTH = 2.5.dp
private val CURVE_GLOW_WIDTH = 7.dp
private val LIVE_POINT_RADIUS = 4.5.dp
private val LIVE_GLOW_RADIUS = 10.dp
private val AXIS_TEXT_SIZE = 10.5.sp
private val AXIS_LINE_HEIGHT = 12.sp
private val AXIS_GAP = 3.dp
private val AXIS_GAP_V = 1.dp
