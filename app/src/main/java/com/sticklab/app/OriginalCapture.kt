package com.sticklab.app

// Ein Wert, den StickLab beim ersten Start als "Original" merkt (Entscheidung R-1, 15.10.2026).
// key = Schlüssel in der Datei original_values. Nie umbenennen, sonst findet eine neue
// App-Version das gemerkte Original nicht mehr und würde einen schon veränderten Wert merken
enum class OriginalField(val key: String) {
    DEADZONE_LEFT("deadzone.left"),
    DEADZONE_RIGHT("deadzone.right"),
    TRIGGER_L2("triggerDeadzone.l2"),
    TRIGGER_R2("triggerDeadzone.r2"),
    SENSITIVITY_LEFT("sensitivity.left"),
    SENSITIVITY_RIGHT("sensitivity.right"),
    M1("m1"),
    M2("m2"),
}

// Regel für das Merken der Originalwerte, ohne Android-Teile (auf dem Laptop geprüft: OriginalCaptureTest).
// Jeder Wert wird nur einmal gemerkt: beim ersten Mal, an dem er sich lesen lässt.
// Danach wird er nie überschrieben, sonst wäre ein von StickLab veränderter Wert plötzlich das "Original"
object OriginalCapture {

    // Liefert die Werte, die jetzt neu gemerkt werden sollen:
    // nur Felder, die noch nicht gespeichert sind und gerade einen brauchbaren (nicht leeren) Wert haben.
    // Ein Feld ohne Wert (null, z. B. Live-Weg noch nicht verbunden) wird beim nächsten Mal nachgeholt
    fun valuesToAdd(alreadySaved: Set<String>, readNow: Map<OriginalField, String?>): Map<OriginalField, String> =
        readNow
            .filterKeys { field -> field.key !in alreadySaved }
            .mapNotNull { (field, value) -> value?.trim()?.takeIf { it.isNotEmpty() }?.let { field to it } }
            .toMap()
}
