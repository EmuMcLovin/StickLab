package com.sticklab.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Reiter "Buttons" (Phase 4, Entwurf BUTTONS_ENTWURF.html, vom Besitzer bestätigt 26.10.2026):
// links das Bild des Odin (nur Anzeige), rechts die Auswahl für M1/M2 mit "Apply" und eigenem
// "Restore original" (Entscheidung B4: getrennt von der Deadzone). Passt quer ohne Scrollen.
// Nur per Touch (Entscheidung 26.10.2026): Die Controller-Tasten fängt MainActivity hier ab, sie leuchten nur im
// Bild – so verstellt das Ausprobieren einer Belegung (z. B. M1 auf RIGHT) nie die Auswahl.
// Antippen einer Zeile öffnet die Kachel-Übersicht mit allen 22 Einträgen über dem ganzen Reiter
@Composable
fun ButtonsTab(
    layout: ControllerLayout,
    applied: Map<BackButton, ButtonTarget>,
    chosen: Map<BackButton, ButtonTarget>,
    pressedParts: () -> Set<OdinPart>,
    restoreEnabled: Boolean,
    lastApplySuccessAt: Long,
    onStep: (BackButton, Int) -> Unit,
    onChoose: (BackButton, ButtonTarget) -> Unit,
    onApply: () -> Unit,
    onRestore: () -> Unit,
) {
    // Für welche Taste die Kachel-Übersicht offen ist (null = zu). Nur Anzeige-Zustand: Beim Reiterwechsel
    // geht sie wieder zu, gewählt ist erst, was in chosen steht
    var picking by remember { mutableStateOf<BackButton?>(null) }
    // Der Reiter hält die Auswahl selbst (unsichtbar, ohne Ring). Sonst setzt Android sie beim ersten
    // Controller-Tastendruck aus dem Touch-Modus auf das erste Element, das oberste Symbol der Leiste (Lauf B3, 26.10.2026) –
    // das sähe nach Tastenbedienung aus, obwohl hier nur Touch gilt
    val holdFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { holdFocus.requestFocus() }
    Box(modifier = Modifier.focusRequester(holdFocus).focusable()) {
        val pickingNow = picking
        if (pickingNow != null) {
            ButtonTargetPicker(
                button = pickingNow,
                current = chosen[pickingNow],
                layout = layout,
                onChoose = { target -> onChoose(pickingNow, target); picking = null },
                onClose = { picking = null },
            )
        } else {
            ButtonsCards(
                layout, applied, chosen, pressedParts, restoreEnabled, lastApplySuccessAt,
                onStep, onOpenPicker = { picking = it }, onApply, onRestore,
            )
        }
    }
}

// Die beiden Karten nebeneinander: links das Bild, rechts die Auswahl
@Composable
private fun ButtonsCards(
    layout: ControllerLayout,
    applied: Map<BackButton, ButtonTarget>,
    chosen: Map<BackButton, ButtonTarget>,
    pressedParts: () -> Set<OdinPart>,
    restoreEnabled: Boolean,
    lastApplySuccessAt: Long,
    onStep: (BackButton, Int) -> Unit,
    onOpenPicker: (BackButton) -> Unit,
    onApply: () -> Unit,
    onRestore: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(StickLabDimens.ColumnGap)) {
        OdinPictureCard(layout, chosen, pressedParts, Modifier.width(PICTURE_CARD_WIDTH))
        ButtonMappingCard(
            layout = layout,
            applied = applied,
            chosen = chosen,
            restoreEnabled = restoreEnabled,
            lastApplySuccessAt = lastApplySuccessAt,
            onStep = onStep,
            onOpenPicker = onOpenPicker,
            onApply = onApply,
            onRestore = onRestore,
            modifier = Modifier.weight(1f),
        )
    }
}

