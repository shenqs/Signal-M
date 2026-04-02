package com.signalmontor.app

import org.junit.Assert.*
import org.junit.Test

class SatelliteInfoTest {

    @Test
    fun testSatelliteInfo_Creation() {
        val sat = SatelliteInfo(
            constellation = "GPS",
            prn = 12,
            snr = 35.5f,
            elevation = 45.0f,
            azimuth = 180.0f,
            hasEphemeris = true,
            hasAlmanac = true,
            usedInFix = true
        )
        assertEquals("GPS", sat.constellation)
        assertEquals(12, sat.prn)
        assertEquals(35.5f, sat.snr, 0.01f)
        assertEquals(45.0f, sat.elevation, 0.01f)
        assertEquals(180.0f, sat.azimuth, 0.01f)
        assertTrue(sat.hasEphemeris)
        assertTrue(sat.hasAlmanac)
        assertTrue(sat.usedInFix)
    }

    @Test
    fun testLocationInfo_Creation() {
        val sats = listOf(
            SatelliteInfo("GPS", 1, 30f, 40f, 180f, true, true, true),
            SatelliteInfo("北斗", 2, 25f, 30f, 90f, true, false, false)
        )
        val loc = LocationInfo(
            latitude = 39.9042,
            longitude = 116.4074,
            altitude = 50.0,
            accuracy = 10f,
            speed = 0f,
            satelliteCount = 2,
            gpsCount = 1,
            beidouCount = 1,
            glonassCount = 0,
            galileoCount = 0,
            satellites = sats
        )
        assertEquals(39.9042, loc.latitude!!, 0.0001)
        assertEquals(116.4074, loc.longitude!!, 0.0001)
        assertEquals(2, loc.satelliteCount)
        assertEquals(1, loc.gpsCount)
        assertEquals(1, loc.beidouCount)
        assertEquals(2, loc.satellites.size)
    }

    @Test
    fun testPositionSourceType_Values() {
        assertEquals(6, PositionSourceType.values().size)
        assertEquals("真实卫星信号", PositionSourceType.GNSS.label)
        assertEquals("AGPS辅助", PositionSourceType.AGPS.label)
        assertEquals("WiFi定位", PositionSourceType.WIFI.label)
        assertEquals("基站定位", PositionSourceType.CELL.label)
        assertEquals("被动定位", PositionSourceType.PASSIVE.label)
        assertEquals("未知来源", PositionSourceType.UNKNOWN.label)
    }

    @Test
    fun testPositionSource_Creation() {
        val source = PositionSource(
            type = PositionSourceType.GNSS,
            label = "真实卫星信号 (GNSS)",
            isActive = true,
            accuracy = 5f
        )
        assertEquals(PositionSourceType.GNSS, source.type)
        assertTrue(source.isActive)
        assertEquals(5f, source.accuracy!!, 0.01f)
    }
}
