package com.signalmontor.app

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import kotlin.math.*

enum class StepState {
    IDLE,
    PEAK_DETECTED,
    VALLEY_DETECTED
}

class SpeedCalculator {

    companion object {
        private const val GRAVITY = 9.80665f
        private const val ACCEL_WINDOW_SIZE = 50
        private const val SEA_LEVEL_PRESSURE_HPA = 1013.25f
        private const val TEMPERATURE_SMOOTH_ALPHA = 0.2f
        private const val SPEED_SMOOTH_ALPHA = 0.08f
        private const val BEARING_SMOOTH_ALPHA = 0.05f
        private const val ACCEL_SMOOTH_ALPHA = 0.04f
        private const val GPS_ACCURACY_GOOD = 20f
        private const val GPS_ACCURACY_OK = 50f
        private const val GPS_FIX_TIMEOUT = 10000L
        private const val DISPLAY_SPEED_MAX = 1000f
        private const val GPS_SPEED_MAX_MS = 150f
        
        private const val STEP_MIN_INTERVAL_MS = 200L
        private const val STEP_MAX_INTERVAL_MS = 2000L
        private const val STEP_WINDOW_SIZE = 50
        private const val MIN_CONSECUTIVE_STEPS = 5
        private const val WALK_FUSION_SPEED_THRESHOLD = 5f
        private const val STEP_FREQ_DECAY_ALPHA = 0.02f
        private const val STEP_FREQ_SMOOTH_ALPHA = 0.25f
        private const val STEP_SPEED_KALMAN_Q = 0.1f
        private const val STEP_SPEED_KALMAN_R = 2.0f
        private const val MAG_LOW_PASS_ALPHA = 0.2f
        private const val ACCEL_LOW_PASS_ALPHA = 0.15f
    }

    private var kalmanP = 0f; private var kalmanX = 0f; private var kalmanR = 0.1f; private var kalmanQ = 0.001f; private var kalmanReady = false
    private var basePressure = 0f; private var baseAltitude = 0f; private var baseAltitudeSet = false; private var gpsAltitudeSampleCount = 0
    private var lastUpdate = 0L
    private var gravity = floatArrayOf(0f, 0f, 0f); private var linearAcceleration = floatArrayOf(0f, 0f, 0f)
    private val accelHistory = mutableListOf<Float>(); private var smoothedAcceleration = 0f; private var currentAcceleration = 0f
    private var currentBearing = 0f; private var gpsBearing = 0f; private var gpsAcceleration = 0f
    private var lastGpsSpeedForAccel = 0f; private var lastGpsSpeedTime = 0L
    private var stepCount = 0; private var lastStepTime = 0L; private var stepFrequency = 0f; private var estimatedStepSpeed = 0f
    private val stepIntervalHistory = mutableListOf<Float>(); private val STEP_HISTORY_MAX = 10
    private var smoothedStepFrequency = 0f
    private var stepSpeedKalmanX = 0f; private var stepSpeedKalmanP = 1f; private var stepSpeedKalmanReady = false
    private val accelWindow = mutableListOf<Float>()
    private val stepAccelWindow = mutableListOf<Float>()
    
    private var stepState = StepState.IDLE
    private var consecutiveSteps = 0
    private var walkingDetected = false
    private var walkingConfidence = 0f
    private var lastPeakTime = 0L
    private var lastValleyTime = 0L
    private var currentPeak = 0f
    private var currentValley = 0f
    private var stepAmplitudes = mutableListOf<Float>()
    private var stepIntervals = mutableListOf<Long>()
    