// Karte "Your Odin": Layout, Bild, Legende (Wunsch Besitzer: nicht antippbar)
@Composable
private fun OdinPictureCard(
    layout: ControllerLayout,
    chosen: Map<BackButton, ButtonTarget>,
    pressedParts: () -> Set<OdinPart>,
    modifier: Modifier,
) {
    SettingsCard(modifier = modifier) {
        CardTitle(R.string.buttons_picture_title)
        Text(
            text = stringResource(R.string.buttons_layout, stringResource(layoutName(layout))),
            fontSize = StickLabDimens.LabelSize,
            modifier = Modifier.padding(top = 2.dp).alpha(NOW_TEXT_ALPHA),
        )
        OdinPicture(
            layout = layout,
            chosen = chosen,
            pressedParts = pressedParts,
            modifier = Modifier.padding(top = PICTURE_TOP_GAP).enterStaggered(0),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(LEGEND_GAP),
            modifier = Modifier.padding(top = 4.dp).enterStaggered(1),
        ) {
            LegendDot(StickLabColors.Pink, stringResource(R.string.button_m1))
            LegendDot(StickLabColors.Teal, stringResource(R.string.button_m2))
            LegendDot(Color.White, stringResource(R.string.buttons_legend_pressed))
        }
    }
}

// Ein Punkt der Legende mit Beschriftung
@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(LEGEND_DOT_SIZE).background(color, CircleShape))
        Text(text = text, fontSize = StickLabDimens.HintSize, modifier = Modifier.alpha(LEGEND_ALPHA))
    }
}

// Karte "M1 / M2": was gerade eingestellt ist, je eine Auswahl-Zeile, Hinweis "nur Touch", Apply + Restore original
@Composable
private fun ButtonMappingCard(
    layout: ControllerLayout,
    applied: Map<BackButton, ButtonTarget>,
    chosen: Map<BackButton, ButtonTarget>,
    restoreEnabled: Boolean,
    lastApplySuccessAt: Long,
    onStep: (BackButton, Int) -> Unit,
    onOpenPicker: (BackButton) -> Unit,
    onApply: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier,
) {
    val nowText = stringResource(
        R.string.buttons_now,
        nameOrDash(applied[BackButton.M1], layout),
        nameOrDash(applied[BackButton.M2], layout),
    )
    SettingsCard(modifier = modifier) {
        CardTitle(R.string.buttons_title)
        Text(
            text = nowText,
            fontSize = StickLabDimens.LabelSize,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp).alpha(NOW_TEXT_ALPHA).popOnChange(nowText),
        )
        BackButton.entries.forEachIndexed { index, button ->
            TargetStepRow(
                button = button,
                target = chosen[button],
                layout = layout,
                onStep = { delta -> onStep(button, delta) },
                onOpenPicker = { onOpenPicker(button) },
                modifier = Modifier.padding(top = StickLabDimens.SmallGap).enterStaggered(index),
            )
        }
        Text(
            text = stringResource(
                if (layout == ControllerLayout.UNKNOWN) R.string.buttons_hint_touch_unknown else R.string.buttons_hint_touch,
            ),
            fontSize = StickLabDimens.HintSize,
            modifier = Modifier.padding(top = StickLabDimens.SmallGap).alpha(StickLabDimens.DIMMED_ALPHA),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(StickLabDimens.ButtonGap),
            modifier = Modifier.padding(top = StickLabDimens.ButtonGap).enterStaggered(2),
        ) {
            val confirmed = isRecent(lastApplySuccessAt, APPLY_CONFIRM_MS)
            StickLabButton(
                text = stringResource(if (confirmed) R.string.applied else R.string.apply),
                onClick = onApply,
                primary = true,
                confirmed = confirmed,
                modifier = Modifier.weight(1f),
            )
            StickLabButton(
                text = stringResource(R.string.restore_original),
                onClick = onRestore,
                enabled = restoreEnabled,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// Eine Auswahl-Zeile "● M1  ‹  B  ›" (nur Touch): Pfeile = ein Eintrag weiter/zurück,
// Antippen der Mitte öffnet die Kachel-Übersicht
@Composable
private fun TargetStepRow(
    button: BackButton,
    target: ButtonTarget?,
    layout: ControllerLayout,
    onStep: (Int) -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier,
) {
    val name = nameOrDash(target, layout)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ROW_CORNER))
            .background(StickLabColors.RowFill)
            .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Box(modifier = Modifier.size(LEGEND_DOT_SIZE).background(dotColorOf(button), CircleShape))
        Text(text = stringResource(buttonName(button)), fontSize = StickLabDimens.BodySize, fontWeight = FontWeight.SemiBold)
        TapArea(onTap = { onStep(-1) }) { StepArrow("‹") }
        TapArea(onTap = onOpenPicker, modifier = Modifier.weight(1f)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = name,
                    fontSize = TARGET_NAME_SIZE,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.popOnChange(name),
                )
                Text(
                    text = target?.let { smallTextOf(it, layout) } ?: "",
                    fontSize = TARGET_SMALL_SIZE,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(StickLabDimens.DIMMED_ALPHA),
                )
            }
        }
        TapArea(onTap = { onStep(1) }) { StepArrow("›") }
    }
}

