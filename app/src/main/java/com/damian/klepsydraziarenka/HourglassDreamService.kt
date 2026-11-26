package com.damian.klepsydraziarenka

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class HourglassDreamService : DreamService() {

    private val handler = Handler(Looper.getMainLooper())
    private var hourglassView: HourglassCanvasView? = null

    private val frameRunnable = object : Runnable {
        override fun run() {
            hourglassView?.stepAndInvalidate()
            handler.postDelayed(this, 16L) // ~60 FPS
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        screenBrightness = 0.05f // Łagodne podświetlenie pod ekran OLED / noc

        val view = HourglassCanvasView(this)
        hourglassView = view
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        handler.removeCallbacks(frameRunnable)
        handler.post(frameRunnable)
    }

    override fun onDreamingStopped() {
        handler.removeCallbacks(frameRunnable)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(frameRunnable)
        hourglassView = null
        super.onDetachedFromWindow()
    }
}

private data class SandGrain(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val color: Int,
    var settled: Boolean = false
)

private class HourglassCanvasView(context: android.content.Context) : View(context) {

    private val random = Random(System.currentTimeMillis())

    private val bgPaint = Paint().apply {
        color = Color.parseColor("#06070A")
    }

    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glassFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glassStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val grainColors = intArrayOf(
        Color.parseColor("#F5B041"),
        Color.parseColor("#E59824"),
        Color.parseColor("#F8C471"),
        Color.parseColor("#D68910"),
        Color.parseColor("#FAD7A0")
    )

    private val fallingGrains = mutableListOf<SandGrain>()
    private val settledGrains = mutableListOf<SandGrain>()

    private var topProgress = 1.0f // 1.0 = pełna górna komora, 0.0 = pusta
    private var bottomProgress = 0.0f // 0.0 = pusta dolna komora, 1.0 = pełna
    private var spawnAccumulator = 0f

    private var centerX = 0f
    private var centerY = 0f
    private var hourglassWidth = 0f
    private var hourglassHeight = 0f
    private var neckY = 0f
    private var topBulbTopY = 0f
    private var bottomBulbBottomY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w * 0.5f
        centerY = h * 0.5f

        val baseSize = min(w, h).toFloat()
        hourglassWidth = baseSize * 0.54f
        hourglassHeight = hourglassWidth * 1.48f

        neckY = centerY
        topBulbTopY = centerY - hourglassHeight * 0.42f
        bottomBulbBottomY = centerY + hourglassHeight * 0.42f

        val capHeight = hourglassHeight * 0.065f
        val capWidth = hourglassWidth * 1.12f

        capPaint.shader = LinearGradient(
            centerX - capWidth * 0.5f,
            topBulbTopY - capHeight,
            centerX + capWidth * 0.5f,
            topBulbTopY,
            intArrayOf(
                Color.parseColor("#232730"),
                Color.parseColor("#3B4252"),
                Color.parseColor("#1B1E26")
            ),
            null,
            Shader.TileMode.CLAMP
        )

        glassFillPaint.shader = LinearGradient(
            centerX - hourglassWidth * 0.5f,
            topBulbTopY,
            centerX + hourglassWidth * 0.5f,
            bottomBulbBottomY,
            intArrayOf(
                Color.argb(26, 255, 255, 255),
                Color.argb(10, 220, 235, 255),
                Color.argb(24, 255, 255, 255)
            ),
            null,
            Shader.TileMode.CLAMP
        )

        glassStrokePaint.strokeWidth = maxOf(2.5f, hourglassWidth * 0.016f)
        glassStrokePaint.color = Color.parseColor("#8A96A8")

        highlightPaint.strokeWidth = maxOf(2f, hourglassWidth * 0.012f)
        highlightPaint.color = Color.argb(95, 255, 255, 255)
    }

    fun stepAndInvalidate() {
        val dt = 0.016f

        if (topProgress > 0.02f) {
            topProgress = (topProgress - dt * 0.018f).coerceAtLeast(0.02f)
            bottomProgress = (1.0f - topProgress).coerceIn(0f, 1f)
            spawnAccumulator += dt * 42f

            while (spawnAccumulator >= 1f && fallingGrains.size < 160) {
                spawnAccumulator -= 1f
                val spread = maxOf(1.5f, hourglassWidth * 0.022f)
                fallingGrains.add(
                    SandGrain(
                        x = centerX + random.nextFloat().minus(0.5f) * spread,
                        y = neckY + 2f,
                        vx = random.nextFloat().minus(0.5f) * 18f,
                        vy = 95f + random.nextFloat() * 45f,
                        radius = maxOf(1.8f, hourglassWidth * (0.008f + random.nextFloat() * 0.007f)),
                        color = grainColors[random.nextInt(grainColors.size)]
                    )
                )
            }
        } else {
            // Płynne przewrócenie cyklu po opróżnieniu górnej bańki
            topProgress = 1.0f
            bottomProgress = 0.0f
            settledGrains.clear()
        }

        val iterator = fallingGrains.iterator()
        while (iterator.hasNext()) {
            val g = iterator.next()
            g.vy += 420f * dt
            g.x += g.vx * dt
            g.y += g.vy * dt

            val maxHalfWidthAtY = bulbHalfWidthAt(g.y) * 0.86f
            if (g.x < centerX - maxHalfWidthAtY) {
                g.x = centerX - maxHalfWidthAtY
                g.vx = kotlin.math.abs(g.vx) * 0.35f
            } else if (g.x > centerX + maxHalfWidthAtY) {
                g.x = centerX + maxHalfWidthAtY
                g.vx = -kotlin.math.abs(g.vx) * 0.35f
            }

            val moundTopY = currentBottomMoundTopY(g.x)
            if (g.y >= moundTopY) {
                g.y = moundTopY
                g.settled = true
                if (settledGrains.size < 420) {
                    settledGrains.add(g)
                }
                iterator.remove()
            }
        }

        invalidate()
    }

    private fun bulbHalfWidthAt(y: Float): Float {
        val halfH = hourglassHeight * 0.42f
        val dy = (y - neckY) / halfH
        val clamped = dy.coerceIn(-1f, 1f)
        // Kształt ⌛: łagodnie wygięte ścianki zwężające się do szyjki na środku
        val neckHalfWidth = hourglassWidth * 0.045f
        val wideHalfWidth = hourglassWidth * 0.50f
        val curve = sin(kotlin.math.abs(clamped) * PI * 0.5).toFloat()
        return neckHalfWidth + (wideHalfWidth - neckHalfWidth) * curve
    }

    private fun currentBottomMoundTopY(x: Float): Float {
        val moundHeight = hourglassHeight * 0.34f * bottomProgress
        val baseY = bottomBulbBottomY - hourglassHeight * 0.03f
        val peakY = baseY - moundHeight
        val maxSpan = maxOf(12f, hourglassWidth * 0.42f * (0.25f + 0.75f * bottomProgress))
        val dxRatio = ((x - centerX) / maxSpan).coerceIn(-1f, 1f)
        // Stożek usypujący się na środku pod strumieniem ziaren
        val coneFactor = 1f - kotlin.math.abs(dxRatio)
        return baseY - moundHeight * coneFactor
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val halfH = hourglassHeight * 0.42f
        val steps = 32
        val glassPath = Path()

        // Prawa ścianka od góry do szyjki i w dół do podstawy
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val y = (neckY - halfH) + t * (halfH * 2f)
            val hw = bulbHalfWidthAt(y)
            val px = centerX + hw
            if (i == 0) glassPath.moveTo(px, y) else glassPath.lineTo(px, y)
        }

        // Lewa ścianka od dołu do góry
        for (i in steps downTo 0) {
            val t = i.toFloat() / steps
            val y = (neckY - halfH) + t * (halfH * 2f)
            val hw = bulbHalfWidthAt(y)
            val px = centerX - hw
            glassPath.lineTo(px, y)
        }
        glassPath.close()

        canvas.drawPath(glassPath, glassFillPaint)

        // Piasek w górnej bańce (opadający poziom)
        val topSandHeight = hourglassHeight * 0.32f * topProgress
        if (topSandHeight > 4f) {
            val sandBottomY = neckY - hourglassHeight * 0.02f
            val sandTopY = maxOf(topBulbTopY + hourglassHeight * 0.04f, sandBottomY - topSandHeight)
            val topHalfW = bulbHalfWidthAt(sandTopY) * 0.90f
            val neckHalfW = bulbHalfWidthAt(sandBottomY) * 0.95f

            val topSandPath = Path().apply {
                moveTo(centerX - topHalfW, sandTopY)
                lineTo(centerX + topHalfW, sandTopY)
                lineTo(centerX + neckHalfW, sandBottomY)
                lineTo(centerX - neckHalfW, sandBottomY)
                close()
            }

            grainPaint.color = Color.parseColor("#E59824")
            canvas.drawPath(topSandPath, grainPaint)

            // Drobne ziarenka tekstury na wierzchu górnego piasku
            val grainCountTop = 28
            for (i in 0 until grainCountTop) {
                val frac = (i.toFloat() / (grainCountTop - 1)) - 0.5f
                val gx = centerX + frac * topHalfW * 1.75f
                val gy = sandTopY + sin(i * 1.7f) * 3.5f
                grainPaint.color = grainColors[i % grainColors.size]
                canvas.drawCircle(gx, gy, maxOf(1.6f, hourglassWidth * 0.008f), grainPaint)
            }
        }

        // Dolny rosnący stożek piasku
        if (bottomProgress > 0.01f) {
            val baseY = bottomBulbBottomY - hourglassHeight * 0.03f
            val moundHeight = hourglassHeight * 0.34f * bottomProgress
            val peakY = baseY - moundHeight
            val span = maxOf(10f, hourglassWidth * 0.42f * (0.25f + 0.75f * bottomProgress))

            val bottomSandPath = Path().apply {
                moveTo(centerX - span, baseY)
                lineTo(centerX, peakY)
                lineTo(centerX + span, baseY)
                close()
            }

            grainPaint.color = Color.parseColor("#D68910")
            canvas.drawPath(bottomSandPath, grainPaint)
        }

        // Osadzone pojedyncze ziarenka na kopcu
        for (g in settledGrains) {
            grainPaint.color = g.color
            canvas.drawCircle(g.x, g.y, g.radius, grainPaint)
        }

        // Spadające ziarenka w strumieniu
        for (g in fallingGrains) {
            grainPaint.color = g.color
            canvas.drawCircle(g.x, g.y, g.radius, grainPaint)
        }

        // Obrys szkła i delikatny refleks po lewej stronie
        canvas.drawPath(glassPath, glassStrokePaint)

        val reflectPath = Path().apply {
            val startY = topBulbTopY + hourglassHeight * 0.07f
            val endY = neckY - hourglassHeight * 0.06f
            moveTo(centerX - bulbHalfWidthAt(startY) * 0.72f, startY)
            quadTo(
                centerX - bulbHalfWidthAt((startY + endY) * 0.5f) * 0.76f,
                (startY + endY) * 0.5f,
                centerX - bulbHalfWidthAt(endY) * 0.75f,
                endY
            )
        }
        canvas.drawPath(reflectPath, highlightPaint)

        // Górna i dolna podstawa (kształt ⌛)
        val capHeight = hourglassHeight * 0.065f
        val capWidth = hourglassWidth * 1.12f
        val cornerRadius = capHeight * 0.35f

        canvas.drawRoundRect(
            centerX - capWidth * 0.5f,
            topBulbTopY - capHeight,
            centerX + capWidth * 0.5f,
            topBulbTopY + 2f,
            cornerRadius,
            cornerRadius,
            capPaint
        )

        canvas.drawRoundRect(
            centerX - capWidth * 0.5f,
            bottomBulbBottomY - 2f,
            centerX + capWidth * 0.5f,
            bottomBulbBottomY + capHeight,
            cornerRadius,
            cornerRadius,
            capPaint
        )

        // Delikatny ruch oddechowy całej sceny dla ochrony OLED
        val swayX = cos(System.currentTimeMillis() * 0.0004) * 2.0
        canvas.translate(swayX.toFloat(), 0f)
    }
}
