package com.sticklab.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.hypot
import kotlin.math.max
import kotlinx.coroutines.launch

// Hintergrund A2: drei weiche Farbflecken auf fast schwarzem Grund, Farben je nach Wahl (AppBackground).
// Bewegt sich langsam hin und her (Entscheidung Besitzer 16.10.2026: "bewegt").
// Die Bewegung läuft nur über graphicsLayer (verschieben/drehen der fertigen Ebene), die Flecken
// werden also nicht jedes Bild neu gezeichnet. Ob das Ambilight flüssig bleibt, prüft der Test (R57).
// Beim Wechsel blenden alte und neue Flecken kurz ineinander, die Bewegung läuft dabei weiter (rememberBackgroundFades)
@Composable
fun GradientBackground(background: AppBackground, modifier: Modifier = Modifier) {
    val fades = rememberBackgroundFades(background)
    val drift = rememberInfiniteTransition(label = "backgroundDrift")
    val progress = drift.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(DRIFT_DURATION_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "backgroundDriftProgress",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawRect(StickLabColors.Night) }
            .graphicsLayer {
                // Wert erst hier lesen: dann ändert sich pro Bild nur die Lage der Ebene
                val p = progress.value
                translationX = p * DRIFT_X * size.width
                translationY = p * DRIFT_Y * size.height
                rotationZ = p * DRIFT_ROTATION_DEG
            }
            .drawBehind {
                // Deckkraft erst hier lesen: gezeichnet wird nur, solange eine Überblendung läuft
                for ((shown, fade) in fades) {
                    if (fade.value > 0f) drawColorSpots(shown, fade.value)
                }
            },
    )
}

// Je Hintergrund eine eigene Deckkraft: gewählt läuft nach 1, alle anderen nach 0. Auch beim schnellen Wechseln läuft jeder
// von seinem aktuellen Wert weiter, nichts springt.
// Bewusst nicht Crossfade: Das blendet über eine Zwischenebene in Bildschirmgröße, und die schnitt den Überstand der Flecken
// ab (OVERSIZE). In der gedrehten Ebene sah man dann schräge Kanten und schwarze Ecken (Besitzer und Foto, 06.11.2026)
@Composable
private fun rememberBackgroundFades(selected: AppBackground): Map<AppBackground, Animatable<Float, AnimationVector1D>> {
    val fades = remember {
        AppBackground.entries.associateWith { Animatable(if (it == selected) 1f else 0f) }
    }
    LaunchedEffect(selected) {
        for ((background, fade) in fades) {
            launch { fade.animateTo(if (background == selected) 1f else 0f, tween(CHANGE_FADE_MS)) }
        }
    }
    return fades
}

// Ein Farbfleck: Mitte (Anteil der Fläche), Farbe mit Deckkraft, Reichweite (Anteil bis zur fernsten Ecke)
private class ColorSpot(val x: Float, val y: Float, val color: Color, val reach: Float)

// Lage und Reichweite sind bei allen Hintergründen gleich, nur die Farben wechseln. Werte aus DESIGN_ENTWURF.html A2:
// radial-gradient(circle at 18% 12%, pink .55, transparent 34%) usw.
private fun spots(topLeft: Color, right: Color, bottom: Color) = listOf(
    ColorSpot(0.18f, 0.12f, topLeft, 0.34f),
    ColorSpot(0.85f, 0.38f, right, 0.40f),
    ColorSpot(0.45f, 1.00f, bottom, 0.38f),
)

// Neon = das Aussehen A2. Die anderen dunkel genug für weiße Schrift (am Odin per Foto geprüft, 06.11.2026).
// Graphite ist bewusst fast farblos, für alle, denen die anderen zu bunt sind
private val NEON_SPOTS = spots(
    StickLabColors.Pink.copy(alpha = 0.55f),
    StickLabColors.Violet.copy(alpha = 0.62f),
    StickLabColors.Teal.copy(alpha = 0.50f),
)
private val OCEAN_SPOTS = spots(Color(0xFF0A84FF).copy(alpha = 0.55f), Color(0xFF00A3C7).copy(alpha = 0.50f), Color(0xFF12D6A0).copy(alpha = 0.42f))
private val EMBER_SPOTS = spots(Color(0xFFFF6A13).copy(alpha = 0.50f), Color(0xFFE0163C).copy(alpha = 0.50f), Color(0xFF8E1FB5).copy(alpha = 0.42f))
private val GRAPHITE_SPOTS = spots(Color(0xFF8A94A6).copy(alpha = 0.30f), Color(0xFF5A6675).copy(alpha = 0.38f), Color(0xFF3F4C5C).copy(alpha = 0.45f))

private fun spotsOf(background: AppBackground): List<ColorSpot> = when (background) {
    AppBackground.NEON -> NEON_SPOTS
    AppBackground.OCEAN -> OCEAN_SPOTS
    AppBackground.EMBER -> EMBER_SPOTS
    AppBackground.GRAPHITE -> GRAPHITE_SPOTS
}

// Beim Wechsel: kurz genug, um direkt zu wirken, lang genug, um nicht zu springen
private const val CHANGE_FADE_MS = 500

// Die Fläche der Flecken ist größer als der Bildschirm (Entwurf: inset -25 %),
// damit beim Verschieben und Drehen keine dunklen Ränder sichtbar werden
private const val OVERSIZE = 0.25f

// Bewegung aus dem Entwurf: 16 s hin, 16 s zurück, 5 % nach rechts, 4 % nach oben, 8° gedreht
private const val DRIFT_DURATION_MS = 16_000
private const val DRIFT_X = 0.05f
private const val DRIFT_Y = -0.04f
private const val DRIFT_ROTATION_DEG = 8f

// Zeichnet die drei Flecken als Kreis-Verläufe von der Farbe nach durchsichtig, mit der Deckkraft opacity.
// Auch für die Vorschau-Bilder im Reiter Settings, dort auf kleinerer Fläche
fun DrawScope.drawColorSpots(background: AppBackground, opacity: Float = 1f) {
    val origin = Offset(-OVERSIZE * size.width, -OVERSIZE * size.height)
    val area = Size(size.width * (1 + 2 * OVERSIZE), size.height * (1 + 2 * OVERSIZE))
    for (spot in spotsOf(background)) {
        val center = Offset(origin.x + spot.x * area.width, origin.y + spot.y * area.height)
        // Wie CSS "circle" ohne Größe: 100 % = Abstand zur fernsten Ecke
        val farthestCorner = hypot(
            max(spot.x, 1 - spot.x) * area.width,
            max(spot.y, 1 - spot.y) * area.height,
        )
        val brush = Brush.radialGradient(
            colors = listOf(spot.color, Color.Transparent),
            center = center,
            radius = spot.reach * farthestCorner,
        )
        drawRect(brush, topLeft = origin, size = area, alpha = opacity)
    }
}
