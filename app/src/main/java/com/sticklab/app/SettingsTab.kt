package com.sticklab.app

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Reiter "Settings" (Phase 5d-2, Entwurf SETTINGS_ENTWURF.html, Entscheidungen E1–E3 vom 27.10.2026).
// Links die Karte "Quick Settings tile": Vorschau der Kachel, was Tippen / langes Drücken / "changed" bedeuten,
// ob die Kachel schon im Schnellmenü ist (Merker der Kachel, E2) und der Knopf "Add tile" (Androids eigenes
// Fenster, MainActivity.onAddTile). Rechts die Schritte von Hand (Stift im Schnellmenü) – gilt auch, falls Androids
// Fenster nicht kommt. Darüber seit 06.11.2026 die Wahl des Hintergrunds (Wunsch Besitzer).
// Quer ohne Scrollen; jeder Text mit eigener Zeilenhöhe (sonst 24 sp, Abschnitt 6, 31. Sitzung)
@Composable
fun SettingsTab(
    activeProfileName: String?,
    tileAdded: Boolean,
    onAddTile: () -> Unit,
    background: AppBackground,
    onBackgroundChosen: (AppBackground) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(StickLabDimens.ColumnGap), modifier = Modifier.fillMaxSize()) {
        SettingsCard(maxWidth = Dp.Unspecified, modifier = Modifier.weight(TILE_CARD_WEIGHT).fillMaxHeight()) {
            TileCard(activeProfileName, tileAdded, onAddTile)
        }
        Column(verticalArrangement = Arrangement.spacedBy(RIGHT_CARD_GAP), modifier = Modifier.weight(1f).fillMaxHeight()) {
            SettingsCard(maxWidth = Dp.Unspecified) {
                BackgroundCard(background, onBackgroundChosen)
            }
            SettingsCard(maxWidth = Dp.Unspecified, modifier = Modifier.weight(1f)) {
                ByHandCard()
            }
        }
    }
}

// Karte "Background": Titel und je eine Kachel pro Hintergrund
@Composable
private fun BackgroundCard(selected: AppBackground, onChosen: (AppBackground) -> Unit) {
    CardTitle(R.string.settings_background_title)
    Row(
        horizontalArrangement = Arrangement.spacedBy(BACKGROUND_TILE_GAP),
        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
    ) {
        for (background in AppBackground.entries) {
            BackgroundTile(background, background == selected, { onChosen(background) }, Modifier.weight(1f))
        }
    }
}

// Eine Kachel: kleines Bild des Hintergrunds, darunter sein Name. Gewählt = weiß, wie die Kurven-Kacheln. Mit Fokus etwas
// größer und mit Ring, damit auch die Bedienung mit dem Controller sichtbar ist
@Composable
private fun BackgroundTile(background: AppBackground, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) StickLabMotion.FOCUS_SCALE else 1f, StickLabMotion.bouncy(), label = "backgroundTileScale")
    val shape = RoundedCornerShape(BACKGROUND_TILE_CORNER)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .focusRing(visible = focused, shape = shape)
            .clip(shape)
            .background(if (selected) Color.White else StickLabColors.RowFill)
            .border(1.dp, StickLabColors.GlassBorder, shape)
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick)
            .padding(BACKGROUND_TILE_PADDING),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(BACKGROUND_PICTURE_HEIGHT)
                .clip(RoundedCornerShape(BACKGROUND_PICTURE_CORNER)),
        ) {
            drawRect(StickLabColors.Night)
            drawColorSpots(background)
        }
        Text(
            text = stringResource(background.nameRes),
            color = if (selected) StickLabColors.InkOnLight else StickLabColors.Ink,
            fontSize = BACKGROUND_NAME_SIZE,
            lineHeight = BACKGROUND_NAME_LINE_HEIGHT,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

// Linke Karte: Titel, Untertitel, Vorschau, drei Erklärzeilen, unten bündig Stand und Knopf in einer Zeile, ganz unten der
// Gerätestand
@Composable
private fun ColumnScope.TileCard(
    activeProfileName: String?,
    tileAdded: Boolean,
    onAddTile: () -> Unit,
) {
    CardTitle(R.string.settings_tile_title)
    Text(
        text = stringResource(R.string.settings_tile_subtitle),
        fontSize = StickLabDimens.SubtitleSize,
        lineHeight = SMALL_LINE_HEIGHT,
        modifier = Modifier.alpha(StickLabDimens.DIMMED_ALPHA),
    )
    TilePreview(activeProfileName ?: stringResource(R.string.tile_no_profile_active), Modifier.enterStaggered(0))
    TileFact(R.string.settings_tile_tap_label, R.string.settings_tile_tap)
    TileFact(R.string.settings_tile_long_label, R.string.settings_tile_long)
    TileFact(R.string.settings_tile_changed_label, R.string.settings_tile_changed)
    Spacer(Modifier.weight(1f))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        TileState(tileAdded, Modifier.weight(1f))
        StickLabButton(
            text = stringResource(if (tileAdded) R.string.settings_tile_added_button else R.string.settings_tile_add),
            onClick = onAddTile,
            primary = !tileAdded,
            enabled = !tileAdded,
            modifier = Modifier.enterStaggered(1),
        )
    }
    DeviceState()
}