    private var magneticFieldValues = floatArrayOf(0f, 0f, 0f); private var accelerometerValues = floatArrayOf(0f, 0f, 0f)
    private var filteredMag = floatArrayOf(0f, 0f, 0f); private var filteredAccel = floatArrayOf(0f, 0f, 0f)
    private var rotationMatrix = FloatArray(9); private var inclinationMatrix = FloatArray(9); private var orientationAngles = FloatArray(3)
    private var deviceOrientation = 0f
    private var gpsSpeed = 0f; private var gpsAccuracy = 0f; private var lastGpsSpeed = 0f
    private var displaySpeed = 0f; private var displayBearing = 0f; private var displayAcceleration = 0f
    private var hasGpsFix = false; private var lastGpsUpdateTime = 0L
    private var currentPressure = 0f; private var smoothedPressure = 0f
    private var currentTemperature = 0f; private var smoothedTemperature = 0f
    private var currentAltitude = 0f; private var smoothedAltitude = 0f; private var gpsAltitude = 0f
    private var maxAltitude = Float.MIN_VALUE; private var minAltitude = Float.MAX_VALUE
    private var altitudeChangeRate = 0f; private var lastAltitude = 0f; private var lastAltitudeTime = 0L
    private var gravityMagnitude = GRAVITY; private var hasBarometer = false
    private var pressureHistory = mutableListOf<Float>(); private var altitudeHistory = mutableListOf<Float>()
    private val ALTITUDE_HISTORY_MAX = 60
    private var gyroX = 0f; private var gyroY = 0f; private var gyroZ = 0f
    private var magX = 0f; private var magY = 0f; private var magZ = 0f
    private var magneticDeclination = 0f; private var magSensorAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
    private var lastLocationLat = 0.0; private var lastLocationLon = 0.0

    fun setMagneticDeclination(d: Float) { magneticDeclination = d }
    fun updateMagneticDeclination(lat: Double, lon: Double, alt: Float) {
        lastLocationLat = lat; lastLocationLon = lon
        try { magneticDeclination = GeomagneticField(lat.toFloat(), lon.toFloat(), alt, System.currentTimeMillis()).declination } catch (_: Exception) {}
    }
    fun setLocation(lat: Double, lon: Double) { lastLocationLat = lat; lastLocationLon = lon }
    fun getMagSensorAccuracy(): Int = magSensorAccuracy
    fun onMagAccuracyChanged(a: Int) { magSensorAccuracy = a }

    fun processGravity(e: SensorEvent) {
        gravity[0] = e.values[0]; gravity[1] = e.values[1]; gravity[2] = e.values[2]
        accelerometerValues[0] = gravity[0]; accelerometerValues[1] = gravity[1]; accelerometerValues[2] = gravity[2]
        updateCompassBearing()
    }

    fun processLinearAcceleration(e: SensorEvent) {
        linearAcceleration[0] = e.values[0]; linearAcceleration[1] = e.values[1]; linearAcceleration[2] = e.values[2]
        val rawMag = sqrt(linearAcceleration[0] * linearAcceleration[0] + linearAcceleration[1] * linearAcceleration[1] + linearAcceleration[2] * linearAcceleration[2])
        accelHistory.add(rawMag)
        if (accelHistory.size > ACCEL_WINDOW_SIZE) accelHistory.removeAt(0)
        val avgAccel = if (accelHistory.size >= ACCEL_WINDOW_SIZE / 2) accelHistory.average().toFloat() else rawMag
        if (smoothedAcceleration == 0f) smoothedAcceleration = avgAccel else smoothedAcceleration += ACCEL_SMOOTH_ALPHA * (avgAccel - smoothedAcceleration)
        currentAcceleration = avgAccel * GRAVITY
        lastUpdate = e.timestamp / 1_000_000
        detectStep(rawMag)
    }

