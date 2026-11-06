package com.sticklab.app

// Die Wege, über die ein Profil seine Werte setzt (Phase 5b-1) – genau dieselben wie "Apply" in den Reitern
// Sticks und Buttons: Deadzone und Trigger live + System, Sensitivity nur live (bleibt nach Neustart),
// M1/M2 als Systemeinstellung. Jeder Weg begrenzt den Wert selbst (SettingLimits) und liest nach.
// Kein neuer Befehl, keine neue Stelle zu PServerBinder
class OdinControlsSetter(
    private val deadzone: DeadzoneControl,
    private val trigger: TriggerControl,
    private val sensitivity: SensitivityControl,
    private val buttons: ButtonMappingControl,
) : OdinSetter {

    override fun setDeadzone(side: OdinMapping.Side, value: Float) = deadzone.apply(side, value)

    override fun setTriggerDeadzone(side: OdinMapping.Side, value: Float) = trigger.apply(side, value)

    override fun setSensitivity(side: OdinMapping.Side, value: Float) = sensitivity.apply(side, value)

    override fun setButton(button: BackButton, target: ButtonTarget) =
        buttons.apply(button, target) == ButtonMappingControl.Result.SAVED
}
