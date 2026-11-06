package com.sticklab.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Profil-Zeile oben in jedem Reiter (Entscheidung 31. Sitzung, ersetzt "StickLab · Stick settings for the AYN Odin 3"):
// links das aktive Profil mit Farbe und Marke active/changed (bzw. "No profile active" / "No profile yet"),
// rechts "Save to profile" (übernimmt die Werte am Odin ins aktive Profil, mit Nachfrage alt → neu) und "?"
// (Erklärung "How profiles work"). So sieht man in jedem Reiter, zu welchem Profil die Einstellungen gehören.
// Genauso hoch wie die alte Kopfzeile, damit sich die Karten in keinem Reiter verschieben
@Composable
fun ProfileBar(profiles: ProfilesUi, onSaveToProfile: () -> Unit, onHelp: () -> Unit) {
    val activeSlot = profiles.activeSlot
    val active = activeSlot?.let { profiles.slots.getOrNull(it) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BAR_GAP),
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .padding(start = 4.dp, bottom = BAR_BOTTOM_GAP),
    ) {
        if (active == null) {
            Text(
                text = stringResource(
                    if (profiles.slots.all { it == null }) R.string.profile_bar_none_yet else R.string.profiles_none_active,
                ),
                fontSize = BAR_NAME_SIZE,
                modifier = Modifier.alpha(StickLabDimens.DIMMED_ALPHA),
            )
        } else {
            Box(modifier = Modifier.size(BAR_DOT_SIZE).background(Color(active.color.argb), CircleShape))
            Text(
                text = active.name,
                fontSize = BAR_NAME_SIZE,
                lineHeight = BAR_NAME_LINE_HEIGHT,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ProfileBadgeChip(ProfileSlots.badgeOf(activeSlot, active, activeSlot, profiles.odinValues))
        }
        Spacer(modifier = Modifier.weight(1f))
        if (active != null) {
            StickLabButton(text = stringResource(R.string.profile_save_to), onClick = onSaveToProfile, compact = true)
        }
        StickLabButton(text = stringResource(R.string.profile_help_button), onClick = onHelp, compact = true)
    }
}

// Höhe genau wie die alte Kopfzeile (App-Name 22 sp = 24 dp Zeile + Ränder oben 4 / unten 10 = 38 dp): 32 dp für
// die flachen Knöpfe + 6 dp Abstand zur Karte.
// Name 17 sp wie eine Kartenüberschrift
private val BAR_HEIGHT = 38.dp
private val BAR_BOTTOM_GAP = 6.dp
private val BAR_GAP = 10.dp
private val BAR_DOT_SIZE = 12.dp
private val BAR_NAME_SIZE = 17.sp
private val BAR_NAME_LINE_HEIGHT = 22.sp
