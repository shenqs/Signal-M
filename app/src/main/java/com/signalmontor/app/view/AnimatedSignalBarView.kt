package com.signalmontor.app.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.signalmontor.app.R

class AnimatedSignalBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = 60
    }

    private var barCount = 4
    private var activeBars = 0
    private var barColor = 0xFFBDBDBD.toInt()
    private var animatedProgress = 0f
    private var targetProgress = 0f

    private val barRects = mutableListOf<RectF>()
    private val barGaps = mutableListOf<RectF>()

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.AnimatedSignalBarView,
            0, 0
        ).apply {
            try {
                barCount = getInteger(R.styleable.AnimatedSignalBarView_barCount, 4)
                barColor = getColor(R.styleable.AnimatedSignalBarView_barColor, 0xFFBDBDBD.toInt())
            } finally {
                recycle()
            }
        }
    }

    fun setLevel(level: Int, color: Int) {
        activeBars = level.coerceIn(0, barCount)
        barColor = color
        targetProgress = level.toFloat() / barCount

        val animator = ValueAnimator.ofFloat(animatedProgress, targetProgress)
        animator.duration = 400
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            animatedProgress = animation.animatedValue as Float
            invalidate()
        }
        animator.start()
    }

    fun pulse() {
        val animator = ValueAnimator.ofFloat(1f, 1.15f, 1f)
        animator.duration = 600
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            scaleX = animation.animatedValue as Float
            scaleY = animation.animatedValue as Float
        }
        animator.start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateBarRects(w, h)
    }

    private fun calculateBarRects(width: Int, height: Int) {
        barRects.clear()
        barGaps.clear()

        val barWidth = (width / (barCount * 2 - 1)).coerceAtLeast(6)
        val gap = barWidth / 2
        val totalBarsWidth = barCount * barWidth + (barCount - 1) * gap
        val startX = (width - totalBarsWidth) / 2f

        val minBarHeight = height * 0.2f
        val maxBarHeight = height.toFloat()
        val heightStep = (maxBarHeight - minBarHeight) / (barCount - 1)

        for (i in 0 until barCount) {
            val left = startX + i * (barWidth + gap)
            val barHeight = minBarHeight + i * heightStep
            val top = height - barHeight
            barRects.add(RectF(left, top, left + barWidth, height.toFloat()))
            barGaps.add(RectF(left, height * 0.1f, left + barWidth, height * 0.9f))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val currentActive = animatedProgress * barCount

        for (i in 0 until barCount) {
            val isActive = i < currentActive
            val partialAlpha = if (i == currentActive.toInt()) {
                (currentActive - i) * 195 + 60
            } else {
                if (isActive) 255 else 60
            }

            if (isActive || i == currentActive.toInt()) {
                barPaint.color = barColor
                barPaint.alpha = partialAlpha.toInt().coerceIn(0, 255)
                canvas.drawRect(barRects[i], barPaint)
            } else {
                inactivePaint.color = barColor
                inactivePaint.alpha = 60
                canvas.drawRect(barRects[i], inactivePaint)
            }
        }
    }
}
