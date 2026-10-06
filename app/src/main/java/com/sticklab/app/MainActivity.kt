package com.sticklab.app

import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale
import kotlin.math.roundToInt
import android.widget.CompoundButton
import androidx.appcompat.app.AlertDialog

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var ambiButton: Button

    // Ein Reglerschritt entspricht 0,5 % Deadzone
    private val step = 0.005f

    // Wird aufgerufen, nachdem das Mitlesen erlaubt oder abgelehnt wurde
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            val i = Intent(this, AmbilightService::class.java)
                .putExtra(AmbilightService.EXTRA_CODE, result.resultCode)
                .putExtra(AmbilightService.EXTRA_DATA, data)
            startForegroundService(i)
            ambiButton.setText(R.string.ambi_off)
            status.setText(R.string.status_ambi_running)
        } else {
            status.setText(R.string.status_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Die App ist immer dunkel
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)

        // Deadzone
        setupDeadzone(R.id.seekLeft, R.id.labelLeft, getString(R.string.left_stick), "left_joystick_dead_zone_radius")
        setupDeadzone(R.id.seekRight, R.id.labelRight, getString(R.string.right_stick), "right_joystick_dead_zone_radius")

        // Ambilight ein und aus
        ambiButton = findViewById(R.id.btnAmbi)
        if (AmbilightService.running) ambiButton.setText(R.string.ambi_off)
        ambiButton.setOnClickListener {
            if (AmbilightService.running) {
                stopService(Intent(this, AmbilightService::class.java))
                ambiButton.setText(R.string.ambi_on)
                status.setText(R.string.status_ambi_off)
            } else {
                val mpm = getSystemService(MediaProjectionManager::class.java)
                val intent = if (Build.VERSION.SDK_INT >= 34) {
                    mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                } else {
                    mpm.createScreenCaptureIntent()
                }
                projectionLauncher.launch(intent)
            }
        }

        // Licht-Regler
        LightSettings.load(this)
        addLightSlider(R.string.brightness, " %", 20, 200, { LightSettings.bright }, { LightSettings.bright = it })
        addLightSlider(R.string.color_boost, " %", 0, 100, { LightSettings.boost }, { LightSettings.boost = it })
        addLightSlider(R.string.white_level, " %", 0, 100, { LightSettings.white }, { LightSettings.white = it })
        addLightSlider(R.string.speed, "", 1, 10, { LightSettings.speed }, { LightSettings.speed = it })
        addLightSlider(R.string.green_level, " %", 40, 100, { LightSettings.green }, { LightSettings.green = it })
        addLightSlider(R.string.blue_level, " %", 40, 100, { LightSettings.blue }, { LightSettings.blue = it })

        findViewById<Button>(R.id.btnReset).setOnClickListener {
            LightSettings.reset()
            LightSettings.save(this)
            recreate()
        }

        // Schalter: Aufnahme-Abfrage von Android überspringen
        val skipSwitch = findViewById<CompoundButton>(R.id.switchSkip)
        skipSwitch.isChecked = LightSettings.skipPrompt
        skipSwitch.setOnCheckedChangeListener { _, checked ->
            LightSettings.skipPrompt = checked
            LightSettings.save(this)
            val mode = if (checked) "allow" else "default"
            OdinService.run("appops set $packageName PROJECT_MEDIA $mode")
        }

        // Hinweise beim Start
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (!OdinService.isAvailable()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.unsupported_title)
                .setMessage(R.string.unsupported_text)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        } else if (!prefs.getBoolean("intro_shown", false)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.intro_title)
                .setMessage(R.string.intro_text)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    prefs.edit().putBoolean("intro_shown", true).apply()
                }
                .show()
        }
    }

    // Erzeugt einen Licht-Regler mit Beschriftung
    private fun addLightSlider(
        nameRes: Int, unit: String, min: Int, max: Int,
        get: () -> Int, set: (Int) -> Unit
    ) {
        val container = findViewById<LinearLayout>(R.id.lightContainer)
        val name = getString(nameRes)

        val label = TextView(this)
        label.textSize = 16f
        label.setPadding(0, (16 * resources.displayMetrics.density).roundToInt(), 0, 0)

        val seek = SeekBar(this)
        seek.min = min
        seek.max = max
        seek.progress = get()
        label.text = "$name: ${seek.progress}$unit"

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                label.text = "$name: $progress$unit"
                if (fromUser) {
                    set(progress)
                    LightSettings.save(this@MainActivity)
                }
            }

            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })

        container.addView(label)
        container.addView(
            seek,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    // Verbindet einen Regler mit einer Deadzone-Einstellung
    private fun setupDeadzone(seekId: Int, labelId: Int, name: String, key: String) {
        val seek = findViewById<SeekBar>(seekId)
        val label = findViewById<TextView>(labelId)

        val current = readSetting(key)
        if (current == null) {
            status.setText(R.string.status_read_failed)
        }
        seek.progress = ((current ?: 0.02f) / step).roundToInt().coerceIn(0, seek.max)
        label.text = labelText(name, seek.progress)

        // Wird kurz nach der letzten Reglerbewegung ausgeführt
        val apply = Runnable {
            val value = String.format(Locale.US, "%.3f", seek.progress * step)
            val ok = OdinService.run("settings put system $key $value")
            status.setText(if (ok) R.string.status_saved else R.string.status_error)
        }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                label.text = labelText(name, progress)
                if (fromUser) {
                    s.removeCallbacks(apply)
                    s.postDelayed(apply, 300)
                }
            }

            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
    }

    private fun labelText(name: String, progress: Int): String {
        return "$name: ${String.format("%.1f", progress * 0.5f)} %"
    }

    // Liest einen Wert aus den Systemeinstellungen
    private fun readSetting(key: String): Float? {
        return try {
            Settings.System.getString(contentResolver, key)?.toFloatOrNull()
        } catch (t: Throwable) {
            null
        }
    }
}