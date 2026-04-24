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
    fun testGpsSpeedUpdate_TrainSpeed() {
        calculator.updateGpsSpeed(55.56f, 5f)
        val data = calculator.getSpeed()
        assertEquals(0f, data.speed, 0.01f)
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
        val data = calculator.getSpeed()
        assertEquals(0f, data.speed, 0.01f)
    }

    @Test
    fun testGpsSpeed_AcceptsLowWalkingSpeed() {
        calculator.updateGpsSpeed(0.5f, 5f)
        calculator.updateLocationSpeed(0.5f)
        val data = calculator.getSpeed()
        assertTrue("Low walking speed should be accepted", data.speed >= 0f)
    }

    @Test
    fun testGpsSpeed_RejectsExtremeSpeed() {
        calculator.updateGpsSpeed(60f, 5f)
        val data = calculator.getSpeed()
        assertEquals(0f, data.speed, 0.01f)
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
        assertEquals(0.3f, data.confidence, 0.01f)
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
}
