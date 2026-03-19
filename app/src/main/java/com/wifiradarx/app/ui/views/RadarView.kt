package com.wifiradarx.app.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class RadarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Blip(
        val angle: Float,      // degrees, 0 = top
        val distance: Float,   // 0..1 normalised
        val rssi: Int,
        val label: String,
        val isBle: Boolean = false
    )

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 0, 212, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 0, 212, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val blipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 0, 255, 170)
        textSize = 24f
    }
    private val rssiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 200, 200, 200)
        textSize = 20f
    }

    private var sweepAngle = 0f
    private val blips = mutableListOf<Blip>()
    private val fadeTrails = mutableMapOf<String, Float>() // label -> alpha

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = (min(cx, cy) - 30f).coerceAtLeast(10f)

        canvas.save()
        canvas.translate(cx, cy)

        // Radar rings
        for (i in 1..4) {
            canvas.drawCircle(0f, 0f, radius * (i / 4f), gridPaint)
        }

        // Crosshairs
        canvas.drawLine(-radius, 0f, radius, 0f, crosshairPaint)
        canvas.drawLine(0f, -radius, 0f, radius, crosshairPaint)
        canvas.drawLine(
            (-radius * cos(Math.toRadians(45.0))).toFloat(),
            (-radius * sin(Math.toRadians(45.0))).toFloat(),
            (radius * cos(Math.toRadians(45.0))).toFloat(),
            (radius * sin(Math.toRadians(45.0))).toFloat(),
            crosshairPaint
        )
        canvas.drawLine(
            (-radius * cos(Math.toRadians(135.0))).toFloat(),
            (-radius * sin(Math.toRadians(135.0))).toFloat(),
            (radius * cos(Math.toRadians(135.0))).toFloat(),
            (radius * sin(Math.toRadians(135.0))).toFloat(),
            crosshairPaint
        )

        // Sweep gradient
        val sweepColors = intArrayOf(
            Color.TRANSPARENT,
            Color.argb(20, 0, 212, 255),
            Color.argb(60, 0, 212, 255),
            Color.argb(120, 0, 255, 170)
        )
        sweepPaint.shader = SweepGradient(0f, 0f, sweepColors, floatArrayOf(0f, 0.6f, 0.85f, 1f))
        canvas.save()
        canvas.rotate(sweepAngle - 90f)
        canvas.drawCircle(0f, 0f, radius, sweepPaint)
        canvas.restore()

        // Blips
        for (blip in blips) {
            val rad = Math.toRadians((blip.angle - 90).toDouble())
            val bx = (blip.distance * radius * cos(rad)).toFloat()
            val by = (blip.distance * radius * sin(rad)).toFloat()

            val t = ((blip.rssi + 100f) / 70f).coerceIn(0f, 1f)
            val r = (255 * t).toInt()
            val g = (255 * (1f - t)).toInt()
            val blipColor = Color.argb(220, r, g, 80)
            blipPaint.color = blipColor

            // Glow
            blipPaint.maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(bx, by, 10f, blipPaint)
            blipPaint.maskFilter = null
            canvas.drawCircle(bx, by, 5f, blipPaint)

            // Label
            canvas.drawText(blip.label, bx + 14f, by - 6f, labelPaint)
            canvas.drawText("${blip.rssi} dBm", bx + 14f, by + 16f, rssiPaint)

            // BLE indicator
            if (blip.isBle) {
                val blePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(200, 100, 100, 255)
                    textSize = 18f
                }
                canvas.drawText("BLE", bx + 14f, by + 34f, blePaint)
            }
        }

        canvas.restore()

        sweepAngle = (sweepAngle + 1.5f) % 360f
        postInvalidateDelayed(16)
    }

    fun updateBlips(newBlips: List<Blip>) {
        blips.clear()
        blips.addAll(newBlips)
    }
}
