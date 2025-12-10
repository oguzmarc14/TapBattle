package com.duelodetrazos.ui.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

// Vista que dibuja un círculo guía para el juego
class CircleCanvas(context: Context) : View(context) {

    private val circlePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 8f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Centro del canvas
        val cx = width / 2f
        val cy = height / 2f

        // Radio del círculo (dependiendo del tamaño de pantalla)
        val radius = (width.coerceAtMost(height)) / 2.5f

        // Dibuja el círculo
        canvas.drawCircle(cx, cy, radius, circlePaint)
    }
}

