package com.example.sensorapp.viewmodels

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import kotlin.math.atan2

enum class Algorithm {
    EWMA_FILTER,
    COMPLEMENTARY_FILTER
}

class SensorViewModel(application: Application) : AndroidViewModel(application),
    SensorEventListener {
    private val sensorManager: SensorManager =
        application.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Sensors
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val linearAccelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gravitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // Public UI State
    private val _isMeasuring = mutableStateOf(false)
    val isMeasuring: State<Boolean> = _isMeasuring

    private val _armElevation = mutableFloatStateOf(0f)
    val armElevation: State<Float> = _armElevation

    private val _linearAccelerometerData = mutableStateOf(floatArrayOf(0f, 0f, 0f))
    val linearAccelerometerData: State<FloatArray> = _linearAccelerometerData

    private val _gyroscopeData = mutableStateOf(floatArrayOf(0f, 0f, 0f))
    val gyroscopeData: State<FloatArray> = _gyroscopeData


    // State for current Algorithm
    private val _currentAlgorithm = mutableStateOf(Algorithm.EWMA_FILTER)
    val currentAlgorithm: State<Algorithm> = _currentAlgorithm

    // State and Constants for EWMA Filter
    private val alpha = 0.2f
    private var lastEwmaValue = 0f

    // State and Constants for Complementary Filter
    private val complementaryFilterAlpha = 0.98f
    private var lastTimestamp: Long = 0
    private var fusedAngle: Float = 0f
    private val accReading = FloatArray(3)

    fun setAlgorithm(algorithm: Algorithm) {
        if (!_isMeasuring.value) {
            _currentAlgorithm.value = algorithm
        }
    }


    fun startMeasurement() {
        if (!_isMeasuring.value) {

            resetAlgorithmState()

            when (_currentAlgorithm.value) {
                Algorithm.EWMA_FILTER -> {
                    gravitySensor?.also { grav ->
                        sensorManager.registerListener(
                            this,
                            grav,
                            SensorManager.SENSOR_DELAY_UI
                        )
                    }
                    linearAccelerometer?.also { acc ->
                        sensorManager.registerListener(
                            this,
                            acc,
                            SensorManager.SENSOR_DELAY_UI
                        )
                    }
                }

                Algorithm.COMPLEMENTARY_FILTER -> {
                    accelerometer?.also {
                        sensorManager.registerListener(
                            this,
                            it,
                            SensorManager.SENSOR_DELAY_UI
                        )
                    }
                    gyroscope?.also { gyro ->
                        sensorManager.registerListener(
                            this,
                            gyro,
                            SensorManager.SENSOR_DELAY_UI
                        )
                    }
                }
            }
            _isMeasuring.value = true
        }
    }

    fun stopMeasurement() {
        if (_isMeasuring.value) {
            sensorManager.unregisterListener(this)
            _isMeasuring.value = false
            resetUiState()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !_isMeasuring.value) return

        when (_currentAlgorithm.value) {
            Algorithm.EWMA_FILTER -> processEwma(event)
            Algorithm.COMPLEMENTARY_FILTER -> processComplementary(event)
        }
    }

    private fun processEwma(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                val grav = event.values
                val y = grav[1]
                val z = grav[2]
                val rawAngle = Math.toDegrees(atan2(-y, z).toDouble()).toFloat()

                // y(n) = alpha * x(n) + (1 - alpha) * y(n-1)
                lastEwmaValue = alpha * rawAngle + (1.0f - alpha) * lastEwmaValue
                _armElevation.floatValue = lastEwmaValue
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                _linearAccelerometerData.value = event.values.clone()
            }
        }
    }

    private fun processComplementary(event: SensorEvent) {
        if (lastTimestamp == 0L) {
            lastTimestamp = event.timestamp
            return
        }
        val dt = (event.timestamp - lastTimestamp) * 1.0f / 1_000_000_000.0f
        lastTimestamp = event.timestamp

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accReading, 0, 3)
                _linearAccelerometerData.value = event.values.clone()
            }

            Sensor.TYPE_GYROSCOPE -> {
                val y = accReading[1]
                val z = accReading[2]
                val accAngle = Math.toDegrees(atan2(-y, z).toDouble()).toFloat()

                val gyroRate = event.values[0]
                val gyroAngle = fusedAngle + gyroRate * dt

                fusedAngle =
                    complementaryFilterAlpha * gyroAngle + (1.0f - complementaryFilterAlpha) * accAngle
                _armElevation.floatValue = fusedAngle
            }
        }
    }

    private fun resetAlgorithmState() {
        //EWMA
        lastEwmaValue = 0f

        // Comp
        fusedAngle = 0f
        lastTimestamp = 0L
    }

    private fun resetUiState() {
        _armElevation.floatValue = 0f
        _linearAccelerometerData.value = floatArrayOf(0f, 0f, 0f)
        _gyroscopeData.value = floatArrayOf(0f, 0f, 0f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onCleared() {
        super.onCleared()
        stopMeasurement()
    }
}