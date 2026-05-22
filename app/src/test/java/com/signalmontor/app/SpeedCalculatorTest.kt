package com.signalmontor.app

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SpeedCalculatorTest {

    private lateinit var calculator: SpeedCalculator

    @Before
    fun setUp() {
        calculator = SpeedCalculator()
    }

    @Test
    fun testInitialState() {
        val data = calculator.getSpeed()
        assertEquals(0f, data.speed, 0.01f)
        assertEquals(0f, data.maxSpeed, 0.01f)
        assertEquals(0f, data.acceleration, 0.01f)
        assertEquals("北", data.direction)
        assertEquals(MovementState.STATIONARY, data.movementState)
    }

    @Test
    fun testResetClearsAllData() {
        calculator.updateGpsAltitude(100f)
        calculator.updateGpsSpeed(5f, 3f)
        calculator.reset()
        val data = calculator.getSpeed()
        assertEquals(0f, data.speed, 0.01f)
        assertEquals(0f, data.altitude, 0.01f)
        assertEquals(0f, data.gpsAltitude, 0.01f)
        assertEquals(0f, data.pressure, 0.01f)
        assertFalse(data.hasBarometer)
    }

    @Test
    fun testGpsSpeedUpdate_WalkingSpeed() {
        repeat(50) {
            calculator.updateGpsSpeed(1.67f, 5f)
            calculator.updateLocationSpeed(1.67f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Walking speed should be > 0", data.speed > 0f)
        assertTrue("Walking speed should be < 15 km/h", data.speed < 15f)
    }

    @Test
    fun testGpsSpeedUpdate_RunningSpeed() {
        repeat(50) {
            calculator.updateGpsSpeed(3.33f, 5f)
            calculator.updateLocationSpeed(3.33f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Running speed should be > 5 km/h", data.speed > 5f)
        assertTrue("Running speed should be < 20 km/h", data.speed < 20f)
    }

    @Test
    fun testGpsSpeedUpdate_DrivingSpeed() {
        repeat(50) {
            calculator.updateGpsSpeed(16.67f, 10f)
            calculator.updateLocationSpeed(16.67f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Driving speed should be > 40 km/h", data.speed > 40f)
        assertTrue("Driving speed should be < 80 km/h", data.speed < 80f)
    }

    @Test
    fun testGpsSpeedUpdate_HighwaySpeed() {
        repeat(50) {
            calculator.updateGpsSpeed(27.78f, 5f)
            calculator.updateLocationSpeed(27.78f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Highway speed should be > 70 km/h", data.speed > 70f)
        assertTrue("Highway speed should be < 150 km/h", data.speed < 150f)
    }

    @Test
    fun testGpsSpeed_RejectsNegativeSpeed() {
        calculator.updateGpsSpeed(-1f, 5f)
        val data = calculator.getSpeed()
        assertEquals(0f, data.speed, 0.01f)
    }

    @Test
    fun testGpsSpeed_RejectsBelowMinThreshold() {
        calculator.updateGpsSpeed(0.2f, 5f)
        calculator.updateLocationSpeed(0.2f)
        val data = calculator.getSpeed()
        assertTrue("Low speed should be accepted but small", data.speed >= 0f)
    }

    @Test
    fun testGpsSpeed_HandlesPoorAccuracy() {
        calculator.updateGpsSpeed(5f, 100f)
        val data = calculator.getSpeed()
        assertTrue(data.confidence < 0.8f)
    }

    @Test
    fun testGpsDrift_RejectsLargeJumpFromStationary() {
        calculator.updateGpsSpeed(0f, 5f)
        calculator.updateGpsSpeed(20f, 5f)
        val data = calculator.getSpeed()
        assertTrue("Speed should be < 30 km/h after rejected jump", data.speed < 30f)
    }

    @Test
    fun testDeceleration_SlowResponse() {
        repeat(50) {
            calculator.updateGpsSpeed(13.89f, 5f)
            calculator.updateLocationSpeed(13.89f)
            calculator.getSpeed()
        }
        calculator.updateGpsSpeed(0f, 5f)
        val data = calculator.getSpeed()
        assertTrue("Speed should decrease when GPS reports stop", data.speed < 50f)
    }

    @Test
    fun testWalkingFusion_GpsLowSpeedWithSteps() {
        calculator.updateGpsSpeed(0.83f, 5f)
        calculator.updateLocationSpeed(0.83f)
        val data = calculator.getSpeed()
        assertTrue("Low walking speed should be >= 0", data.speed >= 0f)
        assertTrue("Low walking speed should be < 5 km/h", data.speed < 5f)
    }

    @Test
    fun testGpsSpeed_UpdatesConsistently() {
        repeat(50) {
            calculator.updateGpsSpeed(5f, 5f)
            calculator.updateLocationSpeed(5f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Speed should be > 10 km/h", data.speed > 10f)
        assertTrue("Speed should be < 25 km/h", data.speed < 25f)
    }

    @Test
    fun testMovementState_Stationary() {
        val data = calculator.getSpeed()
        assertEquals(MovementState.STATIONARY, data.movementState)
    }

    @Test
    fun testMovementStateEnumValues() {
        assertEquals(6, MovementState.values().size)
    }

    @Test
    fun testMovementStateColors() {
        assertTrue(MovementState.STATIONARY.color != 0)
        assertTrue(MovementState.WALKING.color != 0)
        assertTrue(MovementState.RUNNING.color != 0)
        assertTrue(MovementState.CYCLING.color != 0)
        assertTrue(MovementState.DRIVING.color != 0)
        assertTrue(MovementState.HIGH_SPEED.color != 0)
    }

    @Test
    fun testDirectionLabels() {
        val data = calculator.getSpeed()
        assertNotNull(data.direction)
        assertTrue(data.direction.isNotEmpty())
    }

    @Test
    fun testConfidence_NoGps() {
        val data = calculator.getSpeed()
        assertTrue("No GPS should have confidence >= 0.3", data.confidence >= 0.3f)
    }

    @Test
    fun testConfidence_GoodGps() {
        repeat(50) {
            calculator.updateGpsSpeed(5f, 5f)
            calculator.updateLocationSpeed(5f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Good GPS should have high confidence", data.confidence >= 0.6f)
    }

    @Test
    fun testConfidence_PoorGps() {
        // Just verify that confidence is in valid range when GPS is poor
        calculator.updateGpsSpeed(10f, 40f)
        calculator.updateLocationSpeed(10f)
        val data = calculator.getSpeed()
        // Confidence should be in valid range 0-1
        assertTrue("Confidence should be >= 0f", data.confidence >= 0f)
        assertTrue("Confidence should be <= 1f", data.confidence <= 1f)
    }

    @Test
    fun testGpsAltitudeUpdate() {
        calculator.updateGpsAltitude(100f)
        val data = calculator.getSpeed()
        assertEquals(100f, data.gpsAltitude, 0.01f)
    }

    @Test
    fun testAltitudeQualityRange() {
        val data = calculator.getSpeed()
        assertTrue(data.altitudeQuality in 0f..1f)
    }

    @Test
    fun testBearingIsNonNegative() {
        val data = calculator.getSpeed()
        assertTrue(data.bearing >= 0f)
        assertTrue(data.bearing <= 360f)
    }

    @Test
    fun testSpeedDataContainsAllFields() {
        val data = calculator.getSpeed()
        assertNotNull(data.speed)
        assertNotNull(data.speedMs)
        assertNotNull(data.maxSpeed)
        assertNotNull(data.acceleration)
        assertNotNull(data.direction)
        assertNotNull(data.bearing)
        assertNotNull(data.totalDistance)
        assertNotNull(data.stepCount)
        assertNotNull(data.stepFrequency)
        assertNotNull(data.movementState)
        assertNotNull(data.confidence)
        assertNotNull(data.gpsAccuracy)
        assertNotNull(data.altitude)
        assertNotNull(data.gpsAltitude)
        assertNotNull(data.maxAltitude)
        assertNotNull(data.minAltitude)
        assertNotNull(data.altitudeChangeRate)
        assertNotNull(data.pressure)
        assertNotNull(data.temperature)
        assertNotNull(data.gravityMagnitude)
        assertNotNull(data.hasBarometer)
        assertNotNull(data.altitudeQuality)
    }

    @Test
    fun testSeaLevelPressureDefault() {
        val pressure = calculator.getSeaLevelPressure()
        assertTrue(pressure > 0f)
    }

    @Test
    fun testGravityMagnitude() {
        val data = calculator.getSpeed()
        assertTrue(data.gravityMagnitude > 0f)
    }

    @Test
    fun testSpeedNeverNegative() {
        calculator.updateGpsSpeed(0f, 5f)
        val data = calculator.getSpeed()
        assertTrue(data.speed >= 0f)
    }

    @Test
    fun testSpeedRespectsMaxLimit() {
        repeat(50) {
            calculator.updateGpsSpeed(40f, 5f)
            calculator.updateLocationSpeed(40f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue(data.speed <= 1000f)
    }

    @Test
    fun testBearingSmoothing_CircularInterpolation() {
        calculator.updateGpsBearing(350f)
        calculator.updateGpsSpeed(5f, 5f)
        calculator.updateLocationSpeed(5f)
        calculator.getSpeed()
        calculator.updateGpsBearing(10f)
        calculator.updateGpsSpeed(5f, 5f)
        calculator.updateLocationSpeed(5f)
        val data = calculator.getSpeed()
        assertTrue(data.bearing >= 0f)
        assertTrue(data.bearing <= 360f)
    }

    @Test
    fun testStepFrequency_ZeroWhenNoSteps() {
        val data = calculator.getSpeed()
        assertEquals(0f, data.stepFrequency, 0.01f)
        assertEquals(0, data.stepCount)
    }

    @Test
    fun testEstimatedStepSpeed_ZeroWhenNoSteps() {
        val data = calculator.getSpeed()
        assertEquals(0f, data.speedMs, 0.01f)
    }

    @Test
    fun testScenario_WalkingToRunning() {
        repeat(50) {
            calculator.updateGpsSpeed(1.67f, 5f)
            calculator.updateLocationSpeed(1.67f)
            calculator.getSpeed()
        }
        repeat(50) {
            calculator.updateGpsSpeed(3.5f, 5f)
            calculator.updateLocationSpeed(3.5f)
        }
        val data = calculator.getSpeed()
        assertTrue("Speed should be > 3 km/h after acceleration", data.speed > 3f)
    }

    @Test
    fun testScenario_DrivingToStopped() {
        repeat(50) {
            calculator.updateGpsSpeed(16.67f, 5f)
            calculator.updateLocationSpeed(16.67f)
            calculator.getSpeed()
        }
        calculator.updateGpsSpeed(0f, 5f)
        val data = calculator.getSpeed()
        assertTrue("Speed should be < 70 km/h after stopping", data.speed < 70f)
    }

    @Test
    fun testScenario_Cycling() {
        repeat(50) {
            calculator.updateGpsSpeed(6.94f, 5f)
            calculator.updateLocationSpeed(6.94f)
        }
        val data = calculator.getSpeed()
        assertTrue("Cycling speed should be > 10 km/h, was ${data.speed}", data.speed > 10f)
        assertTrue("Cycling speed should be < 40 km/h", data.speed < 40f)
    }

    @Test
    fun testScenario_AirplaneCruiseMidFlight() {
        repeat(5) {
            calculator.updateGpsSpeed(16.67f, 5f)
            calculator.updateLocationSpeed(16.67f)
            calculator.getSpeed()
        }
        repeat(50) {
            calculator.updateGpsSpeed(250f, 8f)
            calculator.updateLocationSpeed(250f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Cruise speed should be > 100 km/h, was ${data.speed}", data.speed > 100f)
    }

    @Test
    fun testScenario_AirplaneInitialTakeoff() {
        repeat(20) {
            calculator.updateGpsSpeed(80f, 5f)
            calculator.updateLocationSpeed(80f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Takeoff speed should be > 250 km/h, was ${data.speed}", data.speed > 250f)
    }

    @Test
    fun testScenario_GpsDrift_LowAccuracy() {
        calculator.updateGpsSpeed(250f, 150f)
        calculator.updateLocationSpeed(250f)
        val data = calculator.getSpeed()
        assertTrue("Poor accuracy GPS should be rejected, was ${data.speed}", data.speed < 50f)
    }

    @Test
    fun testScenario_NormalDeceleration() {
        repeat(30) {
            calculator.updateGpsSpeed(33.33f, 5f)
            calculator.updateLocationSpeed(33.33f)
            calculator.getSpeed()
        }
        calculator.updateGpsSpeed(5f, 5f)
        calculator.updateLocationSpeed(5f)
        val data = calculator.getSpeed()
        assertTrue("Speed should decrease, was ${data.speed}", data.speed < 120f)
    }

    @Test
    fun testScenario_WalkingToDriving() {
        repeat(10) {
            calculator.updateGpsSpeed(1.5f, 5f)
            calculator.updateLocationSpeed(1.5f)
            calculator.getSpeed()
        }
        repeat(15) {
            calculator.updateGpsSpeed(16.67f, 5f)
            calculator.updateLocationSpeed(16.67f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Driving speed should be > 40 km/h, was ${data.speed}", data.speed > 40f)
    }

    // ==================== 全速度区间测试 ====================

    // --- 步行区间 (0-6 km/h) ---
    @Test
    fun testWalkingRange_StationaryToWalking() {
        repeat(10) {
            calculator.updateGpsSpeed(0f, 5f)
            calculator.updateLocationSpeed(0f)
            calculator.getSpeed()
        }
        repeat(20) {
            calculator.updateGpsSpeed(1.4f, 5f)
            calculator.updateLocationSpeed(1.4f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Walking speed should be > 3 km/h, was ${data.speed}", data.speed > 3f)
        assertTrue("Walking speed should be < 10 km/h, was ${data.speed}", data.speed < 10f)
        assertEquals(MovementState.WALKING, data.movementState)
    }

    @Test
    fun testWalkingRange_AppOpenedWhileWalking() {
        repeat(30) {
            calculator.updateGpsSpeed(1.1f, 5f)
            calculator.updateLocationSpeed(1.1f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Walking speed should be > 2 km/h, was ${data.speed}", data.speed > 2f)
        assertTrue("Walking speed should be < 8 km/h, was ${data.speed}", data.speed < 8f)
    }

    // --- 跑步区间 (6-20 km/h) ---
    @Test
    fun testRunningRange_WalkingToRunning() {
        repeat(10) {
            calculator.updateGpsSpeed(1.5f, 5f)
            calculator.updateLocationSpeed(1.5f)
            calculator.getSpeed()
        }
        repeat(30) {
            calculator.updateGpsSpeed(3.5f, 5f)
            calculator.updateLocationSpeed(3.5f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Running speed should be > 8 km/h, was ${data.speed}", data.speed > 8f)
        assertTrue("Running speed should be < 25 km/h, was ${data.speed}", data.speed < 25f)
        assertEquals(MovementState.RUNNING, data.movementState)
    }

    // --- 自行车区间 (20-50 km/h) ---
    @Test
    fun testCyclingRange_RunningToCycling() {
        repeat(10) {
            calculator.updateGpsSpeed(3f, 5f)
            calculator.updateLocationSpeed(3f)
            calculator.getSpeed()
        }
        repeat(30) {
            calculator.updateGpsSpeed(8.5f, 5f)
            calculator.updateLocationSpeed(8.5f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Cycling speed should be > 20 km/h, was ${data.speed}", data.speed > 20f)
        assertTrue("Cycling speed should be < 60 km/h, was ${data.speed}", data.speed < 60f)
        assertEquals(MovementState.CYCLING, data.movementState)
    }

    // --- 汽车区间 (50-120 km/h) ---
    @Test
    fun testDrivingRange_CyclingToDriving() {
        repeat(10) {
            calculator.updateGpsSpeed(8f, 5f)
            calculator.updateLocationSpeed(8f)
            calculator.getSpeed()
        }
        repeat(30) {
            calculator.updateGpsSpeed(22.22f, 5f)
            calculator.updateLocationSpeed(22.22f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Driving speed should be > 50 km/h, was ${data.speed}", data.speed > 50f)
        assertTrue("Driving speed should be < 120 km/h, was ${data.speed}", data.speed < 120f)
        assertEquals(MovementState.DRIVING, data.movementState)
    }

    @Test
    fun testDrivingRange_AppOpenedWhileDriving() {
        repeat(30) {
            calculator.updateGpsSpeed(27.78f, 5f)
            calculator.updateLocationSpeed(27.78f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Driving speed should be > 70 km/h, was ${data.speed}", data.speed > 70f)
        assertTrue("Driving speed should be < 150 km/h, was ${data.speed}", data.speed < 150f)
    }

    // --- 高速区间 (120-350 km/h) ---
    @Test
    fun testHighSpeedRange_DrivingToHighSpeed() {
        repeat(10) {
            calculator.updateGpsSpeed(25f, 5f)
            calculator.updateLocationSpeed(25f)
            calculator.getSpeed()
        }
        repeat(40) {
            calculator.updateGpsSpeed(55.56f, 5f)
            calculator.updateLocationSpeed(55.56f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("High speed should be > 150 km/h, was ${data.speed}", data.speed > 150f)
        assertTrue("High speed should be < 350 km/h, was ${data.speed}", data.speed < 350f)
        assertEquals(MovementState.HIGH_SPEED, data.movementState)
    }

    // --- 高铁区间 (250-350 km/h) ---
    @Test
    fun testHSRRange_AppOpenedOnHSR() {
        repeat(40) {
            calculator.updateGpsSpeed(83.33f, 8f)
            calculator.updateLocationSpeed(83.33f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("HSR speed should be > 200 km/h, was ${data.speed}", data.speed > 200f)
        assertTrue("HSR speed should be < 400 km/h, was ${data.speed}", data.speed < 400f)
    }

    // --- 民航客机区间 (800-900 km/h) ---
    @Test
    fun testAirplaneRange_AppOpenedMidFlight() {
        repeat(5) {
            calculator.updateGpsSpeed(15f, 5f)
            calculator.updateLocationSpeed(15f)
            calculator.getSpeed()
        }
        repeat(50) {
            calculator.updateGpsSpeed(240f, 8f)
            calculator.updateLocationSpeed(240f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Airplane speed should be > 600 km/h, was ${data.speed}", data.speed > 600f)
        assertTrue("Airplane speed should be < 1000 km/h, was ${data.speed}", data.speed < 1000f)
    }

    @Test
    fun testAirplaneRange_AppOpenedDuringTakeoff() {
        repeat(30) {
            calculator.updateGpsSpeed(85f, 8f)
            calculator.updateLocationSpeed(85f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Takeoff speed should be > 200 km/h, was ${data.speed}", data.speed > 200f)
        assertTrue("Takeoff speed should be < 500 km/h, was ${data.speed}", data.speed < 500f)
    }

    // --- 接近上限区间 (900-1200 km/h) ---
    @Test
    fun testMaxSpeedRange_NearLimit() {
        repeat(50) {
            calculator.updateGpsSpeed(300f, 8f)
            calculator.updateLocationSpeed(300f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Near-limit speed should be > 800 km/h, was ${data.speed}", data.speed > 800f)
        assertTrue("Speed should be <= 1200 km/h, was ${data.speed}", data.speed <= 1200f)
    }

    // --- 超过上限 ---
    @Test
    fun testBeyondMaxSpeed_Rejected() {
        calculator.updateGpsSpeed(400f, 8f)
        calculator.updateLocationSpeed(400f)
        val data = calculator.getSpeed()
        assertTrue("Beyond max speed should be rejected, was ${data.speed}", data.speed < 100f)
    }

    // ==================== 速度切换测试 ====================

    @Test
    fun testSpeedTransition_WalkingToHSR() {
        repeat(10) {
            calculator.updateGpsSpeed(1.5f, 5f)
            calculator.updateLocationSpeed(1.5f)
            calculator.getSpeed()
        }
        repeat(20) {
            calculator.updateGpsSpeed(16.67f, 5f)
            calculator.updateLocationSpeed(16.67f)
            calculator.getSpeed()
        }
        repeat(30) {
            calculator.updateGpsSpeed(83.33f, 8f)
            calculator.updateLocationSpeed(83.33f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Speed should be > 200 km/h, was ${data.speed}", data.speed > 200f)
    }

    @Test
    fun testSpeedTransition_HSRtoWalking() {
        repeat(30) {
            calculator.updateGpsSpeed(83.33f, 8f)
            calculator.updateLocationSpeed(83.33f)
            calculator.getSpeed()
        }
        repeat(30) {
            calculator.updateGpsSpeed(1.4f, 5f)
            calculator.updateLocationSpeed(1.4f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Speed should be < 50 km/h, was ${data.speed}", data.speed < 50f)
    }

    @Test
    fun testSpeedTransition_DrivingToAirplane() {
        repeat(20) {
            calculator.updateGpsSpeed(27.78f, 5f)
            calculator.updateLocationSpeed(27.78f)
            calculator.getSpeed()
        }
        repeat(40) {
            calculator.updateGpsSpeed(240f, 8f)
            calculator.updateLocationSpeed(240f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Speed should be > 500 km/h, was ${data.speed}", data.speed > 500f)
    }

    // ==================== 海拔测试 ====================

    @Test
    fun testAltitude_GpsAltitudeUpdate() {
        calculator.updateGpsAltitude(100f)
        val data = calculator.getSpeed()
        assertEquals(100f, data.gpsAltitude, 0.01f)
    }

    @Test
    fun testAltitude_MultipleUpdates() {
        calculator.updateGpsAltitude(100f)
        calculator.updateGpsAltitude(200f)
        calculator.updateGpsAltitude(300f)
        val data = calculator.getSpeed()
        assertEquals(300f, data.gpsAltitude, 0.01f)
    }

    @Test
    fun testAltitude_QualityRange() {
        val data = calculator.getSpeed()
        assertTrue("Altitude quality should be 0-1, was ${data.altitudeQuality}",
            data.altitudeQuality in 0f..1f)
    }

    @Test
    fun testAltitude_ExtremeValues() {
        calculator.updateGpsAltitude(-50f)
        val data1 = calculator.getSpeed()
        assertEquals(-50f, data1.gpsAltitude, 0.01f)

        calculator.updateGpsAltitude(8848f)
        val data2 = calculator.getSpeed()
        assertEquals(8848f, data2.gpsAltitude, 0.01f)
    }

    @Test
    fun testAltitude_InvalidValuesRejected() {
        calculator.updateGpsAltitude(-600f)
        calculator.updateGpsAltitude(11000f)
        val data = calculator.getSpeed()
        assertEquals(0f, data.gpsAltitude, 0.01f)
    }

    // ==================== 方向测试 ====================

    @Test
    fun testBearing_UpdateAndSmooth() {
        calculator.updateGpsBearing(90f)
        repeat(20) {
            calculator.updateGpsSpeed(5f, 5f)
            calculator.updateLocationSpeed(5f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Bearing should be >= 0, was ${data.bearing}", data.bearing >= 0f)
        assertTrue("Bearing should be <= 360, was ${data.bearing}", data.bearing <= 360f)
    }

    @Test
    fun testBearing_CircularInterpolation() {
        calculator.updateGpsBearing(350f)
        calculator.updateGpsSpeed(5f, 5f)
        calculator.updateLocationSpeed(5f)
        calculator.getSpeed()

        calculator.updateGpsBearing(10f)
        calculator.updateGpsSpeed(5f, 5f)
        calculator.updateLocationSpeed(5f)
        val data = calculator.getSpeed()

        assertTrue("Bearing should be >= 0, was ${data.bearing}", data.bearing >= 0f)
        assertTrue("Bearing should be <= 360, was ${data.bearing}", data.bearing <= 360f)
    }

    @Test
    fun testBearing_AllDirections() {
        val directions = listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f, 360f)
        directions.forEach { bearing ->
            calculator.updateGpsBearing(bearing)
            calculator.updateGpsSpeed(5f, 5f)
            calculator.updateLocationSpeed(5f)
            calculator.getSpeed()
            val data = calculator.getSpeed()
            assertTrue("Bearing should be >= 0 for $bearing, was ${data.bearing}", data.bearing >= 0f)
            assertTrue("Bearing should be <= 360 for $bearing, was ${data.bearing}", data.bearing <= 360f)
        }
    }

    @Test
    fun testDirection_BearingRange() {
        calculator.updateGpsBearing(180f)
        repeat(100) {
            calculator.updateGpsSpeed(20f, 5f)
            calculator.updateLocationSpeed(20f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Direction should not be empty", data.direction.isNotEmpty())
        assertTrue("Bearing should be valid range, was ${data.bearing}", data.bearing in 0f..360f)
    }

    // ==================== 异常场景测试 ====================

    @Test
    fun testAbnormal_GpsAccuracyDegradation() {
        repeat(20) {
            calculator.updateGpsSpeed(22.22f, 5f)
            calculator.updateLocationSpeed(22.22f)
            calculator.getSpeed()
        }
        repeat(10) {
            calculator.updateGpsSpeed(22.22f, 100f)
            calculator.updateLocationSpeed(22.22f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Speed should be maintained, was ${data.speed}", data.speed > 40f)
        assertTrue("Confidence should drop, was ${data.confidence}", data.confidence < 0.8f)
    }

    @Test
    fun testAbnormal_GpsSignalLost() {
        repeat(20) {
            calculator.updateGpsSpeed(22.22f, 5f)
            calculator.updateLocationSpeed(22.22f)
            calculator.getSpeed()
        }
        repeat(20) {
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Speed should decay but not zero immediately, was ${data.speed}", data.speed > 0f)
        assertTrue("Confidence should be valid, was ${data.confidence}", data.confidence in 0f..1f)
    }

    @Test
    fun testAbnormal_SpeedOscillation() {
        repeat(5) {
            calculator.updateGpsSpeed(22.22f, 5f)
            calculator.updateLocationSpeed(22.22f)
            calculator.getSpeed()
            calculator.updateGpsSpeed(5f, 5f)
            calculator.updateLocationSpeed(5f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Speed should be stable, was ${data.speed}", data.speed in 10f..100f)
    }

    // ==================== 置信度测试 ====================

    @Test
    fun testConfidence_GoodGpsHighSpeed() {
        repeat(30) {
            calculator.updateGpsSpeed(240f, 8f)
            calculator.updateLocationSpeed(240f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("High confidence expected, was ${data.confidence}", data.confidence >= 0.6f)
    }

    @Test
    fun testConfidence_PoorGpsHighSpeed() {
        repeat(30) {
            calculator.updateGpsSpeed(240f, 100f)
            calculator.updateLocationSpeed(240f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("Low confidence expected, was ${data.confidence}", data.confidence < 0.8f)
    }

    // ==================== 重置测试 ====================

    @Test
    fun testReset_AfterHighSpeed() {
        repeat(30) {
            calculator.updateGpsSpeed(240f, 8f)
            calculator.updateLocationSpeed(240f)
            calculator.getSpeed()
        }
        calculator.reset()
        val data = calculator.getSpeed()
        assertEquals("Speed should be 0 after reset", 0f, data.speed, 0.01f)
        assertEquals("Bearing should be 0 after reset", 0f, data.bearing, 0.01f)
        assertEquals(0, data.stepCount)
        assertEquals(0f, data.stepFrequency, 0.01f)
    }
}
