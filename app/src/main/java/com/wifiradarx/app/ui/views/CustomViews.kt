package com.wifiradarx.app.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.*

// ─── RadarView ─────────────────────────────────────────────────────────────────
class RadarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class RadarBlip(
        val bssid: String,
        val ssid: String,
        val rssi: Int,
        val angleDeg: Float,
        val isBle: Boolean = false
    )

    var blips: List<RadarBlip> = emptyList()
        set(value) { field = value; invalidate() }

    var rangeDbm: Int = 100
        set(value) { field = value.coerceIn(20, 120); invalidate() }

    private var sweepAngle = 0f
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = null
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#1A00D4FF")
        strokeWidth = 1.5f
    }
    private val ringLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4400D4FF")
        textSize = 24f
    }
    private val blipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
    }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2200D4FF")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val sweepRunnable = object : Runnable {
        override fun run() {
            sweepAngle = (sweepAngle + 2f) % 360f
            invalidate()
            postDelayed(this, 16)
        }
    }

    private var scaleFactor = 1f
    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(0.5f, 3f)
                invalidate()
                return true
            }
        })

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(sweepRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(sweepRunnable)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxR = (minOf(width, height) / 2f - 20f) * scaleFactor

        canvas.save()
        canvas.translate(cx, cy)

        // Background
        canvas.drawColor(Color.parseColor("#07090F"))

        // Rings (25%, 50%, 75%, 100%)
        for (i in 1..4) {
            val r = maxR * i / 4f
            canvas.drawCircle(0f, 0f, r, ringPaint)
            val label = "${rangeDbm * i / 4} dBm"
            canvas.drawText(label, r + 4f, -4f, ringLabelPaint)
        }

        // Crosshairs
        canvas.drawLine(-maxR, 0f, maxR, 0f, crosshairPaint)
        canvas.drawLine(0f, -maxR, 0f, maxR, crosshairPaint)

        // Sweep gradient
        val sweepShader = SweepGradient(
            0f, 0f,
            intArrayOf(
                Color.TRANSPARENT,
                Color.parseColor("#0000D4FF"),
                Color.parseColor("#5500D4FF"),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.05f, 0.15f, 0.2f)
        )
        val sweepMatrix = Matrix()
        sweepMatrix.setRotate(sweepAngle)
        sweepShader.setLocalMatrix(sweepMatrix)
        sweepPaint.shader = sweepShader
        canvas.drawCircle(0f, 0f, maxR, sweepPaint)

        // Blips
        for (blip in blips) {
            val norm = ((blip.rssi + rangeDbm).toFloat() / rangeDbm).coerceIn(0f, 1f)
            val r = maxR * (1f - norm)
            val angleRad = Math.toRadians(blip.angleDeg.toDouble())
            val bx = (sin(angleRad) * r).toFloat()
            val by = (-cos(angleRad) * r).toFloat()

            val color = rssiColor(blip.rssi)
            blipPaint.color = color
            blipPaint.alpha = 200
            val blipR = if (blip.isBle) 10f else 14f
            canvas.drawCircle(bx, by, blipR, blipPaint)

            // Glow ring
            blipPaint.style = Paint.Style.STROKE
            blipPaint.strokeWidth = 2f
            blipPaint.alpha = 80
            canvas.drawCircle(bx, by, blipR + 4f, blipPaint)
            blipPaint.style = Paint.Style.FILL

            // Label
            val truncated = if (blip.ssid.length > 10) blip.ssid.take(9) + "…" else blip.ssid
            canvas.drawText(truncated, bx + blipR + 3f, by + 4f, labelPaint)
        }

        canvas.restore()
    }

    private fun rssiColor(rssi: Int): Int {
        val norm = ((rssi + 100f) / 100f).coerceIn(0f, 1f)
        return when {
            norm < 0.33f -> Color.parseColor("#4444FF")
            norm < 0.66f -> Color.parseColor("#00D4FF")
            else -> Color.parseColor("#00FF88")
        }
    }
}

// ─── HeatmapView ───────────────────────────────────────────────────────────────
class HeatmapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class HeatPoint(val x: Float, val y: Float, val rssi: Int)

    var points: List<HeatPoint> = emptyList()
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        strokeWidth = 1f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88FFFFFF")
        textSize = 22f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#07090F"))

        if (points.isEmpty()) {
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("No scan data", width / 2f, height / 2f, labelPaint)
            return
        }

        val pad = 40f
        val xs = points.map { it.x }
        val ys = points.map { it.y }
        val xMin = xs.minOrNull()!!; val xMax = xs.maxOrNull()!!
        val yMin = ys.minOrNull()!!; val yMax = ys.maxOrNull()!!

        fun toScreenX(v: Float) = pad + (v - xMin) / (xMax - xMin + 0.001f) * (width - 2 * pad)
        fun toScreenY(v: Float) = pad + (v - yMin) / (yMax - yMin + 0.001f) * (height - 2 * pad)

        // Axes
        canvas.drawLine(pad, height - pad, width - pad, height - pad, axisPaint)
        canvas.drawLine(pad, pad, pad, height - pad, axisPaint)

        // Points with radial gradient
        for (p in points) {
            val sx = toScreenX(p.x)
            val sy = toScreenY(p.y)
            val norm = ((p.rssi + 100f) / 100f).coerceIn(0f, 1f)
            val radius = 20f + norm * 30f
            val color = heatColor(norm)

            val shader = RadialGradient(
                sx, sy, radius,
                intArrayOf(color, Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
            paint.shader = shader
            canvas.drawCircle(sx, sy, radius, paint)
        }
        paint.shader = null

        // Dot overlay
        paint.color = Color.WHITE
        paint.alpha = 160
        for (p in points) {
            canvas.drawCircle(toScreenX(p.x), toScreenY(p.y), 4f, paint)
        }
    }

    private fun heatColor(norm: Float): Int {
        val r = (255 * minOf(1f, 2f * (1f - norm))).toInt()
        val g = (255 * minOf(1f, 2f * norm)).toInt()
        return Color.argb(180, r, g, 40)
    }
}
