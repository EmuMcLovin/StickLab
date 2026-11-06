package com.sticklab.app

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// Karte "Live" im Reiter Sticks.
// Phase 3a: zeigt, wo die Sticks gerade stehen und wie weit L2/R2 gedrückt sind – so, wie Spiele es
// bekommen. Kein Deadzone-Ring: Mit wirksamer Deadzone zählt die Firmware ab ihrem Rand wieder
// von 0 (Vermutung A21), ein Ring säße an der falschen Stelle.
// Phase 3c-1: Knopf "Auto-Set" öffnet die große Auto-Set-Ansicht (ersetzt "Measure drift" aus 3b, Entscheidung K).
// liveInput wird erst beim Zeichnen bzw. in den kleinen Text-Teilen gelesen: Bei jeder Stick-Bewegung
// wird so nur der Punkt neu gemalt und die Zahl neu gesetzt, nicht der ganze Bildschirm (Risiko R66)
// highlight (Schritt S): Kreise bzw. Trigger-Balken umrandet orange, wenn in der Regler-Karte daneben
// Sensitivity bzw. Trigger gewählt ist – dort sieht man die Wirkung
@Composable
fun StickLiveCard(
    liveInput: () -> LiveInput?,
    onAutoSet: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: LiveHighlight = LiveHighlight.NONE,
) {
    SettingsCard(modifier = modifier) {
        CardTitle(R.string.live_title)
        Text(
            text = stringResource(R.string.live_subtitle),
            fontSize = StickLabDimens.HintSize,
            modifier = Modifier.padding(top = 2.dp).alpha(StickLabDimens.DIMMED_ALPHA),
        )
        // Eine Zeile wie am Gerät: L2 links außen, die beiden Sticks, R2 rechts außen (Besitzer 25.10.2026:
        // Trigger neben statt unter die Kreise, damit die Karte ohne Scrollen auf den Bildschirm passt)
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(top = StickLabDimens.SectionGap).enterStaggered(0),
        ) {
            val sticks = highlight == LiveHighlight.STICKS
            val triggers = highlight == LiveHighlight.TRIGGERS
            TriggerColumn(R.string.live_l2, triggers) { liveInput()?.l2Percent }
            StickGauge(R.string.live_left, sticks) { liveInput()?.left }
            StickGauge(R.string.live_right, sticks) { liveInput()?.right }
            TriggerColumn(R.string.live_r2, triggers) { liveInput()?.r2Percent }
        }
        StickLabButton(
            text = stringResource(R.string.autoset_start),
            onClick = onAutoSet,
            modifier = Modifier.fillMaxWidth().padding(top = StickLabDimens.ButtonGap).enterStaggered(2),
        )
    }
}

// Welcher Teil der Live-Karte hervorgehoben ist (Schritt S)
enum class LiveHighlight { NONE, STICKS, TRIGGERS }

// Ein Stick: Kreis, darunter Text und Kennzeichen. position() = null: noch keine Bewegung gesehen
@Composable
private fun StickGauge(@StringRes name: Int, highlighted: Boolean, position: () -> StickPosition?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StickCircle(highlighted, position)
        StickReadout(name, position)
    }
}

// Der Kreis mit dünnem Kreuz durch die Mitte und dem Punkt an der Stick-Stelle (grün = genau in der Mitte).
// highlighted: Kreis orange statt blass
@Composable
private fun StickCircle(highlighted: Boolean, position: () -> StickPosition?) {
    Canvas(modifier = Modifier.size(GAUGE_SIZE)) {
        val radius = size.minDimension / 2f - RING_GLOW_WIDTH.toPx() / 2f
        // Erst hier gelesen → bei Bewegung nur neu malen (siehe oben)
        val now = position()
        drawRing(highlighted, radius, now?.reach() ?: 0f)
        drawLine(StickLabColors.GlassBorder, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y))
        drawLine(StickLabColors.GlassBorder, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius))

        val dotColor = when {
            now == null -> StickLabColors.Track
            now.isExactlyCenter() -> StickLabColors.Success
            else -> StickLabColors.Ink
        }
        val dot = (now ?: StickPosition.CENTER).clampedToCircle()
        drawCircle(dotColor, radius = DOT_RADIUS.toPx(), center = Offset(center.x + dot.x * radius, center.y + dot.y * radius))
    }
}

// Der Ring um die Stick-Anzeige (Plan Phase 8): Neon-Farben des App-Symbols, leuchtet stärker, je weiter der Stick
// ausgelenkt ist. Hervorgehoben (Schritt S) stattdessen orange
private fun DrawScope.drawRing(highlighted: Boolean, radius: Float, reach: Float) {
    if (highlighted) {
        drawCircle(StickLabColors.Warning, radius = radius, style = Stroke(CIRCLE_STROKE.toPx()))
        return
    }
    val neon = Brush.sweepGradient(listOf(StickLabColors.Pink, StickLabColors.SwitchViolet, StickLabColors.Teal, StickLabColors.Pink), center)
    val strength = RING_IDLE_ALPHA + (1f - RING_IDLE_ALPHA) * reach
    drawCircle(neon, radius = radius, alpha = strength * RING_GLOW_ALPHA, style = Stroke(RING_GLOW_WIDTH.toPx()))
    drawCircle(neon, radius = radius, alpha = strength, style = Stroke(CIRCLE_STROKE.toPx()))
}

