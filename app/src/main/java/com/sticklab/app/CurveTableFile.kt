package com.sticklab.app

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

// Tabellen-Datei für das Hilfsprogramm (Phase 6b, Entscheidungen B1–B4 vom 27.10.2026). Die einzige Stelle, die
// diese Datei schreibt. Das Programm (sticklab_mirror.c, Modus curve) liest sie als root aus dem privaten Ordner,
// prüft sie genauso streng und fällt bei jedem Fehler auf 1:1 zurück (B3).
// Aufbau (B4), alle Zahlen little endian, genau FILE_SIZE Bytes:
//   "SLCT" | Version (2 Bytes) | Punkte (2) | 256 × links (je 2) | 256 × rechts (je 2) | Grenze L2 (2) | Grenze R2 (2) |
//   CRC32 über alles davor (4). Grenze = Trigger-Wert, ab dem er schon voll ausgibt (Z11, seit Version 2)
// Name, Aufbau und Größe nie ändern, ohne das Programm mitzuändern (beide Seiten haben die Werte fest drin)
object CurveTableFile {

    // Fester Name im privaten Ordner, so steht er auch im Programm (CURVE_PATH)
    const val FILE_NAME = "curve_table.bin"

    // Erst hierhin schreiben, dann umbenennen: Umbenennen ist atomar, das Programm sieht nie eine halbe Datei (R143)
    private const val TEMP_NAME = "curve_table.tmp"

    private val MAGIC = "SLCT".toByteArray(Charsets.US_ASCII)
    const val VERSION = 2

    // Kennung (4) + Version (2) + Punkte (2) + zwei Tabellen + zwei Trigger-Grenzen (je 2) + CRC32 (4) = 1040 Bytes
    private const val HEADER_SIZE = 8
    private const val CRC_OFFSET = HEADER_SIZE + 2 * CurveTable.SIZE * 2 + 2 * 2
    const val FILE_SIZE = CRC_OFFSET + 4

    // Kleinster und größter erlaubter Grenzwert, im Programm genauso (TRIGGER_LIMIT_MIN, CURVE_MAX): 10 % bis 100 %
    // des Wegs (SettingLimits.TRIGGER_FULL_OUTPUT)
    private val TRIGGER_LIMITS = triggerLimitOf(SettingLimits.TRIGGER_FULL_OUTPUT.start)..CurveTable.MAX_OUTPUT

    // Keine Grenze: Trigger gibt erst am Ende voll aus, also 1:1
    val NO_TRIGGER_LIMITS = LeftRight(CurveTable.MAX_OUTPUT, CurveTable.MAX_OUTPUT)

    // Bruchteil des Wegs (0.5 = 50 %) → Grenzwert für die Datei
    fun triggerLimitOf(fullOutput: Float): Int = Math.round(fullOutput * CurveTable.MAX_OUTPUT)

    // Baut den Datei-Inhalt. null, wenn eine Tabelle die Prüfregel nicht besteht oder eine Trigger-Grenze außerhalb
    // liegt (dann gar nicht erst schreiben)
    fun encode(left: IntArray, right: IntArray, triggerLimits: LeftRight<Int> = NO_TRIGGER_LIMITS): ByteArray? {
        if (!CurveTable.isValid(left) || !CurveTable.isValid(right)) return null
        if (triggerLimits.left !in TRIGGER_LIMITS || triggerLimits.right !in TRIGGER_LIMITS) return null
        val buffer = ByteBuffer.allocate(FILE_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(MAGIC)
        buffer.putShort(VERSION.toShort())
        buffer.putShort(CurveTable.SIZE.toShort())
        // Werte 0 bis 32767 passen genau in 2 Bytes (isValid oben)
        left.forEach { buffer.putShort(it.toShort()) }
        right.forEach { buffer.putShort(it.toShort()) }
        buffer.putShort(triggerLimits.left.toShort())
        buffer.putShort(triggerLimits.right.toShort())
        buffer.putInt(crcOf(buffer.array()).toInt())
        return buffer.array()
    }

    // Schreibt die Tabellen in den privaten Ordner (erst Zwischendatei, dann umbenennen) und liest zur Kontrolle
    // zurück. true = die Datei steht genau so da. Nicht im Haupt-Thread aufrufen
    fun write(filesDir: File, left: IntArray, right: IntArray, triggerLimits: LeftRight<Int> = NO_TRIGGER_LIMITS): Boolean {
        val bytes = encode(left, right, triggerLimits) ?: return false
        return try {
            val temp = File(filesDir, TEMP_NAME)
            val target = File(filesDir, FILE_NAME)
            temp.writeBytes(bytes)
            temp.renameTo(target) && target.readBytes().contentEquals(bytes)
        } catch (_: Exception) {
            false
        }
    }

    // Kurve aus: Datei weg → das Programm gibt die Sticks innerhalb von 1 s wieder 1:1 weiter (B1, B3).
    // true = keine Datei mehr da (auch, wenn vorher schon keine da war)
    fun delete(filesDir: File): Boolean {
        val target = File(filesDir, FILE_NAME)
        return !target.exists() || target.delete()
    }

    // CRC32 über alles vor der Prüfsumme, gleiche Rechnung wie crc32_of im Programm
    private fun crcOf(bytes: ByteArray): Long = CRC32().apply { update(bytes, 0, CRC_OFFSET) }.value
}
