package com.sticklab.app

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Große Auto-Set-Ansicht (Phase 3c-1, Entscheidung U1): ersetzt während des Auto-Sets beide Karten im
// Reiter Sticks, damit die Kreise groß sind und nichts abgeschnitten wird.
// Links je Stick ein Kreis mit 8 Feldern (grau = offen, violett = Anschlag erreicht, grün = erledigt) und
// dem Höchstausschlag im Feld, darunter die Knöpfe; rechts die Anleitung bzw. das Ergebnis mit den Vorschlägen.
// Knöpfe unter den Kreisen (Besitzer 17.10.2026, Lauf 1): rechts neben dem langen Ergebnis wurden sie
// unten abgeschnitten.
// Seit 3c-2: Knopf "Check" startet die Kontrollmessung (Vorschlag nur live), das Ergebnis zeigt je Stick das
// Urteil (✓ bestanden, ✗ eine Erhöhung = 0,5 % höher). "Apply" öffnet nur die Nachfrage – gesetzt wird in MainActivity
// Seit 3c-3: "Zurück" beendet nur die Runde bzw. die Ansicht, nicht die App (BackHandler)
@Composable
fun AutoSetPanel(
    autoSet: AutoSetUi,
    liveInput: () -> LiveInput?,
    onStop: () -> Unit,
    onAgain: () -> Unit,
    onCheck: () -> Unit,
    onApply: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Die Kreise zeigen die laufende Runde bzw. nach einer Kontrollmessung deren Stand
    val reach = when (autoSet) {
        is AutoSetUi.Running -> autoSet.reach
        is AutoSetUi.Finished -> autoSet.result.lastCheck ?: autoSet.result.measured
        AutoSetUi.Idle -> return
    }
    // "Zurück" (Wisch vom Bildschirmrand oder Zurück-Taste) schließt hier nicht mehr die App, sonst wäre das
    // Ergebnis weg (Fund F1, 25. Sitzung). In der Runde wie "Stop", im Ergebnis wie "Done" (Entscheidung B1).
    // Gilt nur, solange diese Ansicht zu sehen ist – danach wirkt "Zurück" wieder normal
    BackHandler {
        if (autoSet is AutoSetUi.Running) onStop() else onDone()
    }
    val buttons = PanelActions(onStop, onAgain, onCheck, onApply, onDone)
    SettingsCard(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(PANEL_GAP)) {
            // Feste Breite (zwei Kreise + Abstand), damit die Knöpfe genau so breit sind wie die Kreise
            Column(modifier = Modifier.width(DIALS_WIDTH)) {
                Row(horizontalArrangement = Arrangement.spacedBy(PANEL_GAP)) {
                    StickColumn(R.string.live_left, reach.left) { liveInput()?.left }
                    StickColumn(R.string.live_right, reach.right) { liveInput()?.right }
                }
                PanelButtons(autoSet, buttons)
            }
            Column(modifier = Modifier.weight(1f)) {
                val checking = (autoSet as? AutoSetUi.Running)?.checking != null
                CardTitle(if (checking) R.string.autoset_check_title else R.string.autoset_title)
                when (autoSet) {
                    is AutoSetUi.Running -> RunningInfo(autoSet)
                    is AutoSetUi.Finished -> ResultInfo(autoSet.result)
                    AutoSetUi.Idle -> Unit
                }
            }
        }
    }
}

fun <T> LeftRight<T>.of(side: OdinMapping.Side): T = when (side) {
    OdinMapping.Side.LEFT -> left
    OdinMapping.Side.RIGHT -> right
}

// Name einer Seite für die Ergebniszeilen ("Left", "Right")
@StringRes
private fun nameOf(side: OdinMapping.Side): Int = when (side) {
    OdinMapping.Side.LEFT -> R.string.live_left
    OdinMapping.Side.RIGHT -> R.string.live_right
}

// ---------- Linke Seite: je Stick Kreis + zwei Zeilen ----------

// Ein Stick: Kreis mit 8 Feldern, darunter "Left · 6/8" und der größte Rest nach dem Loslassen
@Composable
private fun StickColumn(@StringRes name: Int, reach: StickReach, position: () -> StickPosition?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ReachDial(reach, position)
        Text(
            text = stringResource(R.string.autoset_stick_fields, stringResource(name), reach.doneCount, reach.fields.size),
            fontSize = StickLabDimens.LabelSize,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = StickLabDimens.SmallGap),
        )
        Text(
            text = restText(reach.rest),
            fontSize = StickLabDimens.HintSize,
            modifier = Modifier.padding(top = 2.dp).alpha(StickLabDimens.DIMMED_ALPHA),
        )
    }
}

