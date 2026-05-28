package com.example.cahier.features.drawing

data class InkDebugMetrics(
    val pressure: Float = 0f,
    val toolType: String = "unknown",
    val pointCount: Long = 0,
    val eventRateHz: Int = 0,
    val finalizedStrokeCount: Int = 0,
    val cancelEventCount: Long = 0,
    val palmEventCount: Long = 0,
)
