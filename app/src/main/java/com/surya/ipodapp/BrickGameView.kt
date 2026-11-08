package com.surya.ipodapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Choreographer
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Breakout played with the click wheel.
 * Rotate = move paddle, center = launch / pause / restart, MENU = exit.
 */
class BrickGameView(ctx: Context) : View(ctx) {
    private enum class St { READY, PLAYING, PAUSED, OVER, WON }

    private var state = St.READY
    private val d = ctx.resources.displayMetrics.density
    private val cols = 7
    private val rows = 5
    private val ballR = 5f * d
    private val baseSpeed = 210f * d
    private val padW = 56f * d
    private val padH = 8f * d
    private var padX = 0f
    private var bx = 0f
    private var by = 0f
    private var vx = 0f
    private var vy = 0f
    private var alive = BooleanArray(cols * rows) { true }
    private var score = 0
    private var lives = 3
    private var built = false
    private var looping = false
    private var lastNs = 0L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val colors = intArrayOf(
        Color.rgb(230, 64, 64), Color.rgb(242, 140, 38), Color.rgb(242, 217, 51),
        Color.rgb(77, 191, 89), Color.rgb(64, 140, 230)
    )

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            tick(frameTimeNanos)
            if (looping) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    val isRunning: Boolean get() = state == St.PLAYING

    private val gap get() = 3f * d
    private val brickW get() = (width - gap * (cols + 1)) / cols
    private val brickH get() = 12f * d
    private val padY get() = height - 24f * d

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (!built && w > 0) {
            built = true
            newGame()
        }
    }

    fun start() {
        if (looping) return
        looping = true
        lastNs = System.nanoTime()
        Choreographer.getInstance().postFrameCallback(frame)
    }

    fun stop() {
        looping = false
        Choreographer.getInstance().removeFrameCallback(frame)
    }

    fun newGame() {
        if (width <= 0) return
        alive = BooleanArray(cols * rows) { true }
        score = 0
        lives = 3
        padX = (width - padW) / 2f
        resetBall()
        state = St.READY
        invalidate()
    }

    /** Wheel rotation in degrees. */
    fun rotate(deg: Float) {
        if (state != St.READY && state != St.PLAYING) return
        padX = (padX + deg * 1.3f * d).coerceIn(0f, width - padW)
        if (state == St.READY) resetBall()
        invalidate()
    }

    /** Center button. */
    fun press() {
        when (state) {
            St.READY -> {
                vx = baseSpeed * 0.5f
                vy = -baseSpeed * 0.87f
                state = St.PLAYING
            }
            St.PLAYING -> state = St.PAUSED
            St.PAUSED -> {
                lastNs = System.nanoTime()
                state = St.PLAYING
            }
            St.OVER, St.WON -> newGame()
        }
        invalidate()
    }

    private fun resetBall() {
        bx = padX + padW / 2f
        by = padY - ballR - 1f
        vx = 0f
        vy = 0f
    }

    private fun tick(ns: Long) {
        var dt = (ns - lastNs) / 1e9f
        lastNs = ns
        if (state != St.PLAYING) return
        if (dt > 1f / 30f) dt = 1f / 30f

        var px = bx + vx * dt
        var py = by + vy * dt
        val w = width.toFloat()

        if (px < ballR) { px = ballR; vx = abs(vx) }
        if (px > w - ballR) { px = w - ballR; vx = -abs(vx) }
        if (py < ballR) { py = ballR; vy = abs(vy) }

        val pTop = padY
        if (vy > 0 && py + ballR >= pTop && py - ballR <= pTop + padH &&
            px >= padX - ballR && px <= padX + padW + ballR
        ) {
            val rel = ((px - (padX + padW / 2f)) / (padW / 2f)).coerceIn(-1f, 1f)
            val speed = hypot(vx, vy)
            val ang = rel * (Math.PI.toFloat() / 3f)
            vx = speed * sin(ang)
            vy = -speed * cos(ang)
            py = pTop - ballR
        }

        for (i in alive.indices) {
            if (!alive[i]) continue
            val c = i % cols
            val r = i / cols
            val left = gap + c * (brickW + gap)
            val top = 30f * d + r * (brickH + gap)
            val right = left + brickW
            val bottom = top + brickH
            val cx = px.coerceIn(left, right)
            val cy = py.coerceIn(top, bottom)
            val dx = px - cx
            val dy = py - cy
            if (dx * dx + dy * dy <= ballR * ballR) {
                if (dx == 0f && dy == 0f) {
                    vy = -vy
                } else if (abs(dx) > abs(dy)) {
                    vx = if (dx > 0) abs(vx) else -abs(vx)
                } else {
                    vy = if (dy > 0) abs(vy) else -abs(vy)
                }
                alive[i] = false
                score += 10
                if (hypot(vx, vy) < 380f * d) {
                    vx *= 1.015f
                    vy *= 1.015f
                }
                break
            }
        }
        bx = px
        by = py

        if (alive.none { it }) {
            state = St.WON
            invalidate()
            return
        }
        if (py - ballR > height) {
            lives -= 1
            if (lives <= 0) {
                state = St.OVER
            } else {
                resetBall()
                state = St.READY
            }
        }
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.rgb(13, 13, 13))
        paint.style = Paint.Style.FILL
        for (i in alive.indices) {
            if (!alive[i]) continue
            val col = i % cols
            val row = i / cols
            val left = gap + col * (brickW + gap)
            val top = 30f * d + row * (brickH + gap)
            rect.set(left, top, left + brickW, top + brickH)
            paint.color = colors[row]
            c.drawRoundRect(rect, 2f * d, 2f * d, paint)
        }
        paint.color = Color.WHITE
        rect.set(padX, padY, padX + padW, padY + padH)
        c.drawRoundRect(rect, 4f * d, 4f * d, paint)
        c.drawCircle(bx, by, ballR, paint)

        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 11f * d
        c.drawText("Score $score    Lives $lives", 6f * d, 15f * d, paint)

        val msg = when (state) {
            St.READY -> "Turn wheel to move\nPress center to launch"
            St.PAUSED -> "Paused\nPress center"
            St.OVER -> "Game Over\nScore $score\nPress center"
            St.WON -> "You Win!\nScore $score\nPress center"
            St.PLAYING -> ""
        }
        if (msg.isNotEmpty()) {
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 14f * d
            var y = height * 0.52f + 14f * d
            for (line in msg.split("\n")) {
                c.drawText(line, width / 2f, y, paint)
                y += 20f * d
            }
        }
    }
}
