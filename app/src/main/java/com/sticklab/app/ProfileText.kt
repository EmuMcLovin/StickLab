package com.sticklab.app

import android.content.res.Resources

// Wie die Werte eines Profils als Text erscheinen – gleich im Reiter Profiles und in der Nachfrage "Save current"
// (Entwurf PROFILE_ENTWURF.html): Deadzone "1.5 / 2.5 %", Trigger "5 / 5 %", Sensitivity "1.00 / 1.00",
// Kurve "Linear" (beide gleich) bzw. "Linear / Shooter", Tasten nach Aufdruck wie im Reiter Buttons
class ProfileText(private val resources: Resources) {

    fun deadzone(pair: LeftRight<Float>): String =
        resources.getString(R.string.profile_deadzone_pair, pair.left * PERCENT, pair.right * PERCENT)

    fun trigger(pair: LeftRight<Float>): String =
        resources.getString(R.string.profile_trigger_pair, pair.left * PERCENT, pair.right * PERCENT)

    fun sensitivity(pair: LeftRight<Float>): String =
        resources.getString(R.string.profile_sensitivity_pair, pair.left, pair.right)

    // Gleiche Kurve beider Sticks = ein Name, sonst "links / rechts" (Platz im Feld ist knapp)
    fun curve(pair: LeftRight<CurveSettings>): String =
        if (pair.left.curve == pair.right.curve) curveName(pair.left.curve)
        else resources.getString(R.string.profile_text_pair, curveName(pair.left.curve), curveName(pair.right.curve))

    // Name einer Belegung, wie ihn StickLab überall zeigt (A/B/X/Y nach Aufdruck)
    fun buttonName(target: ButtonTarget, layout: ControllerLayout): String = ButtonLayoutMap.printedName(target, layout)

    // "M1 B · M2 A" für die Nachfrage
    fun buttons(m1: ButtonTarget, m2: ButtonTarget, layout: ControllerLayout): String =
        resources.getString(R.string.profile_buttons, buttonName(m1, layout), buttonName(m2, layout))

    private fun curveName(curve: Curve): String = resources.getString(nameOf(curve))

    companion object {
        // Bruchteil → Prozent (0.025 → 2.5)
        private const val PERCENT = 100f

        // Name einer Kurve (auch auf den Kacheln im Reiter Sticks)
        fun nameOf(curve: Curve): Int = when (curve) {
            Curve.LINEAR -> R.string.curve_linear
            Curve.PRECISION -> R.string.curve_precision
            Curve.FAST -> R.string.curve_fast
            Curve.S_CURVE -> R.string.curve_s_curve
            Curve.SHOOTER -> R.string.curve_shooter
            Curve.CUSTOM -> R.string.curve_custom
        }
    }
}
