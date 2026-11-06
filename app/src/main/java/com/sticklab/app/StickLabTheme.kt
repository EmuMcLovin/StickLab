package com.sticklab.app

import androidx.annotation.StringRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Farben des Aussehens A2. Herkunft: DESIGN_ENTWURF.html, Abschnitt A2 (vom Besitzer gewählt 16.10.2026)
object StickLabColors {
    val Night = Color(0xFF07070B)                  // dunkler Grund unter dem Farbverlauf
    val Pink = Color(0xFFFF2D95)                   // Farbfleck oben links
    val Violet = Color(0xFF5E5CE6)                 // Farbfleck rechts
    val Teal = Color(0xFF00C7BE)                   // Farbfleck unten
    val SwitchViolet = Color(0xFF7D5CFF)           // Ende des Verlaufs im eingeschalteten Schalter
    val Ink = Color.White                          // Schrift
    val InkOnLight = Color(0xFF111111)             // Schrift auf weißen Flächen (Kapsel, Apply)
    val Glass = Color.White.copy(alpha = 0.07f)    // halbdurchsichtige Fläche von Karten und Leiste
    val GlassBorder = Color.White.copy(alpha = 0.13f)
    val ButtonFill = Color.White.copy(alpha = 0.08f)
    val ButtonBorder = Color.White.copy(alpha = 0.20f)
    val RowFill = Color.White.copy(alpha = 0.05f)          // Schalter-Zeile
    val RowFillFocused = Color.White.copy(alpha = 0.10f)
    val Track = Color.White.copy(alpha = 0.16f)            // leerer Teil der Regler-Spur
    val Glow = Color(0xFFAA96FF).copy(alpha = 0.5f)        // Leuchten um Fokus-Ringe
    val FocusRing = Color.White.copy(alpha = 0.8f)
    val Success = Color(0xFF30D158)                        // Haken bei Apply, Punkt in der Statuszeile
    val OnSuccess = Color(0xFF03240E)
    val Warning = Color(0xFFFF9F0A)                        // Punkt in der Statuszeile bei Fehlern
    val DialogSurface = Color(0xFF1D1D24)
    val Bubble = Color(0xF21C1C22)                         // Namensblase neben der Leiste
}

// Abstände und Größen. Herkunft: DESIGN_ENTWURF.html A2, gebaut für den Odin-Bildschirm quer
// (ca. 832 × 468 dp, gemessen 16.10.2026 mit wm size/density)
object StickLabDimens {
    val ScreenPadding = 14.dp          // Rand um den ganzen Bildschirm
    val ColumnGap = 14.dp              // Abstand Leiste ↔ Inhalt
    val CardPaddingH = 20.dp           // Innenabstand einer Karte links/rechts
    val CardPaddingV = 16.dp           // Innenabstand einer Karte oben/unten
    val CardCornerRadius = 28.dp
    val CardMaxWidth = 560.dp          // Karte nicht über die ganze Breite ziehen
    // Karte "Live" neben der Deadzone-Karte (Entscheidung L1, 16.10.2026): 2 Kreise à 104 dp + 16 dp Abstand
    // + 2 × 20 dp Innenrand = 264 dp, dazu Luft für die Trigger-Zahlen. Inhalt ca. 716 dp breit
    // (832 dp − Rand − Leiste) → Deadzone-Karte behält ca. 410 dp
    val LiveCardWidth = 330.dp         // Platz für L2/R2 senkrecht neben den Kreisen (25.10.2026, vorher 290)
    val SectionGap = 12.dp             // Abstand vor einer Regler- oder Schalter-Zeile
    val ButtonGap = 14.dp              // Abstand vor einer Knopf-Zeile
    val SmallGap = 8.dp
    val ButtonHeight = 46.dp
    val ButtonPaddingH = 20.dp
    val SubtitleSize = 12.5.sp
    val CardTitleSize = 17.sp
    val BodySize = 14.sp
    val LabelSize = 13.sp
    val HintSize = 12.sp
    const val DIMMED_ALPHA = 0.65f     // blassere Schrift für Untertitel, Hinweise und Statuszeile
}

// Bewegungen. Die Federung ahmt die Kurve "cubic-bezier(.34,1.56,.64,1)" aus dem Entwurf nach:
// schießt leicht über das Ziel hinaus und federt zurück
object StickLabMotion {
    private const val BOUNCY_DAMPING = 0.55f
    fun <T> bouncy() = spring<T>(dampingRatio = BOUNCY_DAMPING, stiffness = Spring.StiffnessMediumLow)
    const val FOCUS_SCALE = 1.05f      // Knopf mit Fokus
    const val PRESS_SCALE = 0.93f      // Knopf beim Drücken
}

// Farben und Schrift der Oberfläche: immer dunkel (wie v1.0), Systemschrift (Entscheidung 16.10.2026)
@Composable
fun StickLabTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}

// Eine Karte: abgerundet, halbdurchsichtig mit feinem Rand. Bewusst ohne "clip",
// damit Fokus-Ringe der Knöpfe am Kartenrand nicht abgeschnitten werden.
// modifier: z. B. Breite, wenn zwei Karten nebeneinander stehen (Reiter Sticks, Phase 3a).
// maxWidth: Höchstbreite; Dp.Unspecified = ganze Breite (Reiter Licht mit Spalten, damit nichts scrollen muss)
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    maxWidth: Dp = StickLabDimens.CardMaxWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(StickLabDimens.CardCornerRadius)
    Column(
        modifier = modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .background(StickLabColors.Glass, shape)
            .border(1.dp, StickLabColors.GlassBorder, shape)
            .padding(horizontal = StickLabDimens.CardPaddingH, vertical = StickLabDimens.CardPaddingV),
        content = content,
    )
}

// Überschrift oben in einer Karte
@Composable
fun CardTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        fontSize = StickLabDimens.CardTitleSize,
        fontWeight = FontWeight.SemiBold,
    )
}
