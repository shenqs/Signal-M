package com.signalmontor.app.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.signalmontor.app.MovementState
import com.signalmontor.app.SpeedCalculator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class SpeedMonitorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val speedArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
    }

    private val speedArcBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        color = 0xFFE8E8E8.toInt()
    }

    private val speedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val unitTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val stateTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val descTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val infoLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
    }

    private val infoValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }

    private val compassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val compassTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val headingArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private val headingArcBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = 0xFFE8E8E8.toInt()
    }

    private val headingTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1f
        color = 0xFFEEEEEE.toInt()
    }

    private val speedRect = RectF()
    private val headingRect = RectF()
    private var animatedSpeed = 0f
    private var animatedHeading = 0f
    private var currentColor = 0xFF9E9E9E.toInt()

    private var currentSpeedData: SpeedCalculator.SpeedData? = null

    private var speedAnimator: ValueAnimator? = null
    private var headingAnimator: ValueAnimator? = null
    private var lastUiUpdate = 0L
    private val UI_THROTTLE_MS = 500L

    private var gaugeSize = 0f
    private var descY1 = 0f
    private var descY2 = 0f
    private var compassDescY = 0f

    fun updateSpeedData(data: SpeedCalculator.SpeedData) {
        val now = System.currentTimeMillis()
        if (now - lastUiUpdate < UI_THROTTLE_MS) {
            currentSpeedData = data
            return
        }
        lastUiUpdate = now
        currentSpeedData = data
        currentColor = data.movementState.color

        val targetSpeed = data.speed.coerceIn(0f, 200f)
        speedAnimator?.cancel()
        speedAnimator = ValueAnimator.ofFloat(animatedSpeed, targetSpeed).apply {
            duration = 800
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                animatedSpeed = anim.animatedValue as Float
                invalidate()
            }
            start()
        }

        val targetHeading = data.bearing
        headingAnimator?.cancel()
        headingAnimator = ValueAnimator.ofFloat(animatedHeading, targetHeading).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                animatedHeading = anim.animatedValue as Float
                invalidate()
            }
            start()
        }

        invalidate()
    }

    fun reset() {
        speedAnimator?.cancel()
        headingAnimator?.cancel()
        animatedSpeed = 0f
        animatedHeading = 0f
        currentColor = 0xFF9E9E9E.toInt()
        currentSpeedData = null
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padding = 10f

        val halfW = w / 2f
        gaugeSize = min(h * 0.30f, halfW - padding * 3)

        val speedLeft = (halfW - gaugeSize) / 2f
        val speedTop = padding
        speedRect.set(speedLeft, speedTop, speedLeft + gaugeSize, speedTop + gaugeSize)

        val headingLeft = halfW + (halfW - gaugeSize) / 2f
        val headingTop = padding
        headingRect.set(headingLeft, headingTop, headingLeft + gaugeSize, headingTop + gaugeSize)

        val gaugeTextSize = gaugeSize * 0.15f
        speedTextPaint.textSize = gaugeTextSize
        unitTextPaint.textSize = gaugeTextSize * 0.36f
        stateTextPaint.textSize = gaugeTextSize * 0.30f
        descTextPaint.textSize = gaugeTextSize * 0.26f
        compassTextPaint.textSize = gaugeSize * 0.12f
        headingTextPaint.textSize = gaugeSize * 0.08f
        infoLabelPaint.textSize = w * 0.030f
        infoValuePaint.textSize = w * 0.030f

        descY1 = speedRect.bottom + 12f
        descY2 = descY1 + descTextPaint.textSize + 6f
        compassDescY = headingRect.bottom + 12f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        val data = currentSpeedData
        if (data == null) {
            drawPlaceholder(canvas, w, h)
            return
        }

        val halfW = w / 2f
        canvas.drawLine(halfW, 0f, halfW, h, dividerPaint)

        drawSpeedGauge(canvas, data)
        drawHeadingGauge(canvas, data)
        drawInfoSection(canvas, data, h)
    }

    private fun drawPlaceholder(canvas: Canvas, w: Float, h: Float) {
        canvas.drawArc(speedRect, 135f, 270f, false, speedArcBgPaint)
        speedTextPaint.color = 0xFF9E9E9E.toInt()
        canvas.drawText("0.0", speedRect.centerX(), speedRect.centerY() + speedTextPaint.textSize / 3, speedTextPaint)

        canvas.drawCircle(headingRect.centerX(), headingRect.centerY(), headingRect.width() / 2f, headingArcBgPaint)
        compassTextPaint.color = 0xFF9E9E9E.toInt()
        canvas.drawText("等待传感器数据...", w / 2f, h / 2f, compassTextPaint)
    }

    private fun drawSpeedGauge(canvas: Canvas, data: SpeedCalculator.SpeedData) {
        val cx = speedRect.centerX()
        val cy = speedRect.centerY()

        canvas.drawArc(speedRect, 135f, 270f, false, speedArcBgPaint)
        speedArcPaint.color = currentColor
        val sweep = (animatedSpeed / 200f) * 270f
        if (sweep > 0) {
            canvas.drawArc(speedRect, 135f, sweep, false, speedArcPaint)
        }

        speedTextPaint.color = currentColor
        canvas.drawText(String.format("%.1f", data.speed), cx, cy + speedTextPaint.textSize / 3, speedTextPaint)

        unitTextPaint.color = 0xFF757575.toInt()
        canvas.drawText("km/h", cx, cy + speedTextPaint.textSize + unitTextPaint.textSize + 4, unitTextPaint)

        stateTextPaint.color = data.movementState.color
        canvas.drawText("${data.movementState.icon} ${data.movementState.label}", cx, cy + speedTextPaint.textSize + unitTextPaint.textSize + 20, stateTextPaint)

        descTextPaint.textAlign = Paint.Align.CENTER
        descTextPaint.color = 0xFF4CAF50.toInt()
        canvas.drawText(data.speedDescription, cx, descY1, descTextPaint)

        descTextPaint.color = 0xFF2196F3.toInt()
        canvas.drawText(data.altitudeDescription, cx, descY2, descTextPaint)
    }

    private fun drawHeadingGauge(canvas: Canvas, data: SpeedCalculator.SpeedData) {
        val cx = headingRect.centerX()
        val cy = headingRect.centerY()
        val r = headingRect.width() / 2f - 5f

        canvas.drawCircle(cx, cy, r + 2f, headingArcBgPaint)

        headingArcPaint.color = currentColor
        val headingSweep = (animatedHeading / 360f) * 360f
        canvas.drawArc(
            RectF(cx - r, cy - r, cx + r, cy + r),
            -90f, headingSweep, false, headingArcPaint
        )

        canvas.save()
        canvas.rotate(data.bearing, cx, cy)

        compassPaint.color = 0xFFF44336.toInt()
        val arrowPath = Path()
        arrowPath.moveTo(cx, cy - r * 0.55f)
        arrowPath.lineTo(cx - r * 0.16f, cy + r * 0.10f)
        arrowPath.lineTo(cx + r * 0.16f, cy + r * 0.10f)
        arrowPath.close()
        canvas.drawPath(arrowPath, compassPaint)

        compassPaint.color = 0xFFBDBDBD.toInt()
        val tailPath = Path()
        tailPath.moveTo(cx, cy + r * 0.50f)
        tailPath.lineTo(cx - r * 0.10f, cy)
        tailPath.lineTo(cx + r * 0.10f, cy)
        tailPath.close()
        canvas.drawPath(tailPath, compassPaint)

        canvas.restore()

        compassTextPaint.textSize = r * 0.22f
        compassTextPaint.color = 0xFFF44336.toInt()
        canvas.drawText("N", cx, cy - r - 3f, compassTextPaint)

        compassTextPaint.color = 0xFF757575.toInt()
        canvas.drawText("S", cx, cy + r + 12f, compassTextPaint)
        canvas.drawText("E", cx + r + 10f, cy + compassTextPaint.textSize / 3, compassTextPaint)
        canvas.drawText("W", cx - r - 10f, cy + compassTextPaint.textSize / 3, compassTextPaint)

        headingTextPaint.color = 0xFF212121.toInt()
        canvas.drawText(String.format("%.0f\u00B0", data.bearing), cx, cy + r + 26f, headingTextPaint)

        descTextPaint.textAlign = Paint.Align.CENTER
        descTextPaint.color = 0xFF757575.toInt()
        canvas.drawText("方位: ${data.direction}", cx, compassDescY, descTextPaint)
    }

    private fun drawInfoSection(canvas: Canvas, data: SpeedCalculator.SpeedData, h: Float) {
        val col1X = width * 0.06f
        val col2X = width * 0.54f
        val valX1 = width * 0.22f
        val valX2 = width * 0.70f
        var y = h * 0.52f
        val lineHeight = h * 0.055f

        infoLabelPaint.color = 0xFF9E9E9E.toInt()
        infoValuePaint.textAlign = Paint.Align.LEFT
        infoLabelPaint.textAlign = Paint.Align.LEFT

        drawRow(canvas, "加速度", String.format("%.2f m/s\u00B2", data.acceleration), getAccelColor(data.acceleration), col1X, valX1, y)
        drawRow(canvas, "最高速", String.format("%.1f km/h", data.maxSpeed), currentColor, col2X, valX2, y)
        y += lineHeight

        drawRow(canvas, "总距离", String.format("%.3f km", data.totalDistance), 0xFF212121.toInt(), col1X, valX1, y)
        drawRow(canvas, "步数", "${data.stepCount} 步", 0xFF212121.toInt(), col2X, valX2, y)
        y += lineHeight

        val altText = if (data.hasBarometer) String.format("%.1f m", data.altitude) else if (data.gpsAltitude != 0f) String.format("%.1f m (GPS)", data.gpsAltitude) else "--"
        val altColor = if (data.hasBarometer) 0xFF2196F3.toInt() else if (data.gpsAltitude != 0f) 0xFFFF9800.toInt() else 0xFF9E9E9E.toInt()
        drawRow(canvas, "海拔", altText, altColor, col1X, valX1, y)

        val pressText = if (data.hasBarometer && data.pressure > 0) String.format("%.1f hPa", data.pressure) else "--"
        drawRow(canvas, "气压", pressText, 0xFF4CAF50.toInt(), col2X, valX2, y)
        y += lineHeight

        val gravText = String.format("%.3f m/s\u00B2", data.gravityMagnitude)
        drawRow(canvas, "重力", gravText, 0xFF757575.toInt(), col1X, valX1, y)

        val rateText = if (data.altitudeChangeRate > 0) "+%.1f m/s" else "%.1f m/s"
        drawRow(canvas, "升降率", String.format(rateText, data.altitudeChangeRate), if (data.altitudeChangeRate > 0) 0xFFF44336.toInt() else 0xFF2196F3.toInt(), col2X, valX2, y)

        if (data.temperature != 0f) {
            y += lineHeight
            drawRow(canvas, "温度", String.format("%.1f \u00B0C", data.temperature), 0xFFFF9800.toInt(), col1X, valX1, y)
        }
    }

    private fun drawRow(canvas: Canvas, label: String, value: String, valueColor: Int, labelX: Float, valX: Float, y: Float) {
        infoLabelPaint.color = 0xFF9E9E9E.toInt()
        canvas.drawText(label, labelX, y, infoLabelPaint)
        infoValuePaint.color = valueColor
        canvas.drawText(value, valX, y, infoValuePaint)
    }

    private fun getAccelColor(accel: Float): Int {
        return when {
            accel < 2f -> 0xFF4CAF50.toInt()
            accel < 5f -> 0xFFFFC107.toInt()
            accel < 10f -> 0xFFFF9800.toInt()
            else -> 0xFFF44336.toInt()
        }
    }
}
