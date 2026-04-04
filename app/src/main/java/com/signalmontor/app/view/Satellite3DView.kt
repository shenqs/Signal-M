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
    private var landMasses = listOf<LandMass>()
    private var tropicalRegions = listOf<TropicalRegion>()
    private var desertRegions = listOf<DesertRegion>()
    private var iceCaps = listOf<IceCap>()
    private var userLocation: UserLocation? = null

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

    private val oceanP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val atmosP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
        alpha = 40
    }
    private val eqP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        alpha = 80
        color = 0xFFFFEB3B.toInt()
    }
    private val landP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val tropicalP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val desertP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val iceP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val chinaP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val chinaStrokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFFFF1744.toInt()
    }
    private val taiwanP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val taiwanStrokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFFFF1744.toInt()
    }
    private val userLocP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val userLocRingP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
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
    private val regionLabelP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f
        color = 0xFFFFFFFF.toInt()
        alpha = 255
        isFakeBoldText = true
    }
    private val regionSubLabelP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 9f
        color = 0xFFE0E0E0.toInt()
        alpha = 220
    }

    data class Star(val x: Float, val y: Float, val sz: Float, val sp: Float, val color: Int)
    data class CP(val lat: Float, val lon: Float)
    data class LandMass(val pts: List<CP>, val col: Int)
    data class TropicalRegion(val pts: List<CP>, val col: Int)
    data class DesertRegion(val pts: List<CP>, val col: Int)
    data class IceCap(val pts: List<CP>, val col: Int)
    data class Sat3D(
        val info: SatelliteInfo,
        var ax: Float = 0f, var ay: Float = 0f, var az: Float = 0f,
        var tx: Float = 0f, var ty: Float = 0f, var tz: Float = 0f, var al: Float = 0f
    )
    data class UserLocation(val lat: Double, val lon: Double, val region: String, val subRegion: String)

    init {
        genStars()
        genLandMasses()
        genTropicalRegions()
        genDesertRegions()
        genIceCaps()
    }

    private fun genStars() {
        val colors = listOf(0xFFFFFFFF.toInt(), 0xFFE8E0D0.toInt(), 0xFFD0D8FF.toInt(), 0xFFFFF0D0.toInt())
        stars = (0..300).map {
            Star(randomF(), randomF(), 0.2f + randomF() * 1.5f, 0.3f + randomF() * 4f, colors[(randomF() * colors.size).toInt()])
        }
    }

    private fun genLandMasses() {
        landMasses = listOf(
            LandMass(listOf(
                CP(72f, -168f), CP(71f, -155f), CP(70f, -145f), CP(68f, -140f),
                CP(65f, -140f), CP(62f, -145f), CP(60f, -148f), CP(58f, -153f),
                CP(57f, -155f), CP(55f, -160f), CP(55f, -163f), CP(57f, -165f),
                CP(58f, -160f), CP(60f, -165f), CP(62f, -165f), CP(65f, -165f),
                CP(68f, -165f), CP(70f, -165f), CP(72f, -168f)
            ), 0xFF33691E.toInt()),
            LandMass(listOf(
                CP(70f, -140f), CP(72f, -120f), CP(70f, -100f), CP(65f, -80f),
                CP(60f, -65f), CP(55f, -60f), CP(50f, -55f), CP(48f, -55f),
                CP(45f, -55f), CP(43f, -65f), CP(44f, -68f), CP(45f, -65f),
                CP(47f, -60f), CP(49f, -58f), CP(50f, -58f), CP(52f, -56f),
                CP(55f, -58f), CP(58f, -55f), CP(60f, -55f), CP(62f, -50f),
                CP(65f, -40f), CP(68f, -30f), CP(70f, -22f), CP(72f, -22f),
                CP(74f, -20f), CP(76f, -28f), CP(78f, -30f), CP(80f, -35f),
                CP(82f, -45f), CP(83f, -55f), CP(82f, -65f), CP(80f, -70f),
                CP(78f, -72f), CP(76f, -68f), CP(74f, -70f), CP(72f, -72f),
                CP(70f, -75f), CP(68f, -80f), CP(65f, -78f), CP(63f, -75f),
                CP(60f, -65f), CP(58f, -60f), CP(55f, -58f), CP(52f, -56f),
                CP(50f, -58f), CP(48f, -55f), CP(47f, -55f), CP(45f, -60f),
                CP(44f, -63f), CP(43f, -66f), CP(42f, -70f), CP(41f, -72f),
                CP(40f, -74f), CP(39f, -76f), CP(38f, -82f), CP(37f, -88f),
                CP(35f, -90f), CP(33f, -90f), CP(30f, -88f), CP(29f, -85f),
                CP(28f, -83f), CP(26f, -82f), CP(25f, -81f), CP(25f, -80f),
                CP(28f, -80f), CP(30f, -80f), CP(33f, -78f), CP(35f, -75f),
                CP(38f, -76f), CP(39f, -76f), CP(40f, -74f), CP(41f, -72f),
                CP(42f, -70f), CP(43f, -68f), CP(45f, -65f), CP(47f, -65f),
                CP(49f, -60f), CP(50f, -58f), CP(48f, -55f), CP(45f, -55f),
                CP(42f, -60f), CP(40f, -65f), CP(38f, -70f), CP(35f, -75f),
                CP(33f, -78f), CP(30f, -80f), CP(28f, -80f), CP(25f, -80f),
                CP(25f, -81f), CP(24f, -82f), CP(25f, -82f), CP(26f, -82f),
                CP(28f, -83f), CP(29f, -85f), CP(30f, -88f), CP(29f, -89f),
                CP(28f, -90f), CP(27f, -90f), CP(26f, -90f), CP(25f, -90f),
                CP(22f, -90f), CP(20f, -90f), CP(18f, -90f), CP(16f, -90f),
                CP(15f, -92f), CP(16f, -94f), CP(18f, -95f), CP(20f, -97f),
                CP(22f, -98f), CP(25f, -100f), CP(28f, -105f), CP(30f, -105f),
                CP(32f, -105f), CP(32f, -108f), CP(30f, -110f), CP(32f, -115f),
                CP(33f, -117f), CP(34f, -120f), CP(36f, -121f), CP(38f, -123f),
                CP(40f, -124f), CP(42f, -124f), CP(45f, -124f), CP(48f, -125f),
                CP(50f, -128f), CP(52f, -130f), CP(55f, -132f), CP(57f, -136f),
                CP(58f, -137f), CP(60f, -140f), CP(60f, -145f), CP(61f, -148f),
                CP(60f, -150f), CP(58f, -153f), CP(57f, -155f), CP(55f, -160f),
                CP(55f, -163f), CP(57f, -165f), CP(58f, -160f), CP(60f, -165f),
                CP(62f, -165f), CP(65f, -165f), CP(68f, -165f), CP(70f, -165f),
                CP(72f, -168f), CP(71f, -155f), CP(70f, -145f), CP(70f, -140f)
            ), 0xFF2E7D32.toInt()),
            LandMass(listOf(
                CP(32f, -117f), CP(30f, -115f), CP(28f, -113f), CP(26f, -110f),
                CP(24f, -108f), CP(22f, -106f), CP(20f, -105f), CP(18f, -103f),
                CP(16f, -100f), CP(15f, -97f), CP(16f, -94f), CP(18f, -95f),
                CP(20f, -97f), CP(22f, -98f), CP(25f, -100f), CP(28f, -105f),
                CP(30f, -105f), CP(32f, -108f), CP(32f, -115f), CP(32f, -117f)
            ), 0xFF388E3C.toInt()),
            LandMass(listOf(
                CP(18f, -90f), CP(16f, -88f), CP(14f, -87f), CP(12f, -85f),
                CP(10f, -84f), CP(8f, -83f), CP(7f, -80f), CP(8f, -77f),
                CP(10f, -76f), CP(12f, -78f), CP(15f, -80f), CP(17f, -82f),
                CP(18f, -85f), CP(18f, -90f)
            ), 0xFF43A047.toInt()),
            LandMass(listOf(
                CP(12f, -72f), CP(10f, -68f), CP(8f, -62f), CP(6f, -55f),
                CP(4f, -52f), CP(2f, -50f), CP(0f, -50f), CP(-2f, -45f),
                CP(-5f, -42f), CP(-8f, -38f), CP(-10f, -37f), CP(-15f, -39f),
                CP(-18f, -40f), CP(-22f, -42f), CP(-23f, -44f), CP(-25f, -48f),
                CP(-28f, -50f), CP(-30f, -52f), CP(-33f, -52f), CP(-35f, -55f),
                CP(-38f, -58f), CP(-40f, -62f), CP(-42f, -63f), CP(-45f, -65f),
                CP(-48f, -66f), CP(-50f, -70f), CP(-52f, -70f), CP(-55f, -68f),
                CP(-55f, -65f), CP(-52f, -62f), CP(-50f, -58f), CP(-48f, -55f),
                CP(-45f, -52f), CP(-42f, -48f), CP(-40f, -45f), CP(-38f, -42f),
                CP(-35f, -38f), CP(-32f, -35f), CP(-30f, -32f), CP(-28f, -30f),
                CP(-25f, -28f), CP(-22f, -25f), CP(-20f, -22f), CP(-18f, -20f),
                CP(-15f, -18f), CP(-12f, -18f), CP(-10f, -20f), CP(-8f, -22f),
                CP(-5f, -25f), CP(-3f, -30f), CP(-2f, -35f), CP(-2f, -40f),
                CP(-2f, -45f), CP(0f, -50f), CP(2f, -50f), CP(4f, -52f),
                CP(6f, -55f), CP(8f, -60f), CP(10f, -65f), CP(12f, -70f),
                CP(12f, -72f)
            ), 0xFF338A3C.toInt()),
            LandMass(listOf(
                CP(36f, -6f), CP(37f, -2f), CP(38f, 0f), CP(40f, 0f),
                CP(42f, 2f), CP(43f, 2f), CP(44f, 0f), CP(46f, -2f),
                CP(48f, -4f), CP(48f, 0f), CP(49f, 2f), CP(50f, 2f),
                CP(51f, 0f), CP(52f, 2f), CP(54f, 4f), CP(55f, 8f),
                CP(56f, 8f), CP(58f, 6f), CP(60f, 5f), CP(62f, 5f),
                CP(63f, 8f), CP(65f, 12f), CP(68f, 15f), CP(70f, 20f),
                CP(70f, 25f), CP(71f, 25f), CP(70f, 28f), CP(68f, 28f),
                CP(65f, 25f), CP(60f, 30f), CP(55f, 25f), CP(50f, 20f),
                CP(48f, 15f), CP(45f, 15f), CP(42f, 20f), CP(40f, 25f),
                CP(38f, 25f), CP(35f, 25f), CP(33f, 10f), CP(35f, 0f),
                CP(36f, -6f)
            ), 0xFF2E7D32.toInt()),
            LandMass(listOf(
                CP(37f, -8f), CP(38f, -8f), CP(40f, -8f), CP(42f, -9f),
                CP(43f, -8f), CP(44f, -2f), CP(43f, 2f), CP(42f, 2f),
                CP(40f, 0f), CP(38f, 0f), CP(37f, -2f), CP(36f, -6f),
                CP(37f, -8f)
            ), 0xFF388E3C.toInt()),
            LandMass(listOf(
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
            LandMass(listOf(
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
            LandMass(listOf(
                CP(-15f, 120f), CP(-18f, 115f), CP(-22f, 114f), CP(-25f, 114f),
                CP(-28f, 114f), CP(-32f, 116f), CP(-35f, 118f), CP(-37f, 140f),
                CP(-38f, 145f), CP(-37f, 148f), CP(-35f, 150f), CP(-30f, 153f),
                CP(-25f, 153f), CP(-20f, 148f), CP(-18f, 146f), CP(-15f, 145f),
                CP(-12f, 142f), CP(-12f, 135f), CP(-14f, 130f), CP(-15f, 125f),
                CP(-15f, 120f)
            ), 0xFF338A3C.toInt()),
            LandMass(listOf(
                CP(-35f, 166f), CP(-37f, 172f), CP(-40f, 175f), CP(-42f, 174f),
                CP(-45f, 168f), CP(-46f, 167f), CP(-44f, 168f), CP(-42f, 170f),
                CP(-40f, 172f), CP(-38f, 176f), CP(-36f, 178f), CP(-35f, 176f),
                CP(-35f, 172f), CP(-35f, 166f)
            ), 0xFF388E3C.toInt()),
            LandMass(listOf(
                CP(38f, 130f), CP(37f, 132f), CP(36f, 135f), CP(35f, 136f),
                CP(34f, 135f), CP(33f, 132f), CP(33f, 130f), CP(34f, 129f),
                CP(35f, 130f), CP(36f, 131f), CP(37f, 131f), CP(38f, 130f)
            ), 0xFF388E3C.toInt()),
            LandMass(listOf(
                CP(32f, 130f), CP(31f, 131f), CP(30f, 131f), CP(29f, 130f),
                CP(28f, 129f), CP(28f, 128f), CP(29f, 128f), CP(30f, 129f),
                CP(31f, 130f), CP(32f, 130f)
            ), 0xFF43A047.toInt())
        )
    }

    private fun genTropicalRegions() {
        tropicalRegions = listOf(
            TropicalRegion(listOf(
                CP(10f, 80f), CP(12f, 79f), CP(15f, 78f), CP(18f, 77f),
                CP(20f, 76f), CP(22f, 75f), CP(24f, 76f), CP(25f, 78f),
                CP(24f, 80f), CP(22f, 81f), CP(20f, 82f), CP(18f, 82f),
                CP(15f, 81f), CP(12f, 81f), CP(10f, 80f)
            ), 0xFF43A047.toInt()),
            TropicalRegion(listOf(
                CP(5f, 80f), CP(6f, 79f), CP(8f, 79f), CP(9f, 80f),
                CP(8f, 81f), CP(6f, 81f), CP(5f, 80f)
            ), 0xFF4CAF50.toInt()),
            TropicalRegion(listOf(
                CP(6f, 100f), CP(5f, 101f), CP(4f, 103f), CP(3f, 104f),
                CP(2f, 105f), CP(1f, 105f), CP(0f, 104f), CP(1f, 103f),
                CP(2f, 102f), CP(3f, 101f), CP(4f, 100f), CP(5f, 99f),
                CP(6f, 100f)
            ), 0xFF43A047.toInt()),
            TropicalRegion(listOf(
                CP(-1f, 105f), CP(-2f, 106f), CP(-3f, 108f), CP(-4f, 110f),
                CP(-5f, 112f), CP(-6f, 114f), CP(-7f, 115f), CP(-8f, 114f),
                CP(-7f, 112f), CP(-6f, 110f), CP(-5f, 108f), CP(-4f, 106f),
                CP(-3f, 105f), CP(-2f, 104f), CP(-1f, 105f)
            ), 0xFF4CAF50.toInt()),
            TropicalRegion(listOf(
                CP(20f, 105f), CP(18f, 104f), CP(15f, 105f), CP(12f, 106f),
                CP(10f, 108f), CP(8f, 110f), CP(8f, 112f), CP(10f, 114f),
                CP(12f, 115f), CP(15f, 114f), CP(18f, 112f), CP(20f, 110f),
                CP(20f, 105f)
            ), 0xFF388E3C.toInt())
        )
    }

    private fun genDesertRegions() {
        desertRegions = listOf(
            DesertRegion(listOf(
                CP(30f, -10f), CP(32f, 0f), CP(33f, 10f), CP(32f, 20f),
                CP(30f, 25f), CP(28f, 30f), CP(25f, 33f), CP(22f, 35f),
                CP(20f, 35f), CP(18f, 33f), CP(18f, 30f), CP(20f, 25f),
                CP(22f, 20f), CP(23f, 15f), CP(24f, 10f), CP(25f, 5f),
                CP(25f, 0f), CP(26f, -5f), CP(28f, -8f), CP(30f, -10f)
            ), 0xFFC8B47A.toInt()),
            DesertRegion(listOf(
                CP(35f, 40f), CP(36f, 45f), CP(35f, 50f), CP(33f, 55f),
                CP(30f, 58f), CP(28f, 60f), CP(26f, 58f), CP(24f, 55f),
                CP(23f, 50f), CP(24f, 45f), CP(26f, 42f), CP(28f, 40f),
                CP(30f, 40f), CP(32f, 40f), CP(35f, 40f)
            ), 0xFFD4C08A.toInt()),
            DesertRegion(listOf(
                CP(38f, 70f), CP(40f, 75f), CP(42f, 80f), CP(44f, 85f),
                CP(45f, 90f), CP(44f, 95f), CP(42f, 98f), CP(40f, 100f),
                CP(38f, 98f), CP(36f, 95f), CP(35f, 90f), CP(35f, 85f),
                CP(36f, 80f), CP(37f, 75f), CP(38f, 70f)
            ), 0xFFD4C08A.toInt()),
            DesertRegion(listOf(
                CP(-25f, 115f), CP(-24f, 118f), CP(-23f, 122f), CP(-22f, 126f),
                CP(-23f, 130f), CP(-24f, 134f), CP(-26f, 138f), CP(-28f, 140f),
                CP(-30f, 138f), CP(-32f, 135f), CP(-33f, 130f), CP(-33f, 125f),
                CP(-32f, 120f), CP(-30f, 118f), CP(-28f, 116f), CP(-25f, 115f)
            ), 0xFFC8B47A.toInt()),
            DesertRegion(listOf(
                CP(35f, -110f), CP(36f, -108f), CP(37f, -105f), CP(36f, -102f),
                CP(35f, -100f), CP(33f, -100f), CP(32f, -102f), CP(31f, -105f),
                CP(32f, -108f), CP(33f, -110f), CP(35f, -110f)
            ), 0xFFC8B47A.toInt()),
            DesertRegion(listOf(
                CP(-20f, -70f), CP(-18f, -68f), CP(-16f, -66f), CP(-15f, -64f),
                CP(-16f, -62f), CP(-18f, -60f), CP(-20f, -60f), CP(-22f, -62f),
                CP(-24f, -64f), CP(-24f, -66f), CP(-22f, -68f), CP(-20f, -70f)
            ), 0xFFC8B47A.toInt())
        )
    }

    private fun genIceCaps() {
        iceCaps = listOf(
            IceCap(listOf(
                CP(65f, -180f), CP(70f, -160f), CP(75f, -140f), CP(78f, -120f),
                CP(80f, -100f), CP(82f, -80f), CP(83f, -60f), CP(82f, -40f),
                CP(80f, -20f), CP(78f, 0f), CP(75f, 20f), CP(72f, 40f),
                CP(70f, 60f), CP(68f, 80f), CP(67f, 100f), CP(68f, 120f),
                CP(70f, 140f), CP(72f, 160f), CP(73f, 180f)
            ), 0xFFF5F5F5.toInt()),
            IceCap(listOf(
                CP(-60f, -180f), CP(-65f, -150f), CP(-68f, -120f), CP(-70f, -90f),
                CP(-72f, -60f), CP(-73f, -30f), CP(-72f, 0f), CP(-70f, 30f),
                CP(-68f, 60f), CP(-66f, 90f), CP(-65f, 120f), CP(-63f, 150f),
                CP(-62f, 180f), CP(-90f, 180f), CP(-90f, -180f)
            ), 0xFFFAFAFA.toInt())
        )
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        vW = w; vH = h; cx = w / 2f; cy = h / 2f; eR = min(w, h) * 0.22f
        oceanP.shader = RadialGradient(cx - eR * 0.3f, cy - eR * 0.3f, eR * 1.3f,
            intArrayOf(0xFF1565C0.toInt(), 0xFF0D47A1.toInt(), 0xFF0A1929.toInt(), 0xFF050D1A.toInt()),
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
            starP.color = s.color; starP.alpha = (tw * 180).toInt(); starP.strokeWidth = s.sz
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
        c.drawCircle(cx, cy, r, oceanP)

        gridP.color = 0xFF4FC3F7.toInt(); gridP.alpha = 30
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

        iceCaps.forEach { ice ->
            val sp = ice.pts.map { cp ->
                val lr = Math.toRadians(cp.lat.toDouble())
                val lor = Math.toRadians(cp.lon.toDouble())
                val (sx, sy, sz) = pj((r * cos(lr) * cos(lor)).toFloat(), (r * sin(lr)).toFloat(), (r * cos(lr) * sin(lor)).toFloat())
                Triple(cx + sx, cy + sy, sz)
            }
            if (sp.any { it.third > -r * 0.2f }) {
                val path = Path()
                sp.forEachIndexed { i, (sx, sy, _) -> if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy) }
                path.close()
                iceP.color = ice.col; iceP.alpha = 200
                c.drawPath(path, iceP)
            }
        }

        landMasses.forEach { cont ->
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
                landP.color = cont.col; landP.alpha = 180
                c.drawPath(path, landP)
            }
        }

        tropicalRegions.forEach { region ->
            val sp = region.pts.map { cp ->
                val lr = Math.toRadians(cp.lat.toDouble())
                val lor = Math.toRadians(cp.lon.toDouble())
                val (sx, sy, sz) = pj((r * cos(lr) * cos(lor)).toFloat(), (r * sin(lr)).toFloat(), (r * cos(lr) * sin(lor)).toFloat())
                Triple(cx + sx, cy + sy, sz)
            }
            if (sp.any { it.third > -r * 0.2f }) {
                val path = Path()
                sp.forEachIndexed { i, (sx, sy, _) -> if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy) }
                path.close()
                tropicalP.color = region.col; tropicalP.alpha = 150
                c.drawPath(path, tropicalP)
            }
        }

        desertRegions.forEach { region ->
            val sp = region.pts.map { cp ->
                val lr = Math.toRadians(cp.lat.toDouble())
                val lor = Math.toRadians(cp.lon.toDouble())
                val (sx, sy, sz) = pj((r * cos(lr) * cos(lor)).toFloat(), (r * sin(lr)).toFloat(), (r * cos(lr) * sin(lor)).toFloat())
                Triple(cx + sx, cy + sy, sz)
            }
            if (sp.any { it.third > -r * 0.2f }) {
                val path = Path()
                sp.forEachIndexed { i, (sx, sy, _) -> if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy) }
                path.close()
                desertP.color = region.col; desertP.alpha = 160
                c.drawPath(path, desertP)
            }
        }

        drawChina(c, r)
        drawTaiwan(c, r)
        drawUserLocation(c, r)

        val eqPts = mutableListOf<Pair<Float, Float>>()
        for (lon in -180..180 step 2) {
            val lor = Math.toRadians(lon.toDouble())
            val (sx, sy, sz) = pj((r * cos(lor)).toFloat(), 0f, (r * sin(lor)).toFloat())
            if (sz > -r * 0.3f) eqPts.add(Pair(cx + sx, cy + sy))
        }
        if (eqPts.size > 1) { val p = Path(); eqPts.forEachIndexed { i, pt -> if (i == 0) p.moveTo(pt.first, pt.second) else p.lineTo(pt.first, pt.second) }; c.drawPath(p, eqP) }

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

    private fun drawChina(c: Canvas, r: Float) {
        val chinaPts = listOf(
            CP(53.3f, 123.5f),
            CP(53.0f, 126.0f),
            CP(52.5f, 128.0f),
            CP(52.0f, 130.0f),
            CP(51.5f, 131.0f),
            CP(51.0f, 132.5f),
            CP(50.0f, 134.5f),
            CP(49.5f, 135.0f),
            CP(48.5f, 135.5f),
            CP(48.0f, 134.5f),
            CP(47.5f, 134.0f),
            CP(46.5f, 134.5f),
            CP(45.5f, 133.0f),
            CP(44.5f, 131.0f),
            CP(43.5f, 130.5f),
            CP(42.5f, 130.5f),
            CP(42.0f, 129.0f),
            CP(41.5f, 128.0f),
            CP(41.0f, 127.0f),
            CP(40.5f, 125.5f),
            CP(40.0f, 124.5f),
            CP(39.5f, 123.0f),
            CP(39.0f, 121.5f),
            CP(38.5f, 121.0f),
            CP(38.0f, 120.5f),
            CP(37.5f, 122.0f),
            CP(37.0f, 122.5f),
            CP(36.5f, 122.0f),
            CP(36.0f, 121.0f),
            CP(35.5f, 120.5f),
            CP(35.0f, 120.0f),
            CP(34.0f, 120.5f),
            CP(33.0f, 121.0f),
            CP(32.0f, 121.5f),
            CP(31.0f, 122.0f),
            CP(30.0f, 122.5f),
            CP(29.0f, 122.0f),
            CP(28.0f, 121.5f),
            CP(27.0f, 121.0f),
            CP(26.0f, 120.0f),
            CP(25.5f, 119.5f),
            CP(25.0f, 119.0f),
            CP(24.0f, 118.5f),
            CP(23.5f, 117.5f),
            CP(23.0f, 116.0f),
            CP(22.5f, 115.0f),
            CP(22.0f, 114.0f),
            CP(21.5f, 111.5f),
            CP(21.5f, 110.5f),
            CP(21.0f, 110.3f),
            CP(20.5f, 110.0f),
            CP(20.2f, 109.5f),
            CP(20.0f, 109.0f),
            CP(20.0f, 108.0f),
            CP(21.0f, 108.0f),
            CP(21.5f, 107.5f),
            CP(22.0f, 107.0f),
            CP(22.5f, 106.5f),
            CP(23.0f, 106.5f),
            CP(23.5f, 105.5f),
            CP(24.0f, 105.0f),
            CP(24.5f, 104.0f),
            CP(25.0f, 103.5f),
            CP(25.5f, 103.0f),
            CP(26.0f, 104.0f),
            CP(27.0f, 104.5f),
            CP(28.0f, 104.0f),
            CP(28.5f, 103.0f),
            CP(29.0f, 102.0f),
            CP(29.5f, 101.0f),
            CP(30.0f, 100.0f),
            CP(30.5f, 99.0f),
            CP(31.0f, 98.5f),
            CP(32.0f, 98.0f),
            CP(32.5f, 97.0f),
            CP(33.0f, 96.5f),
            CP(33.5f, 96.0f),
            CP(34.0f, 95.0f),
            CP(34.5f, 94.0f),
            CP(35.0f, 92.0f),
            CP(35.5f, 90.5f),
            CP(36.0f, 89.0f),
            CP(36.5f, 87.5f),
            CP(37.0f, 86.0f),
            CP(37.5f, 84.0f),
            CP(38.0f, 81.0f),
            CP(38.5f, 79.0f),
            CP(39.0f, 77.0f),
            CP(39.5f, 75.5f),
            CP(40.0f, 74.0f),
            CP(40.5f, 73.5f),
            CP(41.0f, 73.5f),
            CP(42.0f, 74.0f),
            CP(43.0f, 75.0f),
            CP(43.5f, 76.0f),
            CP(44.0f, 77.0f),
            CP(44.5f, 78.0f),
            CP(45.0f, 79.0f),
            CP(45.5f, 80.0f),
            CP(46.0f, 81.0f),
            CP(46.5f, 82.0f),
            CP(47.0f, 83.0f),
            CP(47.5f, 84.0f),
            CP(48.0f, 85.0f),
            CP(48.5f, 86.0f),
            CP(49.0f, 87.0f),
            CP(49.5f, 88.0f),
            CP(50.0f, 89.0f),
            CP(50.5f, 90.0f),
            CP(51.0f, 92.0f),
            CP(51.5f, 94.0f),
            CP(52.0f, 96.0f),
            CP(52.0f, 98.0f),
            CP(52.5f, 100.0f),
            CP(52.5f, 103.0f),
            CP(52.5f, 105.0f),
            CP(52.5f, 107.0f),
            CP(52.5f, 109.0f),
            CP(53.0f, 111.0f),
            CP(53.0f, 113.0f),
            CP(53.0f, 115.0f),
            CP(53.0f, 117.0f),
            CP(53.0f, 119.0f),
            CP(53.3f, 121.0f),
            CP(53.3f, 123.5f)
        )
        val sp = chinaPts.map { cp ->
            val lr = Math.toRadians(cp.lat.toDouble())
            val lor = Math.toRadians(cp.lon.toDouble())
            val (sx, sy, sz) = pj((r * cos(lr) * cos(lor)).toFloat(), (r * sin(lr)).toFloat(), (r * cos(lr) * sin(lor)).toFloat())
            Triple(cx + sx, cy + sy, sz)
        }
        if (sp.any { it.third > -r * 0.2f }) {
            val path = Path()
            sp.forEachIndexed { i, (sx, sy, _) -> if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy) }
            path.close()
            chinaP.color = 0xFFD32F2F.toInt(); chinaP.alpha = 160
            c.drawPath(path, chinaP)
            c.drawPath(path, chinaStrokeP)
        }
    }

    private fun drawTaiwan(c: Canvas, r: Float) {
        val taiwanPts = listOf(
            CP(25.3f, 121.5f), CP(25.0f, 121.8f), CP(24.5f, 122.0f),
            CP(23.5f, 121.5f), CP(22.5f, 120.5f), CP(22.0f, 120.2f),
            CP(22.2f, 120.0f), CP(23.0f, 120.2f), CP(24.0f, 120.5f),
            CP(24.5f, 120.3f), CP(25.0f, 121.0f), CP(25.3f, 121.5f)
        )
        val sp = taiwanPts.map { cp ->
            val lr = Math.toRadians(cp.lat.toDouble())
            val lor = Math.toRadians(cp.lon.toDouble())
            val (sx, sy, sz) = pj((r * cos(lr) * cos(lor)).toFloat(), (r * sin(lr)).toFloat(), (r * cos(lr) * sin(lor)).toFloat())
            Triple(cx + sx, cy + sy, sz)
        }
        if (sp.any { it.third > -r * 0.2f }) {
            val path = Path()
            sp.forEachIndexed { i, (sx, sy, _) -> if (i == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy) }
            path.close()
            taiwanP.color = 0xFFD32F2F.toInt(); taiwanP.alpha = 180
            c.drawPath(path, taiwanP)
            c.drawPath(path, taiwanStrokeP)
        }
    }

    private fun drawUserLocation(c: Canvas, r: Float) {
        val loc = userLocation ?: return
        val lr = Math.toRadians(loc.lat)
        val lor = Math.toRadians(loc.lon)
        val (sx, sy, sz) = pj((r * cos(lr) * cos(lor)).toFloat(), (r * sin(lr)).toFloat(), (r * cos(lr) * sin(lor)).toFloat())
        if (sz < -r * 0.2f) return

        val px = cx + sx
        val py = cy + sy

        val pulse = (sin(System.currentTimeMillis() / 300f) * 0.3f + 0.7f)
        val pulseR = 12f * pulse * zoom

        userLocRingP.color = 0xFF4CAF50.toInt()
        userLocRingP.alpha = (200 * pulse).toInt()
        c.drawCircle(px, py, pulseR, userLocRingP)

        userLocP.color = 0xFF4CAF50.toInt()
        c.drawCircle(px, py, 6f * zoom, userLocP)

        userLocP.color = 0xFFFFFFFF.toInt()
        c.drawCircle(px, py, 3f * zoom, userLocP)

        regionLabelP.textSize = 12f * zoom
        c.drawText(loc.region, px, py - 14f * zoom, regionLabelP)

        regionSubLabelP.textSize = 10f * zoom
        c.drawText(loc.subRegion, px, py - 2f * zoom, regionSubLabelP)
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

    fun updateUserLocation(lat: Double, lon: Double, region: String, subRegion: String) {
        userLocation = UserLocation(lat, lon, region, subRegion)
        invalidate()
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
                    tRY += dx * 0.5f; tRX = (tRX + dy * 0.3f).coerceIn(-180f, 180f)
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
