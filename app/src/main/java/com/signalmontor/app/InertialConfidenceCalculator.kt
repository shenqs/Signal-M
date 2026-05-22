package com.signalmontor.app

import kotlin.math.*

class InertialConfidenceCalculator {
    
    companion object {
        private const val GRAVITY = 9.80665f
        
        private const val ACC_NOISE_DENSITY = 0.02f
        private const val GYRO_NOISE_DENSITY = 0.005f
        private const val ACC_BIAS_INSTABILITY = 0.005f
        private const val GYRO_BIAS_INSTABILITY = 0.001f
        
        private const val SPEED_ACCEPTABLE_ERROR = 2.0f
        private const val POSITION_ACCEPTABLE_ERROR = 5.0f
        
        private const val ZUPT_ACC_THRESHOLD = 0.5f
        private const val ZUPT_GYRO_THRESHOLD = 0.1f
        private const val ZUPT_WINDOW_SIZE = 20
        
        private const val ACC_VAR_THRESHOLD_LOW = 0.1f
        private const val ACC_VAR_THRESHOLD_HIGH = 2.0f
        private const val GYRO_VAR_THRESHOLD_LOW = 0.05f
        private const val GYRO_VAR_THRESHOLD_HIGH = 0.5f
    }
    
    private var velocityErrorVariance = 1.0f
    private var speedEstimateVariance = 0.5f
    private var gpsLostStartTime = 0L
    private var totalGpsLostTime = 0L
    
    private val accVarianceWindow = mutableListOf<Float>()
    private val gyroVarianceWindow = mutableListOf<Float>()
    private val speedVarianceWindow = mutableListOf<Float>()
    
    private var lastAccMagnitude = 0f
    private var lastGyroMagnitude = 0f
    private var accBiasEstimate = 0f
    private var gyroBiasEstimate = 0f
    private var biasVariance = 0.01f
    
    private val speedHistoryWindow = mutableListOf<Float>()
    private val SPEED_HISTORY_MAX = 30
    
    private var eskfP = floatArrayOf(
        1.0f, 0.0f, 0.0f,
        0.0f, 1.0f, 0.0f,
        0.0f, 0.0f, 1.0f
    )
    private var eskfQ = 0.1f
    private var eskfR = 0.5f
    
    fun updateIMUData(accMagnitude: Float, gyroMagnitude: Float, dt: Float) {
        accVarianceWindow.add(accMagnitude)
        gyroVarianceWindow.add(gyroMagnitude)
        
        if (accVarianceWindow.size > ZUPT_WINDOW_SIZE) accVarianceWindow.removeAt(0)
        if (gyroVarianceWindow.size > ZUPT_WINDOW_SIZE) gyroVarianceWindow.removeAt(0)
        
        lastAccMagnitude = accMagnitude
        lastGyroMagnitude = gyroMagnitude
        
        updateBiasEstimate(accMagnitude, gyroMagnitude, dt)
        
        propagateESKF(dt, accMagnitude)
    }
    
    fun updateSpeedData(speed: Float) {
        speedHistoryWindow.add(speed)
        if (speedHistoryWindow.size > SPEED_HISTORY_MAX) speedHistoryWindow.removeAt(0)
        
        if (speedHistoryWindow.size >= 5) {
            val speedVariance = calculateVariance(speedHistoryWindow)
            speedVarianceWindow.add(speedVariance)
            if (speedVarianceWindow.size > SPEED_HISTORY_MAX) speedVarianceWindow.removeAt(0)
            
            updateSpeedEstimateVariance(speedVariance)
        }
    }
    
    fun setGpsLostState(isLost: Boolean, timestamp: Long) {
        if (isLost && gpsLostStartTime == 0L) {
            gpsLostStartTime = timestamp
        } else if (!isLost && gpsLostStartTime > 0L) {
            totalGpsLostTime += timestamp - gpsLostStartTime
            gpsLostStartTime = 0L
            resetESKF()
        }
    }
    
    fun computeOverallConfidence(
        gpsValid: Boolean,
        gpsAccuracy: Float,
        inertialSpeed: Float,
        speedTrend: SpeedTrend
    ): Float {
        val gpsConfidence = computeGPSConfidence(gpsValid, gpsAccuracy)
        val inertialConfidence = computeInertialConfidence(inertialSpeed, speedTrend)
        val motionConfidence = computeMotionConfidence()
        val eskfConfidence = computeESKFConfidence()
        
        if (gpsValid) {
            return gpsConfidence * 0.7f + motionConfidence * 0.2f + eskfConfidence * 0.1f
        } else {
            val timeWeight = computeTimeBasedWeight()
            return inertialConfidence * timeWeight * 0.5f + 
                   motionConfidence * 0.3f + 
                   eskfConfidence * 0.2f
        }
    }
    
