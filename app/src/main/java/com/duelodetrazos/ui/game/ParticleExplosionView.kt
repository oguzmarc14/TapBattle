package com.duelodetrazos.ui.game

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.AccelerateInterpolator
import kotlin.random.Random

class ParticleExplosionView(context: Context) : View(context) {

    private val particles = mutableListOf<Particle>()
    private val paint = Paint()
    private var animator: ValueAnimator? = null

    private data class Particle(
        var x: Float,
        var y: Float,
        var radius: Float,
        var color: Int,
        var alpha: Int,
        var velocityX: Float,
        var velocityY: Float
    )

    fun startExplosion(startX: Float, startY: Float, color: Int) {
        // Detiene cualquier animación anterior
        animator?.cancel()
        particles.clear()

        // Crea un número aleatorio de partículas para la explosión
        val particleCount = Random.nextInt(30, 50)
        for (i in 0 until particleCount) {
            particles.add(createParticle(startX, startY, color))
        }

        // Inicia el animador para la explosión
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000 // La explosión dura 1 segundo
            interpolator = AccelerateInterpolator()
            addUpdateListener { animation ->
                updateParticles(animation.animatedFraction)
            }
            start()
        }
    }

    private fun createParticle(startX: Float, startY: Float, color: Int): Particle {
        val angle = Random.nextDouble(0.0, 2 * Math.PI)
        val speed = Random.nextFloat() * 15f + 5f // Velocidad aleatoria
        return Particle(
            x = startX,
            y = startY,
            radius = Random.nextFloat() * 15f + 5f, // Radio aleatorio
            color = color,
            alpha = 255,
            velocityX = (speed * Math.cos(angle)).toFloat(),
            velocityY = (speed * Math.sin(angle)).toFloat()
        )
    }

    private fun updateParticles(fraction: Float) {
        particles.forEach { particle ->
            particle.x += particle.velocityX
            particle.y += particle.velocityY
            // Las partículas se desvanecen con el tiempo
            particle.alpha = (255 * (1 - fraction)).toInt()
        }
        invalidate() // Vuelve a dibujar
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        particles.forEach { particle ->
            paint.color = particle.color
            paint.alpha = particle.alpha
            canvas.drawCircle(particle.x, particle.y, particle.radius, paint)
        }
    }
}
