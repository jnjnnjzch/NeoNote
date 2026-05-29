package com.example.cahier.features.drawing

data class InkDebugMetrics(
    val pressure: Float = 0f,
    val toolType: String = "unknown",
    val tiltRadians: Float? = null,
    val pointCount: Long = 0,
    val eventRateHz: Int = 0,
    val finalizedStrokeCount: Int = 0,
    val cancelEventCount: Long = 0,
    val palmEventCount: Long = 0,
    val screenX: Float = 0f,
    val screenY: Float = 0f,
    val documentX: Float = 0f,
    val documentY: Float = 0f,
    val scale: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
)