    private fun detectStep(accelMag: Float) {
        val nowMs = System.currentTimeMillis()
        
        stepAccelWindow.add(accelMag)
        if (stepAccelWindow.size > STEP_WINDOW_SIZE) stepAccelWindow.removeAt(0)
        
        if (stepAccelWindow.size < 8) return
        
        val mean = stepAccelWindow.average().toFloat()
        val std = sqrt(stepAccelWindow.map { (it - mean) * (it - mean) }.average().toFloat())
        
        val threshold = mean + std * 0.6f
        
        when (stepState) {
            StepState.IDLE -> {
                if (accelMag > threshold) {
                    currentPeak = accelMag
                    stepState = StepState.PEAK_DETECTED
                }
            }
            StepState.PEAK_DETECTED -> {
                if (accelMag > currentPeak) {
                    currentPeak = accelMag
                } else if (accelMag < mean) {
                    currentValley = accelMag
                    stepState = StepState.VALLEY_DETECTED
                    lastPeakTime = nowMs
                }
            }
            StepState.VALLEY_DETECTED -> {
                if (accelMag < currentValley) {
                    currentValley = accelMag
                } else if (accelMag > mean + std * 0.3f) {
                    val stepAmplitude = currentPeak - currentValley
                    val timeSinceLastStep = nowMs - lastStepTime
                    
                    if (stepAmplitude > std * 0.8f && 
                        (timeSinceLastStep > STEP_MIN_INTERVAL_MS || lastStepTime == 0L)) {
                        
                        stepAmplitudes.add(stepAmplitude)
                        if (stepAmplitudes.size > 10) stepAmplitudes.removeAt(0)
                        
                        stepIntervals.add(timeSinceLastStep)
                        if (stepIntervals.size > 10) stepIntervals.removeAt(0)
                        
                        consecutiveSteps++
                        stepCount++
                        lastStepTime = nowMs
                        
                        if (consecutiveSteps >= 3) {
                            walkingDetected = true
                            walkingConfidence = minOf(1f, walkingConfidence + 0.15f)
                            
                            if (stepIntervals.size >= 3) {
                                val avgInterval = stepIntervals.takeLast(5).filter { it > 0 }.average()
                                
                                if (avgInterval > 0 && avgInterval < 2000) {
                                    val rawFreq = 1000f / avgInterval.toFloat()
                                    
                                    if (rawFreq in 0.5f..4.0f) {
                                        smoothedStepFrequency += STEP_FREQ_SMOOTH_ALPHA * (rawFreq - smoothedStepFrequency)
                                        stepFrequency = smoothedStepFrequency.coerceIn(0.5f, 3.5f)
                                        
                                        val avgAmp = stepAmplitudes.takeLast(5).average().toFloat()
                                        val ampFactor = when {
                                            avgAmp < std * 1.5f -> 0.65f
                                            avgAmp < std * 2.0f -> 0.72f
                                            avgAmp < std * 2.5f -> 0.78f
                                            else -> 0.82f
                                        }
                                        
                                        val stepLength = ampFactor
                                        val rawStepSpeed = stepFrequency * stepLength
                                        
                                        if (!stepSpeedKalmanReady) {
                                            stepSpeedKalmanX = rawStepSpeed
                                            stepSpeedKalmanP = 1f
                                            stepSpeedKalmanReady = true
                                        } else {
                                            stepSpeedKalmanP += STEP_SPEED_KALMAN_Q
                                            val kg = stepSpeedKalmanP / (stepSpeedKalmanP + STEP_SPEED_KALMAN_R)
                                            stepSpeedKalmanX += kg * (rawStepSpeed - stepSpeedKalmanX)
                                            stepSpeedKalmanP *= (1f - kg)
                                        }
                                        estimatedStepSpeed = stepSpeedKalmanX.coerceIn(0.3f, 2.8f)
                                    }
                                }
                            }
                        }
                    }
                    
                    stepState = StepState.IDLE
                    currentPeak = 0f
                    currentValley = 0f
                }
            }
        }
        
        val timeSinceLastStep = nowMs - lastStepTime
        if (timeSinceLastStep > 2000 && lastStepTime > 0) {
            stepFrequency *= 0.6f
            estimatedStepSpeed *= 0.6f
            walkingConfidence = maxOf(0f, walkingConfidence - 0.2f)
            consecutiveSteps = maxOf(0, consecutiveSteps - 1)
            
            if (consecutiveSteps < 2) {
                walkingDetected = false
            }
        }
        
        if (timeSinceLastStep > 3000) {
            stepState = StepState.IDLE
            stepFrequency = 0f
            estimatedStepSpeed = 0f
            smoothedStepFrequency = 0f
            stepSpeedKalmanX = 0f
            stepSpeedKalmanP = 1f
            stepSpeedKalmanReady = false
            walkingDetected = false
            walkingConfidence = 0f
            consecutiveSteps = 0
            stepAmplitudes.clear()
            stepIntervals.clear()
            currentPeak = 0f
            currentValley = 0f
            lastPeakTime = 0L
            lastValleyTime = 0L
        }
        
        accelWindow.add(accelMag)
        if (accelWindow.size > ACCEL_WINDOW_SIZE) accelWindow.removeAt(0)
    }

