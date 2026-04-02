package com.signalmontor.app.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.signalmontor.app.SatelliteInfo
import kotlin.math.*

class Satellite3DView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var satellites = listOf<Sat3D>()
    private var stars = listOf<Star>()
    private var continents = listOf<Continent>()

    private var cx = 0f
    private var cy = 0f
    private var eR = 0f
    private var vW = 0
    private var vH = 0

    private var rX = -20f
    private var rY = 0f
    private var tRX = -20f
    private var tRY = 0f
    private var zoom = 1f
    private var tZoom = 1f

    private var dragging = false
    private var autoRot = true
    private var lastTX = 0f
    private var lastTY = 0f
    private var autoTimer = 0L

    private val scaleDet = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            tZoom = (tZoom * detector.scaleFactor).coerceIn(0.5f, 2.5f)
            autoRot = false
            autoTimer = System.currentTimeMillis()
            invalidate()
            return true
        }
    })

    private val earthP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val atmosP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
        alpha = 50
    }
    private val eqP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        alpha = 100
        color = 0xFFFFEB3B.toInt()
    }
    private val landP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val starP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 10f
        color = 0xFFFFFFFF.toInt()
        alpha = 220
        isFakeBoldText = true
    }
    private val smallP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 8f
        color = 0xFFBDBDBD.toInt()
        alpha = 180
    }

    data class Star(val x: Float, val y: Float, val sz: Float, val sp: Float)
    data class CP(val lat: Float, val lon: Float)
    data class Continent(val pts: List<CP>, val col: Int)
    data class Sat3D(
        val info: SatelliteInfo,
        var ax: Float = 0f,
        var ay: Float = 0f,
        var az: Float = 0f,
        var tx: Float = 0f,
        var ty: Float = 0f,
        var tz: Float = 0f,
        var al: Float = 0f
    )

    init {
        genStars()
        genContinents()
    }

    private fun genStars() {
        stars = (0..200).map {
            Star(randomF(), randomF(), 0.3f + randomF() * 1.2f, 0.5f + randomF() * 3f)
        }
    }

    private fun genContinents() {
        continents = listOf(
            Continent(listOf(
                CP(70f, -160f), CP(72f, -140f), CP(70f, -120f), CP(65f, -100f),
                CP(60f, -80f), CP(55f, -60f), CP(50f, -55f), CP(45f, -55f),
                CP(30f, -80f), CP(25f, -80f), CP(25f, -100f), CP(30f, -115f),
                CP(35f, -120f), CP(40f, -125f), CP(50f, -130f), CP(55f, -135f),
                CP(60f, -145f), CP(65f, -165f), CP(68f, -170f)
            ), 0xFF2E7D32.toInt()),
            Continent(listOf(
                CP(15f, -90f), CP(20f, -88f), CP(20f, -80f), CP(15f, -75f),
                CP(10f, -75f), CP(8f, -77f), CP(5f, -80f), CP(8f, -83f),
                CP(12f, -86f), CP(15f, -90f)
            ), 0xFF388E3C.toInt()),
            Continent(listOf(
                CP(12f, -70f), CP(10f, -60f), CP(5f, -50f), CP(0f, -50f),
                CP(-5f, -35f), CP(-10f, -37f), CP(-15f, -40f), CP(-20f, -42f),
                CP(-25f, -48f), CP(-30f, -52f), CP(-35f, -55f), CP(-40f, -62f),
                CP(-45f, -65f), CP(-50f, -70f), CP(-52f, -70f), CP(-50f, -75f),
                CP(-45f, -75f), CP(-35f, -72f), CP(-25f, -70f), CP(-18f, -70f),
                CP(-10f, -77f), CP(-5f, -80f), CP(0f, -80f), CP(5f, -77f),
                CP(10f, -72f), CP(12f, -70f)
            ), 0xFF338A3C.toInt()),
            Continent(listOf(
                CP(37f, -10f), CP(40f, 0f), CP(43f, 5f), CP(45f, 10f),
                CP(48f, 5f), CP(50f, 5f), CP(55f, 5f), CP(58f, 10f),
                CP(60f, 10f), CP(62f, 5f), CP(65f, 15f), CP(68f, 18f),
                CP(70f, 25f), CP(70f, 30f), CP(68f, 28f), CP(65f, 25f),
                CP(60f, 30f), CP(55f, 25f), CP(50f, 20f), CP(48f, 15f),
                CP(45f, 15f), CP(42f, 20f), CP(40f, 25f), CP(38f, 25f),
                CP(35f, 25f), CP(33f, 10f), CP(35f, 0f), CP(36f, -5f),
                CP(37f, -10f)
            ), 0xFF2E7D32.toInt()),
            Continent(listOf(
                CP(37f, 0f), CP(35f, -5f), CP(35f, -15f), CP(30f, -17f),
                CP(25f, -17f), CP(20f, -17f), CP(15f, -17f), CP(10f, -15f),
                CP(5f, -10f), CP(5f, -5f), CP(5f, 5f), CP(5f, 10f),
                CP(2f, 10f), CP(0f, 10f), CP(-5f, 12f), CP(-10f, 14f),
                CP(-15f, 12f), CP(-20f, 15f), CP(-25f, 18f), CP(-30f, 20f),
                CP(-33f, 25f), CP(-35f, 20f), CP(-35f, 18f), CP(-30f, 30f),
                CP(-25f, 35f), CP(-20f, 35f), CP(-15f, 40f), CP(-10f, 42f),
                CP(-5f, 42f), CP(0f, 42f), CP(5f, 45f), CP(8f, 45f),
                CP(10f, 42f), CP(12f, 44f), CP(15f, 42f), CP(18f, 40f),
                CP(20f, 40f), CP(22f, 38f), CP(25f, 35f), CP(30f, 33f),
                CP(32f, 32f), CP(35f, 35f), CP(37f, 10f), CP(37f, 0f)
            ), 0xFF388E3C.toInt()),
            Continent(listOf(
                CP(42f, 30f), CP(45f, 35f), CP(48f, 40f), CP(50f, 45f),
                CP(55f, 40f), CP(58f, 35f), CP(60f, 30f), CP(62f, 28f),
                CP(65f, 30f), CP(68f, 35f), CP(70f, 40f), CP(72f, 50f),
                CP(72f, 60f), CP(70f, 70f), CP(68f, 80f), CP(65f, 90f),
                CP(65f, 100f), CP(68f, 110f), CP(70f, 120f), CP(72f, 130f),
                CP(72f, 140f), CP(70f, 150f), CP(68f, 160f), CP(65f, 170f),
                CP(62f, 175f), CP(60f, 165f), CP(55f, 160f), CP(50f, 155f),
                CP(48f, 145f), CP(45f, 140f), CP(42f, 135f), CP(40f, 130f),
                CP(38f, 125f), CP(35f, 120f), CP(30f, 120f), CP(25f, 120f),
                CP(22f, 115f), CP(20f, 110f), CP(18f, 105f), CP(15f, 100f),
                CP(12f, 100f), CP(10f, 105f), CP(8f, 105f), CP(5f, 100f),
                CP(2f, 108f), CP(0f, 110f), CP(-2f, 118f), CP(-5f, 120f),
                CP(-8f, 118f), CP(-8f, 112f), CP(-5f, 108f), CP(-2f, 108f),
                CP(0f, 105f), CP(2f, 108f), CP(5f, 105f), CP(8f, 100f),
                CP(10f, 95f), CP(12f, 90f), CP(15f, 85f), CP(18f, 80f),
                CP(20f, 75f), CP(22f, 70f), CP(25f, 68f), CP(28f, 65f),
                CP(30f, 62f), CP(32f, 55f), CP(35f, 50f), CP(38f, 45f),
                CP(40f, 40f), CP(42f, 35f), CP(42f, 30f)
            ), 0xFF2E7D32.toInt()),
            Continent(listOf(
                CP(-15f, 120f), CP(-18f, 115f), CP(-22f, 114f), CP(-25f, 114f),
                CP(-28f, 114f), CP(-32f, 116f), CP(-35f, 118f), CP(-37f, 140f),
                CP(-38f, 145f), CP(-37f, 148f), CP(-35f, 150f), CP(-30f, 153f),
                CP(-25f, 153f), CP(-20f, 148f), CP(-18f, 146f), CP(-15f, 145f),
                CP(-12f, 142f), CP(-12f, 135f), CP(-14f, 130f), CP(-15f, 125f),
                CP(-15f, 120f)
            ), 0xFF338A3C.toInt()),
            Continent(listOf(
                CP(-35f, 166f), CP(-37f, 172f), CP(-40f, 175f), CP(-42f, 174f),
                CP(-45f, 168f), CP(-46f, 167f), CP(-44f, 168f), CP(-42f, 170f),
                CP(-40f, 172f), CP(-38f, 176f), CP(-36f, 178f), CP(-35f, 176f),
                CP(-35f, 172f), CP(-35f, 166f)
            ), 0xFF388E3C.toInt())
        )
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        vW = w; vH = h; cx = w / 2f; cy = h / 2f; eR = min(w, h) * 0.22f
        earthP.shader = RadialGradient(cx - eR * 0.3f, cy - eR * 0.3f, eR * 1.3f,
            intArrayOf(0xFF42A5F5.toInt(), 0xFF1976D2.toInt(), 0xFF0D47A1.toInt(), 0xFF0A1929.toInt()),
            floatArrayOf(0f, 0.35f, 0.75f, 1f), Shader.TileMode.CLAMP)
        atmosP.shader = RadialGradient(cx, cy, eR * 1.5f,
            intArrayOf(0x404FC3F7.toInt(), 0x101976D2.toInt(), 0x00000000.toInt()),
            floatArrayOf(0.6f, 0.85f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.drawColor(0xFF030612.toInt())
        drawStars(c)
        if (autoRot && System.currentTimeMillis() - autoTimer > 4000) tRY += 0.12f
        rX += (tRX - rX) * 0.1f; rY += (tRY - rY) * 0.1f; zoom += (tZoom - zoom) * 0.1f
        val r = eR * zoom
        val sorted = satellites.sortedBy { it.az }
        sorted.filter { it.az < 0 }.forEach { drawSat(c, it, r) }
        drawGlobe(c, r)
        sorted.filter { it.az >= 0 }.forEach { drawSat(c, it, r) }
        drawLegend(c)
    }

    private fun drawStars(c: Canvas) {
        val t = System.currentTimeMillis() / 1000f
        stars.forEach { s ->
            val tw = (sin(t * s.sp + s.x * 100) * 0.35f + 0.65f)
            starP.color = 0xFFFFFFFF.toInt(); starP.alpha = (tw * 180).toInt(); starP.strokeWidth = s.sz
            c.drawPoint(s.x * vW, s.y * vH, starP)
        }
    }

    private fun rY3(x: Float, y: Float, z: Float, a: Float): Triple<Float, Float, Float> {
        val r = Math.toRadians(a.toDouble()).toFloat()
        val co = cos(r); val si = sin(r)
        return Triple(x * co + z * si, y, -x * si + z * co)
    }
    private fun rX3(x: Float, y: Float, z: Float, a: Float): Triple<Float, Float, Float> {
        val r = Math.toRadians(a.toDouble()).toFloat()
        val co = cos(r); val si = sin(r)
        return Triple(x, y * co - z * si, y * si + z * co)
    }
    private fun pj(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
        val (x1, y1, z1) = rY3(x, y, z, rY)
        val (x2, y2, z2) = rX3(x1, y1, z1, rX)
        val p = 1000f; val sc = p / (p + z2)
        return Triple(x2 * sc, y2 * sc, z2)
    }

    private fun drawGlobe(c: Canvas, r: Float) {
        c.drawCircle(cx, cy, r * 1.4f, atmosP)
        val clip = Path(); clip.addCircle(cx, cy, r, Path.Direction.CW)
        c.save(); c.clipPath(clip)
        c.drawCircle(cx, cy, r, earthP)
        gridP.color = 0xFF4FC3F7.toInt(); gridP.alpha = 50
        for (lat in -60..60 step 30) {
            val pts = mutableListOf<Pair<Float, Float>>()
            for (lon in -180..180 step 3) {
                val lr = Math.toRadians(lat.toDouble())
                val lor = Math.toRadians(lon.toDouble())
                val (sx, sy, sz) = pj((r * cos(lr) * cos(lor)).toFloat(), (r * sin(lr)).toFloat(), (r * cos(lr) * sin(lor)).toFloat())
                if (sz > -r * 0.3f) pts.add(Pair(cx + sx, cy + sy))
            }
            if (pts.size > 1) { val p = Path(); pts.forEachIndexed { i, pt -> if (i == 0) p.moveTo(pt.first, pt.second) else p.lineTo(pt.first, pt.second) }; c.drawPath(p, gridP) }
        }
        for (lon in -180..180 step 30) {
            val pts = mutableListOf<Pair<Float, Float>>()
            for (lat in -90..90 step 3) {
                val lr = Math.toRadians(lat.toDouble())
                val lor = Math.toRadians(lon.toDouble())
                val (sx, sy, sz) = pj((r * cos(lr) * cos(lor)).toFloat(), (r * sin(lr)).toFloat(), (r * cos(lr) * sin(lor)).toFloat())
                if (sz > -r * 0.3f) pts.add(Pair(cx + sx, cy + sy))
            }
            if (pts.size > 1) { val p = Path(); pts.forEachIndexed { i, pt -> if (i == 0) p.moveTo(pt.first, pt.second) else p.lineTo(pt.first, pt.second) }; c.drawPath(p, gridP) }
        }
        val eqPts = mutableListOf<Pair<Float, Float>>()
        for (lon in -180..180 step 2) {
            val lor = Math.toRadians(lon.toDouble())
            val (sx, sy, sz) = pj((r * cos(lor)).toFloat(), 0f, (r * sin(lor)).toFloat())
            if (sz > -r * 0.3f) eqPts.add(Pair(cx + sx, cy + sy))
        }
        if (eqPts.size > 1) { val p = Path(); eqPts.forEachIndexed { i, pt -> if (i == 0) p.moveTo(pt.first, pt.second) else p.lineTo(pt.first, pt.second) }; c.drawPath(p, eqP) }
        continents.forEach { cont ->
            val sp = cont.pts.map { cp ->
                val lr = Math.toRadians(cp.lat.toDouble())
                val lor = Math.toRadians(cp.lon.toDouble())
                val (sx, sy, sz) = pj((r * cos(lr) * cos(lor)).toFloat(), (r * sin(lr)).toFloat(), (r * cos(lr) * sin(lor)).toFloat())
                Triple(cx + sx, cy + sy, sz)
            }
            if (sp.any { it.third > -r * 0.2f }) {
                val path = Path()
                sp.forEachIndexed { i, (sx, sy, _) -> if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy) }
                path.close()
                landP.color = cont.col; landP.alpha = 160
                c.drawPath(path, landP)
            }
        }
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx - r * 0.35f, cy - r * 0.35f, r * 0.8f,
                intArrayOf(0x70FFFFFF.toInt(), 0x00FFFFFF.toInt()), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        })
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx + r * 0.4f, cy + r * 0.35f, r * 0.9f,
                intArrayOf(0x00000000.toInt(), 0x90000000.toInt()), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        })
        c.restore()
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f; color = 0xFF4FC3F7.toInt(); alpha = 80
        })
    }

    private fun drawSat(c: Canvas, s: Sat3D, r: Float) {
        val oR = r * 2.5f
        val elR = Math.toRadians(s.info.elevation.toDouble())
        val azR = Math.toRadians((s.info.azimuth - 90).toDouble())
        val d = oR + r * 0.3f
        val x = (d * cos(elR) * cos(azR)).toFloat()
        val y = (d * cos(elR) * sin(azR)).toFloat()
        val z = (d * sin(elR)).toFloat()
        val (sx, sy, sz) = pj(x, y, z)
        s.ax = sx; s.ay = sy; s.az = sz
        if (sx * sx + sy * sy > (r * 2.8f) * (r * 2.8f)) return
        val col = sCol(s.info.constellation)
        val sz2 = 5f + s.info.snr / 15f
        val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx + sx, cy + sy, sz2 * 5f, intArrayOf(col, 0x00000000.toInt()), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            alpha = 100
        }
        c.drawCircle(cx + sx, cy + sy, sz2 * 5f, gp)
        c.drawCircle(cx + sx, cy + sy, sz2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; alpha = 230 })
        c.drawCircle(cx + sx - sz2 * 0.3f, cy + sy - sz2 * 0.3f, sz2 * 0.35f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); alpha = 150 })
        if (s.info.usedInFix) {
            c.drawLine(cx, cy, cx + sx, cy + sy, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = col; strokeWidth = 1f; alpha = 80; style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
            })
        }
        if (s.info.elevation > 5) {
            c.drawText("${s.info.constellation} ${s.info.prn}", cx + sx, cy + sy - sz2 - 6, labelP)
            c.drawText("${s.info.snr.toInt()}dB ${s.info.elevation.toInt()}°", cx + sx, cy + sy + sz2 + 11, smallP)
        }
    }

    private fun sCol(c: String) = when (c) {
        "GPS" -> 0xFF2196F3.toInt(); "北斗" -> 0xFFF44336.toInt()
        "GLONASS" -> 0xFF4CAF50.toInt(); "Galileo" -> 0xFFFF9800.toInt()
        else -> 0xFF9E9E9E.toInt()
    }

    private fun drawLegend(c: Canvas) {
        val items = listOf(Pair("GPS", 0xFF2196F3.toInt()), Pair("北斗", 0xFFF44336.toInt()),
            Pair("GLONASS", 0xFF4CAF50.toInt()), Pair("Galileo", 0xFFFF9800.toInt()))
        c.drawRoundRect(8f, 8f, 105f, 8f + items.size * 16f + 10f, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x80000000.toInt() })
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; alpha = 220 }
        var y = 22f
        items.forEach { (n, cl) ->
            c.drawCircle(18f, y, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cl })
            tp.color = 0xFFFFFFFF.toInt(); c.drawText(n, 30f, y + 3, tp); y += 16f
        }
        c.drawText("拖拽旋转 | 双指缩放", cx, vH - 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f; color = 0xFFBDBDBD.toInt(); alpha = 160; textAlign = Paint.Align.CENTER
        })
    }

    fun updateSatellites(ns: List<SatelliteInfo>) {
        val na = ns.filter { it.snr >= 0 }.map { sat ->
            val ex = satellites.find { it.info.prn == sat.prn && it.info.constellation == sat.constellation }
            val (x, y, z) = ae3d(sat.azimuth, sat.elevation)
            if (ex != null) ex.copy(info = sat, tx = x, ty = y, tz = z)
            else Sat3D(info = sat, tx = x, ty = y, tz = z)
        }
        satellites = na
        na.forEach { s ->
            av(s.ax, s.tx) { s.ax = it }; av(s.ay, s.ty) { s.ay = it }
            av(s.az, s.tz) { s.az = it }; av(s.al, 255f) { s.al = it }
        }
    }

    private fun av(f: Float, t: Float, u: (Float) -> Unit) {
        ValueAnimator.ofFloat(f, t).apply {
            duration = 700; interpolator = DecelerateInterpolator()
            addUpdateListener { u(it.animatedValue as Float); invalidate() }; start()
        }
    }

    private fun ae3d(az: Float, el: Float): Triple<Float, Float, Float> {
        val d = eR * 2.5f; val elR = Math.toRadians(el.toDouble()); val azR = Math.toRadians((az - 90).toDouble())
        return Triple((d * cos(elR) * cos(azR)).toFloat(), (d * cos(elR) * sin(azR)).toFloat(), (d * sin(elR)).toFloat())
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDet.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true; autoRot = false; lastTX = e.x; lastTY = e.y
                autoTimer = System.currentTimeMillis(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging && !scaleDet.isInProgress) {
                    val dx = e.x - lastTX; val dy = e.y - lastTY
                    tRY += dx * 0.5f; tRX = (tRX + dy * 0.3f).coerceIn(-80f, 80f)
                    lastTX = e.x; lastTY = e.y; invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(e)
    }

    private fun randomF() = Math.random().toFloat()
}
