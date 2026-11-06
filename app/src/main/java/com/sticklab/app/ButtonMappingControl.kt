package com.sticklab.app

import android.content.ContentResolver

// Liest und setzt die Belegung von M1/M2 und liest das Controller-Layout (Plan Phase 4).
// Gelesen wird über SystemSettings (ohne root), geschrieben nur über OdinService mit festen Werten (Regel 7).
// Ob ein von StickLab geschriebener Wert sofort am Controller wirkt, ist noch eine Vermutung (R105):
// gesichert ist nur, dass das Odin-Menü genau diese Einstellung schreibt und sie dann sofort wirkt
class ButtonMappingControl(private val resolver: ContentResolver) {

    enum class Result { SAVED, FAILED }

    // Gerade eingestellte Belegung, null = nicht lesbar oder keine Zahl aus der Menü-Liste
    fun read(button: BackButton): ButtonTarget? =
        ButtonTarget.fromSettingText(SystemSettings.readText(resolver, button.settingKey))

    // Gerade eingestelltes Layout (Xbox/Odin), bestimmt die Namen von A/B/X/Y
    fun layout(): ControllerLayout =
        ControllerLayout.fromFlipSetting(SystemSettings.readText(resolver, ControllerLayout.FLIP_SETTING_KEY))

    // Schreibt die Belegung und liest nach. Gilt erst als gespeichert, wenn die Einstellung danach
    // genau diese Zahl zeigt (PServerBinder wartet, bis der Befehl fertig ist, A28)
    fun apply(button: BackButton, target: ButtonTarget): Result {
        if (!OdinService.putButtonTarget(button, target)) return Result.FAILED
        return if (read(button) == target) Result.SAVED else Result.FAILED
    }
}
