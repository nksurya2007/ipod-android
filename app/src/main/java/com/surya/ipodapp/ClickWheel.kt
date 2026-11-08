package com.surya.ipodapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot

/** Touch click wheel: rotate to scroll, tap center / MENU / play / prev / next. */
class ClickWheel(ctx: Context) : View(ctx) {
    var onStep: ((Int) -> Unit)? = null
    var onRotate: ((Float) -> Unit)? = null
    var onCenter: (() -> Unit)? = null
    var onMenu: (() -> Unit)? = null
    var onPlay: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrev: (() -> Unit)? = null

    var style: WheelStyle = WheelStyle.all[0]
        set(v) {
            field = v
            invalidate()
        }

    private val d = ctx.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        textSize = 14f * d
    }
    private val path = Path()

    private var lastAngle = 0f
    private var acc = 0f
    private var moved = false
    private var sx = 0f
    private var sy = 0f

    private fun tri(c: Canvas, x: Float, cy: Float, size: Float, right: Boolean) {
        path.reset()
        val tip = size * 0.85f
        path.moveTo(x, cy - size / 2f)
        path.lineTo(if (right) x + tip else x - tip, cy)
        path.lineTo(x, cy + size / 2f)
        path.close()
        c.drawPath(path, label)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val r = w / 2f
        fill.color = style.ring
        c.drawCircle(r, r, r, fill)

        val cr = w * 0.34f / 2f
        fill.color = style.center
        c.drawCircle(r, r, cr, fill)
        stroke.color = style.border
        stroke.strokeWidth = d
        c.drawCircle(r, r, cr - d / 2f, stroke)

        label.color = style.label
        label.style = Paint.Style.FILL
        val fm = label.fontMetrics
        c.drawText("MENU", r, w * 0.12f - (fm.ascent + fm.descent) / 2f, label)

        val s = 12f * d
        val tip = s * 0.85f
        // previous: two left-pointing triangles
        val prevCx = w * 0.12f + tip
        tri(c, prevCx + tip / 2f, w / 2f, s, false)
        tri(c, prevCx + tip / 2f + tip, w / 2f, s, false)
        // next: two right-pointing triangles
        val nextCx = w * 0.88f - tip
        tri(c, nextCx - tip / 2f - tip, w / 2f, s, true)
        tri(c, nextCx - tip / 2f, w / 2f, s, true)
        // play / pause
        val py = w * 0.88f
        tri(c, r - 12f * d, py, s, true)
        c.drawRect(r + 2f * d, py - s / 2f, r + 5f * d, py + s / 2f, label)
        c.drawRect(r + 7f * d, py - s / 2f, r + 10f * d, py + s / 2f, label)
    }

    private fun angle(x: Float, y: Float): Float =
        Math.toDegrees(atan2((y - height / 2f).toDouble(), (x - width / 2f).toDouble())).toFloat()

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastAngle = angle(e.x, e.y)
                acc = 0f
                moved = false
                sx = e.x
                sy = e.y
            }
            MotionEvent.ACTION_MOVE -> {
                val a = angle(e.x, e.y)
                var dd = a - lastAngle
                if (dd > 180f) dd -= 360f
                if (dd < -180f) dd += 360f
                lastAngle = a
                acc += dd
                if (hypot(e.x - sx, e.y - sy) > 12f * d) moved = true
                if (moved) {
                    onRotate?.invoke(dd)
                    while (acc >= 24f) {
                        onStep?.invoke(1)
                        acc -= 24f
                    }
                    while (acc <= -24f) {
                        onStep?.invoke(-1)
                        acc += 24f
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) {
                    val dx = e.x - width / 2f
                    val dy = e.y - height / 2f
                    val dist = hypot(dx, dy)
                    if (dist <= width / 2f) {
                        if (dist < width * 0.17f) {
                            onCenter?.invoke()
                        } else {
                            val a = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                            when {
                                a >= -135 && a < -45 -> onMenu?.invoke()
                                a >= 45 && a < 135 -> onPlay?.invoke()
                                a >= -45 && a < 45 -> onNext?.invoke()
                                else -> onPrev?.invoke()
                            }
                        }
                    }
                }
            }
        }
        return true
    }
}
