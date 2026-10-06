package com.sticklab.app

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// Läuft im Hintergrund, liest den Bildschirm mit und färbt die Sticks
class AmbilightService : Service() {

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"

        @Volatile
        var running = false

        private const val KEY = "joystick_led_light_picker_color"

        // Größe des verkleinerten Bildes, das Android liefert
        private const val W = 96
        private const val H = 54

        // Einstellwerte kommen jetzt aus den Reglern der App
        private val BRIGHT get() = LightSettings.bright
        private val BOOST get() = LightSettings.boost
        private val WHITE get() = LightSettings.white
        private val GREEN get() = LightSettings.green
        private val BLUE get() = LightSettings.blue
        private val SLOW get() = LightSettings.slow
        private val FAST get() = LightSettings.fast
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var original: String? = null

    // Je 6 Werte: links Rot/Grün/Blau, rechts Rot/Grün/Blau
    private val target = IntArray(6)
    private val current = FloatArray(6)
    private val sent = IntArray(6) { -1 }
    private var ticking = false
    private var lastFrame = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

        if (projection != null) return START_NOT_STICKY

        val code = intent?.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)
        if (code != Activity.RESULT_OK || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        original = try {
            Settings.System.getString(contentResolver, KEY)
        } catch (t: Throwable) {
            null
        }

        val t = HandlerThread("ambilight")
        t.start()
        thread = t
        val h = Handler(t.looper)
        handler = h

        val mpm = getSystemService(MediaProjectionManager::class.java)
        val p = mpm.getMediaProjection(code, data)
        if (p == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        projection = p
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, h)

        val r = ImageReader.newInstance(W, H, PixelFormat.RGBA_8888, 2)
        reader = r
        r.setOnImageAvailableListener({ onFrame(it) }, h)
        display = p.createVirtualDisplay(
            "StickLab", W, H, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, h
        )

        running = true
        return START_NOT_STICKY
    }

    // Wird für jedes neue Bild aufgerufen
    private fun onFrame(r: ImageReader) {
        val img = try {
            r.acquireLatestImage()
        } catch (t: Throwable) {
            null
        }
        if (img == null) return
        try {
            val now = SystemClock.uptimeMillis()
            if (now - lastFrame < 15) return
            lastFrame = now
            val plane = img.planes[0]
            // Linker Stick: oberes linkes Viertel, rechter Stick: unteres rechtes Viertel
            region(plane.buffer, plane.rowStride, plane.pixelStride, 3, 45, 12, 50, 0)
            region(plane.buffer, plane.rowStride, plane.pixelStride, 55, 97, 50, 88, 3)
        } finally {
            img.close()
        }
        if (!ticking) {
            ticking = true
            handler?.post(tick)
        }
    }

    // Berechnet die Farbe eines Bildbereichs (Angaben in Prozent)
    private fun region(
        buf: ByteBuffer, rowStride: Int, pixelStride: Int,
        x0: Int, x1: Int, y0: Int, y1: Int, out: Int
    ) {
        var sr = 0L
        var sg = 0L
        var sb = 0L
        var sw = 0L
        for (y in H * y0 / 100 until H * y1 / 100) {
            var i = y * rowStride + (W * x0 / 100) * pixelStride
            for (x in W * x0 / 100 until W * x1 / 100) {
                val r = buf.get(i).toInt() and 0xff
                val g = buf.get(i + 1).toInt() and 0xff
                val b = buf.get(i + 2).toInt() and 0xff
                // Bunte Bildpunkte zählen mehr als graue
                val w = (maxOf(r, g, b) - minOf(r, g, b)) / 2 + 8
                sr += r * w
                sg += g * w
                sb += b * w
                sw += w
                i += pixelStride
            }
        }
        if (sw == 0L) return
        tune((sr / sw).toInt(), (sg / sw).toInt(), (sb / sw).toInt(), out)
    }

    // Farbkorrektur wie im Script: kräftiger, Weiß gedimmt, Schwarz dunkel
    private fun tune(r0: Int, g0: Int, b0: Int, out: Int) {
        var r = r0
        var g = g0
        var b = b0
        val mx = maxOf(r, g, b)
        var mn = minOf(r, g, b)
        if (mx < 1) {
            target[out] = 0
            target[out + 1] = 0
            target[out + 2] = 0
            return
        }
        val f = (((mx - mn) * 100 / mx - 5) * 5).coerceIn(0, 100)
        val k = mn * BOOST / 100 * f / 100
        r = (r - k) * mx / (mx - k)
        g = (g - k) * mx / (mx - k)
        b = (b - k) * mx / (mx - k)
        r = r * r / mx
        g = g * g / mx
        b = b * b / mx
        mn = minOf(r, g, b)
        val s2 = (mx - mn) * 100 / mx
        var v = mx * mx / 255
        v = v * (WHITE * 100 + (100 - WHITE) * s2) / 10000 * BRIGHT / 100
        r = (r * v / mx).coerceIn(0, 255)
        g = (g * v / mx * (100 - (100 - GREEN) * s2 / 100) / 100).coerceIn(0, 255)
        b = (b * v / mx * BLUE / 100).coerceIn(0, 255)
        if (r < 3 && g < 3 && b < 3) {
            r = 0
            g = 0
            b = 0
        }
        target[out] = r
        target[out + 1] = g
        target[out + 2] = b
    }

    // Läuft 20-mal pro Sekunde und blendet weich zur Zielfarbe
    private val tick = object : Runnable {
        override fun run() {
            var moving = false
            for (s in 0..1) {
                val o = s * 3
                var dmax = 0f
                for (i in o until o + 3) dmax = max(dmax, abs(target[i] - current[i]))
                if (dmax < 0.5f) {
                    for (i in o until o + 3) current[i] = target[i].toFloat()
                    continue
                }
                val a = if (dmax > 70f) FAST else SLOW
                for (i in o until o + 3) current[i] += (target[i] - current[i]) * a
                moving = true
            }

            val threshold = if (moving) 2 else 1
            var changed = false
            for (i in 0 until 6) {
                if (abs(current[i].roundToInt() - sent[i]) >= threshold) changed = true
            }
            if (changed) {
                for (i in 0 until 6) sent[i] = current[i].roundToInt()
                OdinService.run("settings put system $KEY '${hex(0)},${hex(3)}'")
            }

            if (moving && running) {
                handler?.postDelayed(this, 25)
            } else {
                ticking = false
            }
        }
    }

    private fun hex(o: Int): String {
        return String.format("#ff%02x%02x%02x", sent[o], sent[o + 1], sent[o + 2])
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("ambilight", "Ambilight", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, "ambilight")
            .setContentTitle("StickLab")
            .setContentText(getString(R.string.notif_text))
        .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        running = false
        val h = handler
        val t = thread
        val r = reader
        val restore = original
        try {
            display?.release()
        } catch (e: Throwable) {
        }
        try {
            projection?.stop()
        } catch (e: Throwable) {
        }
        h?.removeCallbacksAndMessages(null)
        // Zum Schluss die alte Farbe wiederherstellen
        h?.post {
            if (restore != null) {
                OdinService.run("settings put system $KEY '$restore'")
            }
            try {
                r?.close()
            } catch (e: Throwable) {
            }
            t?.quitSafely()
        }
        super.onDestroy()
    }
}