package com.duelodetrazos.ui.game

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.random.Random

class ObjectiveCircleView(context: Context) : View(context) {

    private val colors = listOf(
        Color.parseColor("#E91E63"), Color.parseColor("#3F51B5"),
        Color.parseColor("#00BCD4"), Color.parseColor("#FFC107"),
        Color.parseColor("#4CAF50"), Color.parseColor("#FF5722")
    )

    private val paint = Paint().apply {
        style = Paint.Style.FILL
    }

    private var circleX = -200f
    private var circleY = -200f
    private val baseRadius = 60f
    private var animatedRadius = baseRadius

    var onHit: (() -> Unit)? = null

    private val pulseAnimator = ValueAnimator.ofFloat(0.9f, 1.1f).apply {
        duration = 800
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = LinearInterpolator()
        addUpdateListener {
            animatedRadius = baseRadius * (it.animatedValue as Float)
            invalidate()
        }
    }

    init {
        paint.color = colors.random()
        pulseAnimator.start()
    }

    // --- MÉTODOS GETTER PARA LA POSICIÓN Y EL COLOR ---
    fun getCircleX(): Float = circleX
    fun getCircleY(): Float = circleY
    fun getCircleColor(): Int = paint.color

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(circleX, circleY, animatedRadius, paint)
    }

    fun setPosition(x: Float, y: Float) {
        circleX = x
        circleY = y
        paint.color = colors.random()
        if (!pulseAnimator.isStarted) {
            pulseAnimator.start()
        }
        invalidate()
    }

    fun checkHit(x: Float, y: Float): Boolean {
        val dx = x - circleX
        val dy = y - circleY
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        if (distance <= animatedRadius) {
            onHit?.invoke()
            return true
        }
        return false
    }
}
