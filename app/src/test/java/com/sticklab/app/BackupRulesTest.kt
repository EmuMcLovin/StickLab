package com.sticklab.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

// Das gemerkte Original darf nie in Androids Sicherung landen, sonst setzt "Restore everything" nach einer
// Wiederherstellung fremde Werte (OFFENE_FRAGEN 2). Android meldet einen falschen Dateinamen in diesen Regeln nicht,
// das Original wäre dann still wieder dabei. Darum hier geprüft, gegen den echten Namen aus AppStorage
class BackupRulesTest {

    private val excludeRule = "<exclude domain=\"sharedpref\" path=\"${AppStorage.AppFile.ORIGINAL_VALUES.fileName}.xml\" />"

    // Wie oft die Regel in einer Datei steht. Prüfungen laufen im Modul-Ordner app
    private fun excludeCount(fileName: String): Int =
        File("src/main/res/xml/$fileName").readText().windowed(excludeRule.length).count { it == excludeRule }

    @Test
    fun oldBackupRulesExcludeOriginal() {
        assertEquals(1, excludeCount("backup_rules.xml"))
    }

    // Zwei Wege ab Android 12: Cloud und Umzug auf ein neues Gerät
    @Test
    fun cloudAndTransferExcludeOriginal() {
        assertEquals(2, excludeCount("data_extraction_rules.xml"))
    }
}