// Vorschau der Kachel, wie sie im Schnellmenü des Odin aussieht (Foto 27.10.2026: helle Pille, Symbol links,
// Name, darunter "StickLab")
@Composable
private fun TilePreview(name: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .padding(vertical = PREVIEW_MARGIN)
            .width(PREVIEW_WIDTH)
            .height(PREVIEW_HEIGHT)
            .background(PREVIEW_FILL, RoundedCornerShape(PREVIEW_HEIGHT / 2))
            .padding(horizontal = 18.dp),
    ) {
        Icon(StickLabIcons.Profiles, contentDescription = null, tint = PREVIEW_INK, modifier = Modifier.size(22.dp))
        Column {
            Text(name, color = PREVIEW_INK, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.tile_subtitle), color = PREVIEW_INK.copy(alpha = 0.7f), fontSize = 12.5.sp, lineHeight = 15.sp, maxLines = 1)
        }
    }
}

// Eine Erklärzeile: fettes Stichwort in fester Breite, daneben der Satz
@Composable
private fun TileFact(@StringRes label: Int, @StringRes text: Int) {
    Row(modifier = Modifier.padding(bottom = FACT_GAP)) {
        Text(
            text = stringResource(label),
            fontSize = FACT_TEXT_SIZE,
            lineHeight = SMALL_LINE_HEIGHT,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(FACT_LABEL_WIDTH),
        )
        Text(
            text = stringResource(text),
            fontSize = FACT_TEXT_SIZE,
            lineHeight = SMALL_LINE_HEIGHT,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(FACT_ALPHA),
        )
    }
}

// Stand: grüner Punkt "ist im Schnellmenü" bzw. oranger Punkt "noch nicht"
@Composable
private fun TileState(tileAdded: Boolean, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = modifier,
    ) {
        Box(Modifier.size(8.dp).background(if (tileAdded) StickLabColors.Success else StickLabColors.Warning, CircleShape))
        Text(
            text = stringResource(if (tileAdded) R.string.settings_tile_added else R.string.settings_tile_not_added),
            fontSize = FACT_TEXT_SIZE,
            lineHeight = SMALL_LINE_HEIGHT,
        )
    }
}

// Rechte Karte unten: drei nummerierte Schritte mit dem Stift im Schnellmenü und der Hinweis
@Composable
private fun ColumnScope.ByHandCard() {
    CardTitle(R.string.settings_hand_title)
    Spacer(Modifier.height(8.dp))
    HandStep(1, stringResource(R.string.settings_hand_step1))
    HandStep(2, stringResource(R.string.settings_hand_step2))
    HandStep(3, stringResource(R.string.settings_hand_step3, stringResource(R.string.tile_label)))
    Text(
        text = stringResource(R.string.settings_hand_hint),
        fontSize = StickLabDimens.HintSize,
        lineHeight = 16.sp,
        modifier = Modifier.alpha(HINT_ALPHA),
    )
}

