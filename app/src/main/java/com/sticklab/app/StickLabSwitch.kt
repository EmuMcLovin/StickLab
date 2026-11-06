package com.sticklab.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Eine ganze Zeile mit Text links und Schalter rechts (Aussehen A2).
// Die Zeile ist ein Element: antippen oder mit Steuerkreuz + A schaltet um.
// Mit Fokus wird sie etwas größer, heller und bekommt einen Ring
@Composable
fun SwitchRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (focused) ROW_FOCUS_SCALE else 1f, StickLabMotion.bouncy(), label = "switchRowScale")
    val fill by animateColorAsState(if (focused) StickLabColors.RowFillFocused else StickLabColors.RowFill, label = "switchRowFill")
    val shape = RoundedCornerShape(ROW_CORNER)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .focusRing(visible = focused, shape = shape)
            .fillMaxWidth()
            .clip(shape)
            .background(fill)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text = text, fontSize = StickLabDimens.BodySize, modifier = Modifier.weight(1f))
        StickLabSwitch(checked = checked, pressed = pressed)
    }
}

// Der Schalter selbst, nur zum Anzeigen (geschaltet wird über die Zeile).
// Aus: Ring-Knopf links auf dunkler Schiene mit feinem Rand (Strichstil der Symbole).
// An: Schiene mit Farbverlauf pink → violett und Leuchten, voller weißer Knopf rechts.
// Beim Drücken dehnt sich der Knopf. Bewusst nicht wie Apples Schalter (Wunsch Besitzer 16.10.2026)
@Composable
private fun StickLabSwitch(checked: Boolean, pressed: Boolean) {
    val knobWidth by animateDpAsState(if (pressed) KNOB_PRESSED_WIDTH else KNOB_SIZE, tween(KNOB_STRETCH_MS), label = "knobWidth")
    // Linke Kante des Knopfs; an + gedrückt rückt sie nach links, damit der Knopf zur Mitte hin wächst
    val knobLeft by animateDpAsState(
        targetValue = when {
            !checked -> KNOB_INSET
            pressed -> TRACK_WIDTH - KNOB_INSET - KNOB_PRESSED_WIDTH
            else -> TRACK_WIDTH - KNOB_INSET - KNOB_SIZE
        },
        animationSpec = StickLabMotion.bouncy(),
        label = "knobLeft",
    )
    val on by animateFloatAsState(if (checked) 1f else 0f, tween(SWITCH_FADE_MS), label = "switchOn")

    Canvas(modifier = Modifier.size(TRACK_WIDTH, TRACK_HEIGHT)) {
        drawTrack(on)
        drawKnob(on, knobLeft.toPx(), knobWidth.toPx())
    }
}

// Schiene: aus = dunkel mit feinem Rand, an = Farbverlauf mit Leuchten (weich überblendet über "on")
private fun DrawScope.drawTrack(on: Float) {
    val radius = CornerRadius(size.height / 2)
    if (on > 0f) {
        // Leuchten: zwei größere, blasse Rundungen um die Schiene
        for (layer in 1..2) {
            val spread = layer * 3.dp.toPx()
            drawRoundRect(
                color = StickLabColors.SwitchViolet.copy(alpha = 0.22f * on / layer),
                topLeft = Offset(-spread, -spread),
                size = Size(size.width + 2 * spread, size.height + 2 * spread),
                cornerRadius = CornerRadius(size.height / 2 + spread),
            )
        }
    }
    drawRoundRect(color = Color.White.copy(alpha = 0.06f * (1 - on)), cornerRadius = radius)
    drawRoundRect(
        brush = Brush.horizontalGradient(listOf(StickLabColors.Pink, StickLabColors.SwitchViolet)),
        alpha = on,
        cornerRadius = radius,
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.22f - 0.07f * on),
        cornerRadius = radius,
        style = Stroke(1.dp.toPx()),
    )
}

// Knopf: aus = nur Ring, an = voll weiß; Breite wächst beim Drücken
private fun DrawScope.drawKnob(on: Float, left: Float, width: Float) {
    val height = KNOB_SIZE.toPx()
    val top = (size.height - height) / 2
    val radius = CornerRadius(height / 2)
    drawRoundRect(Color.White.copy(alpha = on), Offset(left, top), Size(width, height), radius)
    val ringWidth = 2.dp.toPx()
    drawRoundRect(
        color = Color.White.copy(alpha = KNOB_RING_ALPHA * (1 - on)),
        topLeft = Offset(left + ringWidth / 2, top + ringWidth / 2),
        size = Size(width - ringWidth, height - ringWidth),
        cornerRadius = radius,
        style = Stroke(ringWidth),
    )
}

// Maße aus DESIGN_ENTWURF.html A2: Schiene 50 × 28, Knopf 16 (gedrückt 22), 6 vom Rand
private val TRACK_WIDTH: Dp = 50.dp
private val TRACK_HEIGHT: Dp = 28.dp
private val KNOB_SIZE: Dp = 16.dp
private val KNOB_PRESSED_WIDTH: Dp = 22.dp
private val KNOB_INSET: Dp = 6.dp
private const val KNOB_RING_ALPHA = 0.65f
private const val KNOB_STRETCH_MS = 200
private const val SWITCH_FADE_MS = 300
private val ROW_CORNER = 20.dp
private const val ROW_FOCUS_SCALE = 1.03f
