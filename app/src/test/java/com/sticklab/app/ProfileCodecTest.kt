package com.sticklab.app

import org.junit.Assert.assertEquals
import org.junit.Test

// Prüft, dass ein Profil unverändert gespeichert und zurückgelesen wird,
// und dass fehlende oder kaputte Werte sicher durch Standardwerte ersetzt werden
class ProfileCodecTest {

    private val shooter = Profile(
        name = "Shooter",
        color = ProfileColor.RED,
        deadzone = LeftRight(0.015f, 0.025f),
        sensitivity = LeftRight(0.9f, 1.1f),
        triggerDeadzone = LeftRight(0.05f, 0.1f),
        curves = LeftRight(
            CurveSettings(Curve.LINEAR),
            CurveSettings(Curve.SHOOTER, outerDeadzone = 0.95f, antiDeadzone = 0.08f),
        ),
        m1 = ButtonTarget.X,
        m2 = ButtonTarget.NONE,
        lightMode = LightMode.AMBILIGHT,
    )

    @Test
    fun profileSurvivesEncodeAndDecode() {
        val decoded = ProfileCodec.decode(ProfileCodec.encode(shooter), "Ersatz")
        assertEquals(shooter, decoded)
    }

    @Test
    fun emptyStorageGivesDefaultProfile() {
        val decoded = ProfileCodec.decode(emptyMap(), "Profil 1")
        assertEquals(Profile.withDefaults("Profil 1"), decoded)
    }

    @Test
    fun outOfRangeValuesAreLimited() {
        val stored = ProfileCodec.encode(shooter) + mapOf(
            "deadzone.right" to 0.5f,       // über 20 %
            "sensitivity.left" to 9.0f,     // über 2,00
            "triggerDeadzone.left" to -1f,  // unter 5 %
        )
        val decoded = ProfileCodec.decode(stored, "Ersatz")
        assertEquals(0.20f, decoded.deadzone.right)
        assertEquals(2.0f, decoded.sensitivity.left)
        assertEquals(0.05f, decoded.triggerDeadzone.left)
    }

    @Test
    fun brokenValuesFallBackToDefaults() {
        val stored = ProfileCodec.encode(shooter) + mapOf(
            "name" to "   ",                // leer
            "deadzone.left" to Float.NaN,   // keine Zahl
            "sensitivity.right" to "viel",  // falsche Art
            "curve.left" to "UNBEKANNT",    // unbekannte Kurve
            "m1" to 55,                     // keine Zahl aus der Menü-Liste
            "lightMode" to null,            // fehlt
        )
        val decoded = ProfileCodec.decode(stored, "Ersatz")
        assertEquals("Ersatz", decoded.name)
        assertEquals(Profile.DEFAULT_DEADZONE, decoded.deadzone.left)
        assertEquals(Profile.DEFAULT_SENSITIVITY, decoded.sensitivity.right)
        assertEquals(Profile.DEFAULT_CURVE, decoded.curves.left.curve)
        assertEquals(Profile.DEFAULT_M1, decoded.m1)
        assertEquals(Profile.DEFAULT_LIGHT_MODE, decoded.lightMode)
    }

    // Fund F2 (30. Sitzung): "None" (0) gehört zur Menü-Liste und darf nicht still zu A werden;
    // eine Farbe außerhalb der 8 festen wird zur Standardfarbe
    @Test
    fun noneStaysNoneAndUnknownColorFallsBack() {
        val stored = ProfileCodec.encode(shooter) + mapOf("m1" to 0, "color" to 0xFF123456.toInt())
        val decoded = ProfileCodec.decode(stored, "Ersatz")
        assertEquals(ButtonTarget.NONE, decoded.m1)
        assertEquals(Profile.DEFAULT_COLOR, decoded.color)
    }

    // So steht ein Profil in der Datei: Farbe als ARGB-Zahl, M1/M2 als die Zahl, die auch das Odin-Menü schreibt
    @Test
    fun storedNumbersMatchOdinMenu() {
        val encoded = ProfileCodec.encode(shooter)
        assertEquals(99, encoded["m1"])
        assertEquals(0, encoded["m2"])
        assertEquals(ProfileColor.RED.argb, encoded["color"])
    }

    // Eigene Kurve (Phase 7): Punkte kommen unverändert zurück, kaputte werden zur Geraden
    @Test
    fun customCurveSurvivesAndBrokenPointsFallBack() {
        val points = CustomCurve(listOf(CurvePoint(0.2f, 0.1f), CurvePoint(0.5f, 0.3f), CurvePoint(0.8f, 0.9f)))
        val custom = shooter.copy(curves = LeftRight(CurveSettings(Curve.CUSTOM, custom = points), CurveSettings()))
        val encoded = ProfileCodec.encode(custom)
        assertEquals(custom, ProfileCodec.decode(encoded, "Ersatz"))
        val broken = ProfileCodec.decode(encoded + ("customCurve.left" to "0.5:0.9;0.2:0.1"), "Ersatz")
        assertEquals(CustomCurve.DEFAULT, broken.curves.left.custom)
    }

    // Ein Profil von vor Phase 6 hat diese Felder nicht: Es darf sich dadurch nicht ändern (Linear, nichts dazu)
    @Test
    fun oldProfileWithoutCurveFieldsKeepsDefaults() {
        val old = ProfileCodec.encode(shooter)
            .filterKeys { !it.startsWith("outerDeadzone") && !it.startsWith("customCurve") }
        val decoded = ProfileCodec.decode(old, "Ersatz")
        assertEquals(CurveSettings.NO_OUTER_DEADZONE, decoded.curves.right.outerDeadzone)
        assertEquals(CustomCurve.DEFAULT, decoded.curves.right.custom)
    }

    @Test
    fun outerDeadzoneIsLimited() {
        val stored = ProfileCodec.encode(shooter) + ("outerDeadzone.left" to 0.2f)
        assertEquals(0.80f, ProfileCodec.decode(stored, "Ersatz").curves.left.outerDeadzone)
    }

    // Trigger voll ab (Z11): bleibt je Seite erhalten, wird begrenzt, fehlt bei alten Profilen = keine Grenze
    @Test
    fun triggerFullOutputSurvivesAndIsLimited() {
        val custom = shooter.copy(curves = LeftRight(CurveSettings(triggerFullOutput = 0.5f), CurveSettings(triggerFullOutput = 0.25f)))
        assertEquals(custom, ProfileCodec.decode(ProfileCodec.encode(custom), "Ersatz"))
        val tooLow = ProfileCodec.encode(custom) + ("triggerFullOutput.left" to 0.01f)
        assertEquals(0.10f, ProfileCodec.decode(tooLow, "Ersatz").curves.left.triggerFullOutput)
        val old = ProfileCodec.encode(custom).filterKeys { !it.startsWith("triggerFullOutput") }
        assertEquals(CurveSettings.NO_TRIGGER_LIMIT, ProfileCodec.decode(old, "Ersatz").curves.left.triggerFullOutput)
    }

    @Test
    fun longNameIsShortened() {
        val stored = ProfileCodec.encode(shooter) + ("name" to "A".repeat(40))
        val decoded = ProfileCodec.decode(stored, "Ersatz")
        assertEquals(Profile.MAX_NAME_LENGTH, decoded.name.length)
    }
}
