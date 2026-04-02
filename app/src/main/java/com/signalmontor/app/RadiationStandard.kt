package com.signalmontor.app

object RadiationStandard {

    data class StandardItem(
        val source: String,
        val limit: String,
        val frequency: String,
        val description: String
    )

    val standards = listOf(
        StandardItem(
            source = "ICNIRP (国际非电离辐射防护委员会)",
            limit = "2.0 W/kg (10g平均)",
            frequency = "100kHz - 6GHz",
            description = "公众暴露限值，被欧盟及多数国家采用"
        ),
        StandardItem(
            source = "FCC (美国联邦通信委员会)",
            limit = "1.6 W/kg (1g平均)",
            frequency = "300kHz - 6GHz",
            description = "美国标准，比ICNIRP更严格"
        ),
        StandardItem(
            source = "中国国家标准 GB 21288-2007",
            limit = "2.0 W/kg (10g平均)",
            frequency = "300MHz - 3GHz",
            description = "中国移动电话无线电设备SAR限值"
        ),
        StandardItem(
            source = "IEEE C95.1",
            limit = "2.0 W/kg (10g平均)",
            frequency = "3kHz - 300GHz",
            description = "IEEE射频暴露标准"
        )
    )

    data class DeviceRadiationLevel(
        val device: String,
        val typicalSAR: String,
        val riskLevel: String
    )

    val deviceLevels = listOf(
        DeviceRadiationLevel("手机通话（贴近头部）", "0.5 ~ 1.6 W/kg", "高"),
        DeviceRadiationLevel("手机通话（免提/耳机）", "0.01 ~ 0.1 W/kg", "低"),
        DeviceRadiationLevel("WiFi (2.4GHz)", "0.01 ~ 0.1 W/kg", "低"),
        DeviceRadiationLevel("WiFi (5GHz)", "0.01 ~ 0.08 W/kg", "低"),
        DeviceRadiationLevel("4G LTE数据", "0.1 ~ 0.8 W/kg", "中"),
        DeviceRadiationLevel("5G Sub-6", "0.1 ~ 1.0 W/kg", "中"),
        DeviceRadiationLevel("5G mmWave", "0.2 ~ 1.2 W/kg", "中高"),
        DeviceRadiationLevel("GPS/北斗接收", "< 0.001 W/kg", "极低"),
        DeviceRadiationLevel("蓝牙", "< 0.01 W/kg", "极低")
    )

    data class EnvironmentLevel(
        val source: String,
        val powerDensity: String,
        val riskLevel: String
    )

    val environmentLevels = listOf(
        EnvironmentLevel("微波炉泄漏 (<5cm)", "1 ~ 5 mW/cm²", "中"),
        EnvironmentLevel("基站天线 (10m外)", "0.001 ~ 0.01 mW/cm²", "极低"),
        EnvironmentLevel("WiFi路由器 (1m)", "0.0001 ~ 0.001 mW/cm²", "极低"),
        EnvironmentLevel("手机基站楼顶", "0.00001 ~ 0.0001 mW/cm²", "极低"),
        EnvironmentLevel("自然背景辐射", "0.000001 mW/cm²", "无")
    )
}