    fun processAccelerometer(e: SensorEvent) {
        filteredAccel[0] = lowPass(e.values[0], filteredAccel[0], ACCEL_LOW_PASS_ALPHA)
        filteredAccel[1] = lowPass(e.values[1], filteredAccel[1], ACCEL_LOW_PASS_ALPHA)
        filteredAccel[2] = lowPass(e.values[2], filteredAccel[2], ACCEL_LOW_PASS_ALPHA)
        accelerometerValues[0] = filteredAccel[0]; accelerometerValues[1] = filteredAccel[1]; accelerometerValues[2] = filteredAccel[2]
        gravity[0] = filteredAccel[0]; gravity[1] = filteredAccel[1]; gravity[2] = filteredAccel[2]
        linearAcceleration[0] = e.values[0] - filteredAccel[0]; linearAcceleration[1] = e.values[1] - filteredAccel[1]; linearAcceleration[2] = e.values[2] - filteredAccel[2]
        val rawMag = sqrt(linearAcceleration[0] * linearAcceleration[0] + linearAcceleration[1] * linearAcceleration[1] + linearAcceleration[2] * linearAcceleration[2])
        accelHistory.add(rawMag)
        if (accelHistory.size > ACCEL_WINDOW_SIZE) accelHistory.removeAt(0)
        val avgAccel = if (accelHistory.size >= ACCEL_WINDOW_SIZE / 2) accelHistory.average().toFloat() else rawMag
        if (smoothedAcceleration == 0f) smoothedAcceleration = avgAccel else smoothedAcceleration += ACCEL_SMOOTH_ALPHA * (avgAccel - smoothedAcceleration)
        currentAcceleration = avgAccel * GRAVITY
        lastUpdate = e.timestamp / 1_000_000
        detectStep(rawMag)
        updateCompassBearing()
    }

    fun processGyroscope(e: SensorEvent) { gyroX = e.values[0]; gyroY = e.values[1]; gyroZ = e.values[2] }

    fun processMagneticField(e: SensorEvent) {
        filteredMag[0] = lowPass(e.values[0], filteredMag[0], MAG_LOW_PASS_ALPHA)
        filteredMag[1] = lowPass(e.values[1], filteredMag[1], MAG_LOW_PASS_ALPHA)
        filteredMag[2] = lowPass(e.values[2], filteredMag[2], MAG_LOW_PASS_ALPHA)
        magX = filteredMag[0]; magY = filteredMag[1]; magZ = filteredMag[2]
        magneticFieldValues[0] = filteredMag[0]; magneticFieldValues[1] = filteredMag[1]; magneticFieldValues[2] = filteredMag[2]
        updateCompassBearing()
    }