// Stand der Geräteprüfung (Plan Regel 7): grüner Punkt = alles bekannt, oranger Punkt = eingeschränkt
@Composable
private fun DeviceState() {
    val support = DeviceCheck.current
    val model = DeviceCheck.currentModel
    val firmware = DeviceCheck.currentFirmware ?: stringResource(R.string.deadzone_unknown)
    val text = when (support) {
        DeviceSupport.KNOWN -> stringResource(R.string.settings_device_known, model, firmware)
        DeviceSupport.UNKNOWN_FIRMWARE -> stringResource(R.string.settings_device_new_firmware, model, firmware)
        DeviceSupport.UNKNOWN_DEVICE -> stringResource(R.string.settings_device_unknown, model)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        val dot = if (support == DeviceSupport.KNOWN) StickLabColors.Success else StickLabColors.Warning
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Text(text, fontSize = StickLabDimens.HintSize, lineHeight = 16.sp)
    }
}

// Ein Schritt: Nummer im Kreis, daneben der Satz
@Composable
private fun HandStep(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = STEP_GAP)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(STEP_DOT).background(STEP_DOT_FILL, CircleShape),
        ) {
            Text(number.toString(), fontSize = 11.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(text, fontSize = FACT_TEXT_SIZE, lineHeight = SMALL_LINE_HEIGHT, modifier = Modifier.alpha(FACT_ALPHA))
    }
}

// Maße aus SETTINGS_ENTWURF.html (im Browser gemessen: beide Karten passen in 317 dp, linke 416, rechte 283 breit)
private const val TILE_CARD_WEIGHT = 1.55f
private val PREVIEW_WIDTH = 250.dp
private val PREVIEW_HEIGHT = 52.dp                // Entwurf 64, kleiner seit dem Gerätestand in dieser Karte (auf Deutsch sonst abgeschnitten, Foto 06.11.2026)
private val PREVIEW_MARGIN = 6.dp                 // Entwurf 10, aus demselben Grund
private val PREVIEW_FILL = Color(0xFFE6E6E6)   // Kachel-Farbe im Schnellmenü des Odin (Foto 27.10.2026)
private val PREVIEW_INK = Color(0xFF111111)
private val FACT_TEXT_SIZE = 12.5.sp
private val SMALL_LINE_HEIGHT = 17.5.sp           // Entwurf: line-height 1.4 × 12,5
private val FACT_LABEL_WIDTH = 74.dp
private val FACT_GAP = 5.dp
private const val FACT_ALPHA = 0.85f
private const val HINT_ALPHA = 0.55f
private val STEP_DOT = 22.dp
private val STEP_DOT_FILL = Color.White.copy(alpha = 0.12f)
private val STEP_GAP = 6.dp

// Rechte Spalte seit dem Hintergrund zweigeteilt: Kacheln so klein, dass die Karte darunter noch ganz passt (Foto am Odin)
private val RIGHT_CARD_GAP = 10.dp
private val BACKGROUND_TILE_GAP = 6.dp
private val BACKGROUND_TILE_CORNER = 12.dp
private val BACKGROUND_TILE_PADDING = 4.dp
private val BACKGROUND_PICTURE_HEIGHT = 30.dp
private val BACKGROUND_PICTURE_CORNER = 8.dp
private val BACKGROUND_NAME_SIZE = 10.5.sp
private val BACKGROUND_NAME_LINE_HEIGHT = 13.sp

// Vorschau in Android Studio (Größe wie der Inhalt quer auf dem Odin)
@Preview(widthDp = 716, heightDp = 325)
@Composable
private fun SettingsTabPreview() {
    StickLabTheme {
        SettingsTab(
            activeProfileName = "Standart",
            tileAdded = false,
            onAddTile = {},
            background = AppBackground.DEFAULT,
            onBackgroundChosen = {},
        )
    }
}
