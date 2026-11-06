package com.sticklab.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Die beiden Fenster des Reiters Profiles (Phase 5a, Entwurf PROFILE_ENTWURF.html): Umbenennen und Farbe.
// Beide ändern nur das Profil in der eigenen Datei, nie etwas am Odin

// Umbenennen (P3): Textfeld mit dem alten Name (ganz markiert, Tippen ersetzt ihn), die Bildschirmtastatur
// von Android öffnet sich von selbst. OK oder "Fertig" auf der Tastatur übernimmt. Höchstens
// Profile.MAX_NAME_LENGTH Zeichen. Wie die Tastatur quer aussieht, ist am Odin zu prüfen (Risiko R118)
@Composable
fun ProfileRenameDialog(currentName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    StickLabDialogFrame(onDismiss = onDismiss) {
        var field by remember { mutableStateOf(TextFieldValue(currentName, TextRange(0, currentName.length))) }
        val fieldFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { fieldFocus.requestFocus() }
        DialogTitle(stringResource(R.string.profile_rename_title))
        BasicTextField(
            value = field,
            onValueChange = { if (it.text.length <= Profile.MAX_NAME_LENGTH) field = it },
            singleLine = true,
            textStyle = TextStyle(color = StickLabColors.Ink, fontSize = NAME_FIELD_TEXT_SIZE),
            cursorBrush = SolidColor(StickLabColors.Ink),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirm(field.text) }),
            modifier = Modifier
                .padding(top = 10.dp, bottom = 16.dp)
                .fillMaxWidth()
                .focusRequester(fieldFocus)
                .background(NAME_FIELD_FILL, RoundedCornerShape(NAME_FIELD_CORNER))
                .border(1.dp, NAME_FIELD_BORDER, RoundedCornerShape(NAME_FIELD_CORNER))
                .padding(horizontal = 14.dp, vertical = 11.dp),
        )
        DialogButtons(onConfirm = { onConfirm(field.text) }, onDismiss = onDismiss)
    }
}

// Farbe (P8): die 8 festen Farben als Kacheln, 4 je Reihe, die jetzige mit Ring. Antippen wählt und schließt
@Composable
fun ProfileColorDialog(current: ProfileColor, onChoose: (ProfileColor) -> Unit, onDismiss: () -> Unit) {
    StickLabDialogFrame(onDismiss = onDismiss) {
        DialogTitle(stringResource(R.string.profile_color_title))
        ProfileColor.entries.chunked(COLOR_COLUMNS).forEach { rowColors ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(COLOR_GAP),
                modifier = Modifier.padding(top = COLOR_GAP),
            ) {
                for (color in rowColors) {
                    ColorTile(color, color == current, onClick = { onChoose(color) }, Modifier.weight(1f))
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        StickLabButton(text = stringResource(R.string.profile_close), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

// Eine Farb-Kachel mit Namen; mit Fokus (Steuerkreuz) oder gewählt mit Ring
@Composable
private fun ColorTile(color: ProfileColor, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(COLOR_TILE_CORNER)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .focusRing(visible = selected || focused, shape = shape)
            .height(COLOR_TILE_HEIGHT)
            .background(Color(color.argb), shape)
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = stringResource(colorName(color)),
            color = COLOR_TILE_INK,
            fontSize = COLOR_TILE_TEXT_SIZE,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DialogTitle(text: String) {
    Text(text = text, fontSize = StickLabDimens.CardTitleSize, fontWeight = FontWeight.SemiBold)
}

// Abbrechen + OK wie in den anderen Nachfragen
@Composable
private fun DialogButtons(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StickLabButton(text = stringResource(android.R.string.cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
        StickLabButton(text = stringResource(android.R.string.ok), onClick = onConfirm, primary = true, modifier = Modifier.weight(1f))
    }
}

// Name einer Farbe (Texte EN/DE)
private fun colorName(color: ProfileColor): Int = when (color) {
    ProfileColor.PINK -> R.string.color_pink
    ProfileColor.VIOLET -> R.string.color_violet
    ProfileColor.BLUE -> R.string.color_blue
    ProfileColor.TURQUOISE -> R.string.color_turquoise
    ProfileColor.GREEN -> R.string.color_green
    ProfileColor.YELLOW -> R.string.color_yellow
    ProfileColor.ORANGE -> R.string.color_orange
    ProfileColor.RED -> R.string.color_red
}

// Maße aus PROFILE_ENTWURF.html: Textfeld 16 sp mit 14er-Ecken, Farb-Kacheln 4 × 2, 54 dp hoch, 16er-Ecken,
// Name dunkel auf der Farbe (11,5 sp)
private val NAME_FIELD_TEXT_SIZE = 16.sp
private val NAME_FIELD_CORNER = 14.dp
private val NAME_FIELD_FILL = Color.White.copy(alpha = 0.06f)
private val NAME_FIELD_BORDER = Color.White.copy(alpha = 0.25f)
private const val COLOR_COLUMNS = 4
private val COLOR_GAP = 10.dp
private val COLOR_TILE_HEIGHT = 54.dp
private val COLOR_TILE_CORNER = 16.dp
private val COLOR_TILE_TEXT_SIZE = 11.5.sp
private val COLOR_TILE_INK = Color.Black.copy(alpha = 0.65f)
