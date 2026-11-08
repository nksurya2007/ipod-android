package com.surya.ipodapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** Flat progress bar (Now Playing progress and volume). */
class BarView(ctx: Context) : View(ctx) {
    var progress = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }
    var track = Color.GRAY
        set(v) {
            field = v
            invalidate()
        }
    var fill = Color.BLUE
        set(v) {
            field = v
            invalidate()
        }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val r = height / 2f
        p.color = track
        c.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), r, r, p)
        if (progress > 0f) {
            p.color = fill
            c.drawRoundRect(RectF(0f, 0f, width * progress, height.toFloat()), r, r, p)
        }
    }
}

/** Play triangle / pause bars shown in the title bar. */
class PlayIcon(ctx: Context) : View(ctx) {
    var playing = false
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }
    var color = Color.BLACK
        set(v) {
            field = v
            invalidate()
        }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.color = color
        if (playing) {
            path.reset()
            path.moveTo(w * 0.15f, 0f)
            path.lineTo(w * 0.15f, h)
            path.lineTo(w * 0.95f, h / 2f)
            path.close()
            c.drawPath(path, p)
        } else {
            c.drawRect(w * 0.15f, 0f, w * 0.42f, h, p)
            c.drawRect(w * 0.58f, 0f, w * 0.85f, h, p)
        }
    }
}

/** Battery icon: outline, fill level, charging bolt. */
class BatteryView(ctx: Context) : View(ctx) {
    var level = 1f
        set(v) {
            field = v
            invalidate()
        }
    var charging = false
        set(v) {
            field = v
            invalidate()
        }
    var color = Color.BLACK
        set(v) {
            field = v
            invalidate()
        }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val nub = 2f * d
        val body = RectF(0.5f * d, 0.5f * d, w - nub - 1f * d, h - 0.5f * d)
        p.style = Paint.Style.STROKE
        p.strokeWidth = d
        p.color = color
        c.drawRoundRect(body, 2.5f * d, 2.5f * d, p)

        p.style = Paint.Style.FILL
        c.drawRoundRect(
            RectF(body.right + 1f * d, h / 2f - 2.5f * d, body.right + 1f * d + nub, h / 2f + 2.5f * d),
            d, d, p
        )

        val inner = RectF(body.left + 2f * d, body.top + 2f * d, body.right - 2f * d, body.bottom - 2f * d)
        val fillRect = RectF(inner.left, inner.top, inner.left + inner.width() * level.coerceIn(0f, 1f), inner.bottom)
        p.color = when {
            charging -> Color.rgb(51, 191, 77)
            level <= 0.2f -> Color.rgb(230, 51, 51)
            else -> color
        }
        c.drawRoundRect(fillRect, d, d, p)

        if (charging) {
            val cx = body.centerX()
            val cy = body.centerY()
            val u = d
            val bolt = Path()
            bolt.moveTo(cx + 1.5f * u, cy - 4f * u)
            bolt.lineTo(cx - 2.5f * u, cy + 0.5f * u)
            bolt.lineTo(cx - 0.2f * u, cy + 0.5f * u)
            bolt.lineTo(cx - 1.5f * u, cy + 4f * u)
            bolt.lineTo(cx + 2.5f * u, cy - 0.5f * u)
            bolt.lineTo(cx + 0.2f * u, cy - 0.5f * u)
            bolt.close()
            p.color = Color.rgb(26, 26, 26)
            c.drawPath(bolt, p)
        }
    }
}
