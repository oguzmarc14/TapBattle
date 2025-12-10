package com.duelodetrazos.ui.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View

// DrawingView es la vista donde el usuario dibuja
// Maneja trazo, color y movimiento del dedo
class DrawingView(context: Context) : View(context) {

    // Path almacena el trazo que va dibujando el usuario
    private val drawPath = Path()

    // Pintura del pincel
    private val drawPaint = Paint().apply {
        color = Color.WHITE     // Color del pincel
        strokeWidth = 14f       // Grosor
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    // Listener para enviar la posicion del toque a GameActivity
    var onTouchPoint: ((Float, Float) -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Dibujo del trazo del usuario
        canvas.drawPath(drawPath, drawPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                drawPath.moveTo(x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                drawPath.lineTo(x, y)
            }
        }

        // Informo al juego la posicion tocada
        onTouchPoint?.invoke(x, y)

        invalidate()
        return true
    }

    // Limpia todo el dibujo
    fun clear() {
        drawPath.reset()
        invalidate()
    }
}
