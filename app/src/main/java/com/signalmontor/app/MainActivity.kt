package com.signalmontor.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.card.MaterialCardView
import com.signalmontor.app.view.AnimatedSignalBarView
import com.signalmontor.app.view.RadiationGaugeView
import com.signalmontor.app.view.Satellite3DView

class MainActivity : AppCompatActivity() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var wifiCard: MaterialCardView
    private lateinit var cellularCard: MaterialCardView
    private lateinit var satelliteCard: MaterialCardView
    private lateinit var overallCard: MaterialCardView
    private lateinit var standardsCard: MaterialCardView
    private lateinit var permissionBtn: Button

    private var wifiManager: android.net.wifi.WifiManager? = null
    private var telephonyManager: TelephonyManager? = null
    private var locationManager: LocationManager? = null
    private var gnssStatusCallback: GnssStatus.Callback? = null

    private var currentGpsCount = 0
    private var currentBeidouCount = 0
    private var currentGlonassCount = 0
    private var currentGalileoCount = 0
    private var currentSatellites = mutableListOf<SatelliteInfo>()
    private var lastLocation: Location? = null

    private val refreshHandler = Handler(Looper.getMainLooper())
    private var autoRefreshRunnable: Runnable? = null
    private var isAutoRefreshing = false
    private var refreshIntervalMs = 3000L

    private val positionSources = mutableListOf<PositionSource>()

    private val requiredPermissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_PHONE_STATE
    ).toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            permissionBtn.visibility = View.GONE
            refreshAll()
            startGnssListener()
            startLocationListener()
            startAutoRefresh()
        } else {
            val denied = permissions.filter { !it.value }.keys
            Toast.makeText(this, "以下权限未授予: ${denied.joinToString(", ")}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initManagers()
        setupListeners()
        showPermissionDialog()
        renderStandards()
    }

    private fun initViews() {
        swipeRefresh = findViewById(R.id.swipeRefresh)
        wifiCard = findViewById(R.id.wifiCard)
        cellularCard = findViewById(R.id.cellularCard)
        satelliteCard = findViewById(R.id.satelliteCard)
        overallCard = findViewById(R.id.overallCard)
        standardsCard = findViewById(R.id.standardsCard)
        permissionBtn = findViewById(R.id.permissionBtn)
    }

    private fun initManagers() {
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private fun setupListeners() {
        swipeRefresh.setOnRefreshListener {
            refreshAll()
            swipeRefresh.isRefreshing = false
        }

        findViewById<Button>(R.id.refreshBtn).setOnClickListener {
            refreshAll()
        }

        permissionBtn.setOnClickListener {
            requestPermissions()
        }
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("权限请求")
            .setMessage("本应用需要以下权限才能正常工作：\n\n" +
                    "📍 位置权限：用于获取WiFi信息和GPS定位\n" +
                    "📞 电话状态：用于获取蜂窝网络信号强度\n\n" +
                    "这些权限仅用于信号监测，不会收集您的个人数据。")
            .setPositiveButton("同意并继续") { _, _ ->
                requestPermissions()
            }
            .setNegativeButton("拒绝") { _, _ ->
                Toast.makeText(this, "未授予权限，部分功能将不可用", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestPermissions() {
        permissionLauncher.launch(requiredPermissions)
    }

    private fun startAutoRefresh() {
        autoRefreshRunnable = object : Runnable {
            override fun run() {
                if (!isAutoRefreshing) return
                refreshAll()
                refreshHandler.postDelayed(this, refreshIntervalMs)
            }
        }
        isAutoRefreshing = true
        refreshHandler.post(autoRefreshRunnable!!)
    }

    private fun stopAutoRefresh() {
        isAutoRefreshing = false
        autoRefreshRunnable?.let { refreshHandler.removeCallbacks(it) }
    }

    private fun refreshAll() {
        refreshWifi()
        refreshCellular()
        updateOverallRadiation()
        updatePositionSources()
    }

    @SuppressLint("MissingPermission")
    private fun refreshWifi() {
        val wifiInfo = wifiManager?.connectionInfo
        val wifiStrength = findViewById<TextView>(R.id.wifiStrength)
        val wifiStatus = findViewById<TextView>(R.id.wifiStatus)
        val wifiDetails = findViewById<TextView>(R.id.wifiDetails)
        val wifiRadiationLevel = findViewById<TextView>(R.id.wifiRadiationLevel)
        val wifiSAR = findViewById<TextView>(R.id.wifiSAR)
        val wifiPercentOfLimit = findViewById<TextView>(R.id.wifiPercentOfLimit)
        val wifiRecommendation = findViewById<TextView>(R.id.wifiRecommendation)
        val wifiBars = findViewById<AnimatedSignalBarView>(R.id.wifiBars)
        val wifiRadiationGauge = findViewById<RadiationGaugeView>(R.id.wifiRadiationGauge)

        if (wifiInfo == null || wifiInfo.ssid == "<unknown ssid>") {
            wifiStatus.text = "未连接"
            wifiStrength.text = "-- dBm"
            wifiDetails.text = "未连接到WiFi网络"
            wifiRadiationLevel.text = "--"
            wifiSAR.text = "SAR: -- W/kg"
            wifiPercentOfLimit.text = "占限值: --%"
            wifiRecommendation.text = "未连接WiFi"
            wifiBars.setLevel(0, Color.GRAY)
            wifiRadiationGauge.reset()
            return
        }

        val rssi = wifiInfo.rssi
        val level = android.net.wifi.WifiManager.calculateSignalLevel(rssi, 5).coerceIn(0, 4)
        val quality = getWifiQuality(rssi)

        wifiStatus.text = "已连接"
        wifiStrength.text = "$rssi dBm"
        wifiStrength.setTextColor(quality.color)
        wifiDetails.text = "SSID: ${wifiInfo.ssid.replace("\"", "")} | BSSID: ${wifiInfo.bssid} | 频率: ${wifiInfo.frequency}MHz | 速率: ${wifiInfo.linkSpeed}Mbps"

        wifiBars.setLevel(level, quality.color)
        wifiBars.startAnimation(AnimationUtils.loadAnimation(this, R.anim.signal_pulse))

        val radiation = RadiationCalculator.calculateWiFiRadiation(rssi)
        wifiRadiationLevel.text = radiation.riskLevel.label
        wifiRadiationLevel.setTextColor(radiation.riskLevel.color)
        wifiSAR.text = "SAR: ${String.format("%.4f", radiation.estimatedSAR)} W/kg"
        wifiPercentOfLimit.text = "占安全限值: ${String.format("%.1f", radiation.percentageOfLimit)}%"
        wifiRecommendation.text = radiation.recommendation

        wifiRadiationGauge.setValue(
            radiation.percentageOfLimit,
            radiation.riskLevel.color,
            "${String.format("%.0f", radiation.percentageOfLimit)}%",
            radiation.riskLevel.label
        )
    }

    @SuppressLint("MissingPermission")
    private fun refreshCellular() {
        val cellularStrength = findViewById<TextView>(R.id.cellularStrength)
        val cellularNetworkType = findViewById<TextView>(R.id.cellularNetworkType)
        val cellularDetails = findViewById<TextView>(R.id.cellularDetails)
        val cellularRadiationLevel = findViewById<TextView>(R.id.cellularRadiationLevel)
        val cellularSAR = findViewById<TextView>(R.id.cellularSAR)
        val cellularPercentOfLimit = findViewById<TextView>(R.id.cellularPercentOfLimit)
        val cellularRecommendation = findViewById<TextView>(R.id.cellularRecommendation)
        val cellularBars = findViewById<AnimatedSignalBarView>(R.id.cellularBars)
        val cellularRadiationGauge = findViewById<RadiationGaugeView>(R.id.cellularRadiationGauge)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            cellularStrength.text = "权限不足"
            cellularNetworkType.text = "--"
            cellularDetails.text = "请授予电话状态权限"
            cellularBars.setLevel(0, Color.GRAY)
            cellularRadiationGauge.reset()
            return
        }

        val cellInfos = telephonyManager?.allCellInfo
        if (cellInfos.isNullOrEmpty()) {
            cellularStrength.text = "-- dBm"
            cellularNetworkType.text = "--"
            cellularDetails.text = "未检测到蜂窝网络"
            cellularRadiationLevel.text = "--"
            cellularSAR.text = "SAR: -- W/kg"
            cellularPercentOfLimit.text = "占限值: --%"
            cellularRecommendation.text = "无蜂窝网络"
            cellularBars.setLevel(0, Color.GRAY)
            cellularRadiationGauge.reset()
            return
        }

        var signalType = SignalType.CELLULAR_4G
        var dbm = -120
        var details = ""
        var level = 0

        for (cellInfo in cellInfos) {
            if (cellInfo is CellInfoLte) {
                signalType = SignalType.CELLULAR_4G
                val signalStrength = cellInfo.cellSignalStrength as CellSignalStrengthLte
                dbm = signalStrength.dbm
                level = signalStrength.level
                details = "类型: LTE | PCI: ${cellInfo.cellIdentity.pci} | CI: ${cellInfo.cellIdentity.ci}"
                break
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo is CellInfoNr) {
                signalType = SignalType.CELLULAR_5G
                val signalStrength = cellInfo.cellSignalStrength as CellSignalStrengthNr
                dbm = signalStrength.dbm
                level = signalStrength.level
                details = "类型: 5G NR | 信号等级: $level/4"
                break
            }
        }

        val quality = getCellularQuality(dbm, signalType)

        cellularStrength.text = "$dbm dBm"
        cellularStrength.setTextColor(quality.color)
        cellularNetworkType.text = when (signalType) {
            SignalType.CELLULAR_2G -> "2G"
            SignalType.CELLULAR_3G -> "3G"
            SignalType.CELLULAR_4G -> "4G"
            SignalType.CELLULAR_5G -> "5G"
            else -> "--"
        }
        cellularDetails.text = if (details.isNotEmpty()) details else "信号强度: $dbm dBm | 等级: $level/4"

        cellularBars.setLevel(level, quality.color)
        cellularBars.startAnimation(AnimationUtils.loadAnimation(this, R.anim.signal_pulse))

        val radiation = RadiationCalculator.calculateCellularRadiation(dbm, signalType)
        cellularRadiationLevel.text = radiation.riskLevel.label
        cellularRadiationLevel.setTextColor(radiation.riskLevel.color)
        cellularSAR.text = "SAR: ${String.format("%.4f", radiation.estimatedSAR)} W/kg"
        cellularPercentOfLimit.text = "占安全限值: ${String.format("%.1f", radiation.percentageOfLimit)}%"
        cellularRecommendation.text = radiation.recommendation

        cellularRadiationGauge.setValue(
            radiation.percentageOfLimit,
            radiation.riskLevel.color,
            "${String.format("%.0f", radiation.percentageOfLimit)}%",
            radiation.riskLevel.label
        )
    }

    private fun updateOverallRadiation() {
        val overallRadiationGauge = findViewById<RadiationGaugeView>(R.id.overallRadiationGauge)
        val overallRadiationLevel = findViewById<TextView>(R.id.overallRadiationLevel)
        val overallPercent = findViewById<TextView>(R.id.overallPercent)
        val overallRecommendation = findViewById<TextView>(R.id.overallRecommendation)

        val wifiInfo = wifiManager?.connectionInfo
        val cellInfos = telephonyManager?.allCellInfo

        var maxRadiation = 0.0
        var maxRisk = RadiationCalculator.RadiationRisk.LOW
        var recommendation = "无活跃网络连接"

        if (wifiInfo != null && wifiInfo.ssid != "<unknown ssid>") {
            val wifiRad = RadiationCalculator.calculateWiFiRadiation(wifiInfo.rssi)
            if (wifiRad.estimatedSAR > maxRadiation) {
                maxRadiation = wifiRad.estimatedSAR
                maxRisk = wifiRad.riskLevel
                recommendation = "WiFi: ${wifiRad.recommendation}"
            }
        }

        if (!cellInfos.isNullOrEmpty()) {
            var cellularDbm = -120
            var signalType = SignalType.CELLULAR_4G
            for (cellInfo in cellInfos) {
                if (cellInfo is CellInfoLte) {
                    cellularDbm = (cellInfo.cellSignalStrength as CellSignalStrengthLte).dbm
                    signalType = SignalType.CELLULAR_4G
                    break
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo is CellInfoNr) {
                    cellularDbm = (cellInfo.cellSignalStrength as CellSignalStrengthNr).dbm
                    signalType = SignalType.CELLULAR_5G
                    break
                }
            }
            val cellularRad = RadiationCalculator.calculateCellularRadiation(cellularDbm, signalType)
            if (cellularRad.estimatedSAR > maxRadiation) {
                maxRadiation = cellularRad.estimatedSAR
                maxRisk = cellularRad.riskLevel
                recommendation = "蜂窝网络: ${cellularRad.recommendation}"
            }
        }

        val percentage = (maxRadiation / 2.0 * 100).toFloat().coerceIn(0f, 100f)
        overallRadiationGauge.setValue(
            percentage,
            maxRisk.color,
            "${String.format("%.0f", percentage)}%",
            maxRisk.label
        )
        overallRadiationLevel.text = maxRisk.label
        overallRadiationLevel.setTextColor(maxRisk.color)
        overallPercent.text = "占安全限值: ${String.format("%.1f", percentage)}%"
        overallRecommendation.text = recommendation
    }

    @SuppressLint("MissingPermission")
    private fun startGnssListener() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        try {
            var lastGnssUpdate = 0L
            val throttleMs = 2000L

            gnssStatusCallback = object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    val now = System.currentTimeMillis()
                    if (now - lastGnssUpdate < throttleMs) return
                    lastGnssUpdate = now

                    currentGpsCount = 0
                    currentBeidouCount = 0
                    currentGlonassCount = 0
                    currentGalileoCount = 0
                    currentSatellites.clear()

                    for (i in 0 until status.satelliteCount) {
                        val constellation = status.getConstellationType(i)
                        val prn = status.getSvid(i)
                        val snr = status.getCn0DbHz(i)
                        val elevation = status.getElevationDegrees(i)
                        val azimuth = status.getAzimuthDegrees(i)
                        val usedInFix = status.usedInFix(i)
                        val hasEphemeris = status.hasEphemerisData(i)
                        val hasAlmanac = status.hasAlmanacData(i)

                        val constellationName = when (constellation) {
                            GnssStatus.CONSTELLATION_GPS -> {
                                currentGpsCount++
                                "GPS"
                            }
                            GnssStatus.CONSTELLATION_BEIDOU -> {
                                currentBeidouCount++
                                "北斗"
                            }
                            GnssStatus.CONSTELLATION_GLONASS -> {
                                currentGlonassCount++
                                "GLONASS"
                            }
                            GnssStatus.CONSTELLATION_GALILEO -> {
                                currentGalileoCount++
                                "Galileo"
                            }
                            else -> {
                                "其他"
                            }
                        }

                        currentSatellites.add(SatelliteInfo(
                            constellation = constellationName,
                            prn = prn,
                            snr = snr,
                            elevation = elevation,
                            azimuth = azimuth,
                            hasEphemeris = hasEphemeris,
                            hasAlmanac = hasAlmanac,
                            usedInFix = usedInFix
                        ))
                    }

                    runOnUiThread {
                        updateSatelliteUI()
                    }
                }
            }
            locationManager?.registerGnssStatusCallback(mainExecutor, gnssStatusCallback!!)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationListener() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        try {
            val locationListener = LocationListener { location ->
                lastLocation = location
                runOnUiThread {
                    updatePositionSources()
                    updateSatelliteUI()
                }
            }

            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0f, locationListener)
            locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0f, locationListener)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    private fun updatePositionSources() {
        positionSources.clear()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        val isNetworkEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true

        val hasRealSatellites = currentSatellites.any { it.hasEphemeris && it.snr > 0 }
        val gnssInUse = currentSatellites.count { it.usedInFix }

        if (isGpsEnabled && hasRealSatellites) {
            positionSources.add(PositionSource(
                type = PositionSourceType.GNSS,
                label = "真实卫星信号 (GNSS)",
                isActive = true,
                accuracy = lastLocation?.accuracy
            ))
        }

        if (isNetworkEnabled) {
            val wifiConnected = wifiManager?.connectionInfo?.ssid != "<unknown ssid>" && wifiManager?.connectionInfo?.ssid != null
            val cellConnected = telephonyManager?.allCellInfo?.isNotEmpty() == true

            if (wifiConnected) {
                positionSources.add(PositionSource(
                    type = PositionSourceType.WIFI,
                    label = "WiFi辅助定位",
                    isActive = true,
                    accuracy = null
                ))
            }

            if (cellConnected) {
                positionSources.add(PositionSource(
                    type = PositionSourceType.CELL,
                    label = "基站辅助定位",
                    isActive = true,
                    accuracy = null
                ))
            }

            if (!wifiConnected && !cellConnected) {
                positionSources.add(PositionSource(
                    type = PositionSourceType.AGPS,
                    label = "AGPS辅助",
                    isActive = true,
                    accuracy = null
                ))
            }
        }

        if (lastLocation?.provider == LocationManager.PASSIVE_PROVIDER) {
            positionSources.add(PositionSource(
                type = PositionSourceType.PASSIVE,
                label = "被动定位",
                isActive = true,
                accuracy = lastLocation?.accuracy
            ))
        }

        runOnUiThread {
            updatePositionSourceUI()
        }
    }

    private fun updatePositionSourceUI() {
        val positionSourceView = findViewById<LinearLayout>(R.id.positionSourceContainer)
        positionSourceView.removeAllViews()

        if (positionSources.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "未检测到定位来源"
                textSize = 13f
                setTextColor(Color.GRAY)
                setPadding(0, 8, 0, 8)
            }
            positionSourceView.addView(emptyView)
            return
        }

        for (source in positionSources) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 6, 0, 6)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val iconView = TextView(this).apply {
                text = source.type.icon
                textSize = 16f
                setPadding(0, 0, 8, 0)
            }

            val labelView = TextView(this).apply {
                text = source.label
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.primary_text))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val statusView = TextView(this).apply {
                text = if (source.isActive) "✓ 活跃" else "○ 未激活"
                textSize = 12f
                setTextColor(if (source.isActive) 0xFF4CAF50.toInt() else Color.GRAY)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            row.addView(iconView)
            row.addView(labelView)
            row.addView(statusView)
            positionSourceView.addView(row)
        }

        val hasRealGnss = positionSources.any { it.type == PositionSourceType.GNSS }
        val gnssBadge = findViewById<TextView>(R.id.gnssBadge)
        val assistedBadge = findViewById<TextView>(R.id.assistedBadge)

        if (hasRealGnss) {
            gnssBadge.text = "🛰️ 真实卫星信号"
            gnssBadge.setTextColor(0xFF4CAF50.toInt())
            gnssBadge.visibility = View.VISIBLE
        } else {
            gnssBadge.text = "⚠️ 无真实卫星信号"
            gnssBadge.setTextColor(0xFFFF9800.toInt())
            gnssBadge.visibility = View.VISIBLE
        }

        val hasAssisted = positionSources.any { it.type != PositionSourceType.GNSS }
        if (hasAssisted) {
            assistedBadge.text = "🌐 辅助定位已启用"
            assistedBadge.setTextColor(0xFF2196F3.toInt())
            assistedBadge.visibility = View.VISIBLE
        } else {
            assistedBadge.visibility = View.GONE
        }
    }

    private fun updateSatelliteUI() {
        val satellite3DView = findViewById<Satellite3DView>(R.id.satellite3DView)
        satellite3DView.updateSatellites(currentSatellites)

        val gpsCountView = findViewById<TextView>(R.id.gpsCount)
        val beidouCountView = findViewById<TextView>(R.id.beidouCount)
        val glonassCountView = findViewById<TextView>(R.id.glonassCount)
        val galileoCountView = findViewById<TextView>(R.id.galileoCount)
        val totalSatellitesView = findViewById<TextView>(R.id.totalSatellites)
        val locationInfoView = findViewById<TextView>(R.id.locationInfo)
        val satelliteListView = findViewById<TextView>(R.id.satelliteList)

        val total = currentGpsCount + currentBeidouCount + currentGlonassCount + currentGalileoCount

        gpsCountView.text = "$currentGpsCount"
        beidouCountView.text = "$currentBeidouCount"
        glonassCountView.text = "$currentGlonassCount"
        galileoCountView.text = "$currentGalileoCount"
        totalSatellitesView.text = "$total"

        if (total > 0) {
            val usedInFix = currentSatellites.count { it.usedInFix }
            val withEphemeris = currentSatellites.count { it.hasEphemeris }
            val avgSnr = if (currentSatellites.isNotEmpty()) {
                currentSatellites.map { it.snr }.average()
            } else 0.0

            locationInfoView.text = "用于定位: $usedInFix 颗 | 有星历: $withEphemeris 颗 | 平均信噪比: ${String.format("%.1f", avgSnr)} dB-Hz"

            val sorted = currentSatellites.sortedByDescending { it.snr }.take(8)
            satelliteListView.text = sorted.map { s ->
                val ephStatus = if (s.hasEphemeris) "✓" else "○"
                val fixStatus = if (s.usedInFix) "📍" else " "
                "${s.constellation} PRN${s.prn}: ${String.format("%.1f", s.snr)}dB-Hz | 仰角${s.elevation.toInt()}° | 星历$ephStatus $fixStatus"
            }.joinToString("\n")
        } else {
            val hasNetworkLocation = lastLocation?.provider == LocationManager.NETWORK_PROVIDER
            if (hasNetworkLocation) {
                locationInfoView.text = "⚠️ 当前使用辅助定位（WiFi/基站），无真实卫星信号"
            } else {
                locationInfoView.text = "等待卫星信号..."
            }
            satelliteListView.text = ""
        }

        gpsCountView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.signal_pulse))
        beidouCountView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.signal_pulse))
    }

    private fun renderStandards() {
        val standardsContainer = findViewById<LinearLayout>(R.id.standardsContainer)
        val deviceLevelsContainer = findViewById<LinearLayout>(R.id.deviceLevelsContainer)

        for (standard in RadiationStandard.standards) {
            val textView = TextView(this).apply {
                text = "${standard.source}\n限值: ${standard.limit} | 频率: ${standard.frequency}\n${standard.description}"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.secondary_text))
                setPadding(0, 8, 0, 8)
            }
            standardsContainer.addView(textView)
            if (standardsContainer.childCount > 1) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
                }
                standardsContainer.addView(divider, standardsContainer.childCount - 1)
            }
        }

        for (device in RadiationStandard.deviceLevels) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 6, 0, 6)
            }

            val riskColor = when (device.riskLevel) {
                "高", "中高" -> 0xFFF44336.toInt()
                "中" -> 0xFFFF9800.toInt()
                "低" -> 0xFF4CAF50.toInt()
                "极低" -> 0xFF2196F3.toInt()
                else -> Color.GRAY
            }

            val nameView = TextView(this).apply {
                text = device.device
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.primary_text))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            }

            val sarView = TextView(this).apply {
                text = device.typicalSAR
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.secondary_text))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
                gravity = android.view.Gravity.CENTER
            }

            val riskView = TextView(this).apply {
                text = device.riskLevel
                textSize = 12f
                setTextColor(riskColor)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                gravity = android.view.Gravity.END
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            row.addView(nameView)
            row.addView(sarView)
            row.addView(riskView)
            deviceLevelsContainer.addView(row)
        }
    }

    private fun getWifiQuality(rssi: Int): SignalQuality {
        return when {
            rssi >= -50 -> SignalQuality.EXCELLENT
            rssi >= -60 -> SignalQuality.GOOD
            rssi >= -70 -> SignalQuality.FAIR
            rssi >= -80 -> SignalQuality.POOR
            else -> SignalQuality.NONE
        }
    }

    private fun getCellularQuality(dbm: Int, type: SignalType): SignalQuality {
        val threshold = when (type) {
            SignalType.CELLULAR_5G -> -80
            SignalType.CELLULAR_4G -> -85
            SignalType.CELLULAR_3G -> -90
            SignalType.CELLULAR_2G -> -95
            else -> -85
        }
        return when {
            dbm >= threshold + 20 -> SignalQuality.EXCELLENT
            dbm >= threshold + 10 -> SignalQuality.GOOD
            dbm >= threshold -> SignalQuality.FAIR
            dbm >= threshold - 15 -> SignalQuality.POOR
            else -> SignalQuality.NONE
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startAutoRefresh()
        }
    }

    override fun onPause() {
        super.onPause()
        stopAutoRefresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoRefresh()
        try {
            gnssStatusCallback?.let { locationManager?.unregisterGnssStatusCallback(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
