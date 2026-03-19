package com.wifiradarx.app.ui.activities

import android.os.Bundle
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wifiradarx.app.R
import com.wifiradarx.app.ui.viewmodel.SettingsViewModel
import com.wifiradarx.app.utils.AppSettings
import com.wifiradarx.app.workers.BackgroundMonitorWorker
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private val vm: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.title = "Settings"

        val seekInterval  = findViewById<SeekBar>(R.id.seek_scan_interval)
        val tvInterval    = findViewById<TextView>(R.id.tv_scan_interval_val)
        val seekIdw       = findViewById<SeekBar>(R.id.seek_idw_power)
        val tvIdw         = findViewById<TextView>(R.id.tv_idw_power_val)
        val seekThreshold = findViewById<SeekBar>(R.id.seek_dead_threshold)
        val tvThreshold   = findViewById<TextView>(R.id.tv_dead_threshold_val)
        val switchBg      = findViewById<Switch>(R.id.switch_background)
        val spinPreset    = findViewById<Spinner>(R.id.spin_env_preset)
        val cbHeatmap     = findViewById<CheckBox>(R.id.cb_ar_heatmap)
        val cbVoxels      = findViewById<CheckBox>(R.id.cb_ar_voxels)
        val cbArrow       = findViewById<CheckBox>(R.id.cb_ar_arrow)
        val cbThreats     = findViewById<CheckBox>(R.id.cb_ar_threats)
        val btnSave       = findViewById<Button>(R.id.btn_save_settings)

        val presets = listOf("FREE_SPACE", "OFFICE", "HOME", "INDUSTRIAL", "OUTDOOR")
        spinPreset?.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, presets)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Populate controls from current settings
        lifecycleScope.launch {
            vm.settings.collect { s ->
                seekInterval?.progress = s.scanIntervalS.coerceIn(1, 60)
                tvInterval?.text       = "${s.scanIntervalS}s"
                seekIdw?.progress      = ((s.idwPower - 1f) * 10).toInt().coerceIn(0, 40)
                tvIdw?.text            = "%.1f".format(s.idwPower)
                seekThreshold?.progress = (-s.deadZoneThreshold - 50f).toInt().coerceIn(0, 50)
                tvThreshold?.text      = "${s.deadZoneThreshold.toInt()} dBm"
                switchBg?.isChecked   = s.backgroundMonitoring
                cbHeatmap?.isChecked  = s.arHeatmapEnabled
                cbVoxels?.isChecked   = s.arVoxelsEnabled
                cbArrow?.isChecked    = s.arArrowEnabled
                cbThreats?.isChecked  = s.arThreatsEnabled
                spinPreset?.setSelection(presets.indexOf(s.environmentPreset).coerceAtLeast(0))
            }
        }

        // Live label updates
        seekInterval?.setOnSeekBarChangeListener(simpleChange { p ->
            tvInterval?.text = "${p.coerceAtLeast(1)}s"
        })
        seekIdw?.setOnSeekBarChangeListener(simpleChange { p ->
            tvIdw?.text = "%.1f".format(1f + p * 0.1f)
        })
        seekThreshold?.setOnSeekBarChangeListener(simpleChange { p ->
            tvThreshold?.text = "${-(50 + p)} dBm"
        })

        btnSave?.setOnClickListener {
            val newSettings = AppSettings.Settings(
                scanIntervalS        = seekInterval?.progress?.coerceAtLeast(1) ?: 5,
                idwPower             = 1f + (seekIdw?.progress ?: 10) * 0.1f,
                deadZoneThreshold    = -(50f + (seekThreshold?.progress ?: 25)),
                backgroundMonitoring = switchBg?.isChecked ?: false,
                environmentPreset    = presets[spinPreset?.selectedItemPosition ?: 1],
                arHeatmapEnabled     = cbHeatmap?.isChecked ?: true,
                arVoxelsEnabled      = cbVoxels?.isChecked ?: false,
                arArrowEnabled       = cbArrow?.isChecked ?: true,
                arThreatsEnabled     = cbThreats?.isChecked ?: true
            )
            vm.save(newSettings)
            if (newSettings.backgroundMonitoring) BackgroundMonitorWorker.schedule(this)
            else BackgroundMonitorWorker.cancel(this)
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun simpleChange(block: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar, p: Int, u: Boolean) = block(p)
        override fun onStartTrackingTouch(sb: SeekBar) {}
        override fun onStopTrackingTouch(sb: SeekBar) {}
    }
}
