package com.sticklab.app

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Reiter "Profiles" (Phase 5a, Entwurf PROFILE_ENTWURF.html, vom Besitzer bestätigt 26.10.2026):
// eine Karte über die ganze Breite, genau 317 dp hoch (passt quer ohne Scrollen). Oben Überschrift, Stand
// ("Active: …") und "Restore everything", darunter 4 feste Plätze, unten die Knöpfe für den gewählten Platz.
// Bedienung per Touch und mit Steuerkreuz + A. Am Odin setzen nur "Use", "Edit" und "Restore everything"
// (5b-1, über ProfilesController); alle anderen Knöpfe ändern nur die eigene Datei
@Composable
fun ProfilesTab(profiles: ProfilesUi, layout: ControllerLayout, actions: MainActions) {
    // Beim Öffnen des Reiters die Werte am Odin neu lesen (Marke "changed", z. B. nach Apply im Reiter Sticks)
    LaunchedEffect(Unit) { actions.onProfilesShown() }
    SettingsCard(maxWidth = Dp.Unspecified, modifier = Modifier.height(PROFILE_CARD_HEIGHT)) {
        ProfilesHeader(profiles, onRestoreEverything = actions::onProfileRestoreEverything)
        Row(
            horizontalArrangement = Arrangement.spacedBy(SLOT_GAP),
            modifier = Modifier
                .padding(top = SLOTS_TOP_GAP)
                .weight(1f)
                .fillMaxWidth()
                .enterStaggered(0),
        ) {
            profiles.slots.forEachIndexed { slot, profile ->
                ProfileSlot(
                    profile = profile,
                    selected = slot == profiles.selectedSlot,
                    badge = ProfileSlots.badgeOf(slot, profile, profiles.activeSlot, profiles.odinValues),
                    layout = layout,
                    onSelect = { actions.onProfileSlotSelected(slot) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        ProfileButtons(profiles, actions, Modifier.padding(top = BUTTONS_TOP_GAP).enterStaggered(1))
    }
}

// Kopfzeile: Überschrift + Stand, rechts "Restore everything" (P5, mit Nachfrage, seit 5b-1 wirksam)
@Composable
private fun ProfilesHeader(profiles: ProfilesUi, onRestoreEverything: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            CardTitle(R.string.tab_profiles)
            Text(
                text = buildAnnotatedString {
                    append(stateText(profiles))
                    // Einmal für alle Plätze: der Hinweis, wenn das
                    // aktive Profil eine Kurve hat und der Curve mode aus ist
                    curveNote(profiles)?.let { note ->
                        withStyle(SpanStyle(color = Color.White.copy(alpha = LATER_ALPHA))) { append(stringResource(note)) }
                    }
                },
                fontSize = StickLabDimens.LabelSize,
                lineHeight = NOW_LINE_HEIGHT,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp).alpha(NOW_TEXT_ALPHA),
            )
        }
        StickLabButton(text = stringResource(R.string.profile_restore_everything), onClick = onRestoreEverything)
    }
}

// Hinweis hinter dem Stand, wenn das aktive Profil den Curve mode
// bräuchte und er aus ist
@StringRes
private fun curveNote(profiles: ProfilesUi): Int? {
    val active = profiles.activeSlot?.let { profiles.slots.getOrNull(it) } ?: return null
    return if (active.needsCurveMode && !profiles.curveModeRunning) R.string.profiles_curve_mode_off else null
}

// Stand: noch keine Profile / aktiv (ggf. "am Odin geändert") / keins aktiv
@Composable
private fun stateText(profiles: ProfilesUi): String {
    val active = profiles.activeSlot?.let { profiles.slots.getOrNull(it) }
    return when {
        profiles.slots.all { it == null } -> stringResource(R.string.profiles_none_yet)
        active == null -> stringResource(R.string.profiles_none_active)
        profiles.odinValues?.matches(active) == false -> stringResource(R.string.profiles_active_changed, active.name)
        else -> stringResource(R.string.profiles_active, active.name)
    }
}

// Ein Platz: Profil mit Farbe, Name, Marke und Werten – oder gestrichelt "+ New". Antippen (oder A) wählt ihn
@Composable
private fun ProfileSlot(
    profile: Profile?,
    selected: Boolean,
    badge: SlotBadge,
    layout: ControllerLayout,
    onSelect: () -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) SLOT_FOCUS_SCALE else 1f, StickLabMotion.bouncy(), label = "slotScale")
    val shape = RoundedCornerShape(SLOT_CORNER)
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .focusRing(visible = selected || focused, shape = shape)
            .clip(shape)
            .background(slotFill(profile, selected))
            .then(if (profile == null) Modifier.dashedBorder(SLOT_CORNER) else Modifier.border(1.dp, SLOT_BORDER, shape))
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onSelect)
            .padding(horizontal = SLOT_PADDING_H, vertical = SLOT_PADDING_V),
    ) {
        if (profile == null) EmptySlotContent() else FilledSlotContent(profile, badge, layout)
    }
}