    private fun updateCompassBearing() {
        if (SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, accelerometerValues, magneticFieldValues)) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            var az = Math.toDegrees(orientationAngles[0].toDouble()).toFloat() + magneticDeclination
            if (az < 0f) az += 360f; if (az >= 360f) az -= 360f
            currentBearing = az
            deviceOrientation = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
        }
    }

    fun getGyroMagnitude(): Float = sqrt(gyroX * gyroX + gyroY * gyroY + gyroZ * gyroZ)
    fun getMagMagnitude(): Float = sqrt(magX * magX + magY * magY + magZ * magZ)
    fun getGravityMagnitude(): Float = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2])
    fun getLinearAccelMagnitude(): Float = sqrt(linearAcceleration[0] * linearAcceleration[0] + linearAcceleration[1] * linearAcceleration[1] + linearAcceleration[2] * linearAcceleration[2])

    fun processPressure(e: SensorEvent) {
        hasBarometer = true; currentPressure = e.values[0]
        smoothedPressure = kalmanFilter(currentPressure)
        if (gpsAltitude != 0f && gpsAltitudeSampleCount >= 5 && !baseAltitudeSet) { baseAltitudeSet = true; basePressure = smoothedPressure; baseAltitude = gpsAltitude }
        pressureHistory.add(smoothedPressure)
        if (pressureHistory.size > ALTITUDE_HISTORY_MAX) pressureHistory.removeAt(0)
        calculateAltitude()
    }

    private fun kalmanFilter(m: Float): Float {
        if (!kalmanReady) { kalmanX = m; kalmanP = 1f; kalmanReady = true; return m }
        kalmanP += kalmanQ; val k = kalmanP / (kalmanP + kalmanR); kalmanX += k * (m - kalmanX); kalmanP *= (1f - k); return kalmanX
    }

    fun processTemperature(e: SensorEvent) {
        currentTemperature = e.values[0]
        smoothedTemperature = if (smoothedTemperature == 0f) currentTemperature else smoothedTemperature + TEMPERATURE_SMOOTH_ALPHA * (currentTemperature - smoothedTemperature)
    }

    fun updateGpsAltitude(a: Float) {
        if (a < -500f || a > 10000f) return
        gpsAltitude = a; gpsAltitudeSampleCount++
        if (gpsAltitudeSampleCount >= 5 && !baseAltitudeSet) { baseAltitudeSet = true; basePressure = smoothedPressure; baseAltitude = gpsAltitude }
        if (!hasBarometer) { smoothedAltitude = if (smoothedAltitude == 0f) a else smoothedAltitude + 0.15f * (a - smoothedAltitude); currentAltitude = smoothedAltitude; updateAltitudeStats(smoothedAltitude) }
    }

    private fun calculateAltitude() {
        val raw = pressureToAltitude(smoothedPressure)
        val cal = if (baseAltitudeSet) baseAltitude + raw - pressureToAltitude(basePressure) else raw
        currentAltitude = cal
        if (smoothedAltitude == 0f) smoothedAltitude = cal else smoothedAltitude += 0.15f * (cal - smoothedAltitude)
        if (hasBarometer && gpsAltitude != 0f && baseAltitudeSet) smoothedAltitude -= (smoothedAltitude - gpsAltitude) * 0.05f
        updateAltitudeStats(smoothedAltitude)
        altitudeHistory.add(smoothedAltitude)
        if (altitudeHistory.size > ALTITUDE_HISTORY_MAX) altitudeHistory.removeAt(0)
        if (altitudeHistory.size >= 2) { val now = System.currentTimeMillis(); if (lastAltitudeTime > 0) { val dt = (now - lastAltitudeTime) / 1000f; if (dt > 0) altitudeChangeRate = (smoothedAltitude - lastAltitude) / dt }; lastAltitude = smoothedAltitude; lastAltitudeTime = now }
        gravityMagnitude = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2]).let { if (it > 0) it else GRAVITY }
    }

    private fun pressureToAltitude(p: Float): Float {
        val t = if (smoothedTemperature != 0f) smoothedTemperature else 15f; val tk = t + 273.15f
        return (tk / 0.0065f) * (1f - (p / SEA_LEVEL_PRESSURE_HPA).toDouble().pow(((0.0065f * 287.05f) / 9.80665f).toDouble())).toFloat()
    }

    private fun updateAltitudeStats(a: Float) { if (a > maxAltitude) maxAltitude = a; if (a < minAltitude) minAltitude = a }
    fun getSeaLevelPressure(): Float = if (pressureHistory.isNotEmpty()) pressureHistory.average().toFloat() else SEA_LEVEL_PRESSURE_HPA

    fun updateGpsSpeed(s: Float, acc: Float) {
        if (s.isNaN() || s < 0f || s > GPS_SPEED_MAX_MS) {
            return
        }
        lastGpsUpdateTime = System.currentTimeMillis()
        
        if (acc > 150f) {
            return
        }
        
        gpsSpeed = s
        gpsAccuracy = acc
        hasGpsFix = true
        
        if (lastGpsSpeedTime > 0) {
            val nowMs = System.currentTimeMillis()
            val dt = (nowMs - lastGpsSpeedTime) / 1000f
            if (dt > 0.2f && dt < 5f) {
                gpsAcceleration = ((s - lastGpsSpeedForAccel) / dt).coerceIn(-20f, 20f)
            }
        }
        lastGpsSpeedForAccel = s
        lastGpsSpeedTime = System.currentTimeMillis()
    }

    fun updateGpsBearing(b: Float) { if (hasGpsFix) gpsBearing = b }
    fun updateLocationSpeed(s: Float) {
        if (s.isNaN() || s < 0f || s > GPS_SPEED_MAX_MS) return
        val kmh = s * 3.6f
        
        if (displaySpeed < 1f) {
            displaySpeed = kmh
        } else {
            val alpha = if (kmh < displaySpeed) 0.4f else 0.15f
            displaySpeed = displaySpeed + alpha * (kmh - displaySpeed)
        }
        displaySpeed = displaySpeed.coerceIn(0f, DISPLAY_SPEED_MAX)
    }

    fun getSpeed(): SpeedData {
        val gpsAge = System.currentTimeMillis() - lastGpsUpdateTime
        val gpsValid = hasGpsFix && gpsAge < GPS_FIX_TIMEOUT
        val gpsKmh = if (gpsValid) gpsSpeed * 3.6f else -1f
        val stepKmh = estimatedStepSpeed * 3.6f
        
        val wasUsingStepSpeed = walkingDetected && stepFrequency > 0.3f
        val isReallyWalking = walkingDetected && 
                               stepFrequency > 0.5f && 
                               walkingConfidence > 0.3f &&
                               consecutiveSteps >= 3
        
        val isGpsStationary = gpsValid && gpsKmh < 0.5f
        val isLowGpsSpeed = gpsValid && gpsKmh < WALK_FUSION_SPEED_THRESHOLD && gpsKmh >= 0.5f
        
        val shouldUseStepFusion = isReallyWalking && !isGpsStationary && (isLowGpsSpeed || !gpsValid)
        
        val stoppingWalk = wasUsingStepSpeed && !isReallyWalking && !gpsValid
        
        var rawKmh = 0f
        
        if (shouldUseStepFusion && isReallyWalking) {
            if (gpsValid && gpsKmh >= 1.0f) {
                val stepWeight = when {
                    walkingConfidence > 0.8f -> 0.6f
                    walkingConfidence > 0.7f -> 0.5f
                    else -> 0.4f
                }
                val gpsWeight = 1f - stepWeight
                rawKmh = stepKmh * stepWeight + gpsKmh * gpsWeight
            } else {
                rawKmh = stepKmh
            }
        } else if (gpsValid && gpsKmh >= 0f && gpsKmh < DISPLAY_SPEED_MAX) {
            if (gpsKmh <= displaySpeed) {
                rawKmh = gpsKmh
            } else {
                val diff = gpsKmh - displaySpeed
                if (diff < maxOf(200f, displaySpeed * 2f)) rawKmh = gpsKmh else rawKmh = displaySpeed
            }
        } else if (!gpsValid) {
            rawKmh = 0f
        }
        
        val isHighSpeed = gpsValid && gpsKmh > 100f
        
        val effBearing = if (gpsValid && (gpsBearing > 0f || isHighSpeed)) gpsBearing else currentBearing
        val effAccel = if (gpsValid) gpsAcceleration else smoothedAcceleration * GRAVITY
        val prev = displaySpeed
        
        if (gpsValid || shouldUseStepFusion) {
            val a = if (rawKmh < displaySpeed) 0.5f else if (isHighSpeed) 0.25f else SPEED_SMOOTH_ALPHA
            displaySpeed += a * (rawKmh - displaySpeed)
            
            val maxChange = if (isHighSpeed) 80f else 50f
            val minChange = if (isHighSpeed) 30f else 15f
            displaySpeed = displaySpeed.coerceIn(prev - maxChange, prev + minChange)
        } else if (stoppingWalk) {
            displaySpeed *= 0.3f
            if (displaySpeed < 1.5f) displaySpeed = 0f
        } else {
            displaySpeed *= 0.6f
            displaySpeed = displaySpeed.coerceIn(0f, DISPLAY_SPEED_MAX)
        }
        
        displaySpeed = displaySpeed.coerceIn(0f, DISPLAY_SPEED_MAX)
        if (!gpsValid && !shouldUseStepFusion && displaySpeed < 1.0f) displaySpeed = 0f
        
        val bearingAlpha = if (isHighSpeed) 0.15f else BEARING_SMOOTH_ALPHA
        displayBearing = smoothBearing(displayBearing, effBearing, bearingAlpha)
        displayAcceleration += 0.05f * (effAccel - displayAcceleration)
        
        val avgStepLength = if (estimatedStepSpeed > 0 && stepFrequency > 0) estimatedStepSpeed / stepFrequency else 0.7f
        val speed = displaySpeed.coerceAtLeast(0f)
        
        return SpeedData(
            speed = speed,
            speedMs = if (gpsValid) gpsSpeed else estimatedStepSpeed,
            maxSpeed = speed,
            acceleration = displayAcceleration,
            direction = getDirectionLabel(displayBearing),
            bearing = displayBearing,
            totalDistance = (stepCount * avgStepLength) / 1000f,
            stepCount = stepCount,
            stepFrequency = stepFrequency,
            movementState = getMovementState(speed, displayAcceleration),
            confidence = calcConfidence(gpsValid, shouldUseStepFusion),
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
            altitudeQuality = calcAltQuality(),
            altitudeDescription = altDesc(smoothedAltitude),
            speedDescription = speedDesc(speed),
            accelDescription = accelDesc(displayAcceleration),
            walkingConfidence = walkingConfidence,
            usingStepSpeed = shouldUseStepFusion
        )
    }

    private fun smoothBearing(c: Float, t: Float, a: Float): Float {
        if (t <= 0f) return c; var d = t - c; if (d > 180f) d -= 360f; if (d < -180f) d += 360f
        var r = c + a * d; if (r < 0f) r += 360f; if (r >= 360f) r -= 360f; return r
    }

    private fun altDesc(a: Float) = when { a < -10f -> "死海以下"; a < 0f -> "海平面以下"; a < 3f -> "海平面附近"; a < 10f -> "约${(a/3).toInt()}层楼高"; a < 30f -> "约${(a/3).toInt()}层住宅楼"; a < 50f -> "约${(a/3).toInt()}层高楼"; a < 100f -> "约${(a/3).toInt()}层高层"; a < 200f -> "约${(a/3).toInt()}层, 如地王大厦"; a < 400f -> "约${(a/3).toInt()}层, 如上海中心"; a < 1000f -> "低山区域"; a < 2000f -> "高山区域, 如黄山"; a < 3000f -> "超高海拔, 如稻城"; a < 3500f -> "极高海拔, 如拉萨"; a < 5000f -> "雪线以上"; a < 8000f -> "死亡地带"; a < 8849f -> "珠峰级别"; else -> "超越珠峰" }
    private fun speedDesc(s: Float) = when { s < 0.5f -> "静止状态"; s < 6f -> "正常步行 (~5km/h)"; s < 12f -> "慢跑"; s < 20f -> "快速奔跑"; s < 35f -> "自行车 (运动)"; s < 45f -> "电动自行车"; s < 80f -> "国道机动车"; s < 120f -> "高速公路 (快速)"; s < 200f -> "快速铁路/动车"; s < 300f -> "高铁 (复兴号)"; s < 350f -> "高铁 (运营极速)"; s < 430f -> "磁悬浮列车"; s < 800f -> "民航客机 (巡航)"; s < 1235f -> "接近音速"; s < 2000f -> "高超音速"; s < 8000f -> "近地轨道"; s < 11200f -> "第二宇宙速度"; s < 28000f -> "星际航行"; else -> "接近光速..." }
    private fun accelDesc(a: Float): String { val v = abs(a); return when { v < 0.3f -> "几乎无加速度"; v < 1.5f -> "缓慢起步"; v < 3.0f -> "汽车正常加速"; v < 5.0f -> "汽车急加速"; v < 10.0f -> "约1G加速度"; v < 15.0f -> "约1.5G (过山车)"; v < 20.0f -> "约2G (战斗机)"; v < 30.0f -> "约3G (赛车)"; v < 50.0f -> "约5G (飞行员极限)"; else -> "极端加速度" } }
    private fun getDirectionLabel(b: Float) = when (b) { in 0f..22.5f -> "北"; in 22.5f..67.5f -> "东北"; in 67.5f..112.5f -> "东"; in 112.5f..157.5f -> "东南"; in 157.5f..202.5f -> "南"; in 202.5f..247.5f -> "西南"; in 247.5f..292.5f -> "西"; in 292.5f..337.5f -> "西北"; in 337.5f..360f -> "北"; else -> "未知" }
    private fun getMovementState(s: Float, a: Float) = when { s < 1f && abs(a) < 1.0f -> MovementState.STATIONARY; s < 6f -> MovementState.WALKING; s < 20f -> MovementState.RUNNING; s < 50f -> MovementState.CYCLING; s < 120f -> MovementState.DRIVING; else -> MovementState.HIGH_SPEED }
    private fun calcConfidence(g: Boolean, stepFusion: Boolean = false): Float {
        return when {
            g && stepFusion && walkingConfidence > 0.7f -> 0.92f
            g && stepFusion -> 0.85f
            g && gpsAccuracy < GPS_ACCURACY_GOOD -> 0.95f
            g && gpsAccuracy < GPS_ACCURACY_OK -> 0.8f
            g -> 0.6f
            stepFusion && walkingConfidence > 0.5f -> 0.7f
            stepFusion -> 0.5f
            else -> 0.3f
        }
    }
    private fun calcAltQuality(): Float { var q = 0.3f; if (hasBarometer) { q += 0.4f; if (pressureHistory.size >= ALTITUDE_HISTORY_MAX / 2) q += 0.15f }; if (gpsAltitude != 0f) q += 0.15f; if (gravityMagnitude > 0) q += 0.1f; return minOf(1f, q) }
    private fun lowPass(c: Float, p: Float, a: Float) = p + a * (c - p)

