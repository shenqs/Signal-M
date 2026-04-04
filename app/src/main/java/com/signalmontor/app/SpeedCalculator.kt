package com.signalmontor.app

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import kotlin.math.*

class SpeedCalculator {

    companion object {
        private const val GRAVITY = 9.80665f
        private const val ACCEL_WINDOW_SIZE = 40
        private const val SEA_LEVEL_PRESSURE_HPA = 1013.25f
        private const val TEMPERATURE_SMOOTH_ALPHA = 0.2f

        private const val SPEED_SMOOTH_ALPHA = 0.08f
        private const val BEARING_SMOOTH_ALPHA = 0.05f
        private const val ACCEL_SMOOTH_ALPHA = 0.04f
        private const val GPS_ACCURACY_GOOD = 20f
        private const val GPS_ACCURACY_OK = 50f
        private const val GPS_FIX_TIMEOUT = 10000L
        private const val DISPLAY_SPEED_MAX = 1000f
        private const val GPS_SPEED_MAX_MS = 278f
        private const val SPEED_DIFF_THRESHOLD = 150f
        private const val MAX_DELTA_PER_CYCLE = 50f
        private const val STEP_LENGTH_M = 0.75f
        private const val STEP_DETECT_THRESHOLD = 1.5f
        private const val STEP_MIN_INTERVAL_MS = 180L
        private const val STEP_FREQ_DECAY_ALPHA = 0.02f

        // Low-pass filter coefficients for compass
        private const val MAG_LOW_PASS_ALPHA = 0.2f
        private const val ACCEL_LOW_PASS_ALPHA = 0.1f
    }

    // Pressure Kalman filter
    private var kalmanP = 0f
    private var kalmanX = 0f
    private var kalmanR = 0.1f
    private var kalmanQ = 0.001f
    private var kalmanReady = false

    private var basePressure = 0f
    private var baseAltitude = 0f
    private var baseAltitudeSet = false
    private var gpsAltitudeSampleCount = 0

    private var lastUpdate = 0L

    // Accelerometer
    private var gravity = floatArrayOf(0f, 0f, 0f)
    private var linearAcceleration = floatArrayOf(0f, 0f, 0f)
    private val accelHistory = mutableListOf<Float>()
    private var smoothedAcceleration = 0f
    private var currentAcceleration = 0f

    // Compass - sensor fusion state
    private var currentBearing = 0f
    private var gpsBearing = 0f
    private var gpsAcceleration = 0f
    private var lastGpsSpeedForAccel = 0f
    private var lastGpsSpeedTime = 0L

    // Step detection
    private var stepCount = 0
    private var lastStepTime = 0L
    private var stepFrequency = 0f
    private var estimatedStepSpeed = 0f
    private val stepIntervalHistory = mutableListOf<Float>()
    private val STEP_HISTORY_MAX = 5
    private var smoothedStepFrequency = 0f
    private var lastPeakAccel = 0f
    private var lastStepDirection = 0f

    // Sensor fusion: accelerometer + magnetometer
    private var magneticFieldValues = floatArrayOf(0f, 0f, 0f)
    private var accelerometerValues = floatArrayOf(0f, 0f, 0f)
    private var filteredMag = floatArrayOf(0f, 0f, 0f)
    private var filteredAccel = floatArrayOf(0f, 0f, 0f)
    private var rotationMatrix = FloatArray(9)
    private var inclinationMatrix = FloatArray(9)
    private var orientationAngles = FloatArray(3)

    // GPS speed
    private var gpsSpeed = 0f
    private var gpsAccuracy = 0f
    private var lastGpsSpeed = 0f
    private var displaySpeed = 0f
    private var displayBearing = 0f
    private var displayAcceleration = 0f

    private var hasGpsFix = false
    private var lastGpsUpdateTime = 0L

    // Pressure / altitude
    private var currentPressure = 0f
    private var smoothedPressure = 0f
    private var currentTemperature = 0f
    private var smoothedTemperature = 0f
    private var currentAltitude = 0f
    private var smoothedAltitude = 0f
    private var gpsAltitude = 0f
    private var maxAltitude = Float.MIN_VALUE
    private var minAltitude = Float.MAX_VALUE
    private var altitudeChangeRate = 0f
    private var lastAltitude = 0f
    private var lastAltitudeTime = 0L
    private var gravityMagnitude = GRAVITY
    private var hasBarometer = false
    private var pressureHistory = mutableListOf<Float>()
    private var altitudeHistory = mutableListOf<Float>()
    private val ALTITUDE_HISTORY_MAX = 60