// Belegter Platz: Farbpunkt + Name, Marke, Werte, M1/M2 farbig wie im Reiter Buttons (je eine eigene Zeile,
// damit auch lange Namen wie APP_SWITCH nie umbrechen – Fund Besitzer 26.10.2026: die gemeinsame Zeile war
// abgeschnitten), ganz unten die Kurve grau
@Composable
private fun FilledSlotContent(profile: Profile, badge: SlotBadge, layout: ControllerLayout) {
    val text = ProfileText(LocalResources.current)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(COLOR_DOT_SIZE).background(Color(profile.color.argb), CircleShape))
            Text(
                text = profile.name,
                fontSize = SLOT_NAME_SIZE,
                lineHeight = SLOT_NAME_LINE_HEIGHT,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ProfileBadgeChip(badge, Modifier.padding(top = BADGE_TOP_GAP))
        Column(modifier = Modifier.padding(top = VALUES_TOP_GAP)) {
            ValueRow(stringResource(R.string.profile_label_deadzone), text.deadzone(profile.deadzone))
            ValueRow(stringResource(R.string.profile_label_trigger), text.trigger(profile.triggerDeadzone))
            ValueRow(stringResource(R.string.profile_label_sensitivity), text.sensitivity(profile.sensitivity))
            ValueRow(stringResource(R.string.button_m1), text.buttonName(profile.m1, layout), labelColor = StickLabColors.Pink)
            ValueRow(stringResource(R.string.button_m2), text.buttonName(profile.m2, layout), labelColor = StickLabColors.Teal)
            ValueRow(stringResource(R.string.profile_label_curve), text.curve(profile.curves))
        }
    }
}

// Marke "● active" (grün) bzw. "● changed" (orange, P9). Ohne Marke bleibt der Platz frei, damit die Werte in
// allen Plätzen auf gleicher Höhe stehen. Auch in der Profil-Zeile oben (ProfileBar)
@Composable
fun ProfileBadgeChip(badge: SlotBadge, modifier: Modifier = Modifier) {
    val (label, ink) = when (badge) {
        SlotBadge.ACTIVE -> R.string.profile_badge_active to BADGE_ACTIVE_INK
        SlotBadge.CHANGED -> R.string.profile_badge_changed to BADGE_CHANGED_INK
        SlotBadge.NONE -> R.string.profile_badge_active to Color.Transparent
    }
    Text(
        text = stringResource(label),
        color = ink,
        fontSize = BADGE_TEXT_SIZE,
        lineHeight = BADGE_LINE_HEIGHT,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .alpha(if (badge == SlotBadge.NONE) 0f else 1f)
            .background(ink.copy(alpha = BADGE_FILL_ALPHA), RoundedCornerShape(BADGE_CORNER))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

// Eine Zeile "Deadzone   1.5 / 2.5 %".
// labelColor = farbige Beschriftung (M1 pink, M2 türkis wie im Reiter Buttons), sonst blass weiß
@Composable
private fun ValueRow(label: String, value: String, labelColor: Color? = null) {
    Row {
        Text(
            text = label,
            color = labelColor ?: Color.Unspecified,
            fontSize = VALUE_SIZE,
            lineHeight = VALUE_LINE_HEIGHT,
            fontWeight = if (labelColor != null) FontWeight.SemiBold else null,
            maxLines = 1,
            modifier = Modifier.width(VALUE_LABEL_WIDTH).alpha(if (labelColor != null) 1f else VALUE_LABEL_ALPHA),
        )
        Text(text = value, fontSize = VALUE_SIZE, lineHeight = VALUE_LINE_HEIGHT, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// Leerer Platz: "+ New" und kurz, was "Save current" hier tut
@Composable
private fun EmptySlotContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(text = "+", fontSize = PLUS_SIZE, modifier = Modifier.alpha(PLUS_ALPHA))
        Text(text = stringResource(R.string.profile_new), fontSize = StickLabDimens.BodySize, fontWeight = FontWeight.SemiBold)
        Text(
            text = stringResource(R.string.profile_new_hint),
            fontSize = EMPTY_HINT_SIZE,
            lineHeight = EMPTY_HINT_LINE_HEIGHT,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(StickLabDimens.DIMMED_ALPHA),
        )
    }
}

// Knopfleiste für den gewählten Platz. Jeder Knopf so breit wie sein Text, gleichmäßig verteilt (gleich breite
// Knöpfe würden "Save current" abschneiden). Use setzt das Profil am Odin (kurz ein Haken, wenn alles sitzt),
// Edit = Use + Sprung in Sticks (am aktiven Profil nur der Sprung, 5b-1)
@Composable
private fun ProfileButtons(profiles: ProfilesUi, actions: MainActions, modifier: Modifier) {
    val hasProfile = profiles.selectedProfile != null
    val saved = isRecent(profiles.lastSaveAt, APPLY_CONFIRM_MS)
    val used = isRecent(profiles.lastUseAt, APPLY_CONFIRM_MS)
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = modifier.fillMaxWidth()) {
        StickLabButton(
            text = stringResource(if (used) R.string.profile_used else R.string.profile_use),
            onClick = actions::onProfileUse,
            primary = true,
            enabled = hasProfile,
            confirmed = used,
            dense = true,
        )
        StickLabButton(stringResource(R.string.profile_edit), actions::onProfileEdit, enabled = hasProfile, dense = true)
        StickLabButton(
            text = stringResource(if (saved) R.string.profile_saved else R.string.profile_save_current),
            onClick = actions::onProfileSave,
            confirmed = saved,
            dense = true,
        )
        StickLabButton(stringResource(R.string.profile_rename), actions::onProfileRename, enabled = hasProfile, dense = true)
        StickLabButton(stringResource(R.string.profile_color), actions::onProfileColor, enabled = hasProfile, dense = true)
        StickLabButton(
            text = stringResource(R.string.profile_copy),
            onClick = actions::onProfileCopy,
            enabled = hasProfile && ProfileSlots.firstFreeSlot(profiles.slots) != null,
            dense = true,
        )
        StickLabButton(stringResource(R.string.profile_delete), actions::onProfileDelete, enabled = hasProfile, dense = true)
    }
}

// Füllung eines Platzes: gewählt heller, leer durchsichtig (Entwurf)
private fun slotFill(profile: Profile?, selected: Boolean): Color = when {
    selected -> SLOT_FILL_SELECTED
    profile == null -> Color.Transparent
    else -> StickLabColors.RowFill
}

// Gestrichelter Rand für einen leeren Platz (Compose hat dafür keinen fertigen Rand)
private fun Modifier.dashedBorder(corner: Dp): Modifier = drawBehind {
    val stroke = Stroke(
        width = DASH_WIDTH.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON.toPx(), DASH_OFF.toPx())),
    )
    drawRoundRect(color = DASH_COLOR, style = stroke, cornerRadius = CornerRadius(corner.toPx()))
}

