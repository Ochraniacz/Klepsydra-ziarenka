package com.damian.klepsydraziarenka

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
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

        private val sandPath = Path()
        private val baseGlassPath = Path()
        private val rotatedGlassPath = Path()
        private val rotateMatrix = Matrix()
        private val random = Random(System.currentTimeMillis())

        private val frameRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                invalidate()
                postDelayed(this, 16L)
            }
        }

        fun startAnimation() {
            if (!isRunning) {
                isRunning = true
                startTimeMs = System.currentTimeMillis()
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

            // Kąt szkła: w trakcie obrotu rośnie płynnie o 180°, po obrocie zostaje stały (0° lub 180°)
            val glassAngle = (cycleIndex * 180f) + (180f * flipT)

            // Pixel-shift chroniący matrycę OLED
            val shiftX = sin(elapsed / 9000.0).toFloat() * (w * 0.03f)
            val shiftY = sin(elapsed / 13000.0).toFloat() * (h * 0.03f)

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

            // Podczas obrotu pełna bańka jest na górze (wędruje z dołu razem z klepsydrą),
            // a podczas sypania górna bańka opróżnia się w dół ekranu, a dolna rośnie od dołu.
            val topFill = if (isFlipping) 1f else (1f - pourProgress)
            val bottomFill = if (isFlipping) 0f else pourProgress

            // 1. Piasek przycięty do aktualnego kształtu obróconego szkła
            canvas.save()
            canvas.clipPath(rotatedGlassPath)

            // Górna połowa ekranu (ubywa w dół)
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

            // Dolna połowa ekranu (rośnie od dołu w górę)
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
