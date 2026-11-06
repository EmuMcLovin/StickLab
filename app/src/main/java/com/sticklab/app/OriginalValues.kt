package com.sticklab.app

import android.content.Context
import androidx.core.content.edit

// Merkt sich die Werte, die vor StickLab 2 auf dem Odin eingestellt waren ("Original"), und liefert sie
// für den Knopf "Restore original" (Plan Phase 2, Entscheidungen R-1–R-3 vom 15.10.2026).
// Gespeichert wird in der eigenen App-Einstellungsdatei "original_values" (AppStorage, nur diese App kann sie
// lesen), jeder Wert roh als Text, so wie er gelesen wurde. Begrenzt wird erst beim Setzen (DeadzoneControl).
// Welche Werte neu dazukommen, entscheidet OriginalCapture (nur leere Felder füllen)
class OriginalValues(context: Context, private val mapping: OdinMapping) {

    private val prefs = AppStorage.open(context, AppStorage.AppFile.ORIGINAL_VALUES)
    private val resolver = context.contentResolver

    // Liest alle Werte und merkt sich die, die noch fehlen. Mehrfach aufrufbar (beim Start und
    // sobald der Live-Weg steht), gemerkte Werte bleiben immer unverändert
    fun captureMissing() {
        val toAdd = OriginalCapture.valuesToAdd(prefs.all.keys, readCurrentValues())
        if (toAdd.isEmpty()) return
        prefs.edit {
            for ((field, value) in toAdd) putString(field.key, value)
        }
    }

    // Gemerkte Deadzone eines Sticks als Bruchteil (2,5 % = 0.025), null = noch nicht gemerkt oder unlesbar
    fun deadzone(side: OdinMapping.Side): Float? = number(
        when (side) {
            OdinMapping.Side.LEFT -> OriginalField.DEADZONE_LEFT
            OdinMapping.Side.RIGHT -> OriginalField.DEADZONE_RIGHT
        }
    )

    // Gemerkte Trigger-Deadzone (Seite LEFT = L2, RIGHT = R2) als Bruchteil (5 % = 0.05), null = fehlt
    fun triggerDeadzone(side: OdinMapping.Side): Float? = number(
        when (side) {
            OdinMapping.Side.LEFT -> OriginalField.TRIGGER_L2
            OdinMapping.Side.RIGHT -> OriginalField.TRIGGER_R2
        }
    )

    // Gemerkte Sensitivity eines Sticks (z. B. 0.9), null = fehlt
    fun sensitivity(side: OdinMapping.Side): Float? = number(
        when (side) {
            OdinMapping.Side.LEFT -> OriginalField.SENSITIVITY_LEFT
            OdinMapping.Side.RIGHT -> OriginalField.SENSITIVITY_RIGHT
        }
    )

    // Ein gemerkter Wert als Zahl, null = nicht gemerkt oder keine Zahl
    private fun number(field: OriginalField): Float? =
        prefs.getString(field.key, null)?.toFloatOrNull()?.takeUnless { it.isNaN() }

    // Die aktuellen Werte vom Odin, null = gerade nicht lesbar.
    // Deadzone, Trigger, M1/M2: gespeicherte Systemeinstellung (gilt nach einem Neustart).
    // Sensitivity: liegt nur bei com.odin.mapping (Aufruf 10), braucht den Live-Weg
    private fun readCurrentValues(): Map<OriginalField, String?> = mapOf(
        OriginalField.DEADZONE_LEFT to readSystemSetting(OdinService.FloatSetting.LEFT_DEADZONE.key),
        OriginalField.DEADZONE_RIGHT to readSystemSetting(OdinService.FloatSetting.RIGHT_DEADZONE.key),
        OriginalField.TRIGGER_L2 to readSystemSetting(OdinService.FloatSetting.L2_DEADZONE.key),
        OriginalField.TRIGGER_R2 to readSystemSetting(OdinService.FloatSetting.R2_DEADZONE.key),
        OriginalField.SENSITIVITY_LEFT to mapping.getSensitivity(OdinMapping.Side.LEFT)?.toString(),
        OriginalField.SENSITIVITY_RIGHT to mapping.getSensitivity(OdinMapping.Side.RIGHT)?.toString(),
        // Tastenbelegung M1/M2 als Android-Tastennummer (96 = A, 97 = B), Einstellungsnamen in BackButton
        OriginalField.M1 to readSystemSetting(BackButton.M1.settingKey),
        OriginalField.M2 to readSystemSetting(BackButton.M2.settingKey),
    )

    // Gemerkte Belegung von M1 bzw. M2, null = noch nicht gemerkt oder keine Zahl aus der Menü-Liste
    fun buttonTarget(button: BackButton): ButtonTarget? {
        val field = when (button) {
            BackButton.M1 -> OriginalField.M1
            BackButton.M2 -> OriginalField.M2
        }
        return ButtonTarget.fromSettingText(prefs.getString(field.key, null))
    }

    private fun readSystemSetting(key: String): String? = SystemSettings.readText(resolver, key)
}
