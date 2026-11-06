package com.sticklab.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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

// Einfaches Bild des Odin 3 im Reiter Buttons (Phase 4, Wunsch Besitzer 25.10.2026).
// Nur Anzeige: nicht antippbar, bekommt keinen Fokus. Zeigt, welche Taste M1 (pink) und M2 (türkis)
// nachahmen – nach der gewählten Belegung – und welche Tasten gerade gedrückt sind (weiß).
// Lage der Teile wie in BUTTONS_ENTWURF.html, vom Besitzer bestätigt am 26.10.2026.
// pressedParts wird erst beim Zeichnen gelesen: Ein Tastendruck malt nur das Bild neu
@Composable
fun OdinPicture(
    layout: ControllerLayout,
    chosen: Map<BackButton, ButtonTarget>,
    pressedParts: () -> Set<OdinPart>,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val backSide = stringResource(R.string.buttons_back_side)
    val m1Target = chosen[BackButton.M1]
    val m2Target = chosen[BackButton.M2]
    val m1Part = m1Target?.let { ButtonLayoutMap.partOf(it, layout) }
    val m2Part = m2Target?.let { ButtonLayoutMap.partOf(it, layout) }

    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(PICTURE_WIDTH / PICTURE_HEIGHT)) {
        val scale = size.width / PICTURE_WIDTH
        val pen = PicturePen(this, scale, textMeasurer)
        pen.drawBody()
        val pressed = pressedParts()
        for (shape in PART_SHAPES) {
            pen.drawPart(shape, ringColorOf(shape.part, m1Part, m2Part), shape.part in pressed)
        }
        pen.text(backSide, BACK_SIDE_LABEL, SMALL_TEXT, LABEL_COLOR_DIM)
        m1Target?.let { pen.text("→ " + ButtonLayoutMap.printedName(it, layout), M1_TARGET_LABEL, SMALL_TEXT, LABEL_COLOR_DIM) }
        m2Target?.let { pen.text("→ " + ButtonLayoutMap.printedName(it, layout), M2_TARGET_LABEL, SMALL_TEXT, LABEL_COLOR_DIM) }
    }
}

// Ringfarbe eines Teils: pink = M1 bzw. Ziel von M1, türkis = M2 bzw. Ziel von M2, weiß = Ziel von beiden
private fun ringColorOf(part: OdinPart, m1Part: OdinPart?, m2Part: OdinPart?): Color? {
    val forM1 = part == OdinPart.M1 || part == m1Part
    val forM2 = part == OdinPart.M2 || part == m2Part
    return when {
        forM1 && forM2 -> Color.White
        forM1 -> StickLabColors.Pink
        forM2 -> StickLabColors.Teal
        else -> null
    }
}

// Ein Teil im Bild: Rechteck mit runden Ecken (Kreis = Ecken halb so groß wie die Seite).
// Alle Maße in Bild-Einheiten (Bild 320 × 196, wie die viewBox im Entwurf)
private class PartShape(
    val part: OdinPart,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val corner: Float,
    val label: String? = null,
    val labelSize: Float = SMALL_TEXT,
) {
    val center get() = Offset(left + width / 2, top + height / 2)
}

// Kreis mit Mittelpunkt und Radius (Sticks, A/B/X/Y, Back/Home)
private fun circle(part: OdinPart, centerX: Float, centerY: Float, radius: Float, label: String? = null, labelSize: Float = SMALL_TEXT) =
    PartShape(part, centerX - radius, centerY - radius, radius * 2, radius * 2, radius, label, labelSize)

// Lage aller Teile (aus BUTTONS_ENTWURF.html, vom Besitzer bestätigt): links Stick oben, Steuerkreuz darunter;
// rechts A/B/X/Y oben (B unten, A rechts, X oben, Y links – aus der Messung geschlossen), Stick darunter; oben die
// Schultertasten, unten die Rückseite mit M1 links und M2 rechts. Home/Zurück am 26.10.2026 korrigiert
private val PART_SHAPES = listOf(
    PartShape(OdinPart.L2, 22f, 2f, 58f, 11f, 5f, "L2"),
    PartShape(OdinPart.L1, 22f, 15f, 58f, 11f, 5f, "L1"),
    PartShape(OdinPart.R2, 240f, 2f, 58f, 11f, 5f, "R2"),
    PartShape(OdinPart.R1, 240f, 15f, 58f, 11f, 5f, "R1"),
    circle(OdinPart.L3, 50f, 64f, 20f, "L3"),
    PartShape(OdinPart.DPAD_UP, 43f, 96f, 14f, 14f, 3f),
    PartShape(OdinPart.DPAD_DOWN, 43f, 124f, 14f, 14f, 3f),
    PartShape(OdinPart.DPAD_LEFT, 29f, 110f, 14f, 14f, 3f),
    PartShape(OdinPart.DPAD_RIGHT, 57f, 110f, 14f, 14f, 3f),
    circle(OdinPart.FACE_X, 270f, 46f, 9f, "X", BIG_TEXT),
    circle(OdinPart.FACE_Y, 252f, 64f, 9f, "Y", BIG_TEXT),
    circle(OdinPart.FACE_A, 288f, 64f, 9f, "A", BIG_TEXT),
    circle(OdinPart.FACE_B, 270f, 82f, 9f, "B", BIG_TEXT),
    circle(OdinPart.R3, 270f, 118f, 20f, "R3"),
    PartShape(OdinPart.SELECT, 76f, 34f, 12f, 9f, 3f),
    PartShape(OdinPart.START, 232f, 34f, 12f, 9f, 3f),
    // Kleine Tasten (Besitzer 26.10.2026): Vierecktaste oben links (= SELECT, gemessen Stufe 4.0c), oben rechts
    // START (Stufe 4.0b), Home unten links, Zurück unten rechts
    circle(OdinPart.HOME, 82f, 140f, 5f),
    circle(OdinPart.BACK, 238f, 140f, 5f),
    PartShape(OdinPart.M1, 30f, 158f, 66f, 22f, 11f, "M1", BIG_TEXT),
    PartShape(OdinPart.M2, 224f, 158f, 66f, 22f, 11f, "M2", BIG_TEXT),
)

