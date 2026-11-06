package com.sticklab.app

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Eigener Symbol-Satz von StickLab (aus DESIGN_ENTWURF.html, eigene Zeichnungen, keine fremden Symbole –
// Apples SF Symbols dürfen auf Android nicht benutzt werden). Alle im 24er-Raster, gleiche Strichstärke,
// runde Enden. Die Farbe kommt beim Anzeigen dazu (Icon "tint")
object StickLabIcons {

    // Stick: großer Kreis (Gehäuse) mit kleinem, leicht verschobenem Kreis (Stick-Kappe)
    val Sticks = strokeIcon("Sticks", circle(12f, 12f, 9f) + circle(13.5f, 10.5f, 3.6f))

    // Tasten: vier Knöpfe in Rautenform wie A/B/X/Y
    val Buttons = strokeIcon(
        "Buttons",
        circle(12f, 5.5f, 2.6f) + circle(12f, 18.5f, 2.6f) + circle(5.5f, 12f, 2.6f) + circle(18.5f, 12f, 2.6f),
    )

    // Licht: Sonne mit acht Strahlen
    val Lights = strokeIcon(
        "Lights",
        circle(12f, 12f, 4f) +
            "M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4",
    )

    // Profile: drei übereinanderliegende Ebenen
    val Profiles = strokeIcon("Profiles", "M12 3.5L21 8l-9 4.5L3 8zM3 12l9 4.5 9-4.5M3 16l9 4.5 9-4.5")

    // Einstellungen (5d-2): Zahnrad aus eigenem Strich – Ring mit 8 kurzen Zähnen und Loch in der Mitte
    val Settings = strokeIcon(
        "Settings",
        circle(12f, 12f, 7f) + circle(12f, 12f, 3f) +
            "M19 12h2.5M5 12H2.5M12 19v2.5M12 5V2.5" +
            "M16.95 16.95l1.77 1.77M7.05 7.05L5.28 5.28M16.95 7.05l1.77-1.77M7.05 16.95l-1.77 1.77",
    )

    // Haken für "Applied" (etwas dickerer Strich wie im Entwurf)
    val Check = strokeIcon("Check", "M5 12.5l4.5 4.5L19 7.5", CHECK_STROKE_WIDTH)
}

private const val ICON_GRID = 24f              // Raster der Pfade (viewBox 0 0 24 24)
private const val ICON_STROKE_WIDTH = 1.8f     // Entwurf: stroke-width 1.8
private const val CHECK_STROKE_WIDTH = 2.4f    // Entwurf: Haken stroke-width 2.4

// Baut ein Strich-Symbol aus einem SVG-Pfad (nur Linie, keine Füllung)
private fun strokeIcon(name: String, pathData: String, strokeWidth: Float = ICON_STROKE_WIDTH): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = ICON_GRID.dp,
        defaultHeight = ICON_GRID.dp,
        viewportWidth = ICON_GRID,
        viewportHeight = ICON_GRID,
    ).addPath(
        pathData = addPathNodes(pathData),
        stroke = SolidColor(Color.White),
        strokeLineWidth = strokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

// SVG-Pfad für einen Kreis: zwei Halbbögen (Pfade kennen keinen Kreis-Befehl)
private fun circle(centerX: Float, centerY: Float, radius: Float): String =
    "M${centerX - radius} ${centerY}a$radius $radius 0 1 1 ${2 * radius} 0a$radius $radius 0 1 1 ${-2 * radius} 0z"