// "Rest 1.5 % · 6×", "Rest exactly 0 · 6×" (jeder Ruhepunkt genau 0, z. B. in der Kontrollmessung;
// "0.0 %" könnte auch 0,04 % heißen) bzw. "No release seen"
@Composable
private fun restText(rest: DriftResult): String {
    val largest = rest.maxPercent ?: return stringResource(R.string.autoset_rest_none)
    if (rest.restPoints.all { it.isExactlyCenter() }) return stringResource(R.string.autoset_rest_zero, rest.count)
    return stringResource(R.string.autoset_rest, largest, rest.count)
}

// Der Kreis: 8 Felder als Ring (oben beginnend im Uhrzeigersinn, wie StickDirection), in jedem Feld der
// Höchstausschlag in %, innen ein dünnes Kreuz und der Stick als Punkt. 100 % Ausschlag = Mitte des Rings,
// so landet der Punkt beim Drücken bis zum Anschlag im Feld. position() wird erst beim Zeichnen gelesen (R66)
@Composable
private fun ReachDial(reach: StickReach, position: () -> StickPosition?) {
    val textMeasurer = rememberTextMeasurer()
    val noPeak = stringResource(R.string.deadzone_unknown)
    Canvas(modifier = Modifier.size(DIAL_SIZE)) {
        val band = FIELD_BAND.toPx()
        val ringRadius = size.minDimension / 2f - band / 2f
        StickDirection.entries.forEach { direction ->
            drawField(direction.ordinal, reach.field(direction), ringRadius, band)
            drawFieldNumber(direction.ordinal, reach.field(direction), ringRadius, textMeasurer, noPeak)
        }
        drawCross(ringRadius - band / 2f)
        position()?.let { drawLiveDot(it, ringRadius) }
    }
}

// Mittlerer Winkel eines Felds (Compose zählt ab 3 Uhr im Uhrzeigersinn, oben = −90°)
private fun fieldCenterAngle(index: Int): Float = FIRST_FIELD_ANGLE + index * FIELD_SWEEP

// Ein Feld als Bogen-Stück mit kleiner Lücke zum Nachbarn
private fun DrawScope.drawField(index: Int, field: ReachField, ringRadius: Float, band: Float) {
    drawArc(
        color = fieldColor(field.state),
        startAngle = fieldCenterAngle(index) - FIELD_SWEEP / 2f + FIELD_GAP_DEGREES / 2f,
        sweepAngle = FIELD_SWEEP - FIELD_GAP_DEGREES,
        useCenter = false,
        topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
        size = Size(ringRadius * 2f, ringRadius * 2f),
        style = Stroke(width = band),
    )
}

// Höchstausschlag mitten im Feld, ganze Prozent ("96"), "–" = noch nie in diese Richtung gedrückt
private fun DrawScope.drawFieldNumber(index: Int, field: ReachField, ringRadius: Float, textMeasurer: TextMeasurer, noPeak: String) {
    val label = field.peakPercent?.roundToInt()?.toString() ?: noPeak
    val text = textMeasurer.measure(label, TextStyle(color = numberColor(field.state), fontSize = FIELD_NUMBER_SIZE))
    val radians = Math.toRadians(fieldCenterAngle(index).toDouble())
    val middle = Offset(center.x + cos(radians).toFloat() * ringRadius, center.y + sin(radians).toFloat() * ringRadius)
    drawText(text, topLeft = middle - Offset(text.size.width / 2f, text.size.height / 2f))
}

// Dünnes Kreuz im Inneren des Rings
private fun DrawScope.drawCross(innerRadius: Float) {
    drawLine(StickLabColors.GlassBorder, Offset(center.x - innerRadius, center.y), Offset(center.x + innerRadius, center.y))
    drawLine(StickLabColors.GlassBorder, Offset(center.x, center.y - innerRadius), Offset(center.x, center.y + innerRadius))
}

// Der Stick als Punkt, höchstens bis zur Mitte des Rings (schräg nicht darüber hinaus)
private fun DrawScope.drawLiveDot(position: StickPosition, ringRadius: Float) {
    val dot = position.clampedToCircle()
    drawCircle(StickLabColors.Ink, radius = DOT_RADIUS.toPx(), center = Offset(center.x + dot.x * ringRadius, center.y + dot.y * ringRadius))
}

