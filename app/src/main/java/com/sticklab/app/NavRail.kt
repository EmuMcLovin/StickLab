package com.sticklab.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Leiste links mit allen Reitern, nur Symbole (Aussehen A2, Besitzer 16.10.2026).
// Die Symbole stehen mit gleichen Abständen über die ganze Höhe (Wunsch Besitzer 06.11.2026: sonst war oben ein großer Bereich leer).
// Eine weiße Kapsel liegt hinter dem gewählten Symbol und gleitet beim Wechsel federnd dorthin.
// Gewechselt wird erst mit A oder Antippen (Entscheidung 16.10.2026), Drüberfahren zeigt nur den Fokus
@Composable
fun NavRail(selected: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(RAIL_CORNER)
    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .width(RAIL_WIDTH)
            .fillMaxHeight()
            .background(StickLabColors.Glass, shape)
            .border(1.dp, StickLabColors.GlassBorder, shape),
    ) {
        // Gleicher Abstand über, zwischen und unter den Symbolen. Kapsel und Symbole rechnen mit demselben Wert
        val tabCount = MainTab.entries.size
        val gap = ((maxHeight - ITEM_SIZE * tabCount) / (tabCount + 1)).coerceAtLeast(0.dp)
        SelectionPill(selected, gap)
        Column(verticalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.padding(top = gap)) {
            for (tab in MainTab.entries) {
                RailItem(tab = tab, selected = tab == selected, onClick = { onSelect(tab) })
            }
        }
    }
}

// Die weiße Kapsel hinter dem gewählten Symbol, mit leichtem Schein
@Composable
private fun SelectionPill(selected: MainTab, gap: Dp) {
    val top by animateDpAsState(gap + (ITEM_SIZE + gap) * selected.ordinal, StickLabMotion.bouncy(), label = "pillTop")
    val glow = with(LocalDensity.current) { PILL_GLOW.toPx() }
    Box(
        modifier = Modifier
            // Lambda-Form: beim Gleiten wird nur verschoben, nicht neu aufgebaut
            .offset { IntOffset(0, top.roundToPx()) }
            .size(ITEM_SIZE)
            .drawBehind { drawCircle(Color.White.copy(alpha = PILL_GLOW_ALPHA), radius = size.minDimension / 2 + glow) }
            .background(Color.White, CircleShape),
    )
}

// Ein Symbol der Leiste. Mit Fokus (Steuerkreuz/Stick) oder Maus darüber: Symbol ×1,4 mit Federung,
// Ring mit Leuchten und der Name als Blase rechts daneben. Beim Drücken ×1,1
@Composable
private fun RailItem(
    tab: MainTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val highlighted = focused || hovered
    val iconScale by animateFloatAsState(
        targetValue = when {
            pressed -> ICON_PRESS_SCALE
            highlighted -> ICON_FOCUS_SCALE
            else -> 1f
        },
        animationSpec = StickLabMotion.bouncy(),
        label = "railIconScale",
    )
    val iconColor by animateColorAsState(
        targetValue = when {
            selected -> StickLabColors.InkOnLight
            highlighted -> Color.White
            else -> Color.White.copy(alpha = ICON_IDLE_ALPHA)
        },
        label = "railIconColor",
    )
    val name = stringResource(tab.title)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(ITEM_SIZE)
            .focusRing(visible = focused, shape = CircleShape, gap = RING_GAP)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = name,
            tint = iconColor,
            modifier = Modifier
                .size(ICON_SIZE)
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
        )
        NameBubble(name = name, visible = highlighted)
    }
}

// Name des Reiters als kleine dunkle Blase rechts neben der Leiste; gleitet beim Erscheinen leicht herein.
// Ragt über die Leiste hinaus in den Inhalt (darum liegt die Leiste im Bildschirm über dem Inhalt)
@Composable
private fun NameBubble(name: String, visible: Boolean) {
    val shown by animateFloatAsState(if (visible) 1f else 0f, StickLabMotion.bouncy(), label = "bubble")
    val slide = with(LocalDensity.current) { BUBBLE_SLIDE.toPx() }
    Box(
        modifier = Modifier
            // Volle Feldgröße, dann linksbündig und über das Feld hinaus: Blase beginnt immer an derselben Stelle
            .fillMaxSize()
            .wrapContentSize(align = Alignment.CenterStart, unbounded = true)
            .offset(x = BUBBLE_OFFSET)
            .graphicsLayer {
                alpha = shown.coerceIn(0f, 1f)
                translationX = (1f - shown) * -slide
                val scale = BUBBLE_START_SCALE + (1f - BUBBLE_START_SCALE) * shown
                scaleX = scale
                scaleY = scale
            }
            .background(StickLabColors.Bubble, RoundedCornerShape(BUBBLE_CORNER))
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(text = name, color = Color.White, fontSize = BUBBLE_TEXT_SIZE, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

// Symbol je Reiter (eigener Symbol-Satz, StickLabIcons)
private val MainTab.icon: ImageVector
    get() = when (this) {
        MainTab.STICKS -> StickLabIcons.Sticks
        MainTab.BUTTONS -> StickLabIcons.Buttons
        MainTab.LIGHTS -> StickLabIcons.Lights
        MainTab.PROFILES -> StickLabIcons.Profiles
        MainTab.SETTINGS -> StickLabIcons.Settings
    }

// Maße aus DESIGN_ENTWURF.html A2: Leiste 74 breit, Ecken 34, Symbol-Feld 52,
// Symbol 22, Fokus ×1,4, Drücken ×1,1, Ring 6 außen, Blase 66 vom Feld-Anfang
private val RAIL_WIDTH = 74.dp
private val RAIL_CORNER = 34.dp
private val ITEM_SIZE = 52.dp
private val ICON_SIZE = 22.dp
private const val ICON_FOCUS_SCALE = 1.4f
private const val ICON_PRESS_SCALE = 1.1f
private const val ICON_IDLE_ALPHA = 0.62f
private val RING_GAP = 6.dp
private val PILL_GLOW = 4.dp
private const val PILL_GLOW_ALPHA = 0.12f
private val BUBBLE_OFFSET = 66.dp
private val BUBBLE_SLIDE = 8.dp
private const val BUBBLE_START_SCALE = 0.9f
private val BUBBLE_CORNER = 11.dp
private val BUBBLE_TEXT_SIZE = 12.5.sp
