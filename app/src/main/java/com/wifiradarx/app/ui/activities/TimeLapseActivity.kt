package com.wifiradarx.app.ui.activities

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wifiradarx.app.R
import com.wifiradarx.app.ui.viewmodel.TimeLapseViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TimeLapseActivity : AppCompatActivity() {

    private val vm: TimeLapseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_timelapse)
        supportActionBar?.title = "Time Lapse"

        val seekBar   = findViewById<SeekBar>(R.id.seek_playback)
        val tvTime    = findViewById<TextView>(R.id.tv_playback_time)
        val tvSpeed   = findViewById<TextView>(R.id.tv_playback_speed)
        val btnPlay   = findViewById<Button>(R.id.btn_play_pause)
        val btnDiff   = findViewById<ToggleButton>(R.id.toggle_diff)
        val spinSpeed = findViewById<Spinner>(R.id.spin_speed)

        val speeds = listOf(0.5f, 1f, 2f, 5f, 10f, 50f)
        spinSpeed?.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            speeds.map { "${it}×" }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        spinSpeed?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p: AdapterView<*>?) {}
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                vm.setSpeed(speeds[pos])
                tvSpeed?.text = "${speeds[pos]}×"
            }
        }

        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, user: Boolean) {
                vm.setPosition(p / 100f)
                tvTime?.text = "${p}%"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        btnPlay?.setOnClickListener { vm.togglePlay() }
        btnDiff?.setOnCheckedChangeListener { _, _ -> vm.toggleDiff() }

        // Playback loop
        lifecycleScope.launch {
            while (isActive) {
                if (vm.isPlaying.value) {
                    val next = (vm.playbackPosition.value + 0.005f * vm.playbackSpeed.value)
                        .coerceAtMost(1f)
                    vm.setPosition(next)
                    seekBar?.progress = (next * 100).toInt()
                    if (next >= 1f) vm.togglePlay()
                }
                delay(50L)
            }
        }

        lifecycleScope.launch {
            vm.isPlaying.collect { playing ->
                btnPlay?.text = if (playing) "⏸ Pause" else "▶ Play"
            }
        }
    }
}
