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

class SensorViewModel(application: Application) : AndroidViewModel(application),
    SensorEventListener {
    private val sensorManager: SensorManager =
        application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val linearAccelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gravitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _isMeasuring = mutableStateOf(false)
    val isMeasuring: State<Boolean> = _isMeasuring

    private val _armElevation = mutableFloatStateOf(0f)
    val armElevation: State<Float> = _armElevation

    private val _linearAccelerometerData = mutableStateOf(floatArrayOf(0f, 0f, 0f))
    val linearAccelerometerData: State<FloatArray> = _linearAccelerometerData

    private val _gyroscopeData = mutableStateOf(floatArrayOf(0f, 0f, 0f))
    val gyroscopeData: State<FloatArray> = _gyroscopeData

    private val alpha = 0.2f
    private var lastFilteredValue = 0f

    private val filterAlpha = 0.98f
    private var lastTimestamp: Long = 0
    private var fusedAngle: Float = 0f


    fun startMeasurement() {
        if (!_isMeasuring.value) {

            lastFilteredValue = 0f

            fusedAngle = 0f
            lastTimestamp = 0

            gravitySensor?.also { grav ->
                sensorManager.registerListener(this, grav, SensorManager.SENSOR_DELAY_NORMAL)
            }
            linearAccelerometer?.also { acc ->
                sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_NORMAL)
            }
            gyroscope?.also { gyro ->
                sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_NORMAL)
            }
            _isMeasuring.value = true
        }
    }

    fun stopMeasurement() {
        if (_isMeasuring.value) {
            sensorManager.unregisterListener(this)
            _isMeasuring.value = false

            //Reset
            _armElevation.floatValue = 0f
            _linearAccelerometerData.value = floatArrayOf(0f, 0f, 0f)
            _gyroscopeData.value = floatArrayOf(0f, 0f, 0f)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                val grav = event.values
                val y = grav[1]
                val z = grav[2]

                val rawAngle = Math.toDegrees(atan2(-y, z).toDouble()).toFloat()

                // y(n) = alpha * x(n) + (1 - alpha) * y(n-1)
                lastFilteredValue = alpha * rawAngle + (1.0f - alpha) * lastFilteredValue
                _armElevation.floatValue = lastFilteredValue
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                _linearAccelerometerData.value = event.values.clone()
            }

            Sensor.TYPE_GYROSCOPE -> {
                _gyroscopeData.value = event.values.clone()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onCleared() {
        super.onCleared()
        stopMeasurement()
    }
}