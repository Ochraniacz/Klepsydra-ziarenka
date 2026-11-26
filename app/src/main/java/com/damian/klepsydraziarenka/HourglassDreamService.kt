package com.damian.klepsydraziarenka

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.BatteryManager
import android.service.dreams.DreamService
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

class HourglassDreamService : DreamService() {

    private var hourglassView: HourglassView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        isInteractive = false
        isFullscreen = true
        isScreenBright = false

        window?.attributes = window?.attributes?.apply {
            screenBrightness = 0.05f
        }

        val view = HourglassView(this)
        hourglassView = view
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        hourglassView?.startAnimation()
    }

    override fun onDreamingStopped() {
        hourglassView?.stopAnimation()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        hourglassView = null
        super.onDetachedFromWindow()
    }

    private class HourglassView(context: Context) : View(context) {

        private var isRunning = false
        private var startTimeMs = 0L
        private var lastBatteryCheckMs = 0L

        private var batteryLevelFraction = 0.5f // 0.0f .. 1.0f
        private var isCharging = false

        private val pourDurationMs = 60_000L // 60 s przesypywania w dół ekranu
        private val flipDurationMs = 1_200L  // 1,2 s obrotu klepsydry o 180°
        private val fullCycleMs = pourDurationMs + flipDurationMs

        private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 200, 220, 240)
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(176, 125, 79)
            style = Paint.Style.FILL
        }

        private val sandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(224, 169, 109)
            style = Paint.Style.FILL
        }

        private val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(244, 213, 141)
            style = Paint.Style.FILL
        }

        private val barTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 255, 255, 255)
            style = Paint.Style.FILL
        }

        private val barFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private val barBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 220, 230, 240)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        private val sandPath = Path()
        private val baseGlassPath = Path()
        private val rotatedGlassPath = Path()
        private val rotateMatrix = Matrix()
        private val trackRect = RectF()
        private val fillRect = RectF()
        private val random = Random(System.currentTimeMillis())

        private val frameRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                val now = System.currentTimeMillis()
                if (now - lastBatteryCheckMs > 5_000L || lastBatteryCheckMs == 0L) {
                    updateBatteryStatus()
                    lastBatteryCheckMs = now
                }
                invalidate()
                postDelayed(this, 16L)
            }
        }

        private fun updateBatteryStatus() {
            val batteryIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ) ?: return

            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                batteryLevelFraction = (level.toFloat() / scale.toFloat()).coerceIn(0f, 1f)
            }

            val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        }

        private fun redToGreenColor(fraction: Float): Int {
            val t = fraction.coerceIn(0f, 1f)
            // Płynne przejście: czerwony (220, 45, 45) -> żółty (235, 195, 35) -> zielony (45, 205, 85)
            val r: Int
            val g: Int
            val b: Int
            if (t < 0.5f) {
                val k = t * 2f
                r = (220 + (235 - 220) * k).toInt()
                g = (45 + (195 - 45) * k).toInt()
                b = (45 + (35 - 45) * k).toInt()
            } else {
                val k = (t - 0.5f) * 2f
                r = (235 + (45 - 235) * k).toInt()
                g = (195 + (205 - 195) * k).toInt()
                b = (35 + (85 - 35) * k).toInt()
            }
            val alpha = if (isCharging) 220 else 175
            return Color.argb(alpha, r, g, b)
        }

        fun startAnimation() {
            if (!isRunning) {
                isRunning = true
                startTimeMs = System.currentTimeMillis()
                lastBatteryCheckMs = 0L
                updateBatteryStatus()
                post(frameRunnable)
            }
        }

        fun stopAnimation() {
            isRunning = false
            removeCallbacks(frameRunnable)
        }

        private fun easeInOutCubic(t: Float): Float {
            val x = t.coerceIn(0f, 1f)
            return if (x < 0.5f) {
                4f * x * x * x
            } else {
                val f = -2f * x + 2f
                1f - (f * f * f) / 2f
            }
        }

        private fun buildGlassPath(
            path: Path,
            cx: Float,
            cy: Float,
            halfW: Float,
            halfH: Float,
            neckHalfW: Float
        ) {
            val topY = cy - halfH
            val bottomY = cy + halfH

            path.reset()
            path.moveTo(cx - halfW, topY)
            path.lineTo(cx + halfW, topY)
            path.cubicTo(
                cx + halfW, topY + halfH * 0.45f,
                cx + neckHalfW, cy - halfH * 0.12f,
                cx + neckHalfW, cy
            )
            path.cubicTo(
                cx + neckHalfW, cy + halfH * 0.12f,
                cx + halfW, bottomY - halfH * 0.45f,
                cx + halfW, bottomY
            )
            path.lineTo(cx - halfW, bottomY)
            path.cubicTo(
                cx - halfW, bottomY - halfH * 0.45f,
                cx - neckHalfW, cy + halfH * 0.12f,
                cx - neckHalfW, cy
            )
            path.cubicTo(
                cx - neckHalfW, cy - halfH * 0.12f,
                cx - halfW, topY + halfH * 0.45f,
                cx - halfW, topY
            )
            path.close()
        }

        private fun drawTopBatteryBar(canvas: Canvas, w: Float, h: Float, shiftX: Float, elapsed: Long) {
            val barWidth = w * 0.52f
            val barHeight = (h * 0.014f).coerceIn(10f, 22f)
            val left = (w - barWidth) / 2f + shiftX * 0.6f
            val right = left + barWidth
            val top = (h * 0.055f) + (sin(elapsed / 11000.0).toFloat() * (h * 0.01f))
            val bottom = top + barHeight
            val radius = barHeight / 2f

            trackRect.set(left, top, right, bottom)
            canvas.drawRoundRect(trackRect, radius, radius, barTrackPaint)

            val fillWidth = (barWidth * batteryLevelFraction).coerceAtLeast(if (batteryLevelFraction > 0f) barHeight else 0f)
            if (fillWidth > 0f) {
                fillRect.set(left, top, left + fillWidth, bottom)
                barFillPaint.color = redToGreenColor(batteryLevelFraction)
                canvas.drawRoundRect(fillRect, radius, radius, barFillPaint)
            }

            canvas.drawRoundRect(trackRect, radius, radius, barBorderPaint)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.BLACK)

            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val elapsed = System.currentTimeMillis() - startTimeMs
            val cycleIndex = (elapsed / fullCycleMs).toInt()
            val cyclePosMs = elapsed % fullCycleMs

            val isFlipping = cyclePosMs >= pourDurationMs
            val pourProgress = if (!isFlipping) {
                (cyclePosMs.toFloat() / pourDurationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                1f
            }

            val flipT = if (isFlipping) {
                easeInOutCubic(((cyclePosMs - pourDurationMs).toFloat() / flipDurationMs.toFloat()).coerceIn(0f, 1f))
            } else {
                0f
            }

            val glassAngle = (cycleIndex * 180f) + (180f * flipT)

            // Pixel-shift chroniący matrycę OLED
            val shiftX = sin(elapsed / 9000.0).toFloat() * (w * 0.03f)
            val shiftY = sin(elapsed / 13000.0).toFloat() * (h * 0.03f)

            // Pasek naładowania na górze ekranu
            drawTopBatteryBar(canvas, w, h, shiftX, elapsed)

            val cx = w / 2f + shiftX
            val cy = h / 2f + shiftY

            val hourglassHalfWidth = minOf(w, h) * 0.22f
            val hourglassHalfHeight = minOf(w, h) * 0.34f
            val neckHalfWidth = hourglassHalfWidth * 0.09f
            val capHeight = hourglassHalfHeight * 0.09f

            val topY = cy - hourglassHalfHeight
            val bottomY = cy + hourglassHalfHeight

            buildGlassPath(baseGlassPath, cx, cy, hourglassHalfWidth, hourglassHalfHeight, neckHalfWidth)
            rotateMatrix.setRotate(glassAngle, cx, cy)
            baseGlassPath.transform(rotateMatrix, rotatedGlassPath)

            val topFill = if (isFlipping) 1f else (1f - pourProgress)
            val bottomFill = if (isFlipping) 0f else pourProgress

            // 1. Piasek przycięty do aktualnego kształtu obróconego szkła
            canvas.save()
            canvas.clipPath(rotatedGlassPath)

            if (topFill > 0.01f) {
                val sandTopY = cy - hourglassHalfHeight * topFill
                sandPath.reset()
                sandPath.moveTo(cx - hourglassHalfWidth * 1.4f, sandTopY)
                sandPath.quadTo(cx, sandTopY + 18f * (1f - topFill), cx + hourglassHalfWidth * 1.4f, sandTopY)
                sandPath.lineTo(cx + hourglassHalfWidth * 1.4f, cy)
                sandPath.lineTo(cx - hourglassHalfWidth * 1.4f, cy)
                sandPath.close()
                canvas.drawPath(sandPath, sandPaint)
            }

            if (bottomFill > 0.01f) {
                val sandSurfaceY = bottomY - hourglassHalfHeight * bottomFill
                val conePeakY = (sandSurfaceY - 26f * (1f - bottomFill * 0.6f)).coerceAtLeast(cy + 4f)
                sandPath.reset()
                sandPath.moveTo(cx - hourglassHalfWidth * 1.4f, sandSurfaceY + 10f)
                sandPath.lineTo(cx, conePeakY)
                sandPath.lineTo(cx + hourglassHalfWidth * 1.4f, sandSurfaceY + 10f)
                sandPath.lineTo(cx + hourglassHalfWidth * 1.4f, bottomY + hourglassHalfHeight)
                sandPath.lineTo(cx - hourglassHalfWidth * 1.4f, bottomY + hourglassHalfHeight)
                sandPath.close()
                canvas.drawPath(sandPath, sandPaint)
            }

            // 2. Spadające ziarenka — zawsze lecą w dół ekranu podczas sypania
            if (!isFlipping && pourProgress < 0.995f) {
                val streamBottom = bottomY - hourglassHalfHeight * bottomFill
                canvas.drawLine(cx, cy, cx, streamBottom, grainPaint)

                for (i in 0 until 14) {
                    val t = ((elapsed / 450.0 + i * 0.07) % 1.0).toFloat()
                    val gy = cy + (streamBottom - cy) * t
                    val gx = cx + (random.nextFloat() - 0.5f) * (neckHalfWidth * 1.2f)
                    canvas.drawCircle(gx, gy, 2.5f, grainPaint)
                }
            }

            canvas.restore()

            // 3. Szkło i podstawy obrócone o glassAngle
            canvas.save()
            canvas.rotate(glassAngle, cx, cy)
            canvas.drawPath(baseGlassPath, glassPaint)

            val capHalfWidth = hourglassHalfWidth * 1.15f
            canvas.drawRoundRect(
                cx - capHalfWidth,
                topY - capHeight,
                cx + capHalfWidth,
                topY,
                10f,
                10f,
                framePaint
            )
            canvas.drawRoundRect(
                cx - capHalfWidth,
                bottomY,
                cx + capHalfWidth,
                bottomY + capHeight,
                10f,
                10f,
                framePaint
            )
            canvas.restore()
        }
    }
}
