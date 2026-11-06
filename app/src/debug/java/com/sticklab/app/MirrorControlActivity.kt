package com.sticklab.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Wegwerf-Bildschirm für Test C Stufe 5 (E-S5c), seit Phase 2 / E mit Knopf Root-Probe. Nur Debug-Version.
// Fünf Knöpfe per Touch (gehen auch, wenn der Controller nicht reagiert): Prüfen / Root-Probe / Start /
// Stopp / Harter Stopp. Alles Weitere (Prüfsumme, Doppelstart-Sperre, feste Befehle) steht in MirrorControl.
// Seit Phase 2 / D startet der Knopf Start den Vordergrund-Dienst MirrorService (der schickt dann START),
// Stopp und Harter Stopp schicken direkt (der Dienst merkt das Ende am Pad). Beim Öffnen fragt Android
// einmal nach der Erlaubnis für Benachrichtigungen (D-2), sonst wird beim Öffnen nichts geschickt.
// Schließen des Bildschirms stoppt nichts: Stopp per Knopf, Benachrichtigung, Empfänger, oder App beenden
// (Wächter). Kein Zeitlimit mehr (Z-1)
// Seit Phase 6b drei Knöpfe für die Tabellen-Datei (Kurve Linear / Precision / aus, CurveTestCases, ohne root);
// dafür stehen Prüfen und Root-Probe nebeneinander, damit alles weiter auf den Bildschirm passt
class MirrorControlActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private val buttons = mutableListOf<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        root.addView(TextView(this).apply {
            textSize = 20f
            text = "Virtueller Controller (Phase 2 / D, mit Vordergrund-Dienst)\n" +
                "Not-Aus: Stopp (hier oder in der Benachrichtigung) · Harter Stopp · App beenden (Wächter, ca. 2 s) · Neustart"
        })
        // Knöpfe, die sich eine Zeile teilen, stehen nebeneinander, damit alle Knöpfe und die Statuszeile passen
        val checkRow = addRow(root)
        addButton(checkRow, "Prüfen (schickt nichts)", MirrorControl.CHECK, weight = 1f)
        addButton(checkRow, "Root-Probe (harmlos)", MirrorControl.PROBE, weight = 1f)
        addButton(root, "Start (mit Dienst)", MirrorControl.START)
        val stopRow = addRow(root)
        addButton(stopRow, "Stopp", MirrorControl.STOP, weight = 1f)
        addButton(stopRow, "Harter Stopp (sofort)", MirrorControl.KILL, weight = 1f)
        val curveRow = addRow(root)
        addButton(curveRow, "Kurve: Linear", CurveTestCases.LINEAR, weight = 1f)
        addButton(curveRow, "Kurve: Precision", CurveTestCases.PRECISION, weight = 1f)
        addButton(curveRow, "Kurve aus", CurveTestCases.OFF, weight = 1f)
        status = TextView(this).apply {
            textSize = 20f
            text = "Noch nichts geschickt"
        }
        root.addView(status)
        setContentView(root)
        askNotificationPermission()
    }

    // D-2: Ohne diese Erlaubnis läuft der Dienst trotzdem, die Benachrichtigung steht dann aber nur im Task-Manager
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    // Start über den Dienst: Bildschirm ist sichtbar, darum erlaubt Android den Start des Vordergrund-Dienstes
    private fun startMirrorService() {
        val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date())
        status.text = try {
            startForegroundService(Intent(this, MirrorService::class.java).setAction(MirrorService.ACTION_START))
            "Zuletzt ($time): Start an den Dienst übergeben (Ergebnis in der Benachrichtigung / Log MirrorService)"
        } catch (t: Throwable) {
            "Zuletzt ($time): Dienst nicht gestartet (${t.javaClass.simpleName}), nichts geschickt"
        }
    }

    // Waagrechte Zeile über die ganze Breite für Knöpfe mit weight > 0
    private fun addRow(parent: LinearLayout): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return row
    }

    // weight = 0: Knopf über die ganze Breite; weight > 0: Knöpfe teilen sich eine Zeile
    private fun addButton(parent: LinearLayout, label: String, caseName: String, weight: Float = 0f) {
        val button = Button(this).apply {
            text = label
            textSize = 24f
            minHeight = 180
            // Start läuft über den Dienst (Phase 2 / D), alle anderen Knöpfe direkt über MirrorControl
            setOnClickListener { if (caseName == MirrorControl.START) startMirrorService() else runCase(caseName) }
        }
        val params = if (weight > 0f) {
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
        } else {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        parent.addView(button, params)
        buttons += button
    }

    // Knöpfe sperren, bis die Antwort da ist (gegen Doppeltippen), Arbeit nicht im Bildschirm-Thread
    private fun runCase(caseName: String) {
        buttons.forEach { it.isEnabled = false }
        status.text = "$caseName …"
        val appContext = applicationContext
        Thread {
            val result = if (caseName in CurveTestCases.CASES) CurveTestCases.run(appContext, caseName)
            else MirrorControl.run(appContext, caseName)
            val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date())
            runOnUiThread {
                status.text = "Zuletzt ($time): $result"
                buttons.forEach { it.isEnabled = true }
            }
        }.start()
    }
}