    // Gyroscope (for display only)
    private var gyroX = 0f
    private var gyroY = 0f
    private var gyroZ = 0f
    private var magX = 0f
    private var magY = 0f
    private var magZ = 0f

    // Magnetic declination for true north
    private var magneticDeclination = 0f
    private var magSensorAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
    private var lastLocationLat = 0.0
    private var lastLocationLon = 0.0

    fun setMagneticDeclination(declination: Float) {
        magneticDeclination = declination
    }

    fun updateMagneticDeclination(lat: Double, lon: Double, altMeters: Float) {
        lastLocationLat = lat
        lastLocationLon = lon
        try {
            val geoField = GeomagneticField(
                lat.toFloat(),
                lon.toFloat(),
                altMeters,
                System.currentTimeMillis()
            )
            magneticDeclination = geoField.declination
        } catch (e: Exception) {
            // Ignore, use previous declination
        }
    }

    fun setLocation(lat: Double, lon: Double) {
        lastLocationLat = lat
        lastLocationLon = lon
    }

    fun getMagSensorAccuracy(): Int = magSensorAccuracy

    fun onMagAccuracyChanged(accuracy: Int) {
        magSensorAccuracy = accuracy
    }

    fun processGravity(event: SensorEvent) {
        gravity[0] = event.values[0]
        gravity[1] = event.values[1]
        gravity[2] = event.values[2]

        // Update accelerometer values for compass rotation matrix
        accelerometerValues[0] = gravity[0]
        accelerometerValues[1] = gravity[1]
        accelerometerValues[2] = gravity[2]

        updateCompassBearing()
    }

    fun processLinearAcceleration(event: SensorEvent) {
        linearAcceleration[0] = event.values[0]
        linearAcceleration[1] = event.values[1]
        linearAcceleration[2] = event.values[2]

        val rawMag = sqrt(
            linearAcceleration[0] * linearAcceleration[0] +
            linearAcceleration[1] * linearAcceleration[1] +
            linearAcceleration[2] * linearAcceleration[2]
        )

        accelHistory.add(rawMag)
        if (accelHistory.size > ACCEL_WINDOW_SIZE) {
            accelHistory.removeAt(0)
        }

        val avgAccel = if (accelHistory.size >= ACCEL_WINDOW_SIZE / 2) {
            accelHistory.average().toFloat()
        } else {
            rawMag
        }

        if (smoothedAcceleration == 0f) {
            smoothedAcceleration = avgAccel
        } else {
            smoothedAcceleration = smoothedAcceleration + ACCEL_SMOOTH_ALPHA * (avgAccel - smoothedAcceleration)
        }

        currentAcceleration = avgAccel * GRAVITY

        val now = event.timestamp / 1_000_000
        lastUpdate = now

        detectStep(avgAccel, now)
    }

    private fun detectStep(avgAccel: Float, @Suppress("UNUSED_PARAMETER") now: Long) {
        val nowMs = System.currentTimeMillis()
        val timeSinceLastStep = nowMs - lastStepTime

        // Peak detection: look for acceleration peaks above threshold
        val isPeak = avgAccel > STEP_DETECT_THRESHOLD && avgAccel > lastPeakAccel
        val minInterval = timeSinceLastStep > STEP_MIN_INTERVAL_MS

        if (isPeak && minInterval) {
            // Check for direction change to avoid double-counting
            if (lastStepTime > 0) {
                val stepInterval = timeSinceLastStep / 1000f
                if (stepInterval > 0.3f && stepInterval < 3.0f) {
                    stepIntervalHistory.add(stepInterval)
                    if (stepIntervalHistory.size > STEP_HISTORY_MAX) {
                        stepIntervalHistory.removeAt(0)
                    }

                    val avgInterval = stepIntervalHistory.average().toFloat()
                    smoothedStepFrequency = 1f / avgInterval
                    stepFrequency = smoothedStepFrequency

                    val dynamicStepLength = getDynamicStepLength(smoothedStepFrequency)
                    estimatedStepSpeed = smoothedStepFrequency * dynamicStepLength
                }
            }

            stepCount++
            lastStepTime = nowMs
            lastPeakAccel = 0f
        } else {
            lastPeakAccel = maxOf(lastPeakAccel, avgAccel)

            // Decay step frequency when no new steps detected
            if (timeSinceLastStep > 2000) {
                stepFrequency *= (1f - STEP_FREQ_DECAY_ALPHA)
                if (stepFrequency < 0.1f) {
                    stepFrequency = 0f
                    estimatedStepSpeed = 0f
                    stepIntervalHistory.clear()
                }
            }
        }
    }

