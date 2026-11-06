package com.sticklab.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Knopf im Aussehen A2: runde Kapsel.
// primary = hell und gefüllt (z. B. "Apply"), sonst halbdurchsichtig mit Rand (z. B. "Restore original").
// Rückmeldung: mit Fokus etwas größer + Ring mit Leuchten, beim Drücken kleiner (federt), Tipp-Welle.
// confirmed = kurz grün mit Haken (nach erfolgreichem "Apply").
// compact = flacher Knopf für die Profil-Zeile oben (die darf nicht höher sein als die alte Kopfzeile).
// dense = normale Höhe, aber weniger Rand neben dem Text, wenn viele Knöpfe in einer Zeile stehen müssen
@Composable
fun StickLabButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    confirmed: Boolean = false,
    compact: Boolean = false,
    dense: Boolean = false,
) {
    val paddingH = when {
        compact -> COMPACT_PADDING_H
        dense -> DENSE_PADDING_H
        else -> StickLabDimens.ButtonPaddingH
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> StickLabMotion.PRESS_SCALE
            focused -> StickLabMotion.FOCUS_SCALE
            else -> 1f
        },
        animationSpec = StickLabMotion.bouncy(),
        label = "buttonScale",
    )
    val fill by animateColorAsState(buttonFill(primary, confirmed), label = "buttonFill")
    val ink by animateColorAsState(buttonInk(primary, confirmed), label = "buttonInk")
    val shape = RoundedCornerShape(50)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .focusRing(visible = focused, shape = shape)
            .height(if (compact) COMPACT_HEIGHT else StickLabDimens.ButtonHeight)
            .clip(shape)
            .background(fill)
            .border(1.dp, if (primary || confirmed) fill else StickLabColors.ButtonBorder, shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = ink),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = paddingH),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (confirmed) {
                Icon(StickLabIcons.Check, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
            }
            Text(
                text = text,
                color = ink,
                fontSize = if (compact) COMPACT_TEXT_SIZE else StickLabDimens.BodySize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Ausgegrauter Knopf (z. B. "Restore original", solange kein Original gemerkt ist)
private const val DISABLED_ALPHA = 0.4f

// Flacher Knopf (Profil-Zeile oben): 32 dp hoch, so bleibt die Zeile so hoch wie die alte Kopfzeile mit dem
// App-Namen (22 sp + Ränder, ca. 38 dp)
private val COMPACT_HEIGHT = 32.dp
private val COMPACT_PADDING_H = 14.dp
private val DENSE_PADDING_H = 12.dp // Reiter Profiles: 7 Knöpfe passen so auch mit den längeren deutschen Texten (Foto 05.11.2026)
private val COMPACT_TEXT_SIZE = 13.sp

// Füllfarbe: grün nach Bestätigung, weiß für den Hauptknopf, sonst halbdurchsichtig
private fun buttonFill(primary: Boolean, confirmed: Boolean): Color = when {
    confirmed -> StickLabColors.Success
    primary -> Color.White
    else -> StickLabColors.ButtonFill
}

// Schriftfarbe passend zur Füllung
private fun buttonInk(primary: Boolean, confirmed: Boolean): Color = when {
    confirmed -> StickLabColors.OnSuccess
    primary -> StickLabColors.InkOnLight
    else -> StickLabColors.Ink
}
