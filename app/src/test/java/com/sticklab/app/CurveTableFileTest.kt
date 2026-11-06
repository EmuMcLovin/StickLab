package com.sticklab.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

// Prüft die Tabellen-Datei für das Hilfsprogramm (Phase 6b, B4) ohne Odin: genaue Größe, Kopf, Werte little endian,
// Prüfsumme wie im Programm, ungültige Tabellen werden nie geschrieben, Schreiben über Zwischendatei
class CurveTableFileTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val linear = CurveTable.build(CurveSettings(Curve.LINEAR))
    private val precision = CurveTable.build(CurveSettings(Curve.PRECISION))

    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    @Test
    fun fileHasExactSizeAndHeader() {
        val bytes = CurveTableFile.encode(linear, precision)!!
        assertEquals(1040, bytes.size)
        assertEquals(CurveTableFile.FILE_SIZE, bytes.size)
        assertEquals("SLCT", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals(2, le(bytes).getShort(4).toInt())
        assertEquals(256, le(bytes).getShort(6).toInt())
    }

    // Links zuerst, dann rechts, je 2 Bytes little endian – so liest das Programm (read_u16)
    @Test
    fun tablesStandInOrderLittleEndian() {
        val bytes = CurveTableFile.encode(linear, precision)!!
        val buffer = le(bytes)
        for (i in 0 until CurveTable.SIZE) {
            assertEquals(linear[i], buffer.getShort(8 + 2 * i).toInt())
            assertEquals(precision[i], buffer.getShort(8 + 2 * (CurveTable.SIZE + i)).toInt())
        }
        // Letzter Wert 32767 = 0xFF 0x7F (niedriges Byte zuerst)
        assertEquals(0xFF.toByte(), bytes[8 + 2 * 255])
        assertEquals(0x7F.toByte(), bytes[8 + 2 * 255 + 1])
    }

    // CRC32 über die ersten 1036 Bytes, steht in den letzten 4 Bytes
    @Test
    fun checksumCoversEverythingBefore() {
        val bytes = CurveTableFile.encode(linear, linear)!!
        val crc = CRC32().apply { update(bytes, 0, 1036) }.value
        assertEquals(crc, le(bytes).getInt(1036).toLong() and 0xFFFFFFFFL)
    }

    // Eine Tabelle, die abfällt oder nicht bei 0 beginnt, kommt nie in die Datei (R136)
    @Test
    fun invalidTableIsNeverEncoded() {
        val falling = linear.copyOf().also { it[100] = it[99] - 1 }
        val notFromZero = linear.copyOf().also { it[0] = 5 }
        assertNull(CurveTableFile.encode(falling, linear))
        assertNull(CurveTableFile.encode(linear, notFromZero))
        assertNull(CurveTableFile.encode(IntArray(10), linear))
    }

    // Trigger-Grenzen (Z11) stehen hinter den Tabellen; ohne Angabe keine Grenze; außerhalb 10–100 % wird nie geschrieben
    @Test
    fun triggerLimitsStandBehindTheTables() {
        assertEquals(32767, le(CurveTableFile.encode(linear, linear)!!).getShort(1032).toInt())
        val limits = LeftRight(CurveTableFile.triggerLimitOf(0.5f), CurveTableFile.triggerLimitOf(0.1f))
        val buffer = le(CurveTableFile.encode(linear, linear, limits)!!)
        assertEquals(16384, buffer.getShort(1032).toInt())
        assertEquals(3277, buffer.getShort(1034).toInt())
        assertNull(CurveTableFile.encode(linear, linear, LeftRight(3276, 32767)))
        assertNull(CurveTableFile.encode(linear, linear, LeftRight(32767, 40000)))
    }

    @Test
    fun writeLeavesOnlyTheFinishedFile() {
        val dir = folder.newFolder()
        assertTrue(CurveTableFile.write(dir, linear, precision))
        val file = File(dir, CurveTableFile.FILE_NAME)
        assertArrayEquals(CurveTableFile.encode(linear, precision), file.readBytes())
        assertFalse(File(dir, "curve_table.tmp").exists())
        // Ungültig → nichts geschrieben, alte Datei bleibt
        assertFalse(CurveTableFile.write(dir, IntArray(10), linear))
        assertArrayEquals(CurveTableFile.encode(linear, precision), file.readBytes())
    }

    @Test
    fun deleteRemovesTheFile() {
        val dir = folder.newFolder()
        CurveTableFile.write(dir, linear, linear)
        assertTrue(CurveTableFile.delete(dir))
        assertFalse(File(dir, CurveTableFile.FILE_NAME).exists())
        assertTrue(CurveTableFile.delete(dir))
    }
}
