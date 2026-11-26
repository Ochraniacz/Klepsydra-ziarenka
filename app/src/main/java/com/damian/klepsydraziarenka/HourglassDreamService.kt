package com.damian.klepsydraziarenka

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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

        private val pourDurationMs = 60_000L // 1 minuta przesypywania piasku
        private val flipDurationMs = 1_200L  // 1.2 s płynnego obrotu o 180°
        private val fullCycleMs = pourDurationMs + flipDurationMs

        private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, 200, 220, 240)
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
        private val clipPath = Path()
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

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.BLACK)

            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val elapsed = System.currentTimeMillis() - startTimeMs
            val cycleIndex = (elapsed / fullCycleMs).toInt()
            val cyclePosMs = elapsed % fullCycleMs

            // Po każdym pełnym obrocie (nieparzysty cykl) klepsydra jest odwrócona o 180°
            val baseAngle = if (cycleIndex % 2 == 0) 0f else 180f

            val pourProgress: Float
            val flipExtraAngle: Float

            if (cyclePosMs < pourDurationMs) {
                // Etap 1: 60 sekund sypania ziarenek
                pourProgress = (cyclePosMs.toFloat() / pourDurationMs.toFloat()).coerceIn(0f, 1f)
                flipExtraAngle = 0f
            } else {
                // Etap 2: płynny obrót o 180°
                pourProgress = 1f
                val flipT = ((cyclePosMs - pourDurationMs).toFloat() / flipDurationMs.toFloat()).coerceIn(0f, 1f)
                flipExtraAngle = 180f * easeInOutCubic(flipT)
            }

            // Delikatny ruch (pixel-shift) chroniący OLED przed wypaleniem
            val shiftX = sin(elapsed / 9000.0).toFloat() * (w * 0.03f)
            val shiftY = sin(elapsed / 13000.0).toFloat() * (h * 0.03f)

            val cx = w / 2f + shiftX
            val cy = h / 2f + shiftY

            canvas.save()
            canvas.rotate(baseAngle + flipExtraAngle, cx, cy)

            val hourglassHalfWidth = minOf(w, h) * 0.22f
            val hourglassHalfHeight = minOf(w, h) * 0.34f
            val neckHalfWidth = hourglassHalfWidth * 0.09f
            val capHeight = hourglassHalfHeight * 0.09f

            val topY = cy - hourglassHalfHeight
            val bottomY = cy + hourglassHalfHeight

            // Górny piasek (ubywa w ciągu 60s)
            val topFill = 1f - pourProgress
            if (topFill > 0.01f) {
                canvas.save()
                clipPath.reset()
                clipPath.moveTo(cx - hourglassHalfWidth, topY)
                clipPath.lineTo(cx + hourglassHalfWidth, topY)
                clipPath.cubicTo(
                    cx + hourglassHalfWidth, topY + hourglassHalfHeight * 0.45f,
                    cx + neckHalfWidth, cy - hourglassHalfHeight * 0.12f,
                    cx + neckHalfWidth, cy
                )
                clipPath.lineTo(cx - neckHalfWidth, cy)
                clipPath.cubicTo(
                    cx - neckHalfWidth, cy - hourglassHalfHeight * 0.12f,
                    cx - hourglassHalfWidth, topY + hourglassHalfHeight * 0.45f,
                    cx - hourglassHalfWidth, topY
                )
                clipPath.close()
                canvas.clipPath(clipPath)

                val sandTopY = cy - hourglassHalfHeight * topFill
                sandPath.reset()
                sandPath.moveTo(cx - hourglassHalfWidth * 1.2f, sandTopY)
                sandPath.quadTo(cx, sandTopY + 18f * (1f - topFill), cx + hourglassHalfWidth * 1.2f, sandTopY)
                sandPath.lineTo(cx + hourglassHalfWidth * 1.2f, cy)
                sandPath.lineTo(cx - hourglassHalfWidth * 1.2f, cy)
                sandPath.close()
                canvas.drawPath(sandPath, sandPaint)
                canvas.restore()
            }

            // Dolny piasek (przybywa w ciągu 60s)
            val bottomFill = pourProgress
            if (bottomFill > 0.01f) {
                canvas.save()
                clipPath.reset()
                clipPath.moveTo(cx - neckHalfWidth, cy)
                clipPath.lineTo(cx + neckHalfWidth, cy)
                clipPath.cubicTo(
                    cx + neckHalfWidth, cy + hourglassHalfHeight * 0.12f,
                    cx + hourglassHalfWidth, bottomY - hourglassHalfHeight * 0.45f,
                    cx + hourglassHalfWidth, bottomY
                )
                clipPath.lineTo(cx - hourglassHalfWidth, bottomY)
                clipPath.cubicTo(
                    cx - hourglassHalfWidth, bottomY - hourglassHalfHeight * 0.45f,
                    cx - neckHalfWidth, cy + hourglassHalfHeight * 0.12f,
                    cx - neckHalfWidth, cy
                )
                clipPath.close()
                canvas.clipPath(clipPath)

                val sandSurfaceY = bottomY - hourglassHalfHeight * bottomFill
                val conePeakY = (sandSurfaceY - 26f * (1f - bottomFill * 0.6f)).coerceAtLeast(cy + 4f)
                sandPath.reset()
                sandPath.moveTo(cx - hourglassHalfWidth * 1.2f, sandSurfaceY + 10f)
                sandPath.lineTo(cx, conePeakY)
                sandPath.lineTo(cx + hourglassHalfWidth * 1.2f, sandSurfaceY + 10f)
                sandPath.lineTo(cx + hourglassHalfWidth * 1.2f, bottomY)
                sandPath.lineTo(cx - hourglassHalfWidth * 1.2f, bottomY)
                sandPath.close()
                canvas.drawPath(sandPath, sandPaint)
                canvas.restore()
            }

            // Strumień i pojedyncze ziarenka podczas przesypywania
            if (pourProgress < 0.995f && flipExtraAngle == 0f) {
                val streamBottom = bottomY - hourglassHalfHeight * bottomFill
                canvas.drawLine(cx, cy, cx, streamBottom, grainPaint)

                for (i in 0 until 14) {
                    val t = ((elapsed / 450.0 + i * 0.07) % 1.0).toFloat()
                    val gy = cy + (streamBottom - cy) * t
                    val gx = cx + (random.nextFloat() - 0.5f) * (neckHalfWidth * 1.2f)
                    canvas.drawCircle(gx, gy, 2.5f, grainPaint)
                }
            }

            // Szklany kontur klepsydry
            sandPath.reset()
            sandPath.moveTo(cx - hourglassHalfWidth, topY)
            sandPath.lineTo(cx + hourglassHalfWidth, topY)
            sandPath.cubicTo(
                cx + hourglassHalfWidth, topY + hourglassHalfHeight * 0.45f,
                cx + neckHalfWidth, cy - hourglassHalfHeight * 0.12f,
                cx + neckHalfWidth, cy
            )
            sandPath.cubicTo(
                cx + neckHalfWidth, cy + hourglassHalfHeight * 0.12f,
                cx + hourglassHalfWidth, bottomY - hourglassHalfHeight * 0.45f,
                cx + hourglassHalfWidth, bottomY
            )
            sandPath.lineTo(cx - hourglassHalfWidth, bottomY)
            sandPath.cubicTo(
                cx - hourglassHalfWidth, bottomY - hourglassHalfHeight * 0.45f,
                cx - neckHalfWidth, cy + hourglassHalfHeight * 0.12f,
                cx - neckHalfWidth, cy
            )
            sandPath.cubicTo(
                cx - neckHalfWidth, cy - hourglassHalfHeight * 0.12f,
                cx - hourglassHalfWidth, topY + hourglassHalfHeight * 0.45f,
                cx - hourglassHalfWidth, topY
            )
            sandPath.close()
            canvas.drawPath(sandPath, glassPaint)

            // Górna i dolna podstawa
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