private fun fieldColor(state: FieldState): Color = when (state) {
    FieldState.OPEN -> StickLabColors.Track
    FieldState.REACHED -> StickLabColors.Violet
    FieldState.DONE -> StickLabColors.Success
}

// Auf Grün dunkle Schrift (wie das Kennzeichen "Exactly 0"), sonst weiß
private fun numberColor(state: FieldState): Color =
    if (state == FieldState.DONE) StickLabColors.OnSuccess else StickLabColors.Ink

// ---------- Rechte Seite: Anleitung bzw. Ergebnis ----------

// Während der Messung: was zu tun ist und wie viele der 16 Felder erledigt sind.
// In der Kontrollmessung zusätzlich, welche Deadzone gerade geprüft wird
@Composable
private fun RunningInfo(running: AutoSetUi.Running) {
    val checking = running.checking
    if (checking == null) {
        HintText(stringResource(R.string.autoset_instruction))
    } else {
        HintText(stringResource(R.string.autoset_check_instruction))
        ResultLine(testingText(checking.proposals))
    }
    val done = running.reach.left.doneCount + running.reach.right.doneCount
    val total = running.reach.left.fields.size + running.reach.right.fields.size
    Text(
        text = stringResource(R.string.autoset_fields, done, total),
        fontSize = StickLabDimens.BodySize,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = StickLabDimens.SectionGap).popOnChange(done),
    )
}

// "Testing Left 4.5 % · Right 3.5 %" (Seite ohne Vorschlag: "–", sie bleibt, wie sie ist)
@Composable
private fun testingText(proposals: LeftRight<SideProposal?>): String =
    stringResource(R.string.autoset_check_testing, proposalPercentText(proposals.left), proposalPercentText(proposals.right))

@Composable
private fun proposalPercentText(proposal: SideProposal?): String =
    if (proposal == null) stringResource(R.string.deadzone_unknown) else stepText(proposal.step)

// Reglerstellung als "4.5 %"
@Composable
private fun stepText(step: Int): String = stringResource(R.string.deadzone_percent, DeadzoneSteps.toPercent(step))

// Nach der Messung bzw. Kontrollmessung: Vorschlag Deadzone mit Urteil, äußere Deadzone je Stick, Hinweise
@Composable
private fun ResultInfo(result: AutoSetResult) {
    // Vor der ersten Kontrolle: "nur Vorschläge"; danach die Erklärung des Hakens
    val subtitle = if (result.lastCheck == null) R.string.autoset_result_subtitle else R.string.autoset_check_legend
    HintText(stringResource(subtitle))
    val sides = OdinMapping.Side.entries

    SectionLabel(R.string.autoset_deadzone_label)
    sides.forEach { side -> ResultLine(deadzoneLineText(side, result.proposals.of(side), result.deadzoneBefore.of(side))) }
    resultHint(result)?.let { (text, color) -> HintText(stringResource(text), color = color) }

    SectionLabel(R.string.autoset_outer_label)
    sides.forEach { side -> ResultLine(outerLineText(side, DeadzoneSuggestion.outerDeadzonePercent(result.measured.of(side)))) }

    // R77: Der letzte Rest bleibt in Android stehen, bis der Stick wieder bewegt wird
    HintText(stringResource(R.string.autoset_move_hint), topPadding = StickLabDimens.SmallGap)
}

// Höchstens ein Hinweis unter den Deadzone-Zeilen (sonst wird die Karte zu lang), der wichtigste zuerst:
// kein passender Wert (R82) → kleiner als jetzt, noch nicht geprüft (Entscheidung V) → noch einmal prüfen
private fun resultHint(result: AutoSetResult): Pair<Int, Color>? {
    val proposals = listOfNotNull(result.proposals.left, result.proposals.right)
    val anySmaller = OdinMapping.Side.entries.any { side ->
        val proposal = result.proposals.of(side) ?: return@any false
        !proposal.passed && proposal.step < DeadzoneSteps.toStep(result.deadzoneBefore.of(side))
    }
    return when {
        proposals.any { it.exhausted } -> R.string.autoset_exhausted_hint to StickLabColors.Warning
        result.lastCheck == null && anySmaller -> R.string.autoset_smaller_hint to StickLabColors.Warning
        result.lastCheck != null && result.anyNeedsCheck -> R.string.autoset_check_again_hint to StickLabColors.Ink
        else -> null
    }
}

