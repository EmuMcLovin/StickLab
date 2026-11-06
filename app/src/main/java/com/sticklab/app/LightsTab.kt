package com.sticklab.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// Reiter "Lights": die Licht-Karte über die ganze Breite, ohne Scrollen (Besitzer 25.10.2026).
// Oben eine Zeile: Titel, Schalter Ambilight (A2, Entscheidung 16.10.2026: Schalter statt Knopf – Wirkung
// gleich, Android fragt beim Einschalten wie bisher nach der Bildschirmaufnahme), Schalter "Don't ask again"
// und Knopf "Defaults", darunter der Hinweis. Darunter die sechs Regler in 2 Reihen × 3 Spalten.
// Warum so: Auf einem Regler verstellen links/rechts den Wert, weg kommt man nur mit hoch/runter
// (leaveSliderWithUpDown). Jede Spalte erreicht man darum von der oberen Zeile mit "runter" und kommt mit
// "hoch" wieder zurück
@Composable
fun LightsTab(
    ambilightRunning: Boolean,
    skipPrompt: Boolean,
    lightValues: Map<LightSlider, Int>,
    onAmbilightClick: () -> Unit,
    onSkipPromptChange: (Boolean) -> Unit,
    onLightChange: (LightSlider, Int) -> Unit,
    onDefaults: () -> Unit,
) {
    SettingsCard(maxWidth = Dp.Unspecified) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(LIGHTS_GAP),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CardTitle(R.string.light_title)
            // Der Schalter zeigt, ob das Ambilight läuft; umschalten startet bzw. stoppt es über MainActivity
            SwitchRow(
                text = stringResource(R.string.ambilight),
                checked = ambilightRunning,
                onCheckedChange = { onAmbilightClick() },
                modifier = Modifier.weight(1f).enterStaggered(0),
            )
            SwitchRow(
                text = stringResource(R.string.skip_prompt),
                checked = skipPrompt,
                onCheckedChange = onSkipPromptChange,
                modifier = Modifier.weight(1f).enterStaggered(1),
            )
            StickLabButton(
                text = stringResource(R.string.defaults),
                onClick = onDefaults,
                modifier = Modifier.widthIn(min = DEFAULTS_MIN_WIDTH).enterStaggered(1),
            )
        }
        Text(
            text = stringResource(R.string.skip_prompt_hint),
            fontSize = StickLabDimens.HintSize,
            maxLines = 1,
            modifier = Modifier
                .padding(top = 4.dp)
                .alpha(StickLabDimens.DIMMED_ALPHA)
                .enterStaggered(1),
        )
        LightSliderGrid(lightValues, onLightChange)
    }
}

// Die sechs Regler als Spalten nebeneinander: Spalte i enthält Regler i und i + 3, so lesen sich die
// Reihen von links nach rechts in der gewohnten Reihenfolge (Brightness, Color boost, White level …)
@Composable
private fun LightSliderGrid(lightValues: Map<LightSlider, Int>, onLightChange: (LightSlider, Int) -> Unit) {
    val sliders = LightSlider.entries
    val columnCount = (sliders.size + SLIDER_ROWS - 1) / SLIDER_ROWS
    Row(horizontalArrangement = Arrangement.spacedBy(LIGHTS_GAP), modifier = Modifier.fillMaxWidth()) {
        for (column in 0 until columnCount) {
            Column(modifier = Modifier.weight(1f)) {
                for (row in 0 until SLIDER_ROWS) {
                    val slider = sliders.getOrNull(row * columnCount + column) ?: continue
                    LightSliderRow(slider, lightValues[slider] ?: slider.min, onLightChange, Modifier.enterStaggered(2 + slider.ordinal))
                }
            }
        }
    }
}

// Regler für einen Licht-Wert in ganzen Schritten von min bis max, z. B. "Brightness 100 %"
@Composable
private fun LightSliderRow(slider: LightSlider, value: Int, onLightChange: (LightSlider, Int) -> Unit, modifier: Modifier) {
    SettingSlider(
        label = stringResource(slider.label),
        valueText = "$value${slider.unit}",
        value = value.toFloat(),
        valueRange = slider.min.toFloat()..slider.max.toFloat(),
        steps = slider.max - slider.min - 1,
        onValueChange = { onLightChange(slider, it.roundToInt()) },
        modifier = modifier,
    )
}

// "Defaults" soll neben den Schaltern nicht winzig wirken
private val DEFAULTS_MIN_WIDTH = 140.dp

// Abstand zwischen den Teilen einer Zeile und zwischen den Regler-Spalten
private val LIGHTS_GAP = 16.dp

// Zwei Reihen Regler: so passt die Karte quer ohne Scrollen (eine Regler-Zeile ist ca. 85 dp hoch,
// gemessen am Foto 25.10.2026; drei Reihen wären zu hoch)
private const val SLIDER_ROWS = 2

// Vorschau in Android Studio mit den Standardwerten aus LightSettings (Größe wie der Inhalt quer auf dem Odin)
@Preview(widthDp = 716, heightDp = 325)
@Composable
private fun LightsTabPreview() {
    StickLabTheme {
        LightsTab(
            ambilightRunning = false,
            skipPrompt = true,
            lightValues = LightSlider.entries.associateWith { it.read() },
            onAmbilightClick = {},
            onSkipPromptChange = {},
            onLightChange = { _, _ -> },
            onDefaults = {},
        )
    }
}
