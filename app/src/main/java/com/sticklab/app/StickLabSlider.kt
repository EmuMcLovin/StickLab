// Der Regler mit eigenem Griff und eigener Spur (thumb/track, SliderState) ist in material3 1.4.0 noch als
// "experimentell" markiert, d. h. Google kann ihn in einer späteren Bibliotheks-Version ändern. Unsere Version
// ist über compose-bom 2026.09.00 fest eingetragen; bei einem Update diese Datei prüfen (20. Sitzung)
@file:OptIn(ExperimentalMaterial3Api::class)

package com.sticklab.app

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Eine Regler-Zeile im Aussehen A2: oben Beschriftung links und Wert rechts (Wert hüpft bei Änderung),
// darunter der Regler mit weißer Spur und rundem Griff (mit Fokus/beim Ziehen größer + Leuchten).
// value/onValueChange in Schritten (0, 1, 2 …), steps = Anzahl Zwischenstellungen wie beim Compose-Regler
@Composable
fun SettingSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val pressed by interaction.collectIsPressedAsState()

    // Regler gleiten sanft auf einen neuen Wert, wenn er von außen kommt (z. B. "Restore original").
    // Beim eigenen Bedienen (Fokus, Ziehen, Tippen) sofort, sonst gingen schnelle Tastendrücke verloren
    val userIsAdjusting = focused || dragged || pressed
    val shownValue by animateFloatAsState(
        targetValue = value,
        animationSpec = if (userIsAdjusting) snap() else tween(GLIDE_MS, easing = LinearOutSlowInEasing),
        label = "sliderGlide",
    )

    Column(modifier = modifier.padding(top = StickLabDimens.SectionGap)) {
        Row {
            Text(text = label, fontSize = StickLabDimens.LabelSize, modifier = Modifier.weight(1f))
            Text(
                text = valueText,
                fontSize = StickLabDimens.LabelSize,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.popOnChange(valueText),
            )
        }
        Slider(
            value = shownValue,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            interactionSource = interaction,
            thumb = { GlowThumb(highlighted = userIsAdjusting) },
            track = { sliderState -> WhiteTrack(sliderState) },
            modifier = Modifier.leaveSliderWithUpDown(),
        )
    }
}

// Runder weißer Griff; mit Fokus oder beim Ziehen ×1,3 mit violettem Leuchten (Entwurf A2)
@Composable
private fun GlowThumb(highlighted: Boolean) {
    val scale by animateFloatAsState(if (highlighted) THUMB_FOCUS_SCALE else 1f, StickLabMotion.bouncy(), label = "thumbScale")
    Box(
        modifier = Modifier
            .size(THUMB_SIZE)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .focusRing(visible = highlighted, shape = CircleShape)
            .background(Color.White, CircleShape),
    )
}

// Spur: links vom Griff weiß, rechts blass (Entwurf: linear-gradient #fff → track)
@Composable
private fun WhiteTrack(state: SliderState) {
    val range = state.valueRange.endInclusive - state.valueRange.start
    val fraction = if (range > 0f) ((state.value - state.valueRange.start) / range).coerceIn(0f, 1f) else 0f
    Canvas(modifier = Modifier.fillMaxWidth().height(TRACK_HEIGHT)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(StickLabColors.Track, cornerRadius = radius)
        drawRoundRect(Color.White, size = Size(size.width * fraction, size.height), cornerRadius = radius)
    }
}

// Maße aus DESIGN_ENTWURF.html A2: Griff 22, Spur 6 hoch, Griff mit Fokus ×1,3, Gleiten 450 ms
private val THUMB_SIZE = 22.dp
private val TRACK_HEIGHT = 6.dp
private const val THUMB_FOCUS_SCALE = 1.3f
private const val GLIDE_MS = 450

// Steuerkreuz (im Reiter Lights auch linker Stick) auf einem Regler: Von sich aus nimmt der Compose-Regler alle vier
// Richtungen (hoch = mehr, runter = weniger), man kommt dann nicht mehr weg (Test 16.10.2026, R52).
// Darum: hoch/runter springt zum Element darüber/darunter, links/rechts verstellt weiter den Wert
@Composable
fun Modifier.leaveSliderWithUpDown(): Modifier {
    val focusManager = LocalFocusManager.current
    return onPreviewKeyEvent { event ->
        val direction = when (event.key) {
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            else -> return@onPreviewKeyEvent false
        }
        // Nur beim Drücken springen; das Loslassen trotzdem abfangen, damit der Regler es nicht sieht
        if (event.type == KeyEventType.KeyDown) focusManager.moveFocus(direction)
        true
    }
}