// Eine Deadzone-Zeile je Stick, je nach Stand der Kontrolle:
// "Left 4.5 % · now 1.5 %" (noch nicht geprüft), "Left 4.5 % ✓ · now 1.5 %" (bestanden),
// "Left 4.5 % ✗ → 5.0 %" (nicht 0, eine Erhöhung = 0,5 % höher), "Left 5.5 % ✗ · no fitting value" (keine Erhöhung mehr),
// "Left 4.5 % · not all 8 fields" (unvollständig) bzw. "Left – · no release seen"
@Composable
private fun deadzoneLineText(side: OdinMapping.Side, proposal: SideProposal?, before: Float): String {
    val name = stringResource(nameOf(side))
    if (proposal == null) return stringResource(R.string.autoset_deadzone_none, name)
    val value = stepText(proposal.step)
    val now = stringResource(R.string.deadzone_percent, before * PERCENT)
    val checkedStep = proposal.checkedStep
    return when {
        proposal.passed -> stringResource(R.string.autoset_deadzone_passed, name, value, now)
        proposal.exhausted -> stringResource(R.string.autoset_deadzone_exhausted, name, value)
        proposal.verdict == CheckVerdict.FAILED && checkedStep != null ->
            stringResource(R.string.autoset_deadzone_raised, name, stepText(checkedStep), value)
        proposal.verdict == CheckVerdict.INCOMPLETE -> stringResource(R.string.autoset_deadzone_incomplete, name, value)
        else -> stringResource(R.string.autoset_deadzone_value, name, value, now)
    }
}

// "Left from 94.0 %", "Left not needed" (ab 100 %) bzw. "Left – · not all 8 fields"
@Composable
private fun outerLineText(side: OdinMapping.Side, startPercent: Float?): String {
    val name = stringResource(nameOf(side))
    return when {
        startPercent == null -> stringResource(R.string.autoset_outer_none, name)
        startPercent >= DeadzoneSuggestion.FULL_PERCENT -> stringResource(R.string.autoset_outer_not_needed, name)
        else -> stringResource(R.string.autoset_outer_value, name, startPercent)
    }
}

@Composable
private fun SectionLabel(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        fontSize = StickLabDimens.LabelSize,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = StickLabDimens.SectionGap),
    )
}

@Composable
private fun ResultLine(text: String) {
    Text(text = text, fontSize = StickLabDimens.LabelSize, modifier = Modifier.padding(top = 2.dp))
}

// Kleine, blasse Zeile (Anleitung, Hinweise); Warnfarbe ohne Blässe
@Composable
private fun HintText(text: String, color: Color = StickLabColors.Ink, topPadding: Dp = 2.dp) {
    val dimmed = if (color == StickLabColors.Ink) StickLabDimens.DIMMED_ALPHA else 1f
    Text(
        text = text,
        fontSize = StickLabDimens.HintSize,
        color = color,
        modifier = Modifier.padding(top = topPadding).alpha(dimmed),
    )
}

// Was die Knöpfe auslösen (gebündelt, damit PanelButtons nicht fünf einzelne Aktionen braucht)
private class PanelActions(
    val onStop: () -> Unit,
    val onAgain: () -> Unit,
    val onCheck: () -> Unit,
    val onApply: () -> Unit,
    val onDone: () -> Unit,
)

