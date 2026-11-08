package com.surya.ipodapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CoverItem(val title: String, val sub: String, val song: Song?)

/** Cover Flow: artwork flipped in 3D. Works for songs, artists, albums and playlists. */
class CoverFlowView(ctx: Context) : FrameLayout(ctx) {
    var index = 0
        private set
    private var items: List<CoverItem> = emptyList()
    private val covers = HashMap<Int, FrameLayout>()
    private val stage = FrameLayout(ctx)
    private val titleLabel = TextView(ctx)
    private val subLabel = TextView(ctx)
    private val d = ctx.resources.displayMetrics.density
    private var coverSize = 0
    private var stageW = 0f
    private var stageH = 0f
    private var placeholder: Bitmap? = null

    // Flip this sign if the tilt looks reversed on your device.
    private val tilt = 57f

    val current: CoverItem? get() = items.getOrNull(index)

    init {
        setBackgroundColor(Color.BLACK)
        clipChildren = true
        stage.clipChildren = false
        val sp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        sp.bottomMargin = (40 * d).toInt()
        addView(stage, sp)

        titleLabel.setTextColor(Color.WHITE)
        titleLabel.textSize = 12f
        titleLabel.setTypeface(null, Typeface.BOLD)
        titleLabel.gravity = Gravity.CENTER
        titleLabel.maxLines = 1
        titleLabel.ellipsize = TextUtils.TruncateAt.END
        subLabel.setTextColor(Color.rgb(166, 166, 166))
        subLabel.textSize = 10f
        subLabel.gravity = Gravity.CENTER
        subLabel.maxLines = 1
        subLabel.ellipsize = TextUtils.TruncateAt.END

        val tp = LayoutParams(LayoutParams.MATCH_PARENT, (18 * d).toInt(), Gravity.BOTTOM)
        tp.bottomMargin = (20 * d).toInt()
        tp.leftMargin = (6 * d).toInt()
        tp.rightMargin = (6 * d).toInt()
        addView(titleLabel, tp)
        val sp2 = LayoutParams(LayoutParams.MATCH_PARENT, (16 * d).toInt(), Gravity.BOTTOM)
        sp2.bottomMargin = (4 * d).toInt()
        sp2.leftMargin = (6 * d).toInt()
        sp2.rightMargin = (6 * d).toInt()
        addView(subLabel, sp2)
    }

    fun setItems(list: List<CoverItem>) {
        items = list
        index = 0
        stage.removeAllViews()
        covers.clear()
        layoutCovers(false)
        updateLabels()
    }

    fun move(delta: Int) {
        if (items.isEmpty()) return
        val n = max(0, min(items.size - 1, index + delta))
        if (n == index) return
        index = n
        layoutCovers(true)
        updateLabels()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        stageW = w.toFloat()
        stageH = max(0f, h - 40 * d)
        val size = max(40f * d, min(w * 0.46f, stageH * 0.60f)).toInt()
        if (size != coverSize) {
            coverSize = size
            placeholder = null
            stage.removeAllViews()
            covers.clear()
        }
        layoutCovers(false)
        updateLabels()
    }

    private fun layoutCovers(animated: Boolean) {
        if (items.isEmpty() || coverSize <= 0 || stageW <= 0f) return
        val lo = max(0, index - 5)
        val hi = min(items.size - 1, index + 5)
        for (k in covers.keys.toList()) {
            if (k < lo || k > hi) {
                stage.removeView(covers[k])
                covers.remove(k)
            }
        }
        for (i in lo..hi) {
            if (covers[i] == null) {
                val v = makeCover(i)
                stage.addView(v)
                covers[i] = v
                place(v, i - index, false)
            }
        }
        for ((k, v) in covers) place(v, k - index, animated)
        for (k in covers.keys.sortedByDescending { abs(it - index) }) covers[k]?.bringToFront()
    }

    private fun place(v: FrameLayout, off: Int, animate: Boolean) {
        val cs = coverSize.toFloat()
        val midX = stageW / 2f
        val midY = stageH / 2f - cs * 0.12f
        var cx = midX + off * cs * 0.30f
        if (off != 0) cx += (if (off > 0) 1 else -1) * cs * 0.38f
        val left = cx - cs / 2f
        val top = midY - cs / 2f
        val rot = if (off < 0) tilt else if (off > 0) -tilt else 0f
        val sc = if (off == 0) 1f else 0.82f
        v.animate().cancel()
        if (animate) {
            v.animate().x(left).y(top).rotationY(rot).scaleX(sc).scaleY(sc).setDuration(220).start()
        } else {
            v.x = left
            v.y = top
            v.rotationY = rot
            v.scaleX = sc
            v.scaleY = sc
        }
    }

    private fun makeCover(i: Int): FrameLayout {
        val cs = coverSize
        val box = FrameLayout(context)
        box.layoutParams = LayoutParams(cs, cs * 2)
        box.pivotX = cs / 2f
        box.pivotY = cs / 2f
        box.cameraDistance = 8000f * d

        val iv = ImageView(context)
        iv.scaleType = ImageView.ScaleType.FIT_XY
        box.addView(iv, LayoutParams(cs, cs))

        val rf = ImageView(context)
        rf.scaleType = ImageView.ScaleType.FIT_XY
        rf.scaleY = -1f
        rf.alpha = 0.28f
        val rp = LayoutParams(cs, cs)
        rp.topMargin = cs + (2 * d).toInt()
        box.addView(rf, rp)

        val ph = placeholderBitmap()
        iv.setImageBitmap(ph)
        rf.setImageBitmap(ph)

        val song = items[i].song
        if (song != null) {
            Art.load(context, song, cs) { b ->
                if (b != null && covers[i] === box) {
                    iv.setImageBitmap(b)
                    rf.setImageBitmap(b)
                }
            }
        }
        return box
    }

    private fun placeholderBitmap(): Bitmap {
        placeholder?.let { return it }
        val s = coverSize
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.rgb(56, 56, 56))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.rgb(128, 128, 128)
        // simple music note
        val u = s / 10f
        c.drawOval(u * 2.6f, u * 6.2f, u * 5.0f, u * 7.6f, p)
        c.drawRect(u * 4.6f, u * 2.2f, u * 5.1f, u * 6.9f, p)
        c.drawRect(u * 4.6f, u * 2.2f, u * 7.2f, u * 3.2f, p)
        placeholder = b
        return b
    }

    private fun updateLabels() {
        val c = current
        if (c == null) {
            titleLabel.text = "Empty"
            subLabel.text = ""
        } else {
            titleLabel.text = c.title
            subLabel.text = c.sub
        }
    }
}
