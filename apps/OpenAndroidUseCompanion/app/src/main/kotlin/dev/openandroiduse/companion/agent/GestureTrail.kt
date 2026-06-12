package dev.openandroiduse.companion.agent

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import dev.openandroiduse.companion.CompanionService

/**
 * "Visible hands" (Phase 3.1b): a non-touchable TYPE_ACCESSIBILITY_OVERLAY
 * that draws a fading ripple for every agent tap and a fading stroke for every
 * swipe, so the user always sees what the agent is doing. Attached while a
 * task runs, removed when it ends. Coordinates are device pixels in screen
 * space — the same values handed to dispatchGesture.
 */
object GestureTrail {

    private const val FADE_MS = 700L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var view: TrailView? = null

    fun attach(service: CompanionService) {
        mainHandler.post {
            if (view != null) return@post
            val trail = TrailView(service)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.START }
            try {
                val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.addView(trail, params)
                view = trail
            } catch (_: Exception) {
                // Overlay is best-effort eye candy; the agent works without it.
            }
        }
    }

    fun detach(service: CompanionService) {
        mainHandler.post {
            val trail = view ?: return@post
            view = null
            try {
                val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(trail)
            } catch (_: Exception) {
            }
        }
    }

    fun tap(x: Int, y: Int) {
        mainHandler.post { view?.addMark(Mark(x.toFloat(), y.toFloat(), x.toFloat(), y.toFloat())) }
    }

    fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int) {
        mainHandler.post {
            view?.addMark(Mark(fromX.toFloat(), fromY.toFloat(), toX.toFloat(), toY.toFloat()))
        }
    }

    private class Mark(val fromX: Float, val fromY: Float, val toX: Float, val toY: Float) {
        val bornAt = SystemClock.uptimeMillis()
        fun alpha(now: Long): Int {
            val age = now - bornAt
            if (age >= FADE_MS) return 0
            return (255 * (1f - age.toFloat() / FADE_MS)).toInt()
        }
    }

    private class TrailView(context: Context) : View(context) {
        private val marks = mutableListOf<Mark>()
        private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.argb(255, 66, 133, 244)
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
            color = Color.argb(255, 66, 133, 244)
        }

        fun addMark(mark: Mark) {
            marks.add(mark)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val now = SystemClock.uptimeMillis()
            marks.removeAll { it.alpha(now) <= 0 }
            for (mark in marks) {
                val alpha = mark.alpha(now)
                val age = (now - mark.bornAt).toFloat() / FADE_MS
                if (mark.fromX == mark.toX && mark.fromY == mark.toY) {
                    ripplePaint.alpha = alpha
                    canvas.drawCircle(mark.fromX, mark.fromY, 24f + 40f * age, ripplePaint)
                } else {
                    strokePaint.alpha = alpha
                    canvas.drawLine(mark.fromX, mark.fromY, mark.toX, mark.toY, strokePaint)
                    ripplePaint.alpha = alpha
                    canvas.drawCircle(mark.toX, mark.toY, 16f, ripplePaint)
                }
            }
            if (marks.isNotEmpty()) {
                postInvalidateOnAnimation()
            }
        }
    }
}