// Text unter dem Kreis: "Left 34.6 %" und darunter "Exactly 0" / "Not 0". Ohne Wert: "Left –" und "Move once"
@Composable
private fun StickReadout(@StringRes name: Int, position: () -> StickPosition?) {
    val now = position()
    val label = stringResource(name)
    Text(
        text = if (now == null) stringResource(R.string.live_stick_unknown, label)
        else stringResource(R.string.live_stick_value, label, now.distancePercent()),
        fontSize = StickLabDimens.LabelSize,
        modifier = Modifier.padding(top = StickLabDimens.SmallGap),
    )
    // Platz für das Kennzeichen bleibt immer gleich hoch, damit die Karte nicht springt
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(top = 4.dp).height(BADGE_HEIGHT)) {
        when {
            now == null -> MoveHint()
            now.isExactlyCenter() -> Badge(R.string.live_exact_zero, StickLabColors.Success, StickLabColors.OnSuccess)
            else -> Badge(R.string.live_not_zero, StickLabColors.ButtonFill, StickLabColors.Ink, outlined = true)
        }
    }
}

// Statt des Kennzeichens, solange kein Wert da ist (beim Öffnen, nach dem Zurückkehren in die App und nach
// jeder Änderung der Deadzone): blasser Hinweis "Move once". Der Controller schickt erst bei Bewegung einen
// neuen Wert (Test A), ein alter Rest wäre sonst falsch (Fund F3, Entscheidung L1 vom 17.10.2026)
@Composable
private fun MoveHint() {
    Text(
        text = stringResource(R.string.live_move_hint),
        fontSize = StickLabDimens.HintSize,
        modifier = Modifier.alpha(StickLabDimens.DIMMED_ALPHA),
    )
}

// Kleines Kennzeichen in Kapselform (grün = genau 0, blass mit Rand = nicht 0)
@Composable
private fun Badge(@StringRes text: Int, fill: Color, textColor: Color, outlined: Boolean = false) {
    val shape = RoundedCornerShape(50)
    val outline = if (outlined) Modifier.border(1.dp, StickLabColors.ButtonBorder, shape) else Modifier
    Text(
        text = stringResource(text),
        fontSize = StickLabDimens.HintSize,
        color = textColor,
        modifier = Modifier.background(fill, shape).then(outline).padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

// Ein Trigger als senkrechte Säule: oben der Name, darunter der Balken (füllt sich von unten, 0–100 % Druck),
// unten die Zahl. percent() = null: noch keine Bewegung. highlighted: oranger Rand um den Balken – nur ein Rand,
// ein gefüllter Balken sähe aus wie ein ganz gedrückter Trigger (Foto 26.10.2026)
@Composable
private fun TriggerColumn(@StringRes name: Int, highlighted: Boolean, percent: () -> Float?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(TRIGGER_COLUMN_WIDTH)) {
        Text(text = stringResource(name), fontSize = StickLabDimens.LabelSize)
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .size(width = TRIGGER_BAR_WIDTH, height = TRIGGER_BAR_LENGTH)
                // Erst beim Zeichnen gelesen → bei Druck nur neu malen
                .drawBehind {
                    val corner = CornerRadius(size.width / 2f)
                    drawRoundRect(StickLabColors.Track, cornerRadius = corner)
                    if (highlighted) {
                        drawRoundRect(StickLabColors.Warning, cornerRadius = corner, style = Stroke(HIGHLIGHT_STROKE.toPx()))
                    }
                    val filledHeight = size.height * (percent() ?: 0f) / PERCENT_FULL
                    if (filledHeight > 0f) {
                        drawRoundRect(
                            StickLabColors.Violet,
                            topLeft = Offset(0f, size.height - filledHeight),
                            size = Size(size.width, filledHeight),
                            cornerRadius = corner,
                        )
                    }
                },
        )
        TriggerValue(percent)
    }
}

// Zahl unter dem Balken, z. B. "37 %", ohne Bewegung "–"
@Composable
private fun TriggerValue(percent: () -> Float?) {
    val now = percent()
    Text(
        text = if (now == null) stringResource(R.string.deadzone_unknown) else stringResource(R.string.live_trigger_value, now),
        fontSize = StickLabDimens.HintSize,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

// Maße der Karte (passend zu StickLabDimens.LiveCardWidth = 330 dp, Innenbreite 290 dp:
// 2 × Trigger 38 + 2 × Kreis 96 = 268 dp, Rest ist Luft zwischen den Teilen).
// Höhe: Kreis 96 + Text + Kennzeichen ≈ 148 dp; der Trigger-Balken ist so lang, dass seine Säule gleich hoch ist
private val GAUGE_SIZE = 96.dp              // Kreis je Stick (vorher 104, für die Trigger daneben)
private val CIRCLE_STROKE = 2.dp
private val RING_GLOW_WIDTH = 6.dp          // breiter, blasser Rand = Leuchten um den Ring
private const val RING_GLOW_ALPHA = 0.3f
private const val RING_IDLE_ALPHA = 0.45f   // Ruhe: Ring gut sichtbar, aber nicht so hell wie am Anschlag
private val DOT_RADIUS = 7.dp               // Punkt gut sichtbar, auch aus Armlänge
private val BADGE_HEIGHT = 22.dp            // Höhe des Kennzeichens (Text 12 sp + Innenrand)
private val TRIGGER_COLUMN_WIDTH = 38.dp    // breit genug für "100 %" in 12 sp
private val TRIGGER_BAR_WIDTH = 8.dp
private val TRIGGER_BAR_LENGTH = 100.dp
private val HIGHLIGHT_STROKE = 2.dp         // oranger Rand um den Trigger-Balken (so dick wie der Kreis)
private const val PERCENT_FULL = 100f

// Vorschau in Android Studio: linker Stick ruht genau in der Mitte, rechter steht schräg oben rechts, R2 halb
@Preview(widthDp = 330)
@Composable
private fun StickLiveCardPreview() {
    StickLabTheme {
        StickLiveCard(
            liveInput = { LiveInput(StickPosition.CENTER, StickPosition(0.6f, -0.4f), 0f, 48f) },
            onAutoSet = {},
        )
    }
}
