package com.sticklab.app

import android.os.Build

// Wie gut StickLab dieses Gerät kennt (Plan Regel 7: "Die App prüft Gerät und Firmware-Version. Auf unbekannten
// Geräten oder Versionen bleiben riskante Funktionen aus")
enum class DeviceSupport {
    // Odin 3 mit einer Firmware, auf der alles gemessen wurde: alle Funktionen
    KNOWN,

    // Odin 3 mit anderer Firmware: Werte wie im Odin-Menü gehen, virtueller Controller bleibt aus (Plan E2:
    // ein Firmware-Update kann den Weg schließen)
    UNKNOWN_FIRMWARE,

    // Anderes Gerät: StickLab schickt nichts an das Gerät (Plan E6: andere Geräte später)
    UNKNOWN_DEVICE,
}

// Die Prüfung selbst: nur Vergleich von zwei Texten, ohne Android-Teile, darum auf dem Laptop geprüft
// (DeviceCheckTest). Die Werte des laufenden Geräts holt DeviceCheck.current
object DeviceCheck {

    // Modellname des Odin 3 (ro.product.model, gelesen 05.11.2026)
    private const val SUPPORTED_MODEL = "Odin3"

    // Firmware-Versionen, auf denen StickLab gemessen wurde. Neue Version erst eintragen, wenn der virtuelle
    // Controller dort geprüft ist. 1.0.0.187: Stand aller Messungen seit 10.10.2026 (PROJEKT_DOKU Abschnitt 2)
    private val KNOWN_FIRMWARE = setOf("1.0.0.187")

    // So steht die Version am Odin: "Odin3_V1.0.0.187_20260616_193307_user" (ro.build.display.id)
    private val FIRMWARE_FORMAT = Regex("^Odin3_V(\\d+(?:\\.\\d+)*)_")

    // Der Stand dieses Geräts (ändert sich nicht, solange die App läuft)
    val current: DeviceSupport by lazy { classify(Build.MODEL, Build.DISPLAY) }

    // Version aus dem langen Firmware-Text, null = Text hat ein anderes Format
    fun firmwareVersion(display: String?): String? =
        display?.let { FIRMWARE_FORMAT.find(it)?.groupValues?.get(1) }

    // Version dieses Geräts für die Anzeige, null = unbekanntes Format
    val currentFirmware: String? get() = firmwareVersion(Build.DISPLAY)

    // Modellname dieses Geräts für die Anzeige
    val currentModel: String get() = Build.MODEL

    fun classify(model: String?, display: String?): DeviceSupport = when {
        model != SUPPORTED_MODEL -> DeviceSupport.UNKNOWN_DEVICE
        firmwareVersion(display) in KNOWN_FIRMWARE -> DeviceSupport.KNOWN
        else -> DeviceSupport.UNKNOWN_FIRMWARE
    }
}
