package com.example.sensorapp

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.sensorapp.ui.theme.SensorAppTheme
import com.example.sensorapp.viewmodels.SensorViewModel
import com.example.sensorapp.views.HomeScreen

class MainActivity : ComponentActivity() {
    private val viewModel: SensorViewModel by viewModels()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // permission is granted.
            } else {
                // Handle the case where the user denies the permission.
                // You could show a message explaining why the permission is needed.
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissionLauncher.launch(Manifest.permission.BODY_SENSORS)

        enableEdgeToEdge()
        setContent {
            SensorAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val isMeasuring by viewModel.isMeasuring
                    val armElevation by viewModel.armElevation
                    val linearAccelerometerData by viewModel.linearAccelerometerData
                    val gyroscopeData by viewModel.gyroscopeData

                    HomeScreen(
                        isMeasuring = isMeasuring,
                        armElevation = armElevation,
                        linearAccelerometerData = linearAccelerometerData,
                        gyroscopeData = gyroscopeData,
                        onButtonClick = {
                            if (isMeasuring) {
                                viewModel.stopMeasurement()
                            } else {
                                viewModel.startMeasurement()
                            }
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}