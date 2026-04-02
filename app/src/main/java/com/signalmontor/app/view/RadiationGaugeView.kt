package com.signalmontor.app.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class RadiationGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        color = 0xFFE0E0E0.toInt()
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        alpha = 180
    }

    private val rect = RectF()
    private var animatedProgress = 0f
    private var currentColor = 0xFFBDBDBD.toInt()
    private var displayText = "--"
    private var subText = ""

    fun setValue(percentage: Float, color: Int, text: String, sub: String = "") {
        val target = percentage.coerceIn(0f, 100f)
        currentColor = color
        displayText = text
        subText = sub

        val startVal = animatedProgress
        ValueAnimator.ofFloat(startVal, target).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                animatedProgress = v
                invalidate()
            }
            start()
        }
    }

    fun reset() {
        animatedProgress = 0f
        currentColor = 0xFFBDBDBD.toInt()
        displayText = "--"
        subText = ""
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padding = 16f
        rect.set(padding, padding, w - padding, h - padding)
        textPaint.textSize = h * 0.22f
        subTextPaint.textSize = h * 0.13f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val startAngle = 135f
        val sweepAngle = 270f

        canvas.drawArc(rect, startAngle, sweepAngle, false, bgPaint)

        arcPaint.color = currentColor
        val sweep = animatedProgress / 100f * sweepAngle
        if (sweep > 0) {
            canvas.drawArc(rect, startAngle, sweep, false, arcPaint)
        }

        textPaint.color = currentColor
        canvas.drawText(displayText, width / 2f, height / 2f + textPaint.textSize / 3, textPaint)

        if (subText.isNotEmpty()) {
            subTextPaint.color = currentColor
            canvas.drawText(subText, width / 2f, height / 2f + textPaint.textSize + subTextPaint.textSize + 4, subTextPaint)
        }
    }
}
