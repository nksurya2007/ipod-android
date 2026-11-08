package com.surya.ipodapp

import android.graphics.Color

private fun c(r: Float, g: Float, b: Float): Int =
    Color.rgb((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())

private fun w(v: Float): Int = c(v, v, v)

private val WHITE90 = Color.argb(230, 255, 255, 255)

class Theme(
    val name: String,
    val body: Int,
    val screen: Int,
    val border: Int,
    val bar: Int,
    val barText: Int,
    val text: Int,
    val subtext: Int,
    val selBg: Int,
    val selText: Int,
    val dark: Boolean
) {
    companion object {
        val all: List<Theme> = listOf(
            Theme("Classic", w(0.93f), Color.WHITE, w(0.33f), w(0.82f), Color.BLACK, Color.BLACK,
                w(0.50f), c(0.10f, 0.45f, 0.82f), Color.WHITE, false),
            Theme("Dark", w(0.10f), w(0.07f), w(0.35f), w(0.20f), Color.WHITE, Color.WHITE,
                w(0.60f), c(0.10f, 0.55f, 0.95f), Color.WHITE, true),
            Theme("Green", c(0.85f, 0.93f, 0.85f), c(0.93f, 0.98f, 0.93f), c(0.20f, 0.40f, 0.20f),
                c(0.70f, 0.86f, 0.70f), c(0.05f, 0.20f, 0.05f), c(0.05f, 0.20f, 0.05f),
                c(0.30f, 0.45f, 0.30f), c(0.15f, 0.55f, 0.25f), Color.WHITE, false),
            Theme("Rose", c(0.98f, 0.90f, 0.92f), c(1.00f, 0.97f, 0.98f), c(0.60f, 0.30f, 0.40f),
                c(0.95f, 0.75f, 0.82f), c(0.25f, 0.05f, 0.12f), c(0.25f, 0.05f, 0.12f),
                c(0.55f, 0.35f, 0.42f), c(0.85f, 0.25f, 0.45f), Color.WHITE, false),
            Theme("Ocean", c(0.80f, 0.91f, 0.97f), c(0.92f, 0.97f, 1.00f), c(0.10f, 0.30f, 0.50f),
                c(0.65f, 0.82f, 0.93f), c(0.03f, 0.15f, 0.30f), c(0.03f, 0.15f, 0.30f),
                c(0.30f, 0.45f, 0.58f), c(0.05f, 0.45f, 0.75f), Color.WHITE, false),
            Theme("Sunset", c(1.00f, 0.90f, 0.80f), c(1.00f, 0.97f, 0.92f), c(0.65f, 0.30f, 0.10f),
                c(0.99f, 0.78f, 0.55f), c(0.35f, 0.12f, 0.02f), c(0.35f, 0.12f, 0.02f),
                c(0.60f, 0.40f, 0.25f), c(0.95f, 0.45f, 0.10f), Color.WHITE, false),
            Theme("Purple", c(0.90f, 0.86f, 0.96f), c(0.97f, 0.95f, 1.00f), c(0.35f, 0.20f, 0.55f),
                c(0.78f, 0.70f, 0.92f), c(0.15f, 0.07f, 0.30f), c(0.15f, 0.07f, 0.30f),
                c(0.45f, 0.35f, 0.60f), c(0.50f, 0.25f, 0.80f), Color.WHITE, false),
            Theme("Gold", c(0.96f, 0.92f, 0.78f), c(1.00f, 0.99f, 0.93f), c(0.55f, 0.45f, 0.10f),
                c(0.90f, 0.82f, 0.50f), c(0.30f, 0.23f, 0.02f), c(0.30f, 0.23f, 0.02f),
                c(0.55f, 0.47f, 0.20f), c(0.78f, 0.60f, 0.05f), Color.WHITE, false),
            Theme("U2 Red", c(0.05f, 0.05f, 0.05f), c(0.08f, 0.08f, 0.08f), c(0.75f, 0.05f, 0.10f),
                c(0.55f, 0.05f, 0.08f), Color.WHITE, Color.WHITE,
                c(0.65f, 0.65f, 0.65f), c(0.85f, 0.10f, 0.15f), Color.WHITE, true),
            Theme("Matrix", c(0.02f, 0.06f, 0.02f), c(0.00f, 0.04f, 0.00f), c(0.00f, 0.50f, 0.10f),
                c(0.00f, 0.20f, 0.05f), c(0.30f, 1.00f, 0.40f), c(0.30f, 1.00f, 0.40f),
                c(0.10f, 0.60f, 0.20f), c(0.10f, 0.70f, 0.25f), Color.BLACK, true)
        )
    }
}

class WheelStyle(val name: String, val ring: Int, val center: Int, val label: Int, val border: Int) {
    companion object {
        val all: List<WheelStyle> = listOf(
            WheelStyle("White", w(0.90f), w(0.98f), w(0.55f), w(0.80f)),
            WheelStyle("Black", w(0.12f), w(0.20f), w(0.85f), w(0.32f)),
            WheelStyle("Silver", c(0.74f, 0.76f, 0.79f), c(0.84f, 0.86f, 0.89f), w(0.30f), c(0.60f, 0.62f, 0.66f)),
            WheelStyle("Red", c(0.80f, 0.12f, 0.16f), c(0.90f, 0.25f, 0.28f), WHITE90, c(0.62f, 0.08f, 0.12f)),
            WheelStyle("Gold", c(0.85f, 0.72f, 0.35f), c(0.93f, 0.83f, 0.52f), c(0.35f, 0.25f, 0.05f), c(0.65f, 0.52f, 0.20f)),
            WheelStyle("Blue", c(0.25f, 0.45f, 0.80f), c(0.40f, 0.60f, 0.92f), WHITE90, c(0.15f, 0.32f, 0.62f)),
            WheelStyle("Pink", c(0.93f, 0.55f, 0.70f), c(0.97f, 0.72f, 0.82f), c(0.45f, 0.10f, 0.25f), c(0.78f, 0.38f, 0.55f)),
            WheelStyle("Green", c(0.25f, 0.62f, 0.35f), c(0.42f, 0.78f, 0.50f), WHITE90, c(0.15f, 0.45f, 0.25f))
        )
    }
}