    fun computeGPSConfidence(gpsValid: Boolean, gpsAccuracy: Float): Float {
        if (!gpsValid) return 0.0f
        
        return when {
            gpsAccuracy < 10f -> 0.95f
            gpsAccuracy < 20f -> 0.85f
            gpsAccuracy < 50f -> 0.70f
            gpsAccuracy < 100f -> 0.50f
            else -> 0.30f
        }
    }
    
    fun computeInertialConfidence(inertialSpeed: Float, speedTrend: SpeedTrend): Float {
        val baseConfidence = when {
            inertialSpeed < 10f -> 0.3f
            inertialSpeed < 50f -> 0.5f
            inertialSpeed < 200f -> 0.6f
            else -> 0.55f
        }
        
        val trendBonus = when (speedTrend) {
            SpeedTrend.STEADY -> 0.15f
            SpeedTrend.ACCELERATING -> 0.05f
            SpeedTrend.DECELERATING -> 0.0f
            SpeedTrend.UNKNOWN -> -0.1f
        }
        
        val accelerationQuality = computeAccelerationQuality()
        
        return (baseConfidence + trendBonus + accelerationQuality * 0.2f).coerceIn(0.1f, 0.75f)
    }
    
    fun computeAccelerationQuality(): Float {
        if (accVarianceWindow.size < 10) return 0.5f
        
        val accVariance = calculateVariance(accVarianceWindow)
        
        return when {
            accVariance < ACC_VAR_THRESHOLD_LOW -> 0.9f
            accVariance < ACC_VAR_THRESHOLD_HIGH -> 0.7f
            else -> 0.4f
        }
    }
    
    fun computeMotionConfidence(): Float {
        if (accVarianceWindow.size < ZUPT_WINDOW_SIZE || gyroVarianceWindow.size < ZUPT_WINDOW_SIZE) {
            return 0.5f
        }
        
        val accVariance = calculateVariance(accVarianceWindow)
        val gyroVariance = calculateVariance(gyroVarianceWindow)
        
        val accScore = exp(-accVariance / ACC_VAR_THRESHOLD_HIGH)
        val gyroScore = exp(-gyroVariance / GYRO_VAR_THRESHOLD_HIGH)
        
        return (accScore * 0.6f + gyroScore * 0.4f).coerceIn(0.2f, 0.9f)
    }
    
    fun detectZeroVelocity(): Boolean {
        if (accVarianceWindow.size < ZUPT_WINDOW_SIZE) return false
        
        val accVariance = calculateVariance(accVarianceWindow)
        val gyroVariance = calculateVariance(gyroVarianceWindow)
        
        val accMean = accVarianceWindow.average()
        val gyroMean = gyroVarianceWindow.average()
        
        return accVariance < ZUPT_ACC_THRESHOLD && 
               gyroVariance < ZUPT_GYRO_THRESHOLD &&
               abs(accMean - GRAVITY) < 0.3f
    }
    
    fun computeTimeBasedWeight(): Float {
        if (gpsLostStartTime == 0L) return 1.0f
        
        val lostDurationMs = System.currentTimeMillis() - gpsLostStartTime
        val lostDurationSeconds = lostDurationMs / 1000f
        
        val estimatedError = estimatePositionDrift(lostDurationSeconds)
        
        return exp(-estimatedError / POSITION_ACCEPTABLE_ERROR).coerceIn(0.1f, 1.0f)
    }
    
    fun estimatePositionDrift(dtSeconds: Float): Float {
        val thetaRW = GYRO_NOISE_DENSITY * sqrt(dtSeconds)
        val thetaBI = GYRO_BIAS_INSTABILITY * dtSeconds
        val thetaTotal = sqrt(thetaRW * thetaRW + thetaBI * thetaBI)
        
        val vRW = ACC_NOISE_DENSITY * sqrt(dtSeconds)
        val vBI = ACC_BIAS_INSTABILITY * dtSeconds
        val vError = sqrt(vRW * vRW + vBI * vBI)
        
        val pError = vError * dtSeconds + 0.5f * GRAVITY * thetaTotal * dtSeconds * dtSeconds
        
        return pError
    }
    
