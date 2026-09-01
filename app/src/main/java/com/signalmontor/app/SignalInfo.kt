package com.signalmontor.app

data class SignalInfo(
    val type: SignalType,
    val strength: Int,
    val strengthUnit: String,
    val quality: SignalQuality,
    val details: String = ""
)

enum class SignalType {
    WIFI,
    CELLULAR_2G,
    CELLULAR_3G,
    CELLULAR_4G,
    CELLULAR_5G
}

enum class SignalQuality(val level: Int, val color: Int, val label: String) {
    EXCELLENT(4, 0xFF69F0AE.toInt(), "极好"),
    GOOD(3, 0xFFAED581.toInt(), "良好"),
    FAIR(2, 0xFFFFD54F.toInt(), "一般"),
    POOR(1, 0xFFFFB74D.toInt(), "较差"),
    NONE(0, 0xFFFF8A80.toInt(), "无信号")
}
