package com.signalmontor.app

object RadiationCalculator {

    private const val WIFI_FREQ_MHZ = 2400.0
    private const val CELLULAR_FREQ_MHZ = 1800.0

    data class RadiationInfo(
        val estimatedSAR: Double,
        val estimatedPowerDensity: Double,
        val riskLevel: RadiationRisk,
        val recommendation: String,
        val percentageOfLimit: Float
    )

    enum class RadiationRisk(
        val label: String,
        val color: Int,
        val description: String
    ) {
        LOW("安全", 0xFF4CAF50.toInt(), "远低于国际安全限值"),
        MODERATE("注意", 0xFFFFC107.toInt(), "处于安全范围内，建议保持距离"),
        HIGH("警告", 0xFFFF9800.toInt(), "接近安全限值，减少暴露时间"),
        VERY_HIGH("危险", 0xFFF44336.toInt(), "超过安全建议值，立即远离")
    }

    data class SafetyStandard(
        val organization: String,
        val limitSAR: Double,
        val averagingMass: String,
        val description: String
    )

    fun getSafetyStandards(): List<SafetyStandard> {
        return listOf(
            SafetyStandard(
                "ICNIRP (国际非电离辐射防护委员会)",
                2.0,
                "10g组织",
                "全球广泛采用的标准，公众暴露限值2.0 W/kg"
            ),
            SafetyStandard(
                "IEEE / FCC (美国联邦通信委员会)",
                1.6,
                "1g组织",
                "美国采用的标准，公众暴露限值1.6 W/kg"
            ),
            SafetyStandard(
                "中国国家标准 (GB 8702-2014)",
                2.0,
                "10g组织",
                "中国电磁环境控制限值，公众暴露限值2.0 W/kg"
            ),
            SafetyStandard(
                "欧盟标准 (EN 50360)",
                2.0,
                "10g组织",
                "欧盟成员国采用的手机辐射标准"
            )
        )
    }

    fun calculateWiFiRadiation(signalStrengthDbm: Int): RadiationInfo {
        val txPowerDbm = estimateWifiTxPower(signalStrengthDbm)
        val powerDensity = dbmToPowerDensity(txPowerDbm, distance = 0.1)
        val sar = estimateSAR(powerDensity, isCellular = false)
        val risk = getRadiationRisk(sar)
        val percentage = (sar / 2.0 * 100).toFloat().coerceIn(0f, 100f)

        return RadiationInfo(
            estimatedSAR = sar,
            estimatedPowerDensity = powerDensity,
            riskLevel = risk,
            recommendation = getWifiRecommendation(risk),
            percentageOfLimit = percentage
        )
    }

    fun calculateCellularRadiation(
        signalStrengthDbm: Int,
        networkType: SignalType
    ): RadiationInfo {
        val txPowerDbm = estimateCellularTxPower(signalStrengthDbm, networkType)
        val powerDensity = dbmToPowerDensity(txPowerDbm, distance = 0.1)
        val sar = estimateSAR(powerDensity, isCellular = true)
        val risk = getRadiationRisk(sar)
        val percentage = (sar / 2.0 * 100).toFloat().coerceIn(0f, 100f)

        return RadiationInfo(
            estimatedSAR = sar,
            estimatedPowerDensity = powerDensity,
            riskLevel = risk,
            recommendation = getCellularRecommendation(risk, networkType),
            percentageOfLimit = percentage
        )
    }

    private fun estimateWifiTxPower(signalStrengthDbm: Int): Double {
        return when {
            signalStrengthDbm >= -50 -> 5.0
            signalStrengthDbm >= -60 -> 10.0
            signalStrengthDbm >= -70 -> 15.0
            signalStrengthDbm >= -80 -> 20.0
            else -> 27.0
        }
    }

    private fun estimateCellularTxPower(signalStrengthDbm: Int, networkType: SignalType): Double {
        val basePower = when {
            signalStrengthDbm >= -70 -> 5.0
            signalStrengthDbm >= -85 -> 10.0
            signalStrengthDbm >= -100 -> 18.0
            else -> 23.0
        }

        val networkFactor = when (networkType) {
            SignalType.CELLULAR_2G -> 1.2
            SignalType.CELLULAR_3G -> 1.0
            SignalType.CELLULAR_4G -> 0.8
            SignalType.CELLULAR_5G -> 0.7
            else -> 1.0
        }

        return basePower * networkFactor
    }

    private fun dbmToPowerDensity(dbm: Double, distance: Double): Double {
        val powerWatts = Math.pow(10.0, (dbm - 30) / 10.0)
        val area = 4 * Math.PI * distance * distance
        return powerWatts / area
    }

    private fun estimateSAR(powerDensity: Double, isCellular: Boolean): Double {
        val freq = if (isCellular) CELLULAR_FREQ_MHZ else WIFI_FREQ_MHZ
        val eField = Math.sqrt(powerDensity * 377.0)
        val sar = (eField * eField) / (1.0 * 1000.0) * 0.001
        return sar.coerceIn(0.0, 2.0)
    }

    fun getRadiationRisk(sar: Double): RadiationRisk {
        return when {
            sar < 0.08 -> RadiationRisk.LOW
            sar < 0.4 -> RadiationRisk.MODERATE
            sar < 0.8 -> RadiationRisk.HIGH
            else -> RadiationRisk.VERY_HIGH
        }
    }

    private fun getWifiRecommendation(risk: RadiationRisk): String {
        return when (risk) {
            RadiationRisk.LOW -> "WiFi信号良好，辐射水平安全"
            RadiationRisk.MODERATE -> "辐射水平中等，建议保持适当距离"
            RadiationRisk.HIGH -> "辐射水平较高，建议减少使用时间"
            RadiationRisk.VERY_HIGH -> "辐射水平高，建议立即远离设备"
        }
    }

    private fun getCellularRecommendation(risk: RadiationRisk, networkType: SignalType): String {
        val networkName = when (networkType) {
            SignalType.CELLULAR_2G -> "2G"
            SignalType.CELLULAR_3G -> "3G"
            SignalType.CELLULAR_4G -> "4G"
            SignalType.CELLULAR_5G -> "5G"
            else -> "蜂窝网络"
        }
        return when (risk) {
            RadiationRisk.LOW -> "${networkName}信号良好，辐射水平安全"
            RadiationRisk.MODERATE -> "${networkName}辐射水平中等，建议使用耳机通话"
            RadiationRisk.HIGH -> "${networkName}辐射水平较高，建议减少通话时间"
            RadiationRisk.VERY_HIGH -> "${networkName}辐射水平高，建议使用免提或短信"
        }
    }
}
