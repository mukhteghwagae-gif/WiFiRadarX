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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * FIX: TimeLapse now loads real session data and renders a network slice text view
 * that updates as the user scrubs the seek bar.  Session picker lets you choose
 * which saved scan session to replay.
 */
class TimeLapseActivity : AppCompatActivity() {

    private val vm: TimeLapseViewModel by viewModels()
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_timelapse)
        supportActionBar?.title = "Time Lapse"

        val seekBar        = findViewById<SeekBar>(R.id.seek_playback)
        val tvTime         = findViewById<TextView>(R.id.tv_playback_time)
        val tvSpeed        = findViewById<TextView>(R.id.tv_playback_speed)
        val btnPlay        = findViewById<Button>(R.id.btn_play_pause)
        val btnDiff        = findViewById<ToggleButton>(R.id.toggle_diff)
        val spinSpeed      = findViewById<Spinner>(R.id.spin_speed)
        val spinSession    = findViewById<Spinner>(R.id.spin_session_picker)
        val tvNetworkSlice = findViewById<TextView>(R.id.tv_network_slice)

        // ── Speed spinner ─────────────────────────────────────────────────
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

        // ── Seek bar ──────────────────────────────────────────────────────
        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, user: Boolean) {
                vm.setPosition(p / 100f)
                tvTime?.text = "$p% of session"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        btnPlay?.setOnClickListener  { vm.togglePlay() }
        btnDiff?.setOnCheckedChangeListener { _, _ -> vm.toggleDiff() }

        // ── Session picker ────────────────────────────────────────────────
        lifecycleScope.launch {
            vm.sessions.collectLatest { sessions ->
                val labels = listOf("— pick a session —") +
                    sessions.mapIndexed { i, s ->
                        val date = dateFormat.format(Date(s.startTime))
                        "Session ${i + 1}  ($date · ${s.apCount} APs · ${s.scanCount} scans)"
                    }
                spinSession?.adapter = ArrayAdapter(
                    this@TimeLapseActivity,
                    android.R.layout.simple_spinner_item, labels
                ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

                spinSession?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(p: AdapterView<*>?) {}
                    override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        val chosen = sessions.getOrNull(pos - 1)?.sessionId
                        vm.selectSession(chosen)
                        vm.setPosition(0f)
                        seekBar?.progress = 0
                        tvTime?.text = "0% of session"
                        if (chosen == null) {
                            tvNetworkSlice?.text = "Select a session and press Play."
                        }
                    }
                }
            }
        }

        // ── FIX: Network slice display ────────────────────────────────────
        lifecycleScope.launch {
            vm.sessionScans.collectLatest { scans ->
                if (scans.isEmpty()) {
                    tvNetworkSlice?.text = if (vm.selectedSessionId.value == null)
                        "Select a session above to begin playback."
                    else
                        "No data at this position — try scrubbing forward."
                    return@collectLatest
                }

                val pos = (vm.playbackPosition.value * 100).toInt()
                val diffOn = vm.diffMode.value
                val sb = StringBuilder()
                sb.append("── ${scans.size} networks at $pos% ──────────────\n\n")

                scans.sortedByDescending { it.rssi }.take(14).forEach { s ->
                    val band = when {
                        s.is6GHz -> "6G"
                        s.is5GHz -> "5G"
                        else     -> "2.4"
                    }
                    val barLen = ((s.rssi + 100) / 10).coerceIn(0, 8)
                    val bar = "█".repeat(barLen) + "░".repeat(8 - barLen)
                    val ssid = s.ssid.ifBlank { "<hidden>" }.take(16).padEnd(16)
                    sb.append("$ssid  $bar  ${s.rssi}dBm  Ch${s.channel.toString().padStart(3)} [$band]\n")
                }
                if (scans.size > 14) sb.append("\n…+${scans.size - 14} more networks")

                tvNetworkSlice?.text = sb.toString()
            }
        }

        // ── Playback animation loop ───────────────────────────────────────
        lifecycleScope.launch {
            while (isActive) {
                if (vm.isPlaying.value) {
                    val next = (vm.playbackPosition.value + 0.005f * vm.playbackSpeed.value)
                        .coerceAtMost(1f)
                    vm.setPosition(next)
                    seekBar?.progress = (next * 100).toInt()
                    tvTime?.text = "${(next * 100).toInt()}% of session"
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