// Maße aus PROFILE_ENTWURF.html (832 × 468 dp, im Browser gemessen 26.10.2026): Karte 317 hoch, Plätze 10 dp Abstand,
// 22er-Ecken, Innenrand 13, Name 15 sp, Werte 12 sp, Marke 11 sp.
// Jeder Text hat eine eigene Zeilenhöhe: ohne sie gibt Compose jeder Zeile 24 sp (Fließtext), dann war die letzte
// Zeile des Platzes abgeschnitten (Foto 26.10.2026, 21:46). Rechnung jetzt:
// Karte 317 = Rand 32 + Kopf 46 + 10 + Platz ca. 175 + 8 + Knöpfe 46.
// Platz 175 = Innenrand 2 × 10 + Name 20 + Marke 4 + 18 + 6 + 6 Werte-Zeilen × 16,5 = 167 (8 dp Luft)
private val PROFILE_CARD_HEIGHT = 317.dp
private val SLOTS_TOP_GAP = 10.dp
private val BUTTONS_TOP_GAP = 8.dp
private val SLOT_GAP = 10.dp
private val SLOT_CORNER = 22.dp
private val SLOT_PADDING_H = 13.dp
private val SLOT_PADDING_V = 10.dp
private const val SLOT_FOCUS_SCALE = 1.03f
private val SLOT_BORDER = Color.White.copy(alpha = 0.08f)
private val SLOT_FILL_SELECTED = Color.White.copy(alpha = 0.11f)
private val COLOR_DOT_SIZE = 12.dp
private val SLOT_NAME_SIZE = 15.sp
private val SLOT_NAME_LINE_HEIGHT = 20.sp
private val BADGE_TOP_GAP = 4.dp
private val BADGE_TEXT_SIZE = 11.sp
private val BADGE_LINE_HEIGHT = 14.sp
private val BADGE_CORNER = 8.dp
private const val BADGE_FILL_ALPHA = 0.18f
private val BADGE_ACTIVE_INK = Color(0xFF7EE29B)
private val BADGE_CHANGED_INK = Color(0xFFFFC46B)
private val VALUES_TOP_GAP = 6.dp
private val VALUE_SIZE = 12.sp
private val VALUE_LINE_HEIGHT = 16.5.sp
private val NOW_LINE_HEIGHT = 18.sp
private val VALUE_LABEL_WIDTH = 60.dp
private const val VALUE_LABEL_ALPHA = 0.55f
private const val LATER_ALPHA = 0.4f
private const val NOW_TEXT_ALPHA = 0.8f
private val PLUS_SIZE = 28.sp
private const val PLUS_ALPHA = 0.85f
private val EMPTY_HINT_SIZE = 11.5.sp
private val EMPTY_HINT_LINE_HEIGHT = 15.5.sp
private val DASH_WIDTH = 1.5.dp
private val DASH_ON = 6.dp
private val DASH_OFF = 5.dp
private val DASH_COLOR = Color.White.copy(alpha = 0.22f)