// Runder Pfeil, groß genug für den Finger (44 dp)
@Composable
private fun StepArrow(symbol: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(ARROW_SIZE).background(StickLabColors.ButtonFill, CircleShape),
    ) {
        Text(text = symbol, fontSize = ARROW_TEXT_SIZE, modifier = Modifier.alpha(ARROW_ALPHA))
    }
}

// Bereich, der nur auf Antippen reagiert und nie den Fokus bekommt (Reiter Buttons ist nur per Touch bedienbar)
@Composable
private fun TapArea(onTap: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val currentOnTap by rememberUpdatedState(onTap)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.pointerInput(Unit) { detectTapGestures { currentOnTap() } },
    ) {
        content()
    }
}

// Kachel-Übersicht: alle 22 Einträge in Menü-Reihenfolge, 6 je Reihe, der gewählte weiß. Antippen wählt
// (gesetzt wird erst mit "Apply"), "✕" schließt ohne Änderung. Liegt über dem ganzen Reiter, damit alle
// Kacheln ohne Scrollen passen (4 Reihen à 50 dp)
@Composable
private fun ButtonTargetPicker(
    button: BackButton,
    current: ButtonTarget?,
    layout: ControllerLayout,
    onChoose: (ButtonTarget) -> Unit,
    onClose: () -> Unit,
) {
    SettingsCard(maxWidth = Dp.Unspecified) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(LEGEND_DOT_SIZE).background(dotColorOf(button), CircleShape))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.buttons_picker_title, stringResource(buttonName(button))),
                fontSize = StickLabDimens.CardTitleSize,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TapArea(onTap = onClose) { StepArrow("✕") }
        }
        ButtonTarget.entries.chunked(PICKER_COLUMNS).forEachIndexed { rowIndex, rowTargets ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(PICKER_GAP),
                modifier = Modifier.padding(top = PICKER_GAP).enterStaggered(rowIndex),
            ) {
                for (target in rowTargets) {
                    PickerTile(target, target == current, layout, { onChoose(target) }, Modifier.weight(1f))
                }
                // Leere Plätze in der letzten Reihe, damit alle Kacheln gleich breit bleiben
                repeat(PICKER_COLUMNS - rowTargets.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

// Eine Kachel: Name nach Aufdruck, bei A/B/X/Y im Xbox-Layout klein der Xbox-Name
@Composable
private fun PickerTile(target: ButtonTarget, selected: Boolean, layout: ControllerLayout, onTap: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(TILE_CORNER)
    val textColor = if (selected) StickLabColors.InkOnLight else StickLabColors.Ink
    TapArea(onTap = onTap, modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(TILE_HEIGHT)
                .clip(shape)
                .background(if (selected) Color.White else StickLabColors.RowFill)
                .border(1.dp, StickLabColors.GlassBorder, shape),
        ) {
            Text(text = ButtonLayoutMap.printedName(target, layout), color = textColor, fontSize = TILE_NAME_SIZE, fontWeight = FontWeight.SemiBold)
            if (target.isFaceButton && layout == ControllerLayout.XBOX) {
                Text(
                    text = stringResource(R.string.target_xbox_short, target.menuName),
                    color = textColor,
                    fontSize = TARGET_SMALL_SIZE,
                    modifier = Modifier.alpha(StickLabDimens.DIMMED_ALPHA),
                )
            }
        }
    }
}

// Name der Belegung (A/B/X/Y nach Aufdruck), unbekannt → "–"
@Composable
private fun nameOrDash(target: ButtonTarget?, layout: ControllerLayout): String =
    target?.let { ButtonLayoutMap.printedName(it, layout) } ?: stringResource(R.string.deadzone_unknown)

// Kleintext unter dem Namen: was Spiele sehen (Xbox-Name im Xbox-Layout), bzw. Default/None erklärt
@Composable
private fun smallTextOf(target: ButtonTarget, layout: ControllerLayout): String = when {
    target == ButtonTarget.DEFAULT -> stringResource(R.string.target_default)
    target == ButtonTarget.NONE -> stringResource(R.string.target_none)
    target.isFaceButton && layout == ControllerLayout.XBOX -> stringResource(R.string.target_games_see_xbox, target.menuName)
    else -> stringResource(R.string.target_games_see, target.menuName)
}

private fun layoutName(layout: ControllerLayout): Int = when (layout) {
    ControllerLayout.XBOX -> R.string.layout_xbox
    ControllerLayout.ODIN -> R.string.layout_odin
    ControllerLayout.UNKNOWN -> R.string.layout_unknown
}

private fun buttonName(button: BackButton): Int = when (button) {
    BackButton.M1 -> R.string.button_m1
    BackButton.M2 -> R.string.button_m2
}

private fun dotColorOf(button: BackButton): Color = when (button) {
    BackButton.M1 -> StickLabColors.Pink
    BackButton.M2 -> StickLabColors.Teal
}

// Maße aus BUTTONS_ENTWURF.html: Bild-Karte 356 breit (Bild ca. 316 dp), Zeile mit 20er-Ecken, Name 17 sp,
// Kleintext 11,5 sp, Legende 9er-Punkte. Pfeile 44 dp (Entscheidung 26.10.2026: groß genug für den Finger)
private val PICTURE_CARD_WIDTH = 356.dp
private val PICTURE_TOP_GAP = 6.dp
private val LEGEND_GAP = 14.dp
private val LEGEND_DOT_SIZE = 9.dp
private const val LEGEND_ALPHA = 0.85f
private val ROW_CORNER = 20.dp
private val ARROW_SIZE = 44.dp
private val ARROW_TEXT_SIZE = 18.sp
private const val ARROW_ALPHA = 0.8f
private val TARGET_NAME_SIZE = 17.sp
private val TARGET_SMALL_SIZE = 11.5.sp
private const val NOW_TEXT_ALPHA = 0.8f

// Kachel-Übersicht: 22 Einträge in 6 Spalten = 4 Reihen; Kopf 44 + 4 × (8 + 50) + Rand 32 ≈ 308 dp (Platz ca. 317)
private const val PICKER_COLUMNS = 6
private val PICKER_GAP = 8.dp
private val TILE_HEIGHT = 50.dp
private val TILE_CORNER = 16.dp
private val TILE_NAME_SIZE = 15.sp

// Vorschau in Android Studio mit der Belegung des Besitzers (M1 = A → Aufdruck B, M2 = B → Aufdruck A)
@Preview(widthDp = 716)
@Composable
private fun ButtonsTabPreview() {
    val mapping = mapOf(BackButton.M1 to ButtonTarget.A, BackButton.M2 to ButtonTarget.B)
    StickLabTheme {
        ButtonsTab(
            layout = ControllerLayout.XBOX,
            applied = mapping,
            chosen = mapping,
            pressedParts = { setOf(OdinPart.FACE_B) },
            restoreEnabled = true,
            lastApplySuccessAt = 0L,
            onStep = { _, _ -> },
            onChoose = { _, _ -> },
            onApply = {},
            onRestore = {},
        )
    }
}