    fun estimateVelocityDrift(dtSeconds: Float): Float {
        val vRW = ACC_NOISE_DENSITY * sqrt(dtSeconds)
        val vBI = ACC_BIAS_INSTABILITY * dtSeconds
        
        return sqrt(vRW * vRW + vBI * vBI) + speedEstimateVariance * dtSeconds * 0.1f
    }
    
    private fun updateBiasEstimate(accMag: Float, gyroMag: Float, dt: Float) {
        val alpha = 0.01f
        
        if (abs(accMag - GRAVITY) < 0.2f) {
            accBiasEstimate = (1 - alpha) * accBiasEstimate + alpha * (accMag - GRAVITY)
        }
        
        if (gyroMag < 0.05f) {
            gyroBiasEstimate = (1 - alpha) * gyroBiasEstimate + alpha * gyroMag
        }
        
        biasVariance = biasVariance * 0.99f + (abs(accMag - GRAVITY) * 0.01f)
    }
    
    private fun propagateESKF(dt: Float, accMag: Float) {
        val innovation = accMag - GRAVITY - accBiasEstimate
        
        eskfP[0] += eskfQ * dt
        eskfP[4] += eskfQ * dt
        eskfP[8] += eskfQ * dt
        
        val kg = eskfP[0] / (eskfP[0] + eskfR)
        velocityErrorVariance = velocityErrorVariance * (1 - kg) + kg * innovation * innovation
        
        eskfP[0] = eskfP[0] * (1 - kg)
        eskfP[4] = eskfP[4] * (1 - kg)
        eskfP[8] = eskfP[8] * (1 - kg)
        
        velocityErrorVariance = velocityErrorVariance.coerceIn(0.1f, 10.0f)
    }
    
    private fun updateSpeedEstimateVariance(speedVariance: Float) {
        speedEstimateVariance = speedEstimateVariance * 0.9f + speedVariance * 0.1f
        speedEstimateVariance = speedEstimateVariance.coerceIn(0.1f, 20.0f)
    }
    
    private fun computeESKFConfidence(): Float {
        val velocityStd = sqrt(velocityErrorVariance)
        
        val confidence = 2.0f * normalCDF(SPEED_ACCEPTABLE_ERROR / velocityStd) - 1.0f
        
        return confidence.coerceIn(0.1f, 0.9f)
    }
    
    private fun normalCDF(x: Float): Float {
        val a1 = 0.254829592f
        val a2 = -0.284496736f
        val a3 = 1.421413741f
        val a4 = -1.453152027f
        val a5 = 1.061405429f
        val p = 0.3275911f
        
        val sign = if (x < 0) -1 else 1
        val xAbs = abs(x) / sqrt(2.0f)
        
        val t = 1.0f / (1.0f + p * xAbs)
        val y = 1.0f - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * exp(-xAbs * xAbs)
        
        return 0.5f * (1.0f + sign * y)
    }
    
    private fun resetESKF() {
        velocityErrorVariance = 1.0f
        speedEstimateVariance = 0.5f
        eskfP = floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f)
    }
    
    private fun calculateVariance(data: List<Float>): Float {
        if (data.isEmpty()) return 0f
        val mean = data.average()
        return data.map { (it - mean) * (it - mean) }.average().toFloat()
    }
    
    fun reset() {
        velocityErrorVariance = 1.0f
        speedEstimateVariance = 0.5f
        gpsLostStartTime = 0L
        totalGpsLostTime = 0L
        accVarianceWindow.clear()
        gyroVarianceWindow.clear()
        speedVarianceWindow.clear()
        speedHistoryWindow.clear()
        lastAccMagnitude = 0f
        lastGyroMagnitude = 0f
        accBiasEstimate = 0f
        gyroBiasEstimate = 0f
        biasVariance = 0.01f
        resetESKF()
    }
    
    fun getVelocityErrorVariance(): Float = velocityErrorVariance
    fun getSpeedEstimateVariance(): Float = speedEstimateVariance
    fun getAccBiasEstimate(): Float = accBiasEstimate
    fun getGyroBiasEstimate(): Float = gyroBiasEstimate
    fun getGpsLostDuration(): Long = if (gpsLostStartTime > 0L) System.currentTimeMillis() - gpsLostStartTime else 0L
}