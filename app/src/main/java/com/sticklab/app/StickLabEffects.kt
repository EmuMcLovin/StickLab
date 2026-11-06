package com.sticklab.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Kleine Effekte, die Knöpfe, Schalter, Regler und Leiste gemeinsam benutzen (Aussehen A2)

// Fokus-Ring: weißer Rand mit violettem Leuchten außen herum, blendet weich ein und aus.
// Zeigt, wo Steuerkreuz/Stick gerade stehen. Leuchten = mehrere blasser werdende Ränder
// (Compose hat keinen Weichzeichner ohne Bibliothek)
@Composable
fun Modifier.focusRing(visible: Boolean, shape: Shape, gap: Dp = 0.dp): Modifier {
    val amount by animateFloatAsState(if (visible) 1f else 0f, StickLabMotion.bouncy(), label = "focusRing")
    return drawBehind {
        // Die Federung schießt über 1 hinaus bzw. unter 0 – Durchsichtigkeit muss zwischen 0 und 1 bleiben
        val strength = amount.coerceIn(0f, 1f)
        if (strength == 0f) return@drawBehind
        val ringOffset = gap.toPx()
        for (layer in GLOW_LAYERS downTo 1) {
            val spread = ringOffset + layer * GLOW_LAYER_STEP.toPx()
            drawShapeOutline(shape, spread, StickLabColors.Glow.copy(alpha = StickLabColors.Glow.alpha * strength / (layer + 1)), RING_WIDTH.toPx() * 2)
        }
        drawShapeOutline(shape, ringOffset, StickLabColors.FocusRing.copy(alpha = StickLabColors.FocusRing.alpha * strength), RING_WIDTH.toPx())
    }
}

// Zeichnet den Umriss einer Form, um "spread" Pixel nach außen vergrößert
private fun DrawScope.drawShapeOutline(shape: Shape, spread: Float, color: Color, width: Float) {
    val grown = Size(size.width + spread * 2, size.height + spread * 2)
    val outline = shape.createOutline(grown, layoutDirection, this)
    translate(-spread, -spread) { drawOutline(outline, color, style = Stroke(width)) }
}

private val RING_WIDTH = 2.dp          // Breite des weißen Rings (Entwurf: border 2px)
private const val GLOW_LAYERS = 3      // Anzahl Ränder für das Leuchten
private val GLOW_LAYER_STEP = 3.dp     // Abstand zwischen den Leucht-Rändern

// "Zahl hüpft": wird kurz größer und federt zurück, wenn sich der Wert (key) ändert.
// Beim ersten Anzeigen nicht
@Composable
fun Modifier.popOnChange(key: Any): Modifier {
    val scale = remember { Animatable(1f) }
    var shownKey by remember { mutableStateOf(key) }
    LaunchedEffect(key) {
        if (key == shownKey) return@LaunchedEffect
        shownKey = key
        scale.animateTo(POP_SCALE, tween(POP_UP_MS))
        scale.animateTo(1f, StickLabMotion.bouncy())
    }
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

private const val POP_SCALE = 1.22f    // Entwurf: scale(1.22)
private const val POP_UP_MS = 110

// Gestaffeltes Einfahren: Beim Öffnen eines Reiters erscheinen die Zeilen einer Karte nacheinander
// von leicht unten (order = Reihenfolge, 0 zuerst)
@Composable
fun Modifier.enterStaggered(order: Int): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(STAGGER_START_MS + order * STAGGER_STEP_MS)
        progress.animateTo(1f, tween(STAGGER_DURATION_MS, easing = LinearOutSlowInEasing))
    }
    val rise = with(LocalDensity.current) { STAGGER_RISE.toPx() }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise
    }
}

// Werte aus dem Entwurf: delay 60 + j·45 ms, Dauer 380 ms, 10 px von unten
private const val STAGGER_START_MS = 60L
private const val STAGGER_STEP_MS = 45L
private const val STAGGER_DURATION_MS = 380
private val STAGGER_RISE = 10.dp
