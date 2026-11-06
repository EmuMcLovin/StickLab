package com.sticklab.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Plan Regel 7: Nur auf dem bekannten Gerät mit bekannter Firmware laufen riskante Funktionen.
// Die Texte stammen vom Odin des Besitzers (gelesen 05.11.2026)
class DeviceCheckTest {

    private val knownDisplay = "Odin3_V1.0.0.187_20260616_193307_user"

    @Test
    fun odinWithMeasuredFirmwareIsKnown() {
        assertEquals(DeviceSupport.KNOWN, DeviceCheck.classify("Odin3", knownDisplay))
    }

    @Test
    fun odinWithOtherFirmwareIsNotKnown() {
        val newer = "Odin3_V1.0.0.200_20270101_000000_user"
        assertEquals(DeviceSupport.UNKNOWN_FIRMWARE, DeviceCheck.classify("Odin3", newer))
    }

    // Eine Version darf nicht durch einen ähnlichen Anfang als bekannt gelten
    @Test
    fun longerVersionWithSameStartIsNotKnown() {
        val similar = "Odin3_V1.0.0.1870_20270101_000000_user"
        assertEquals(DeviceSupport.UNKNOWN_FIRMWARE, DeviceCheck.classify("Odin3", similar))
    }

    @Test
    fun odinWithUnreadableFirmwareIsNotKnown() {
        assertEquals(DeviceSupport.UNKNOWN_FIRMWARE, DeviceCheck.classify("Odin3", "irgendetwas"))
        assertEquals(DeviceSupport.UNKNOWN_FIRMWARE, DeviceCheck.classify("Odin3", null))
    }

    // Anderes Gerät bleibt unbekannt, auch wenn der Firmware-Text passen würde
    @Test
    fun otherDeviceIsUnknown() {
        assertEquals(DeviceSupport.UNKNOWN_DEVICE, DeviceCheck.classify("Odin2 Portal", knownDisplay))
        assertEquals(DeviceSupport.UNKNOWN_DEVICE, DeviceCheck.classify(null, knownDisplay))
    }

    @Test
    fun readsVersionFromFirmwareText() {
        assertEquals("1.0.0.187", DeviceCheck.firmwareVersion(knownDisplay))
        assertNull(DeviceCheck.firmwareVersion("irgendetwas"))
        assertNull(DeviceCheck.firmwareVersion(null))
    }
}