    private fun getDynamicStepLength(freq: Float): Float {
        return when {
            freq < 1.2f -> 0.55f   // 慢走
            freq < 1.6f -> 0.65f   // 正常步行
            freq < 2.0f -> 0.75f   // 快走
            freq < 2.5f -> 0.95f   // 慢跑
            freq < 3.0f -> 1.10f   // 跑步
            else -> 1.20f           // 快速跑
        }
    }

    fun processAccelerometer(event: SensorEvent) {
        // Low-pass filter accelerometer data to reduce jitter
        filteredAccel[0] = lowPass(event.values[0], filteredAccel[0], ACCEL_LOW_PASS_ALPHA)
        filteredAccel[1] = lowPass(event.values[1], filteredAccel[1], ACCEL_LOW_PASS_ALPHA)
        filteredAccel[2] = lowPass(event.values[2], filteredAccel[2], ACCEL_LOW_PASS_ALPHA)

        accelerometerValues[0] = filteredAccel[0]
        accelerometerValues[1] = filteredAccel[1]
        accelerometerValues[2] = filteredAccel[2]

        gravity[0] = filteredAccel[0]
        gravity[1] = filteredAccel[1]
        gravity[2] = filteredAccel[2]

        val linearX = event.values[0] - filteredAccel[0]
        val linearY = event.values[1] - filteredAccel[1]
        val linearZ = event.values[2] - filteredAccel[2]
        linearAcceleration[0] = linearX
        linearAcceleration[1] = linearY
        linearAcceleration[2] = linearZ

        val rawMag = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)

        accelHistory.add(rawMag)
        if (accelHistory.size > ACCEL_WINDOW_SIZE) {
            accelHistory.removeAt(0)
        }

        val avgAccel = if (accelHistory.size >= ACCEL_WINDOW_SIZE / 2) {
            accelHistory.average().toFloat()
        } else {
            rawMag
        }

        if (smoothedAcceleration == 0f) {
            smoothedAcceleration = avgAccel
        } else {
            smoothedAcceleration = smoothedAcceleration + ACCEL_SMOOTH_ALPHA * (avgAccel - smoothedAcceleration)
        }

        currentAcceleration = avgAccel * GRAVITY

        val now = event.timestamp / 1_000_000
        lastUpdate = now

        detectStep(avgAccel, now)

