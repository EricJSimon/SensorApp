package com.example.sensorapp.data.model

data class MeasurementData(
    val timestamp: Long,
    val value: Float,
    val algorithm: String
)