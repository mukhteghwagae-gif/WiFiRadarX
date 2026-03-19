package com.wifiradarx.app.ui.activities

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wifiradarx.app.R
import com.wifiradarx.app.ui.viewmodel.DeviceHunterViewModel
import kotlinx.coroutines.launch

/**
 * FIX — Device Hunter now uses a real 5×5 tap-grid instead of a fake walkX counter.
 *
 * How to use:
 * 1. Walk to a room corner / location.
 * 2. Tap the corresponding cell on the grid (each cell ≈ 1 m).
 * 3. The current strongest WiFi RSSI is captured as the interference score.
 * 4. After ≥ 3 taps the triangulator estimates the signal source location.
 *
 * This gives real signal-strength–vs–position data to the IDW triangulator.
 */
class DeviceHunterActivity : AppCompatActivity() {

    private val vm: DeviceHunterViewModel by viewModels()

    // Grid dimensions in virtual metres
    private val GRID_COLS = 5
    private val GRID_ROWS = 5
    private val CELL_M    = 1f   // each cell represents 1 metre

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_hunter)
        supportActionBar?.title = "Device Hunter"

        val tvSource = findViewById<TextView>(R.id.tv_source_info)
        val tvCount  = findViewById<TextView>(R.id.tv_measurement_count)
        val btnReset = findViewById<Button>(R.id.btn_reset_hunt)

        // FIX: Replace auto-walk with an interactive tap grid
        val gridContainer = buildTapGrid(tvCount)

        // Try to add the grid into the layout – look for a FrameLayout placeholder
        val placeholder = findViewById<FrameLayout>(R.id.fl_hunter_grid)
        placeholder?.addView(gridContainer)

        // ── Observe results ───────────────────────────────────────────────
        lifecycleScope.launch {
            vm.source.collect { src ->
                tvSource?.text = if (src != null) {
                    "Source: ${src.type.displayName}\n" +
                    "Est. location: (${src.x.fmt(1)} m, ${src.z.fmt(1)} m)\n" +
                    "Confidence: ${(src.confidence * 100).toInt()}%\n\n" +
                    "Tap grid cells at different locations\nto improve accuracy."
                } else {
                    "Tap grid cells at your current position\nto add measurements (need ≥ 3)."
                }
            }
        }

        lifecycleScope.launch {
            vm.measurementCount.collect { count ->
                tvCount?.text = "$count / 3+ measurements collected"
            }
        }

        btnReset?.setOnClickListener {
            vm.reset()
            // Clear visual feedback on all cells
            gridContainer.children().forEach { row ->
                (row as? LinearLayout)?.children()?.forEach { cell ->
                    (cell as? Button)?.setBackgroundColor(0xFF1A1A2E.toInt())
                }
            }
        }
    }

    private fun buildTapGrid(tvCount: TextView?): LinearLayout {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Label
        val label = TextView(this).apply {
            text = "← Tap your current position on the map →"
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 8)
        }
        outer.addView(label)

        for (row in 0 until GRID_ROWS) {
            val rowView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            for (col in 0 until GRID_COLS) {
                val cellX = col * CELL_M
                val cellZ = (GRID_ROWS - 1 - row) * CELL_M  // row 0 = top = far north

                val cell = Button(this).apply {
                    text = "${col + 1},${GRID_ROWS - row}"
                    textSize = 10f
                    setBackgroundColor(0xFF1A1A2E.toInt())
                    setTextColor(Color.parseColor("#00D4FF"))
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    ).apply { setMargins(2, 2, 2, 2) }

                    setOnClickListener {
                        val maxRssi = vm.liveScanResults.value
                            .maxOfOrNull { it.level } ?: -80
                        vm.addMeasurement(cellX, cellZ, maxRssi)
                        setBackgroundColor(rssiColor(maxRssi))
                        text = "${maxRssi}dBm"
                    }
                }
                rowView.addView(cell)
            }
            outer.addView(rowView)
        }

        // Compass hint row
        val compass = TextView(this).apply {
            text = "N↑   Grid = 5×5 m   ↓S"
            setTextColor(Color.parseColor("#555555"))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }
        outer.addView(compass)

        return outer
    }

    private fun rssiColor(rssi: Int): Int = when {
        rssi > -50  -> Color.parseColor("#00FF88")
        rssi > -65  -> Color.parseColor("#FFCC00")
        rssi > -80  -> Color.parseColor("#FF8800")
        else        -> Color.parseColor("#FF4444")
    }

    private fun Float.fmt(d: Int) = "%.${d}f".format(this)

    // Simple extension to iterate LinearLayout children
    private fun LinearLayout.children() = (0 until childCount).map { getChildAt(it) }
}
