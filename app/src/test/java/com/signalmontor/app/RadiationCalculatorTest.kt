package com.signalmontor.app

import org.junit.Assert.*
import org.junit.Test

class RadiationCalculatorTest {

    @Test
    fun testWifiRadiation_StrongSignal() {
        val result = RadiationCalculator.calculateWiFiRadiation(-40)
        assertNotNull(result)
        assertTrue(result.estimatedSAR >= 0)
        assertTrue(result.percentageOfLimit >= 0 && result.percentageOfLimit <= 100)
    }

    @Test
    fun testWifiRadiation_WeakSignal() {
        val result = RadiationCalculator.calculateWiFiRadiation(-85)
        assertNotNull(result)
        assertTrue(result.estimatedSAR >= 0)
        assertTrue(result.percentageOfLimit >= 0 && result.percentageOfLimit <= 100)
    }

    @Test
    fun testWifiRadiation_NoSignal() {
        val result = RadiationCalculator.calculateWiFiRadiation(-100)
        assertNotNull(result)
        assertTrue(result.estimatedSAR >= 0)
    }

    @Test
    fun testCellularRadiation_4G_Strong() {
        val result = RadiationCalculator.calculateCellularRadiation(-60, SignalType.CELLULAR_4G)
        assertNotNull(result)
        assertTrue(result.estimatedSAR >= 0)
        assertEquals(RadiationCalculator.RadiationRisk.LOW, result.riskLevel)
    }

    @Test
    fun testCellularRadiation_4G_Weak() {
        val result = RadiationCalculator.calculateCellularRadiation(-110, SignalType.CELLULAR_4G)
        assertNotNull(result)
        assertTrue(result.estimatedSAR > 0)
    }

    @Test
    fun testCellularRadiation_5G() {
        val result = RadiationCalculator.calculateCellularRadiation(-80, SignalType.CELLULAR_5G)
        assertNotNull(result)
        assertTrue(result.estimatedSAR >= 0)
    }

    @Test
    fun testCellularRadiation_2G() {
        val result = RadiationCalculator.calculateCellularRadiation(-90, SignalType.CELLULAR_2G)
        assertNotNull(result)
        assertTrue(result.estimatedSAR >= 0)
    }

    @Test
    fun testRadiationRiskLevels() {
        val low = RadiationCalculator.getRadiationRisk(0.05)
        assertEquals(RadiationCalculator.RadiationRisk.LOW, low)

        val moderate = RadiationCalculator.getRadiationRisk(0.2)
        assertEquals(RadiationCalculator.RadiationRisk.MODERATE, moderate)

        val high = RadiationCalculator.getRadiationRisk(0.6)
        assertEquals(RadiationCalculator.RadiationRisk.HIGH, high)

        val veryHigh = RadiationCalculator.getRadiationRisk(1.0)
        assertEquals(RadiationCalculator.RadiationRisk.VERY_HIGH, veryHigh)
    }

    @Test
    fun testSafetyStandards_NotEmpty() {
        val standards = RadiationCalculator.getSafetyStandards()
        assertTrue(standards.isNotEmpty())
        assertTrue(standards.any { it.limitSAR > 0 })
    }

    @Test
    fun testPercentageOfLimit_ValidRange() {
        val wifiResult = RadiationCalculator.calculateWiFiRadiation(-50)
        assertTrue(wifiResult.percentageOfLimit >= 0f)
        assertTrue(wifiResult.percentageOfLimit <= 100f)

        val cellularResult = RadiationCalculator.calculateCellularRadiation(-70, SignalType.CELLULAR_4G)
        assertTrue(cellularResult.percentageOfLimit >= 0f)
        assertTrue(cellularResult.percentageOfLimit <= 100f)
    }

    @Test
    fun testRecommendation_NotEmpty() {
        val wifiResult = RadiationCalculator.calculateWiFiRadiation(-60)
        assertTrue(wifiResult.recommendation.isNotEmpty())

        val cellularResult = RadiationCalculator.calculateCellularRadiation(-80, SignalType.CELLULAR_5G)
        assertTrue(cellularResult.recommendation.isNotEmpty())
    }
}
