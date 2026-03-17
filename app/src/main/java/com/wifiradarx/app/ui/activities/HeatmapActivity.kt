package com.wifiradarx.app.ui.activities

import android.graphics.*
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.wifiradarx.app.databinding.ActivityHeatmapBinding
import com.wifiradarx.app.intelligence.IdwInterpolator
import com.wifiradarx.app.intelligence.DeadZoneDetector
import kotlin.math.*

class HeatmapActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHeatmapBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHeatmapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Signal Heatmap"
        }

        val heatmapView = HeatmapCanvasView(this)
        binding.heatmapContainer.addView(
            heatmapView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        binding.heatmapLegendMin.text = "-100 dBm (weak)"
        binding.heatmapLegendMax.text = "-30 dBm (strong)"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    inner class HeatmapCanvasView(context: android.content.Context) : View(context) {
        private val idw = IdwInterpolator()
        private val samplePoints = listOf(
            IdwInterpolator.Point3D(0.1f, 0f, 0.1f, -45f),
            IdwInterpolator.Point3D(0.8f, 0f, 0.2f, -75f),
            IdwInterpolator.Point3D(0.5f, 0f, 0.5f, -60f),
            IdwInterpolator.Point3D(0.2f, 0f, 0.8f, -85f),
            IdwInterpolator.Point3D(0.9f, 0f, 0.9f, -50f),
            IdwInterpolator.Point3D(0.3f, 0f, 0.4f, -55f)
        )
        private val paint = Paint()

        override fun onDraw(canvas: Canvas) {
            val W = width.toFloat()
            val H = height.toFloat()
            val resolution = 0.02f
            var x = 0f
            while (x <= 1f) {
                var z = 0f
                while (z <= 1f) {
                    val rssi = idw.predict(x, 0f, z, samplePoints)
                    val t = ((rssi + 100f) / 70f).coerceIn(0f, 1f)
                    paint.color = rssiToColor(t)
                    canvas.drawRect(
                        x * W, z * H,
                        (x + resolution) * W + 1, (z + resolution) * H + 1,
                        paint
                    )
                    z += resolution
                }
                x += resolution
            }
            // Draw sample points
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            for (p in samplePoints) {
                canvas.drawCircle(p.x * W, p.z * H, 12f, paint)
            }
        }

        private fun rssiToColor(t: Float): Int {
            // Blue (weak) → Cyan → Green → Yellow → Red (strong)
            return when {
                t < 0.25f -> {
                    val f = t / 0.25f
                    Color.rgb(0, (f * 255).toInt(), 255)
                }
                t < 0.5f -> {
                    val f = (t - 0.25f) / 0.25f
                    Color.rgb(0, 255, (255 * (1 - f)).toInt())
                }
                t < 0.75f -> {
                    val f = (t - 0.5f) / 0.25f
                    Color.rgb((f * 255).toInt(), 255, 0)
                }
                else -> {
                    val f = (t - 0.75f) / 0.25f
                    Color.rgb(255, (255 * (1 - f)).toInt(), 0)
                }
            }
        }
    }
}