// Zeichnet in Bild-Einheiten: rechnet jede Angabe mit "scale" in Pixel um
private class PicturePen(private val scope: DrawScope, private val scale: Float, private val textMeasurer: TextMeasurer) {

    // Gehäuse und Bildschirm (keine Tasten, leuchten nie)
    fun drawBody() {
        roundRect(BODY_LEFT, BODY_TOP, BODY_WIDTH, BODY_HEIGHT, BODY_CORNER, BODY_FILL)
        roundRect(BODY_LEFT, BODY_TOP, BODY_WIDTH, BODY_HEIGHT, BODY_CORNER, BODY_OUTLINE, Stroke(OUTLINE_WIDTH * scale))
        roundRect(SCREEN_LEFT, SCREEN_TOP, SCREEN_WIDTH, SCREEN_HEIGHT, SCREEN_CORNER, SCREEN_FILL)
    }

    // Ein Teil: Fläche (weiß, wenn gedrückt), Rand bzw. farbiger Ring mit Leuchten, Beschriftung
    fun drawPart(shape: PartShape, ringColor: Color?, pressed: Boolean) {
        with(shape) {
            roundRect(left, top, width, height, corner, if (pressed) Color.White else PART_FILL)
            if (ringColor != null) {
                roundRect(left, top, width, height, corner, ringColor.copy(alpha = RING_GLOW_ALPHA), Stroke(RING_GLOW_WIDTH * scale))
                roundRect(left, top, width, height, corner, ringColor, Stroke(RING_WIDTH * scale))
            } else {
                roundRect(left, top, width, height, corner, PART_OUTLINE, Stroke(OUTLINE_WIDTH * scale))
            }
            label?.let { text(it, center, labelSize, if (pressed) StickLabColors.InkOnLight else LABEL_COLOR) }
        }
    }

    // Text mittig um einen Punkt
    fun text(text: String, center: Offset, size: Float, color: Color) {
        val style = TextStyle(color = color, fontSize = with(scope) { (size * scale).toSp() }, fontWeight = FontWeight.SemiBold)
        val layout = textMeasurer.measure(text, style)
        val topLeft = Offset(center.x * scale - layout.size.width / 2f, center.y * scale - layout.size.height / 2f)
        scope.drawText(layout, topLeft = topLeft)
    }

    private fun roundRect(left: Float, top: Float, width: Float, height: Float, corner: Float, color: Color, stroke: Stroke? = null) {
        val topLeft = Offset(left * scale, top * scale)
        val size = Size(width * scale, height * scale)
        val radius = CornerRadius(corner * scale)
        if (stroke == null) scope.drawRoundRect(color, topLeft, size, radius)
        else scope.drawRoundRect(color, topLeft, size, radius, style = stroke)
    }
}

// Bildgröße in Bild-Einheiten (viewBox des Entwurfs). Auf ca. 316 dp Breite ist eine Einheit knapp 1 dp
private const val PICTURE_WIDTH = 320f
private const val PICTURE_HEIGHT = 196f

// Gehäuse und Bildschirm (Entwurf)
private const val BODY_LEFT = 6f
private const val BODY_TOP = 28f
private const val BODY_WIDTH = 308f
private const val BODY_HEIGHT = 122f
private const val BODY_CORNER = 38f
private const val SCREEN_LEFT = 92f
private const val SCREEN_TOP = 38f
private const val SCREEN_WIDTH = 136f
private const val SCREEN_HEIGHT = 102f
private const val SCREEN_CORNER = 8f

// Strichstärken und Schriftgrößen in Bild-Einheiten (Entwurf: Rand 1,2, Ring 2,4, Schrift 7 bzw. 9)
private const val OUTLINE_WIDTH = 1.2f
private const val RING_WIDTH = 2.4f
private const val RING_GLOW_WIDTH = 6f      // breiter, blasser Ring darunter = Leuchten (Entwurf: drop-shadow 5)
private const val RING_GLOW_ALPHA = 0.35f
private const val SMALL_TEXT = 7f
private const val BIG_TEXT = 9f

// Lage der freien Beschriftungen unter dem Gehäuse
private val BACK_SIDE_LABEL = Offset(160f, 169f)
private val M1_TARGET_LABEL = Offset(63f, 190f)
private val M2_TARGET_LABEL = Offset(257f, 190f)

// Farben (Entwurf A2: Flächen weiß 8 %, Ränder weiß 35 %, Gehäuse weiß 4 % mit Rand 28 %)
private val PART_FILL = Color.White.copy(alpha = 0.08f)
private val PART_OUTLINE = Color.White.copy(alpha = 0.35f)
private val BODY_FILL = Color.White.copy(alpha = 0.04f)
private val BODY_OUTLINE = Color.White.copy(alpha = 0.28f)
private val SCREEN_FILL = Color.Black.copy(alpha = 0.45f)
private val LABEL_COLOR = Color.White.copy(alpha = 0.75f)
private val LABEL_COLOR_DIM = Color.White.copy(alpha = 0.55f)
