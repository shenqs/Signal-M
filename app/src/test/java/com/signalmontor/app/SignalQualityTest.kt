package com.signalmontor.app

import org.junit.Assert.*
import org.junit.Test

class SignalQualityTest {

    @Test
    fun testSignalQuality_EnumValues() {
        assertEquals(4, SignalQuality.EXCELLENT.level)
        assertEquals(3, SignalQuality.GOOD.level)
        assertEquals(2, SignalQuality.FAIR.level)
        assertEquals(1, SignalQuality.POOR.level)
        assertEquals(0, SignalQuality.NONE.level)
    }

    @Test
    fun testSignalQuality_Colors() {
        assertTrue(SignalQuality.EXCELLENT.color != 0)
        assertTrue(SignalQuality.GOOD.color != 0)
        assertTrue(SignalQuality.FAIR.color != 0)
        assertTrue(SignalQuality.POOR.color != 0)
        assertTrue(SignalQuality.NONE.color != 0)
        assertNotEquals(SignalQuality.EXCELLENT.color, SignalQuality.NONE.color)
    }

    @Test
    fun testSignalQuality_Labels() {
        assertEquals("极好", SignalQuality.EXCELLENT.label)
        assertEquals("良好", SignalQuality.GOOD.label)
        assertEquals("一般", SignalQuality.FAIR.label)
        assertEquals("较差", SignalQuality.POOR.label)
        assertEquals("无信号", SignalQuality.NONE.label)
    }

    @Test
    fun testSignalType_Values() {
        assertEquals(5, SignalType.values().size)
        assertEquals(SignalType.WIFI, SignalType.valueOf("WIFI"))
        assertEquals(SignalType.CELLULAR_2G, SignalType.valueOf("CELLULAR_2G"))
        assertEquals(SignalType.CELLULAR_3G, SignalType.valueOf("CELLULAR_3G"))
        assertEquals(SignalType.CELLULAR_4G, SignalType.valueOf("CELLULAR_4G"))
        assertEquals(SignalType.CELLULAR_5G, SignalType.valueOf("CELLULAR_5G"))
    }

    @Test
    fun testSignalInfo_Creation() {
        val info = SignalInfo(
            type = SignalType.WIFI,
            strength = -50,
            strengthUnit = "dBm",
            quality = SignalQuality.EXCELLENT,
            details = "Test WiFi"
        )
        assertEquals(SignalType.WIFI, info.type)
        assertEquals(-50, info.strength)
        assertEquals("dBm", info.strengthUnit)
        assertEquals(SignalQuality.EXCELLENT, info.quality)
        assertEquals("Test WiFi", info.details)
    }
}
