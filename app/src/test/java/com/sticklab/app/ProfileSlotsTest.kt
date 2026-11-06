package com.sticklab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Prüft die Regeln des Reiters Profiles (Phase 5a): Marke active/changed, freie Plätze und Farben, Namen
class ProfileSlotsTest {

    // Werte des Besitzers am 26.10.2026 (gelesen): 1,5/2,5 %, Sens 1,0, Trigger 5 %, M1/M2 = A/B
    private val odinToday = OdinValues(
        deadzone = LeftRight(0.015f, 0.025f),
        sensitivity = LeftRight(1.0f, 1.0f),
        triggerDeadzone = LeftRight(0.05f, 0.05f),
        m1 = ButtonTarget.A,
        m2 = ButtonTarget.B,
    )
    private val everyday = odinToday.toProfile("Everyday", ProfileColor.PINK)

    // Kachel (5d): nächstes gespeichertes Profil im Kreis, leere Plätze übersprungen
    @Test
    fun nextSlotGoesRoundAndSkipsEmptySlots() {
        val other = everyday.copy(name = "2")
        val slots = listOf(everyday, null, other, null)
        assertEquals(2, ProfileSlots.nextSlot(slots, activeSlot = 0))
        assertEquals(0, ProfileSlots.nextSlot(slots, activeSlot = 2))
    }

    @Test
    fun nextSlotWithoutActiveProfileStartsAtTheFirstSaved() {
        assertEquals(1, ProfileSlots.nextSlot(listOf(null, everyday, null, everyday), activeSlot = null))
    }

    @Test
    fun nextSlotWithOneProfileStaysOnIt() {
        assertEquals(3, ProfileSlots.nextSlot(listOf(null, null, null, everyday), activeSlot = 3))
    }

    @Test
    fun nextSlotWithoutProfilesIsNull() {
        assertNull(ProfileSlots.nextSlot(listOf(null, null, null, null), activeSlot = null))
    }

    @Test
    fun profileFromOdinMatchesOdin() {
        assertTrue(odinToday.matches(everyday))
        // Kommazahlen, wie sie gelesen werden, weichen nur winzig ab
        assertTrue(odinToday.copy(deadzone = LeftRight(0.0150001f, 0.0249999f)).matches(everyday))
    }

    @Test
    fun oneStepDifferenceCountsAsChanged() {
        assertFalse(odinToday.copy(deadzone = LeftRight(0.016f, 0.025f)).matches(everyday))   // +0,1 %
        assertFalse(odinToday.copy(sensitivity = LeftRight(1.0f, 1.05f)).matches(everyday))
        assertFalse(odinToday.copy(triggerDeadzone = LeftRight(0.06f, 0.05f)).matches(everyday))
        assertFalse(odinToday.copy(m1 = ButtonTarget.Y).matches(everyday))
    }

    @Test
    fun badgeOnlyOnActiveSlot() {
        val slots = listOf(everyday, null, null, null)
        assertEquals(SlotBadge.ACTIVE, ProfileSlots.badgeOf(0, slots[0], activeSlot = 0, odin = odinToday))
        assertEquals(SlotBadge.NONE, ProfileSlots.badgeOf(0, slots[0], activeSlot = null, odin = odinToday))
        assertEquals(SlotBadge.NONE, ProfileSlots.badgeOf(1, slots[1], activeSlot = 0, odin = odinToday))
        val changed = odinToday.copy(m2 = ButtonTarget.X)
        assertEquals(SlotBadge.CHANGED, ProfileSlots.badgeOf(0, slots[0], activeSlot = 0, odin = changed))
        // Werte nicht lesbar: weiter "active", nicht "changed"
        assertEquals(SlotBadge.ACTIVE, ProfileSlots.badgeOf(0, slots[0], activeSlot = 0, odin = null))
    }

    @Test
    fun newProfileGetsCurrentValuesAndLinearCurve() {
        assertEquals(LeftRight(0.015f, 0.025f), everyday.deadzone)
        assertEquals(ButtonTarget.B, everyday.m2)
        assertEquals(Profile.DEFAULT_CURVES, everyday.curves)
        assertEquals(LightMode.OFF, everyday.lightMode)
    }

    @Test
    fun freeSlotAndColor() {
        val blue = everyday.copy(color = ProfileColor.VIOLET)
        assertEquals(1, ProfileSlots.firstFreeSlot(listOf(everyday, null, blue, null)))
        assertNull(ProfileSlots.firstFreeSlot(listOf(everyday, blue, everyday, blue)))
        assertEquals(ProfileColor.BLUE, ProfileSlots.nextFreeColor(listOf(everyday, null, blue, null)))
        assertEquals(ProfileColor.PINK, ProfileSlots.nextFreeColor(listOf(null, null, null, null)))
    }

    @Test
    fun namesAreCleaned() {
        assertEquals("Shooter", ProfileSlots.cleanName("  Shooter \n"))
        assertNull(ProfileSlots.cleanName("   "))
        assertEquals(Profile.MAX_NAME_LENGTH, ProfileSlots.cleanName("A".repeat(40))?.length)
        // Ein Emoji am Schnitt wird nicht halbiert
        val withEmoji = "A".repeat(Profile.MAX_NAME_LENGTH - 1) + "🎮"
        assertEquals("A".repeat(Profile.MAX_NAME_LENGTH - 1), ProfileSlots.cleanName(withEmoji))
    }

    // Entscheidung D: "Standard" nur beim allerersten Mal und nur, wenn noch kein Profil da ist
    // (der Besitzer hat schon "Standart" → es kommt kein zweites dazu)
    @Test
    fun standardProfileOnlyOnceAndOnlyWithoutProfiles() {
        val empty = listOf<Profile?>(null, null, null, null)
        assertTrue(ProfileSlots.needsStandardProfile(alreadyDone = false, slots = empty))
        assertFalse(ProfileSlots.needsStandardProfile(alreadyDone = true, slots = empty))
        assertFalse(ProfileSlots.needsStandardProfile(alreadyDone = false, slots = listOf(null, everyday, null, null)))
    }

    @Test
    fun copyNameIsShortened() {
        assertEquals("Shooter copy", ProfileSlots.copyName("Shooter") { "$it copy" })
        assertEquals(Profile.MAX_NAME_LENGTH, ProfileSlots.copyName("B".repeat(24)) { "$it copy" }.length)
    }
}
