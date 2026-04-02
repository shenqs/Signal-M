package com.signalmontor.app

data class SatelliteInfo(
    val constellation: String,
    val prn: Int,
    val snr: Float,
    val elevation: Float,
    val azimuth: Float,
    val hasEphemeris: Boolean,
    val hasAlmanac: Boolean,
    val usedInFix: Boolean
)

data class LocationInfo(
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val accuracy: Float?,
    val speed: Float?,
    val satelliteCount: Int,
    val gpsCount: Int,
    val beidouCount: Int,
    val glonassCount: Int,
    val galileoCount: Int,
    val satellites: List<SatelliteInfo>
)

enum class PositionSourceType(val label: String, val icon: String) {
    GNSS("真实卫星信号", "🛰️"),
    AGPS("AGPS辅助", "🌐"),
    WIFI("WiFi定位", "📶"),
    CELL("基站定位", "📡"),
    PASSIVE("被动定位", "📍"),
    UNKNOWN("未知来源", "❓")
}

data class PositionSource(
    val type: PositionSourceType,
    val label: String,
    val isActive: Boolean,
    val accuracy: Float?
)
