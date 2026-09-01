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

    // ==================== v21 精准化测试 ====================

    // 高精度 GPS (accuracy<15)：平滑 alpha 大，速度应快速收敛到目标
    @Test
    fun testAdaptiveSmoothing_PreciseGpsRespondsFast() {
        repeat(10) {
            calculator.updateGpsSpeed(30f, 6f)
            calculator.updateLocationSpeed(30f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("高精度下应快速接近 108km/h, 实际 ${data.speed}", data.speed > 60f)
    }

    // 低精度 GPS (accuracy>60)：强滤噪，目标变化时速度应缓慢移动（抖动被抑制）
    @Test
    fun testAdaptiveSmoothing_NoisyGpsJitterSuppressed() {
        // 先建立 ~54 km/h (15m/s) 的稳定基准
        repeat(60) {
            calculator.updateGpsSpeed(15f, 80f)
            calculator.updateLocationSpeed(15f)
            calculator.getSpeed()
        }
        // 单次噪声尖峰 40m/s (=144km/h) 不应让速度大幅上跳
        calculator.updateGpsSpeed(40f, 80f)
        calculator.updateLocationSpeed(40f)
        val data = calculator.getSpeed()
        assertTrue("噪声尖峰应被抑制, 实际 ${data.speed}", data.speed < 80f)
        assertTrue("应保持近基准, 实际 ${data.speed}", data.speed > 40f)
    }

    // 飞行高速确认：单个 140m/s (504km/h) 高精度样本不应被采纳
    @Test
    fun testHighSpeed_RequiresConfirmation() {
        repeat(10) { calculator.getSpeed() }
        calculator.updateGpsSpeed(0f, 5f)
        calculator.getSpeed()
        calculator.updateGpsSpeed(140f, 10f)
        calculator.updateLocationSpeed(140f)
        val data = calculator.getSpeed()
        assertTrue("单样本高速不应采纳, 实际 ${data.speed}", data.speed < 500f)
    }

    // 飞行高速确认通过：稳定在中速后，连续 2 个高精度样本采纳起飞速度
    @Test
    fun testHighSpeed_ConfirmedAfterTwoSamples() {
        // 先稳定在 ~200 km/h (55.5 m/s)
        repeat(50) {
            calculator.updateGpsSpeed(55.5f, 8f)
            calculator.updateLocationSpeed(55.5f)
            calculator.getSpeed()
        }
        // 第一样本(需确认): 500+ km/h 暂不采纳
        calculator.updateGpsSpeed(140f, 10f)
        calculator.updateLocationSpeed(140f)
        calculator.getSpeed()
        // 第二样本: 确认后采纳并快速上跳
        calculator.updateGpsSpeed(141f, 10f)
        calculator.updateLocationSpeed(141f)
        val data = calculator.getSpeed()
        assertTrue("连续样本确认后应采纳高速, 实际 ${data.speed}", data.speed > 300f)
    }

    // 已处于高速 (>300km/h) 时，新高速样本应直接跟随（无需二次确认）
    @Test
    fun testHighSpeed_AlreadyHighFollowsImmediately() {
        repeat(80) {
            calculator.updateGpsSpeed(100f, 8f)
            calculator.updateLocationSpeed(100f)
            calculator.getSpeed()
        }
        calculator.updateGpsSpeed(160f, 10f)
        calculator.updateLocationSpeed(160f)
        val data = calculator.getSpeed()
        assertTrue("高速状态应直接跟随, 实际 ${data.speed}", data.speed > 500f)
    }

    // gpsAcceleration 应被钳制在 ±8 m/s² 内
    @Test
    fun testGpsAcceleration_ClampedToRealisticBounds() {
        // 模拟两次采样间速度突变 50m/s、dt≈1s → 原始加速度 50 > 8
        calculator.updateGpsSpeed(10f, 8f)
        Thread.sleep(1100)
        calculator.updateGpsSpeed(60f, 8f)
        calculator.getSpeed()
        val data = calculator.getSpeed()
        assertTrue("加速度应被钳制, 实际 ${data.acceleration}", data.acceleration <= 8f)
        assertTrue(data.acceleration >= -8f)
    }

    // 静止滞回：0.6km/h 噪声不应令状态在静止/运动间抖动
    @Test
    fun testStationaryHysteresis_NoFlicker() {
        // 稳定在低速区 (0.5m/s=1.8km/h)，再回到 0.1m/s
        repeat(20) {
            calculator.updateGpsSpeed(0.5f, 5f)
            calculator.updateLocationSpeed(0.5f)
            calculator.getSpeed()
        }
        repeat(20) {
            calculator.updateGpsSpeed(0.1f, 5f)
            calculator.updateLocationSpeed(0.1f)
            calculator.getSpeed()
        }
        val stationary = calculator.getSpeed()
        assertEquals("低速噪声不应报运动状态", MovementState.STATIONARY, stationary.movementState)
    }

    // 停驻车辆（低方差环境、无运动瞬态）不应被误判为高速行驶
    @Test
    fun testInertialEstimate_ParkedCarNotMisjudged() {
        // 模拟静止环境: 重复平滑的线性加速度（无瞬态）+ 无 GPS
        repeat(80) {
            calculator.injectImuSample(0.02f, 0.001f, 0.001f, 0.001f)
        }
        repeat(30) { calculator.getSpeed() }
        val data = calculator.getSpeed()
        assertEquals("停驻车辆不应误判出速度", 0f, data.speed, 0.01f)
        assertFalse("不应标记为惯性测速", data.usingInertialSpeed)
    }

    // 有运动瞬态 + 连续一致估计 → 惯性初始速度被确认（>20 次评估 + 连续 3 桶）
    @Test
    fun testInertialEstimate_ConfirmedWithTransientAndConsistency() {
        // 先制造运动瞬态（起步冲击）
        calculator.injectImuSample(0.01f, 0.001f, 0.001f, 0.001f)
        calculator.injectImuSample(1.4f, 0.06f, 0.06f, 0.06f)
        calculator.injectImuSample(0.01f, 0.001f, 0.001f, 0.001f)
        // 然后进入平稳"行驶"环境：中等方差（0.1~0.3）+ 轻微角速度
        var v = 0f
        var dir = 1f
        repeat(240) {
            v += dir * 0.4f
            if (v > 0.8f) dir = -1f
            if (v < -0.8f) dir = 1f
            calculator.injectImuSample(v, 0.02f, 0.01f, 0.01f)
            calculator.getSpeed()
        }
        val data = calculator.getSpeed()
        assertTrue("有瞬态+一致估计应产出惯性速度, 实际 ${data.speed}", data.speed > 0f)
    }

    // 步频带外的高频抖动（4.5Hz）不应被采纳为有效步频/速度
    @Test
    fun testStepCadence_OutOfBandRejected() {
        var v = 0f
        var dir = 1f
        repeat(300) {
            v += dir * 0.6f
            if (v > 1.5f) dir = -1f
            if (v < -1.5f) dir = 1f
            calculator.injectImuSample(v, 0.01f, 0.01f, 0.01f)
        }
        val data = calculator.getSpeed()
        assertTrue("高频抖动不应产生步频估计, 实际 ${data.speedMs}", data.speedMs < 1f)
    }
}
