package com.signalmontor.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import com.signalmontor.app.view.SpeedMonitorView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject
import android.location.Geocoder

class MainActivity : AppCompatActivity() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var wifiCard: MaterialCardView
    private lateinit var cellularCard: MaterialCardView
    private lateinit var speedCard: MaterialCardView
    private lateinit var satelliteCard: MaterialCardView
    private lateinit var overallCard: MaterialCardView
    private lateinit var standardsCard: MaterialCardView
    private lateinit var permissionBtn: Button

    private var wifiManager: android.net.wifi.WifiManager? = null
    private var telephonyManager: TelephonyManager? = null
    private var locationManager: LocationManager? = null
    private var sensorManager: SensorManager? = null
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

    private lateinit var speedCalculator: SpeedCalculator
    private lateinit var speedMonitorView: SpeedMonitorView
    private var sensorEventListener: SensorEventListener? = null
    private var accelerometerSensor: Sensor? = null
    private var gyroscopeSensor: Sensor? = null
    private var magnetometerSensor: Sensor? = null
    private var pressureSensor: Sensor? = null
    private var temperatureSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null
    private var linearAccelSensor: Sensor? = null
    private var gravitySensor: Sensor? = null

    private var currentWeatherDesc = ""
    private var currentWeatherTemp = ""
    private var lastWeatherFetchTime = 0L
    private val WEATHER_CACHE_INTERVAL = 60 * 60 * 1000L
    private val WEATHER_RETRY_INTERVAL = 60 * 1000L
    private var weatherFetchInProgress = false
    private var currentRegion = ""
    private var currentSubRegion = ""

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

        speedCalculator = SpeedCalculator()
        initViews()
        initManagers()
        initSensors()
        setupListeners()
        showPermissionDialog()
        renderStandards()
    }

    private fun initViews() {
        swipeRefresh = findViewById(R.id.swipeRefresh)
        wifiCard = findViewById(R.id.wifiCard)
        cellularCard = findViewById(R.id.cellularCard)
        speedCard = findViewById(R.id.speedCard)
        satelliteCard = findViewById(R.id.satelliteCard)
        overallCard = findViewById(R.id.overallCard)
        standardsCard = findViewById(R.id.standardsCard)
        permissionBtn = findViewById(R.id.permissionBtn)
        speedMonitorView = findViewById(R.id.speedMonitorView)
    }

    private fun initManagers() {
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private fun initSensors() {
        linearAccelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        accelerometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscopeSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        temperatureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        stepDetectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_LINEAR_ACCELERATION -> speedCalculator.processLinearAcceleration(event)
                    Sensor.TYPE_GRAVITY -> speedCalculator.processGravity(event)
                    Sensor.TYPE_ACCELEROMETER -> if (linearAccelSensor == null) speedCalculator.processAccelerometer(event)
                    Sensor.TYPE_GYROSCOPE -> speedCalculator.processGyroscope(event)
                    Sensor.TYPE_MAGNETIC_FIELD -> speedCalculator.processMagneticField(event)
                    Sensor.TYPE_PRESSURE -> speedCalculator.processPressure(event)
                    Sensor.TYPE_AMBIENT_TEMPERATURE -> speedCalculator.processTemperature(event)
                }
                runOnUiThread { updateSpeedUI() }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
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
                speedCalculator.updateGpsSpeed(location.speed, location.accuracy)
                speedCalculator.updateLocationSpeed(location.speed)
                if (location.hasBearing()) {
                    speedCalculator.updateGpsBearing(location.bearing)
                }
                if (location.hasAltitude()) {
                    speedCalculator.updateGpsAltitude(location.altitude.toFloat())
                }
                runOnUiThread {
                    updatePositionSources()
                    updateSatelliteUI()
                    updateSpeedUI()
                    fetchWeatherIfNeeded()
                    resolveRegion(location.latitude, location.longitude)
                }
            }

            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0f, locationListener)
            locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0f, locationListener)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startSensorListeners() {
        sensorEventListener?.let { listener ->
            linearAccelSensor?.let {
                sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
            }
            gravitySensor?.let {
                sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
            }
            if (linearAccelSensor == null) {
                accelerometerSensor?.let {
                    sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
                }
            }
            gyroscopeSensor?.let {
                sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
            }
            magnetometerSensor?.let {
                sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
            }
            pressureSensor?.let {
                sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            temperatureSensor?.let {
                sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            stepDetectorSensor?.let {
                sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    private fun stopSensorListeners() {
        sensorEventListener?.let { listener ->
            sensorManager?.unregisterListener(listener)
        }
    }

    private fun updateSpeedUI() {
        val data = speedCalculator.getSpeed()
        speedMonitorView.updateSpeedData(data)
        updateSensorStatusUI()
    }

    private fun updateSensorStatusUI() {
        val container = findViewById<LinearLayout>(R.id.sensorStatusContainer)
        container.removeAllViews()

        val sensors = listOf(
            Triple("线性加速度", linearAccelSensor != null, speedCalculator.getLinearAccelMagnitude()),
            Triple("重力", gravitySensor != null, speedCalculator.getGravityMagnitude()),
            Triple("加速度计", accelerometerSensor != null, speedCalculator.getSpeed().acceleration),
            Triple("陀螺仪", gyroscopeSensor != null, speedCalculator.getGyroMagnitude()),
            Triple("磁力计", magnetometerSensor != null, speedCalculator.getMagMagnitude()),
            Triple("气压计", pressureSensor != null, speedCalculator.getSpeed().pressure),
            Triple("温度计", temperatureSensor != null, speedCalculator.getSpeed().temperature)
        )

        for ((name, available, value) in sensors) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 4)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val statusDot = TextView(this).apply {
                text = if (available) "\u25CF" else "\u25CB"
                textSize = 14f
                setTextColor(if (available) 0xFF4CAF50.toInt() else Color.GRAY)
                setPadding(0, 0, 6, 0)
            }

            val nameView = TextView(this).apply {
                text = name
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.primary_text))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val valueView = TextView(this).apply {
                textSize = 11f
                setTextColor(0xFF757575.toInt())
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            if (available && value != null) {
                val v = value as Float
                valueView.text = when (name) {
                    "线性加速度" -> String.format("%.2f m/s²", v)
                    "重力" -> String.format("%.2f m/s²", v)
                    "加速度计" -> String.format("%.2f m/s²", v)
                    "陀螺仪" -> String.format("%.2f rad/s", v)
                    "磁力计" -> String.format("%.1f μT", v)
                    "气压计" -> String.format("%.1f hPa", v)
                    "温度计" -> String.format("%.1f °C", v)
                    else -> ""
                }
            } else if (!available) {
                valueView.text = "不可用"
                valueView.setTextColor(Color.GRAY)
            }

            row.addView(statusDot)
            row.addView(nameView)
            row.addView(valueView)
            container.addView(row)
        }

        val statusText = findViewById<TextView>(R.id.sensorStatus)
        val availableCount = sensors.count { it.second }
        statusText.text = "$availableCount/${sensors.size} 传感器"
    }

    private fun resolveRegion(lat: Double, lon: Double) {
        Thread {
            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (addresses != null && addresses.isNotEmpty()) {
                    val addr = addresses[0]
                    val region = addr.adminArea ?: addr.countryName ?: "未知"
                    val subRegion = addr.subAdminArea ?: addr.locality ?: addr.thoroughfare ?: ""
                    currentRegion = region
                    currentSubRegion = subRegion
                    runOnUiThread {
                        val satellite3DView = findViewById<Satellite3DView>(R.id.satellite3DView)
                        satellite3DView.updateUserLocation(lat, lon, region, subRegion)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun fetchWeatherIfNeeded() {
        if (weatherFetchInProgress) return
        if (lastLocation == null) return

        val now = System.currentTimeMillis()
        val age = now - lastWeatherFetchTime
        if (age > 0 && age < WEATHER_CACHE_INTERVAL) return

        lastWeatherFetchTime = now
        weatherFetchInProgress = true
        fetchWeatherInfo(lastLocation!!.latitude, lastLocation!!.longitude)
    }

    private fun fetchWeatherInfo(lat: Double, lon: Double) {
        Thread {
            try {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&timezone=auto"
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                if (connection.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.use { it.readText() }
                    val json = JSONObject(response)
                    val current = json.getJSONObject("current")

                    val temp = current.getDouble("temperature_2m").toFloat()
                    val humidity = current.getInt("relative_humidity_2m")
                    val windSpeed = current.getDouble("wind_speed_10m").toFloat()
                    val weatherCode = current.getInt("weather_code")

                    val weatherDesc = getWeatherDescription(weatherCode)
                    val timezone = json.getString("timezone")
                    val tzShort = timezone.split("/").lastOrNull()?.replace("_", " ") ?: timezone

                    val weatherInfo = WeatherData(
                        weatherDesc = "$weatherDesc | 湿度${humidity}% | 风速${windSpeed}km/h",
                        temperature = String.format("%.1f", temp),
                        timezone = tzShort
                    )

                    runOnUiThread {
                        currentWeatherDesc = weatherInfo.weatherDesc
                        currentWeatherTemp = weatherInfo.temperature
                        updateWeatherInfoUI(weatherInfo)
                    }
                } else {
                    scheduleWeatherRetry()
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                scheduleWeatherRetry()
            }
        }.start()
    }

    private fun scheduleWeatherRetry() {
        Handler(Looper.getMainLooper()).postDelayed({
            weatherFetchInProgress = false
            if (lastLocation != null) {
                fetchWeatherIfNeeded()
            }
        }, WEATHER_RETRY_INTERVAL)
    }

    private fun getWeatherDescription(code: Int): String {
        return when (code) {
            0 -> "☀️ 晴朗"
            1, 2, 3 -> "⛅ 多云"
            45, 48 -> "🌫️ 雾"
            51, 53, 55 -> "🌦️ 毛毛雨"
            61, 63, 65 -> "🌧️ 雨"
            66, 67 -> "🌨️ 冻雨"
            71, 73, 75 -> "❄️ 雪"
            77 -> "🌨️ 雪粒"
            80, 81, 82 -> "🌧️ 阵雨"
            85, 86 -> "🌨️ 阵雪"
            95 -> "⛈️ 雷暴"
            96, 99 -> "⛈️ 雷暴冰雹"
            else -> "🌤️ 未知"
        }
    }

    private fun updateWeatherInfoUI(weather: WeatherData) {
        val container = findViewById<LinearLayout>(R.id.weatherInfoContainer)
        container.removeAllViews()

        val tz = TimeZone.getDefault()
        val tzName = tz.displayName
        val now = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).apply { timeZone = tz }
        val currentTime = now.format(Date())

        val addressText = if (currentRegion.isNotEmpty()) {
            if (currentSubRegion.isNotEmpty()) "$currentRegion $currentSubRegion" else currentRegion
        } else { "获取中..." }

        val rows = listOf(
            Triple("地址", addressText, 0xFFFF5722.toInt()),
            Triple("温度", "${weather.temperature}°C", 0xFFFF9800.toInt()),
            Triple("天气", weather.weatherDesc, 0xFF2196F3.toInt()),
            Triple("时区", "$tzName", 0xFF757575.toInt()),
            Triple("本地时间", currentTime, 0xFF4CAF50.toInt())
        )

        for ((label, value, color) in rows) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 4)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val labelView = TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(0xFF757575.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val valueView = TextView(this).apply {
                text = value
                textSize = 12f
                setTextColor(color)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            row.addView(labelView)
            row.addView(valueView)
            container.addView(row)
        }

        findViewById<TextView>(R.id.weatherStatus).text = "已更新"
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
            startSensorListeners()
        }
    }

    override fun onPause() {
        super.onPause()
        stopAutoRefresh()
        stopSensorListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoRefresh()
        stopSensorListeners()
        try {
            gnssStatusCallback?.let { locationManager?.unregisterGnssStatusCallback(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    data class WeatherData(
        val weatherDesc: String,
        val temperature: String,
        val timezone: String
    )
}