fun reset() {
        lastUpdate = 0L; gravity = floatArrayOf(0f, 0f, 0f); linearAcceleration = floatArrayOf(0f, 0f, 0f); accelHistory.clear()
        smoothedAcceleration = 0f; currentAcceleration = 0f; currentBearing = 0f; gpsBearing = 0f; gpsAcceleration = 0f
        lastGpsSpeedForAccel = 0f; lastGpsSpeedTime = 0L; stepCount = 0; lastStepTime = 0L; stepFrequency = 0f; estimatedStepSpeed = 0f
        stepIntervalHistory.clear(); smoothedStepFrequency = 0f
        stepSpeedKalmanX = 0f; stepSpeedKalmanP = 1f; stepSpeedKalmanReady = false; accelWindow.clear(); stepAccelWindow.clear()
        gpsSpeed = 0f; gpsAccuracy = 0f; lastGpsSpeed = 0f; displaySpeed = 0f; displayBearing = 0f; displayAcceleration = 0f
        hasGpsFix = false; lastGpsUpdateTime = 0L; currentPressure = 0f; smoothedPressure = 0f
        currentTemperature = 0f; smoothedTemperature = 0f; currentAltitude = 0f; smoothedAltitude = 0f; gpsAltitude = 0f
        maxAltitude = Float.MIN_VALUE; minAltitude = Float.MAX_VALUE; altitudeChangeRate = 0f; lastAltitude = 0f; lastAltitudeTime = 0L
        gravityMagnitude = GRAVITY; hasBarometer = false; pressureHistory.clear(); altitudeHistory.clear(); kalmanReady = false; kalmanP = 0f
        kalmanX = 0f; basePressure = 0f; baseAltitude = 0f; baseAltitudeSet = false; gpsAltitudeSampleCount = 0
        stepState = StepState.IDLE; consecutiveSteps = 0; walkingDetected = false; walkingConfidence = 0f
        lastPeakTime = 0L; lastValleyTime = 0L; currentPeak = 0f; currentValley = 0f
        stepAmplitudes.clear(); stepIntervals.clear(); deviceOrientation = 0f
    }

    data class SpeedData(
        val speed: Float, val speedMs: Float, val maxSpeed: Float, val acceleration: Float,
        val direction: String, val bearing: Float, val totalDistance: Float, val stepCount: Int,
        val stepFrequency: Float, val movementState: MovementState, val confidence: Float,
        val gpsAccuracy: Float, val altitude: Float, val gpsAltitude: Float, val maxAltitude: Float,
        val minAltitude: Float, val altitudeChangeRate: Float, val pressure: Float, val temperature: Float,
        val gravityMagnitude: Float, val hasBarometer: Boolean, val altitudeQuality: Float,
        val altitudeDescription: String, val speedDescription: String, val accelDescription: String,
        val walkingConfidence: Float = 0f, val usingStepSpeed: Boolean = false
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
