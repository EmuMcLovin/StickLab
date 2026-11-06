package com.sticklab.app

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit

// Lichtblitz beim Profilwechsel (Phase 5c, Ziel Z6; Entscheidungen L1–L4 vom 27.10.2026): Nach "Use" leuchten beide
// Stick-LEDs kurz weich in der Profilfarbe, danach kommt genau die Farbe von vorher zurück. Läuft das Ambilight,
// macht es so lange Pause (isRunning) und schickt danach beim nächsten Bild weiter (Risiko R123).
// Läuft in einem eigenen Hintergrund-Thread, damit die Oberfläche nicht hängt (G5). Ein Objekt statt einer Klasse,
// damit ein Blitz auch fertig wird, wenn der Bildschirm gerade neu aufgebaut wird.
// Schreibt die LEDs nur über OdinService.putLedColors (fester Befehl, Format geprüft) – kein neuer root-Befehl
object LightFlash {

    private const val TAG = "LightFlash"

    // Schalter "Licht" im Odin-Menü (gelesen 27.10.2026: "1,1" = links und rechts an). Ist er nicht ganz an,
    // blitzt nichts: StickLab schaltet das Licht nie selbst ein (Plan 5c, R130)
    private const val LIGHT_ENABLED_KEY = "joystick_light_enabled"
    private const val LIGHT_ON = "1,1"

    // Ist der Schalter "Licht" sicher aus? Für den Hinweis vor dem Ambilight (Wunsch Besitzer 06.11.2026). Nicht lesbar = false:
    // Dann weiß die App es nicht und stört nicht mit einem falschen Hinweis
    fun isLightOff(context: Context): Boolean {
        val light = SystemSettings.readText(context.contentResolver, LIGHT_ENABLED_KEY) ?: return false
        return light != LIGHT_ON
    }

    // So lange nach dem Setzen der Pause warten, bis ein gerade laufender Ambilight-Schritt fertig ist
    // (Ambilight-Takt 25 ms + ein LED-Befehl; Vermutung, Plan 5c)
    private const val AMBILIGHT_SETTLE_MS = 60L

    // true, solange ein Blitz läuft (auch in der kurzen Wartezeit davor). Das Ambilight schickt dann nichts
    @Volatile
    var isRunning = false
        private set

    // ---------- Ab hier nur im Blitz-Thread benutzt ----------
    private val handler: Handler by lazy { Handler(HandlerThread("lightflash").apply { start() }.looper) }
    private var appContext: Context? = null

    // Wartezeit vor dem ersten Schritt läuft (dann nicht noch einmal anfangen)
    private var waitingToBegin = false

    // Farbe von vorher (gelesen und als Text, genau so wird sie am Ende zurückgeschickt); null = kein Blitz läuft
    private var before: LedColors? = null
    private var beforeText: String? = null
    private var flashArgb = 0
    private var startedAt = 0L

    // Nur fürs Protokoll: Zahl der Schritte und längster LED-Befehl (G4, R129)
    private var steps = 0
    private var slowestCommandMs = 0L
    private val step = Runnable { nextStep() }

    // Startet einen Blitz in der Profilfarbe. Kommt ein zweiter, solange einer läuft, beginnt der Verlauf mit der
    // neuen Farbe von vorn – mit derselben Farbe von vorher (nicht mit einer halben Blitzfarbe)
    fun start(context: Context, color: ProfileColor) {
        isRunning = true
        handler.post {
            appContext = context.applicationContext
            flashArgb = color.argb
            when {
                before != null -> restartCurve()
                !waitingToBegin -> {
                    waitingToBegin = true
                    handler.postDelayed(::begin, AMBILIGHT_SETTLE_MS)
                }
            }
        }
    }

    // Beim Öffnen der App: Ist die App mitten in einem Blitz beendet worden, steht die alte Farbe noch im Merker –
    // dann einmal zurückstellen (Entscheidung L4, Risiko R112). Nur die gemerkte Farbe, Format geprüft (R131)
    fun restoreInterrupted(context: Context) {
        handler.post {
            appContext = context.applicationContext
            if (before != null || waitingToBegin) return@post
            val saved = readMarker() ?: return@post
            if (LedColors.parse(saved) == null) {
                clearMarker()
                Log.w(TAG, "Merker mit unbekanntem Format verworfen: $saved")
                return@post
            }
            val restored = OdinService.putLedColors(saved)
            if (restored) clearMarker()
            Log.i(TAG, "Unterbrochener Blitz: alte Farbe $saved zurück, angenommen=$restored")
        }
    }

