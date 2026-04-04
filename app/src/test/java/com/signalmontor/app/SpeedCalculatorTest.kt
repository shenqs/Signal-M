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
    fun testGpsAltitudeUpdate() {
        calculator.updateGpsAltitude(100f)
        val data = calculator.getSpeed()
        assertEquals(100f, data.gpsAltitude, 0.01f)
    }

    @Test
    fun testGpsSpeedUpdate() {
        calculator.updateGpsSpeed(5f, 3f)
        calculator.updateLocationSpeed(5f)
        val data = calculator.getSpeed()
        assertTrue(data.speed >= 0f)
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
    fun testMovementStateEnumValues() {
        assertEquals(6, MovementState.values().size)
        assertEquals(MovementState.STATIONARY, MovementState.valueOf("STATIONARY"))
        assertEquals(MovementState.WALKING, MovementState.valueOf("WALKING"))
        assertEquals(MovementState.RUNNING, MovementState.valueOf("RUNNING"))
        assertEquals(MovementState.CYCLING, MovementState.valueOf("CYCLING"))
        assertEquals(MovementState.DRIVING, MovementState.valueOf("DRIVING"))
        assertEquals(MovementState.HIGH_SPEED, MovementState.valueOf("HIGH_SPEED"))
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
    fun testConfidenceRange() {
        val data = calculator.getSpeed()
        assertTrue(data.confidence in 0f..1f)
    }

    @Test
    fun testAltitudeQualityRange() {
        val data = calculator.getSpeed()
        assertTrue(data.altitudeQuality in 0f..1f)
    }

    @Test
    fun testGravityMagnitude() {
        val data = calculator.getSpeed()
        assertTrue(data.gravityMagnitude > 0f)
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
    fun testBearingIsNonNegative() {
        val data = calculator.getSpeed()
        assertTrue(data.bearing >= 0f)
        assertTrue(data.bearing <= 360f)
    }
}
