package com.signalmontor.app

import org.junit.Assert.*
import org.junit.Test

class RadiationStandardTest {

    @Test
    fun testStandards_NotEmpty() {
        assertTrue(RadiationStandard.standards.isNotEmpty())
    }

    @Test
    fun testStandards_HaveValidLimits() {
        RadiationStandard.standards.forEach { standard ->
            assertTrue(standard.source.isNotEmpty())
            assertTrue(standard.limit.isNotEmpty())
            assertTrue(standard.frequency.isNotEmpty())
            assertTrue(standard.description.isNotEmpty())
        }
    }

    @Test
    fun testStandards_ContainsMajorOrganizations() {
        val sources = RadiationStandard.standards.map { it.source }
        assertTrue(sources.any { it.contains("ICNIRP") })
        assertTrue(sources.any { it.contains("FCC") })
        assertTrue(sources.any { it.contains("中国") || it.contains("GB") })
    }

    @Test
    fun testDeviceLevels_NotEmpty() {
        assertTrue(RadiationStandard.deviceLevels.isNotEmpty())
    }

    @Test
    fun testDeviceLevels_ValidData() {
        RadiationStandard.deviceLevels.forEach { device ->
            assertTrue(device.device.isNotEmpty())
            assertTrue(device.typicalSAR.isNotEmpty())
            assertTrue(device.riskLevel.isNotEmpty())
        }
    }

    @Test
    fun testDeviceLevels_ContainsCommonDevices() {
        val devices = RadiationStandard.deviceLevels.map { it.device }
        assertTrue(devices.any { it.contains("手机") })
        assertTrue(devices.any { it.contains("WiFi") })
        assertTrue(devices.any { it.contains("蓝牙") || it.contains("GPS") })
    }

    @Test
    fun testEnvironmentLevels_NotEmpty() {
        assertTrue(RadiationStandard.environmentLevels.isNotEmpty())
    }

    @Test
    fun testEnvironmentLevels_ValidData() {
        RadiationStandard.environmentLevels.forEach { env ->
            assertTrue(env.source.isNotEmpty())
            assertTrue(env.powerDensity.isNotEmpty())
            assertTrue(env.riskLevel.isNotEmpty())
        }
    }
}