    // Erster Schritt nach der Wartezeit: Farbe von vorher lesen und merken, dann den Verlauf starten.
    // Unbekanntes Format oder Licht aus → kein Blitz
    private fun begin() {
        waitingToBegin = false
        val resolver = appContext?.contentResolver ?: return
        val text = SystemSettings.readText(resolver, OdinService.LED_COLOR_KEY)
        val light = SystemSettings.readText(resolver, LIGHT_ENABLED_KEY)
        val colors = LedColors.parse(text)
        if (colors == null || text == null || light != LIGHT_ON) {
            Log.i(TAG, "Kein Blitz: LED-Farbe '$text', Licht '$light'")
            isRunning = false
            return
        }
        // Erst merken, dann schreiben: stirbt die App mitten im Blitz, findet der nächste Start die alte Farbe
        writeMarker(text)
        before = colors
        beforeText = text
        Log.i(TAG, "Blitz Start: vorher $text, Profilfarbe ${LedColors.both(flashArgb).toSetting()}")
        restartCurve()
    }

    // Verlauf von vorn beginnen (erster Blitz oder zweiter, während einer läuft)
    private fun restartCurve() {
        handler.removeCallbacks(step)
        startedAt = SystemClock.uptimeMillis()
        steps = 0
        slowestCommandMs = 0L
        nextStep()
    }

    // Ein Schritt: Farbe zum jetzigen Zeitpunkt schicken, nächsten Schritt planen. Am Ende genau die Farbe von vorher
    private fun nextStep() {
        val colorsBefore = before ?: return
        val elapsed = SystemClock.uptimeMillis() - startedAt
        if (LightFlashCurve.isFinished(elapsed)) {
            finish()
            return
        }
        val sentAt = SystemClock.uptimeMillis()
        OdinService.putLedColors(LightFlashCurve.colorsAt(elapsed, colorsBefore, flashArgb).toSetting())
        val commandMs = SystemClock.uptimeMillis() - sentAt
        steps++
        slowestCommandMs = maxOf(slowestCommandMs, commandMs)
        handler.postDelayed(step, (LightFlashCurve.STEP_MS - commandMs).coerceAtLeast(0L))
    }

    // Letzter Schritt: alte Farbe zurück. Den Merker nur löschen, wenn PServerBinder das angenommen hat –
    // sonst versucht es der nächste Start der App noch einmal
    private fun finish() {
        val text = beforeText ?: return
        val restored = OdinService.putLedColors(text)
        Log.i(
            TAG,
            "Blitz Ende: zurück $text, angenommen=$restored, $steps Schritte, längster Befehl $slowestCommandMs ms, " +
                "Dauer ${SystemClock.uptimeMillis() - startedAt} ms",
        )
        before = null
        beforeText = null
        isRunning = false
        if (restored) clearMarker()
    }

    // ---------- Merker in der Datei "app" (AppKeys.LED_FLASH_RESTORE) ----------
    // Sofort auf die Platte (commit), damit er auch dann da ist, wenn die App gleich danach stirbt

    private fun readMarker(): String? = appContext?.let {
        AppStorage.open(it, AppStorage.AppFile.APP).getString(AppStorage.AppKeys.LED_FLASH_RESTORE, null)
    }

    private fun writeMarker(text: String) {
        appContext?.let { AppStorage.open(it, AppStorage.AppFile.APP).edit(commit = true) {
            putString(AppStorage.AppKeys.LED_FLASH_RESTORE, text)
        } }
    }

    private fun clearMarker() {
        appContext?.let { AppStorage.open(it, AppStorage.AppFile.APP).edit(commit = true) {
            remove(AppStorage.AppKeys.LED_FLASH_RESTORE)
        } }
    }
}
