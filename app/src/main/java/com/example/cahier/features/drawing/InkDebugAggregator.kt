package com.example.cahier.features.drawing

data class InkDebugSample(
    val pressure: Float,
    val toolType: String,
    val historySize: Int,
    val isCancel: Boolean,
    val isPalm: Boolean,
    val tiltRadians: Float?,
)

class InkDebugAggregator(
    private val publishIntervalMs: Long = 100L,
    private val rateWindowMs: Long = 1000L,
) {
    private var eventCounterSinceWindow = 0
    private var eventWindowStartMillis = 0L
    private var lastPublishMillis = 0L

    private var pendingPressure = 0f
    private var pendingToolType = "unknown"
    private var pendingTiltRadians: Float? = null
    private var pendingPointIncrement = 0L
    private var pendingCancelIncrement = 0L
    private var pendingPalmIncrement = 0L

    fun ingest(
        nowMillis: Long,
        sample: InkDebugSample,
        current: InkDebugMetrics,
    ): InkDebugMetrics? {
        if (eventWindowStartMillis == 0L) {
            eventWindowStartMillis = nowMillis
        }
        eventCounterSinceWindow++
        val elapsed = nowMillis - eventWindowStartMillis
        val eventRateHz = if (elapsed > 0L) {
            ((eventCounterSinceWindow * 1000L) / elapsed).toInt()
        } else {
            0
        }

        pendingPressure = sample.pressure
        pendingToolType = sample.toolType
        pendingTiltRadians = sample.tiltRadians
        pendingPointIncrement += sample.historySize + 1L
        if (sample.isCancel) pendingCancelIncrement++
        if (sample.isPalm) pendingPalmIncrement++

        val shouldPublish = nowMillis - lastPublishMillis >= publishIntervalMs
        val published = if (shouldPublish) {
            val next = current.copy(
                pressure = pendingPressure,
                toolType = pendingToolType,
                tiltRadians = pendingTiltRadians,
                pointCount = current.pointCount + pendingPointIncrement,
                eventRateHz = eventRateHz,
                cancelEventCount = current.cancelEventCount + pendingCancelIncrement,
                palmEventCount = current.palmEventCount + pendingPalmIncrement
            )
            pendingPointIncrement = 0L
            pendingCancelIncrement = 0L
            pendingPalmIncrement = 0L
            lastPublishMillis = nowMillis
            next
        } else {
            null
        }

        if (elapsed >= rateWindowMs) {
            eventWindowStartMillis = nowMillis
            eventCounterSinceWindow = 0
        }

        return published
    }
}
