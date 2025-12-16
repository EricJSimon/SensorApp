package com.example.sensorapp.viewmodels

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2

data class MeasurementData(
    val timestamp: Long, val value: Float, val algorithm: String
)

enum class Algorithm {
    EWMA_FILTER, SENSOR_FUSION
}

class SensorViewModel(application: Application) : AndroidViewModel(application),
    SensorEventListener {
    private val sensorManager: SensorManager =
        application.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Sensors
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val linearAccelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
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

    // Data Collection
    private val measurementHistory = mutableListOf<MeasurementData>()
    private var sessionStartTimestamp: Long = 0L

    fun setAlgorithm(algorithm: Algorithm) {
        if (!_isMeasuring.value) {
            _currentAlgorithm.value = algorithm
        }
    }


    fun startMeasurement() {
        if (!_isMeasuring.value) {

            measurementHistory.clear()
            resetAlgorithmState()
            sessionStartTimestamp = 0L

            when (_currentAlgorithm.value) {
                Algorithm.EWMA_FILTER -> {
                    gravitySensor?.also { grav ->
                        sensorManager.registerListener(
                            this, grav, SensorManager.SENSOR_DELAY_UI
                        )
                    }
                    linearAccelerometer?.also { acc ->
                        sensorManager.registerListener(
                            this, acc, SensorManager.SENSOR_DELAY_UI
                        )
                    }
                }

                Algorithm.SENSOR_FUSION -> {
                    accelerometer?.also {
                        sensorManager.registerListener(
                            this, it, SensorManager.SENSOR_DELAY_UI
                        )
                    }
                    gyroscope?.also { gyro ->
                        sensorManager.registerListener(
                            this, gyro, SensorManager.SENSOR_DELAY_UI
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

            //resetUiState()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !_isMeasuring.value) return

        if (sessionStartTimestamp == 0L) {
            sessionStartTimestamp = event.timestamp
        }

        //val initialTimestamp = if (measurementHistory.isEmpty()) event.timestamp else measurementHistory.first().timestamp
        val relativeTimestamp = event.timestamp - sessionStartTimestamp


        when (_currentAlgorithm.value) {
            Algorithm.EWMA_FILTER -> {
                processEwma(event)

                measurementHistory.add(
                    MeasurementData(
                        relativeTimestamp, _armElevation.floatValue, "EWMA"
                    )
                )
            }

            Algorithm.SENSOR_FUSION -> {
                processComplementary(event)

                if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    measurementHistory.add(
                        MeasurementData(
                            relativeTimestamp,
                            _armElevation.floatValue,
                            "Sensor Fusion"
                        )
                    )
                }
            }
        }
    }

    private fun processEwma(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                val grav = event.values
                val y = grav[1]
                val z = grav[2]
                val rawAngle = Math.toDegrees(atan2(-y.toDouble(), z.toDouble())).toFloat()

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
                val accAngle = Math.toDegrees(atan2(-y.toDouble(), z.toDouble())).toFloat()

                val gyroRate = event.values[0]
                val gyroAngle = fusedAngle + gyroRate * dt

                fusedAngle =
                    complementaryFilterAlpha * gyroAngle + (1.0f - complementaryFilterAlpha) * accAngle
                _armElevation.floatValue = fusedAngle
            }
        }
    }

    fun exportDataToCsv() {
        if (measurementHistory.isEmpty()) {
            Toast.makeText(getApplication(), "No data to export.", Toast.LENGTH_SHORT).show()
            return
        }

        val delimiter = ";"
        val csvHeader =
            "Timestamp (HH:mm:ss.ms)${delimiter}Arm Elevation (degrees)${delimiter}Algorithm: ${_currentAlgorithm.value}\n"

        val csvData = measurementHistory.joinToString(separator = "\n") { dataPoint ->
            val formattedTime = formatNanosToTimeString(dataPoint.timestamp)
            val formattedValue =
                String.format(Locale.forLanguageTag("sv-SE"), "%.4f", dataPoint.value)

            "$formattedTime$delimiter$formattedValue"
        }
        val fullCsv = csvHeader + csvData

        val timeFormatter = SimpleDateFormat("HH-mm-ss", Locale.getDefault())
        val currentDateTimeString = timeFormatter.format(Date())

        val resolver = getApplication<Application>().contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "ArmElevationData_$currentDateTimeString.csv")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/SensorApp")
        }

        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

        if (uri != null) {
            try {
                resolver.openOutputStream(uri).use { outputStream ->
                    if (outputStream != null) {
                        outputStream.write(fullCsv.toByteArray(Charsets.UTF_8))
                        Toast.makeText(
                            getApplication(),
                            "Data exported to Downloads/SensorApp",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    getApplication(),
                    "Error exporting file ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            Toast.makeText(getApplication(), "Error creating file URI.", Toast.LENGTH_LONG).show()
        }
        measurementHistory.clear()
    }

    private fun formatNanosToTimeString(nanos: Long): String {
        if (nanos < 0) return "00:00:00.000"
        val totalMillis = nanos / 1_000_000
        val hours = totalMillis / (1000 * 60 * 60)
        val minutes = (totalMillis % (1000 * 60 * 60)) / (1000 * 60)
        val seconds = (totalMillis % (1000 * 60)) / 1000
        val millis = totalMillis % 1000

        return String.format(
            Locale.US,
            "%02d:%02d:%02d.%03d",
            hours,
            minutes,
            seconds,
            millis
        )
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