        // Update compass bearing when we have both accel and mag data
        updateCompassBearing()
    }

    fun processGyroscope(event: SensorEvent) {
        gyroX = event.values[0]
        gyroY = event.values[1]
        gyroZ = event.values[2]
    }

    fun processMagneticField(event: SensorEvent) {
        // Low-pass filter magnetometer data to reduce jitter
        filteredMag[0] = lowPass(event.values[0], filteredMag[0], MAG_LOW_PASS_ALPHA)
        filteredMag[1] = lowPass(event.values[1], filteredMag[1], MAG_LOW_PASS_ALPHA)
        filteredMag[2] = lowPass(event.values[2], filteredMag[2], MAG_LOW_PASS_ALPHA)

        magX = filteredMag[0]
        magY = filteredMag[1]
        magZ = filteredMag[2]
        magneticFieldValues[0] = filteredMag[0]
        magneticFieldValues[1] = filteredMag[1]
        magneticFieldValues[2] = filteredMag[2]

        // Update compass bearing when we have both accel and mag data
        updateCompassBearing()
    }

    /**
     * Sensor fusion: combines accelerometer (gravity direction) and magnetometer (magnetic north)
     * to compute the device's orientation relative to Earth's coordinate system.
     *
     * Uses Android's built-in SensorManager methods:
     * 1. getRotationMatrix() - computes rotation matrix from device to Earth coordinates
     * 2. getOrientation() - extracts azimuth, pitch, roll from the rotation matrix
     * 3. Applies magnetic declination to convert magnetic north to true north
     */
    private fun updateCompassBearing() {
        // Compute rotation matrix from accelerometer + magnetometer
        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            inclinationMatrix,
            accelerometerValues,
            magneticFieldValues
        )

        if (success) {
            // Extract orientation angles: azimuth (bearing), pitch, roll
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            // Convert azimuth from radians to degrees
            var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()

            // Apply magnetic declination to get true north
            azimuth += magneticDeclination

            // Normalize to 0-360 range
            if (azimuth < 0f) azimuth += 360f
            if (azimuth >= 360f) azimuth -= 360f

            currentBearing = azimuth
        }
    }

    fun getGyroMagnitude(): Float = sqrt(gyroX * gyroX + gyroY * gyroY + gyroZ * gyroZ)
    fun getMagMagnitude(): Float = sqrt(magX * magX + magY * magY + magZ * magZ)
    fun getGravityMagnitude(): Float = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2])
    fun getLinearAccelMagnitude(): Float = sqrt(linearAcceleration[0] * linearAcceleration[0] + linearAcceleration[1] * linearAcceleration[1] + linearAcceleration[2] * linearAcceleration[2])

    fun processPressure(event: SensorEvent) {
        hasBarometer = true
        val rawPressure = event.values[0]
        currentPressure = rawPressure

        smoothedPressure = kalmanFilter(rawPressure)

        if (gpsAltitude != 0f && gpsAltitudeSampleCount >= 5 && !baseAltitudeSet) {
            baseAltitudeSet = true
            basePressure = smoothedPressure
            baseAltitude = gpsAltitude
        }

        pressureHistory.add(smoothedPressure)
        if (pressureHistory.size > ALTITUDE_HISTORY_MAX) {
            pressureHistory.removeAt(0)
        }

        calculateAltitude()
    }

    private fun kalmanFilter(measurement: Float): Float {
        if (!kalmanReady) {
            kalmanX = measurement
            kalmanP = 1f
            kalmanReady = true
            return measurement
        }

        kalmanP += kalmanQ

        val k = kalmanP / (kalmanP + kalmanR)
        kalmanX += k * (measurement - kalmanX)
        kalmanP *= (1f - k)

        return kalmanX
    }

    fun processTemperature(event: SensorEvent) {
        val temp = event.values[0]
        currentTemperature = temp
        smoothedTemperature = if (smoothedTemperature == 0f) {
            temp
        } else {
            smoothedTemperature + TEMPERATURE_SMOOTH_ALPHA * (temp - smoothedTemperature)
        }
    }

    fun updateGpsAltitude(altitudeM: Float) {
        if (altitudeM < -500f || altitudeM > 10000f) return
        gpsAltitude = altitudeM
        gpsAltitudeSampleCount++

        if (gpsAltitudeSampleCount >= 5 && !baseAltitudeSet) {
            baseAltitudeSet = true
            basePressure = smoothedPressure
            baseAltitude = gpsAltitude
        }

        if (!hasBarometer) {
            smoothedAltitude = if (smoothedAltitude == 0f) altitudeM else {
                smoothedAltitude + 0.15f * (altitudeM - smoothedAltitude)
            }
            currentAltitude = smoothedAltitude
            updateAltitudeStats(smoothedAltitude)
        }
    }

    private fun calculateAltitude() {
        val rawAltitude = pressureToAltitude(smoothedPressure)

        val calibratedAltitude = if (baseAltitudeSet) {
            val deltaAltitude = rawAltitude - pressureToAltitude(basePressure)
            baseAltitude + deltaAltitude
        } else {
            rawAltitude
        }

        currentAltitude = calibratedAltitude

        if (smoothedAltitude == 0f) {
            smoothedAltitude = calibratedAltitude
        } else {
            smoothedAltitude = smoothedAltitude + 0.15f * (calibratedAltitude - smoothedAltitude)
        }

        if (hasBarometer && gpsAltitude != 0f && baseAltitudeSet) {
            val drift = smoothedAltitude - gpsAltitude
            smoothedAltitude -= drift * 0.05f
        }

        updateAltitudeStats(smoothedAltitude)

        altitudeHistory.add(smoothedAltitude)
        if (altitudeHistory.size > ALTITUDE_HISTORY_MAX) {
            altitudeHistory.removeAt(0)
        }

        if (altitudeHistory.size >= 2) {
            val now = System.currentTimeMillis()
            if (lastAltitudeTime > 0) {
                val dt = (now - lastAltitudeTime) / 1000f
                if (dt > 0) {
                    altitudeChangeRate = (smoothedAltitude - lastAltitude) / dt
                }
            }
            lastAltitude = smoothedAltitude
            lastAltitudeTime = now
        }

        gravityMagnitude = calculateGravityMagnitude()
    }

    private fun pressureToAltitude(pressureHpa: Float): Float {
        val t = if (smoothedTemperature != 0f) smoothedTemperature else 15f
        val tKelvin = t + 273.15f
        val l = 0.0065f
        val r = 287.05f
        val g = 9.80665f
        val p0 = SEA_LEVEL_PRESSURE_HPA
        return (tKelvin / l) * (1f - (pressureHpa / p0).toDouble().pow(((l * r) / g).toDouble())).toFloat()
    }

    private fun calculateGravityMagnitude(): Float {
        val magnitude = sqrt(
            gravity[0] * gravity[0] +
            gravity[1] * gravity[1] +
            gravity[2] * gravity[2]
        )
        return if (magnitude > 0) magnitude else GRAVITY
    }

    private fun updateAltitudeStats(altitude: Float) {
        if (altitude > maxAltitude) maxAltitude = altitude
        if (altitude < minAltitude) minAltitude = altitude
    }

    fun getSeaLevelPressure(): Float {
        return if (pressureHistory.isNotEmpty()) {
            pressureHistory.average().toFloat()
        } else {
            SEA_LEVEL_PRESSURE_HPA
        }
    }

    fun updateGpsSpeed(speedMps: Float, accuracy: Float) {
        if (speedMps < 0f || speedMps > 50f) {
            hasGpsFix = false
            lastGpsUpdateTime = System.currentTimeMillis()
            return
        }

        // Ignore GPS drift when stationary: require minimum 1.5 m/s (5.4 km/h)
        val minGpsSpeed = 1.5f
        val isStationary = speedMps < minGpsSpeed || accuracy > 30f

        val gpsSpeedKmh = speedMps * 3.6f
        val diff = abs(gpsSpeedKmh - displaySpeed)
        val maxAllowedDiff = maxOf(10f, displaySpeed * 0.15f)

        lastGpsUpdateTime = System.currentTimeMillis()

        if (isStationary) {
            // GPS reports stationary - don't update speed value, just mark GPS as not providing valid speed
            hasGpsFix = false
            return
        }

        if (diff > maxAllowedDiff) {
            hasGpsFix = accuracy < GPS_ACCURACY_OK
            return
        }

        gpsSpeed = speedMps
        gpsAccuracy = accuracy
        lastGpsSpeed = speedMps
        hasGpsFix = accuracy < GPS_ACCURACY_OK

        if (hasGpsFix) {
            val nowMs = System.currentTimeMillis()
            if (lastGpsSpeedTime > 0) {
                val dt = (nowMs - lastGpsSpeedTime) / 1000f
                if (dt > 0.5f && dt < 30f) {
                    val rawAccel = (speedMps - lastGpsSpeedForAccel) / dt
                    gpsAcceleration = rawAccel.coerceIn(-10f, 10f)
                }
            }
            lastGpsSpeedForAccel = speedMps
            lastGpsSpeedTime = nowMs
        }
    }

    fun updateGpsBearing(bearing: Float) {
        if (hasGpsFix) {
            gpsBearing = bearing
        }
    }

    fun updateLocationSpeed(@Suppress("UNUSED_PARAMETER") speedMps: Float) {
        // All speed calculation handled in getSpeed()
    }

    fun getSpeed(): SpeedData {
        val gpsAge = System.currentTimeMillis() - lastGpsUpdateTime
        val gpsValid = hasGpsFix && gpsAge < GPS_FIX_TIMEOUT

        val gpsSpeedKmh = if (gpsValid) gpsSpeed * 3.6f else -1f

        var rawSpeedKmh = 0f
        if (gpsValid && gpsSpeedKmh >= 0f && gpsSpeedKmh < DISPLAY_SPEED_MAX) {
            // GPS deceleration: always trust GPS when it reports lower speed
            if (gpsSpeedKmh <= displaySpeed) {
                rawSpeedKmh = gpsSpeedKmh
            } else {
                // GPS acceleration: only accept if within reasonable range
                val speedDiff = gpsSpeedKmh - displaySpeed
                val maxAllowedDiff = maxOf(15f, displaySpeed * 0.2f)
                if (speedDiff < maxAllowedDiff) {
                    rawSpeedKmh = gpsSpeedKmh
                } else {
                    rawSpeedKmh = displaySpeed
                }
            }
        } else if (stepFrequency > 0.5f) {
            rawSpeedKmh = estimatedStepSpeed * 3.6f
        } else {
            rawSpeedKmh = 0f
        }

        val effectiveBearing = if (gpsValid && gpsBearing > 0f) gpsBearing else currentBearing
        val effectiveAccel = if (gpsValid) gpsAcceleration else smoothedAcceleration * GRAVITY

        val prevDisplaySpeed = displaySpeed

        if (gpsValid) {
            // GPS deceleration: very fast response (alpha=0.5)
            val smoothingAlpha = if (rawSpeedKmh < displaySpeed) 0.5f else SPEED_SMOOTH_ALPHA
            displaySpeed = displaySpeed + smoothingAlpha * (rawSpeedKmh - displaySpeed)
            displaySpeed = displaySpeed.coerceIn(prevDisplaySpeed - 50f, prevDisplaySpeed + 15f)
        } else {
            // No GPS: fast decay to zero
            displaySpeed = displaySpeed * 0.5f
            displaySpeed = displaySpeed.coerceIn(0f, DISPLAY_SPEED_MAX)
        }
        displaySpeed = displaySpeed.coerceIn(0f, DISPLAY_SPEED_MAX)

        if (displaySpeed < 0.5f) displaySpeed = 0f
        displayBearing = smoothBearing(displayBearing, effectiveBearing, BEARING_SMOOTH_ALPHA)
        displayAcceleration = displayAcceleration + 0.05f * (effectiveAccel - displayAcceleration)

        val speedKmh = displaySpeed.coerceAtLeast(0f)
        val totalDistanceKm = (stepCount * STEP_LENGTH_M) / 1000f

        val direction = getDirectionLabel(displayBearing)
        val movementState = getMovementState(speedKmh, displayAcceleration)
        val confidence = calculateConfidence(gpsValid)
        val altitudeQuality = calculateAltitudeQuality()

        val altitudeDesc = getAltitudeDescription(smoothedAltitude)
        val speedDesc = getSpeedDescription(speedKmh)
        val accelDesc = getAccelDescription(displayAcceleration)

        return SpeedData(
            speed = speedKmh,
            speedMs = if (gpsValid) gpsSpeed else estimatedStepSpeed,
            maxSpeed = speedKmh,
            acceleration = displayAcceleration,
            direction = direction,
            bearing = displayBearing,
            totalDistance = totalDistanceKm,
            stepCount = stepCount,
            stepFrequency = stepFrequency,
            movementState = movementState,
            confidence = confidence,
            gpsAccuracy = gpsAccuracy,
            altitude = smoothedAltitude,
            gpsAltitude = gpsAltitude,
            maxAltitude = if (maxAltitude == Float.MIN_VALUE) 0f else maxAltitude,
            minAltitude = if (minAltitude == Float.MAX_VALUE) 0f else minAltitude,
            altitudeChangeRate = altitudeChangeRate,
            pressure = smoothedPressure,
            temperature = smoothedTemperature,
            gravityMagnitude = gravityMagnitude,
            hasBarometer = hasBarometer,
            altitudeQuality = altitudeQuality,
            altitudeDescription = altitudeDesc,
            speedDescription = speedDesc,
            accelDescription = accelDesc
        )
    }

    private fun smoothBearing(current: Float, target: Float, alpha: Float): Float {
        if (target <= 0f) return current
        var diff = target - current
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        var result = current + alpha * diff
        if (result < 0f) result += 360f
        if (result >= 360f) result -= 360f
        return result
    }

    private fun getAltitudeDescription(altitude: Float): String {
        return when {
            altitude < -10f -> "死海以下 (海平面下)"
            altitude < 0f -> "海平面以下"
            altitude < 3f -> "海平面附近 (沙滩/码头)"
            altitude < 10f -> "约${(altitude / 3.0f).toInt()}层楼高"
            altitude < 30f -> "约${(altitude / 3.0f).toInt()}层住宅楼"
            altitude < 50f -> "约${(altitude / 3.0f).toInt()}层高楼"
            altitude < 80f -> "约${(altitude / 3.0f).toInt()}层, 如普通写字楼"
            altitude < 100f -> "约${(altitude / 3.0f).toInt()}层, 如高层住宅"
            altitude < 150f -> "约${(altitude / 3.0f).toInt()}层, 如上海金茂大厦低区"
            altitude < 200f -> "约${(altitude / 3.0f).toInt()}层, 如深圳地王大厦"
            altitude < 300f -> "约${(altitude / 3.0f).toInt()}层, 如东方明珠塔"
            altitude < 400f -> "约${(altitude / 3.0f).toInt()}层, 如上海中心大厦"
            altitude < 500f -> "约${(altitude / 3.0f).toInt()}层, 如广州塔基座"
            altitude < 600f -> "约${(altitude / 3.0f).toInt()}层, 超高建筑群"
            altitude < 800f -> "小山丘高度"
            altitude < 1000f -> "低山区域"
            altitude < 1500f -> "中山区域, 如庐山"
            altitude < 2000f -> "高山区域, 如黄山"
            altitude < 2500f -> "高海拔, 如丽江古城"
            altitude < 3000f -> "超高海拔, 如稻城亚丁"
            altitude < 3500f -> "极高海拔, 如拉萨"
            altitude < 4000f -> "接近雪线, 如珠峰大本营"
            altitude < 5000f -> "雪线以上, 如珠穆朗玛峰前进营地"
            altitude < 6000f -> "极端高海拔, 如慕士塔格峰"
            altitude < 7000f -> "死亡地带边缘"
            altitude < 8000f -> "死亡地带, 接近珠峰顶"
            altitude < 8849f -> "珠峰级别"
            else -> "超越珠峰"
        }
    }

    private fun getSpeedDescription(speedKmh: Float): String {
        return when {
            speedKmh < 0.5f -> "静止状态"
            speedKmh < 3f -> "缓慢步行"
            speedKmh < 6f -> "正常步行 (~5km/h)"
            speedKmh < 8f -> "快走"
            speedKmh < 12f -> "慢跑"
            speedKmh < 16f -> "跑步"
            speedKmh < 20f -> "快速奔跑"
            speedKmh < 25f -> "自行车 (休闲)"
            speedKmh < 35f -> "自行车 (运动)"
            speedKmh < 45f -> "电动自行车"
            speedKmh < 60f -> "城市机动车"
            speedKmh < 80f -> "国道机动车"
            speedKmh < 100f -> "高速公路 (普通)"
            speedKmh < 120f -> "高速公路 (快速)"
            speedKmh < 160f -> "普通铁路 (慢车)"
            speedKmh < 200f -> "快速铁路 / 动车"
            speedKmh < 250f -> "高铁 (G字头)"
            speedKmh < 300f -> "高铁 (复兴号)"
            speedKmh < 350f -> "高铁 (运营极速)"
            speedKmh < 430f -> "磁悬浮列车 (上海磁浮)"
            speedKmh < 500f -> "高速磁悬浮"
            speedKmh < 600f -> "螺旋桨飞机 (起飞)"
            speedKmh < 800f -> "民航客机 (巡航)"
            speedKmh < 900f -> "民航客机 (高速巡航)"
            speedKmh < 1000f -> "超音速飞机 (亚音速)"
            speedKmh < 1235f -> "接近音速"
            speedKmh < 1500f -> "超音速 (战斗机)"
            speedKmh < 2000f -> "高超音速"
            speedKmh < 5000f -> "火箭 (上升段)"
            speedKmh < 8000f -> "近地轨道速度"
            speedKmh < 11200f -> "第二宇宙速度 (逃逸地球)"
            speedKmh < 28000f -> "星际航行速度"
            else -> "接近光速..."
        }
    }

    private fun getAccelDescription(accel: Float): String {
        val absAccel = abs(accel)
        return when {
            absAccel < 0.3f -> "几乎无加速度 (静止/匀速)"
            absAccel < 0.8f -> "轻微晃动 (手持)"
            absAccel < 1.5f -> "缓慢起步 (电梯启动)"
            absAccel < 3.0f -> "汽车正常加速"
            absAccel < 5.0f -> "汽车急加速 / 电梯快速升降"
            absAccel < 8.0f -> "强烈加速 (跑车起步)"
            absAccel < 10.0f -> "约1G加速度"
            absAccel < 15.0f -> "约1.5G (过山车级别)"
            absAccel < 20.0f -> "约2G (战斗机机动)"
            absAccel < 30.0f -> "约3G (赛车过弯)"
            absAccel < 50.0f -> "约5G (飞行员极限)"
            else -> "极端加速度 (火箭发射)"
        }
    }

    private fun getDirectionLabel(bearing: Float): String {
        return when (bearing) {
            in 0f..22.5f -> "北"
            in 22.5f..67.5f -> "东北"
            in 67.5f..112.5f -> "东"
            in 112.5f..157.5f -> "东南"
            in 157.5f..202.5f -> "南"
            in 202.5f..247.5f -> "西南"
            in 247.5f..292.5f -> "西"
            in 292.5f..337.5f -> "西北"
            in 337.5f..360f -> "北"
            else -> "未知"
        }
    }

    private fun getMovementState(speedKmh: Float, accel: Float): MovementState {
        return when {
            speedKmh < 1f && abs(accel) < 1.0f -> MovementState.STATIONARY
            speedKmh < 6f -> MovementState.WALKING
            speedKmh < 20f -> MovementState.RUNNING
            speedKmh < 50f -> MovementState.CYCLING
            speedKmh < 120f -> MovementState.DRIVING
            else -> MovementState.HIGH_SPEED
        }
    }

    private fun calculateConfidence(gpsValid: Boolean): Float {
        if (gpsValid && gpsAccuracy < GPS_ACCURACY_GOOD) return 0.95f
        if (gpsValid && gpsAccuracy < GPS_ACCURACY_OK) return 0.8f
        if (gpsValid) return 0.6f
        return 0.3f
    }

    private fun calculateAltitudeQuality(): Float {
        var quality = 0.3f
        if (hasBarometer) {
            quality += 0.4f
            if (pressureHistory.size >= ALTITUDE_HISTORY_MAX / 2) quality += 0.15f
        }
        if (gpsAltitude != 0f) quality += 0.15f
        if (gravityMagnitude > 0) quality += 0.1f
        return minOf(1f, quality)
    }

    private fun lowPass(current: Float, previous: Float, alpha: Float): Float {
        return previous + alpha * (current - previous)
    }

    fun reset() {
        lastUpdate = 0L
        gravity = floatArrayOf(0f, 0f, 0f)
        linearAcceleration = floatArrayOf(0f, 0f, 0f)
        accelHistory.clear()
        smoothedAcceleration = 0f
        currentAcceleration = 0f
        currentBearing = 0f
        gpsBearing = 0f
        gpsAcceleration = 0f
        lastGpsSpeedForAccel = 0f
        lastGpsSpeedTime = 0L
        stepCount = 0
        lastStepTime = 0L
        stepFrequency = 0f
        estimatedStepSpeed = 0f
        stepIntervalHistory.clear()
        smoothedStepFrequency = 0f
        lastPeakAccel = 0f
        lastStepDirection = 0f
        gpsSpeed = 0f
        gpsAccuracy = 0f
        lastGpsSpeed = 0f
        displaySpeed = 0f
        displayBearing = 0f
        displayAcceleration = 0f
        hasGpsFix = false
        lastGpsUpdateTime = 0L
        currentPressure = 0f
        smoothedPressure = 0f
        currentTemperature = 0f
        smoothedTemperature = 0f
        currentAltitude = 0f
        smoothedAltitude = 0f
        gpsAltitude = 0f
        maxAltitude = Float.MIN_VALUE
        minAltitude = Float.MAX_VALUE
        altitudeChangeRate = 0f
        lastAltitude = 0f
        lastAltitudeTime = 0L
        gravityMagnitude = GRAVITY
        hasBarometer = false
        pressureHistory.clear()
        altitudeHistory.clear()
        kalmanReady = false
        kalmanP = 0f
        kalmanX = 0f
        basePressure = 0f
        baseAltitude = 0f
        baseAltitudeSet = false
        gpsAltitudeSampleCount = 0
    }

    data class SpeedData(
        val speed: Float,
        val speedMs: Float,
        val maxSpeed: Float,
        val acceleration: Float,
        val direction: String,
        val bearing: Float,
        val totalDistance: Float,
        val stepCount: Int,
        val stepFrequency: Float,
        val movementState: MovementState,
        val confidence: Float,
        val gpsAccuracy: Float,
        val altitude: Float,
        val gpsAltitude: Float,
        val maxAltitude: Float,
        val minAltitude: Float,
        val altitudeChangeRate: Float,
        val pressure: Float,
        val temperature: Float,
        val gravityMagnitude: Float,
        val hasBarometer: Boolean,
        val altitudeQuality: Float,
        val altitudeDescription: String,
        val speedDescription: String,
        val accelDescription: String
    )
}

enum class MovementState(val label: String, val icon: String, val color: Int) {
    STATIONARY("静止", "\uD83E\uDDD8", 0xFF9E9E9E.toInt()),
    WALKING("步行", "\uD83D\uDEB6", 0xFF4CAF50.toInt()),
    RUNNING("跑步", "\uD83C\uDFC3", 0xFFFF9800.toInt()),
    CYCLING("骑行", "\uD83D\uDEB2", 0xFF2196F3.toInt()),
    DRIVING("驾驶", "\uD83D\uDE97", 0xFF9C27B0.toInt()),
    HIGH_SPEED("高速", "\u26A1", 0xFFF44336.toInt())
}