// Knöpfe. Der erste Knopf steht immer an derselben Stelle (Stop → Again), damit er den Fokus behält.
// Nach einer Runde: Again · (Check bzw. Apply) · Done. Der mittlere Knopf ist der nächste Schritt:
// "Check", solange eine Seite noch geprüft werden muss, sonst "Apply", wenn eine Seite bestanden hat (Ü3),
// sonst gibt es ihn nicht (kein Vorschlag oder kein passender Wert)
@Composable
private fun PanelButtons(autoSet: AutoSetUi, actions: PanelActions) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(StickLabDimens.SmallGap),
        modifier = Modifier.fillMaxWidth().padding(top = StickLabDimens.ButtonGap),
    ) {
        when (autoSet) {
            is AutoSetUi.Running -> StickLabButton(
                text = stringResource(R.string.autoset_stop, secondsLeft(autoSet.endsAt)),
                onClick = actions.onStop,
                primary = true,
                modifier = Modifier.weight(1f),
            )
            is AutoSetUi.Finished -> {
                StickLabButton(text = stringResource(R.string.autoset_again), onClick = actions.onAgain, modifier = Modifier.weight(1f))
                val result = autoSet.result
                when {
                    result.anyNeedsCheck -> StickLabButton(
                        text = stringResource(R.string.autoset_check),
                        onClick = actions.onCheck,
                        primary = true,
                        modifier = Modifier.weight(1f),
                    )
                    result.anyPassed -> StickLabButton(
                        text = stringResource(R.string.apply),
                        onClick = actions.onApply,
                        primary = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                StickLabButton(text = stringResource(R.string.autoset_done), onClick = actions.onDone, modifier = Modifier.weight(1f))
            }
            AutoSetUi.Idle -> Unit
        }
    }
}

// Sekunden bis endsAt (SystemClock.uptimeMillis), aufgerundet; zählt von selbst herunter
@Composable
private fun secondsLeft(endsAt: Long): Int {
    var left by remember(endsAt) { mutableIntStateOf(secondsUntil(endsAt)) }
    LaunchedEffect(endsAt) {
        while (left > 0) {
            delay(COUNTDOWN_TICK_MS)
            left = secondsUntil(endsAt)
        }
    }
    return left
}

private fun secondsUntil(endsAt: Long): Int =
    ceil((endsAt - SystemClock.uptimeMillis()) / MS_PER_SECOND).toInt().coerceAtLeast(0)

// Maße. Inhalt des Reiters ca. 716 dp breit (StickLabDimens): 2 Kreise à 156 + Abstände lassen rechts
// ca. 330 dp für Text. Sichtbare Höhe ca. 316 dp (Foto Lauf 1, 17.10.2026). Mit Kreisen à 180 war die Karte
// genau so hoch und wurde in Lauf 2 (25.10.2026) oben abgeschnitten – seit es kein Scrollen mehr gibt
// (Besitzer 25.10.2026) braucht sie Luft: Kreis 156 + 2 Zeilen ≈ 45 + Knöpfe ≈ 60 + Innenrand 32 ≈ 293 dp
private val DIAL_SIZE = 156.dp
private val FIELD_BAND = 30.dp              // Dicke des Rings mit den Feldern, Platz für "100"
private val FIELD_NUMBER_SIZE = 12.sp
private val DOT_RADIUS = 7.dp               // wie in der Karte "Live"
private val PANEL_GAP = 18.dp
private val DIALS_WIDTH = DIAL_SIZE * 2 + PANEL_GAP

// 8 Felder à 45°, das erste (oben) bei −90°; 3° Lücke zwischen zwei Feldern
private const val FIRST_FIELD_ANGLE = -90f
private val FIELD_SWEEP = 360f / StickDirection.entries.size
private const val FIELD_GAP_DEGREES = 3f

private const val PERCENT = 100f

// Countdown: viermal pro Sekunde nachsehen reicht für eine ganze Sekunden-Zahl
private const val COUNTDOWN_TICK_MS = 250L
private const val MS_PER_SECOND = 1000.0

// Vorschau in Android Studio: links alles erledigt, rechts 6 von 8 Feldern, eins nur erreicht
@Preview(widthDp = 716)
@Composable
private fun AutoSetPanelPreview() {
    val left = StickReach(
        listOf(96f, 100f, 95.1f, 100f, 96.6f, 99f, 97f, 100f).map { ReachField(it, FieldState.DONE) },
        DriftResult(listOf(StickPosition(0.0147f, 0f), StickPosition(0.004f, 0f))),
    )
    val right = StickReach(
        listOf(95f, 99f, 96f, null, 98f, 82f, 95f, 100f).mapIndexed { i, peak ->
            ReachField(peak, if (i == 5) FieldState.REACHED else if (peak == null) FieldState.OPEN else FieldState.DONE)
        },
        DriftResult(listOf(StickPosition(-0.0075f, -0.0038f))),
    )
    StickLabTheme {
        AutoSetPanel(
            autoSet = AutoSetUi.Finished(AutoSetResult.ofMeasurement(LeftRight(0.015f, 0.025f), LeftRight(left, right))),
            liveInput = { LiveInput(StickPosition.CENTER, StickPosition(0.003f, 0f), 0f, 0f) },
            onStop = {},
            onAgain = {},
            onCheck = {},
            onApply = {},
            onDone = {},
        )
    }
}
