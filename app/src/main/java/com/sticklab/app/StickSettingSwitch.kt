package com.sticklab.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Umschalter oben in der Regler-Karte des Reiters Sticks (Schritt S, Entwurf S_ENTWURF.html):
// "Deadzone · Sensitivity · Trigger · Curve". Der gewählte Teil ist eine weiße Kapsel; ein oranger Punkt zeigt,
// dass in diesem Teil ein Regler verschoben, aber noch nicht mit "Apply" übernommen ist.
// Jeder Teil ist einzeln mit dem Steuerkreuz erreichbar (links/rechts), A wählt ihn
@Composable
fun StickSettingSwitch(
    selected: StickSetting,
    pending: Set<StickSetting>,
    onSelect: (StickSetting) -> Unit,
    modifier: Modifier = Modifier,
) {
    SwitchBar(modifier) {
        for (setting in StickSetting.entries) {
            SwitchSegment(
                text = stringResource(nameOf(setting)),
                selected = setting == selected,
                pending = setting in pending,
                onClick = { onSelect(setting) },
            )
        }
    }
}

// Die Leiste um die Teile eines Umschalters (auch die Stick-Wahl im Teil "Curve")
@Composable
fun SwitchBar(modifier: Modifier = Modifier, segments: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(StickLabColors.RowFill)
            .border(1.dp, StickLabColors.GlassBorder, shape)
            .padding(SWITCH_PADDING),
        content = segments,
    )
}

// Ein Teil des Umschalters: Kapsel so breit wie ihr Text (passt auch mit dem längeren deutschen
// "Empfindlichkeit"), mit Fokus etwas größer und mit Ring wie die Knöpfe
@Composable
fun SwitchSegment(text: String, selected: Boolean, pending: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) StickLabMotion.FOCUS_SCALE else 1f, StickLabMotion.bouncy(), label = "segmentScale")
    val fill by animateColorAsState(if (selected) Color.White else Color.Transparent, label = "segmentFill")
    val ink by animateColorAsState(if (selected) StickLabColors.InkOnLight else StickLabColors.Ink, label = "segmentInk")
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .focusRing(visible = focused, shape = shape)
            .height(SEGMENT_HEIGHT)
            .clip(shape)
            .background(fill)
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = SEGMENT_PADDING_H),
    ) {
        Text(text = text, color = ink, fontSize = SEGMENT_TEXT_SIZE, fontWeight = FontWeight.SemiBold, maxLines = 1)
        // Platz für den Punkt bleibt immer frei, damit die Kapsel beim Auftauchen nicht springt
        Box(
            modifier = Modifier
                .padding(start = PENDING_DOT_GAP)
                .size(PENDING_DOT_SIZE)
                .background(if (pending) StickLabColors.Warning else Color.Transparent, CircleShape),
        )
    }
}

// Beschriftung je Teil (Deadzone wie die bisherige Karten-Überschrift)
private fun nameOf(setting: StickSetting): Int = when (setting) {
    StickSetting.DEADZONE -> R.string.deadzone_title
    StickSetting.SENSITIVITY -> R.string.setting_sensitivity
    StickSetting.TRIGGER -> R.string.setting_trigger
    StickSetting.CURVE -> R.string.setting_curve
}

// Maße aus S_ENTWURF.html, aber flacher: Die Regler-Karte ist quer fast voll (ca. 310 von 317 dp, Foto 26.10.),
// der Umschalter (24 + 2 × 2 = 28 dp) ersetzt die Überschrift (ca. 24 dp) und darf kaum höher sein.
// Schmaler seit dem vierten Teil "Curve": muss auch mit dem deutschen "Empfindlichkeit" in die Karte passen
private val SEGMENT_HEIGHT = 24.dp
private val SWITCH_PADDING = 2.dp
private val SEGMENT_GAP = 2.dp
private val SEGMENT_PADDING_H = 7.dp
private val SEGMENT_TEXT_SIZE = 12.5.sp
private val PENDING_DOT_SIZE = 5.dp
private val PENDING_DOT_GAP = 3.dp
