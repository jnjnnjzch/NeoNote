package com.neonote.input

import com.neonote.engine.InputInkSample
import com.neonote.engine.PointerTool

public data class InputDiagnostics(
    val tool: PointerTool = PointerTool.Finger,
    val pressure: Float = 1f,
    val rawPressure: Float? = null,
    val pointerCount: Int = 0,
    val pressureMin: Float = pressure,
    val pressureMax: Float = pressure,
    val historicalSampleCount: Int = 0,
    val eventSampleCount: Int = if (historicalSampleCount > 0 || pointerCount > 0) historicalSampleCount + 1 else 0,
    val androidToolType: Int? = null,
    val androidToolTypeDescription: String = describeAndroidToolType(androidToolType),
    val isEraser: Boolean = androidToolType == AndroidToolTypes.Eraser,
    val buttonState: Int? = null,
    val buttonStateDescription: String = describeAndroidButtonState(buttonState),
    val deviceId: Int? = null,
    val source: Int? = null,
    val sourceDescription: String = "source=unknown",
) {
    public fun asToolbarText(): String = buildString {
        append("tool=").append(tool)
        append(" toolType=").append(androidToolTypeDescription)
        append(" pressure=").append("%.2f".format(pressure))
        append(" rawPressure=").append(rawPressure?.let { "%.2f".format(it) } ?: "unknown")
        append(" range=").append("%.2f..%.2f".format(pressureMin, pressureMax))
        append(" samples=").append(eventSampleCount)
        append(" hist=").append(historicalSampleCount)
        append(" eraser=").append(isEraser)
        append(" buttons=").append(buttonStateDescription)
        append(" pointers=").append(pointerCount)
        append(" device=").append(deviceId?.toString() ?: "unknown")
        append(' ')
        append(sourceDescription)
    }
}

public fun InputDiagnostics.withPressureSamples(samples: List<InputInkSample>): InputDiagnostics {
    if (samples.isEmpty()) return copy(pressureMin = pressure, pressureMax = pressure, eventSampleCount = 0)
    val pressures = samples.map { it.pressure }
    return copy(
        pressureMin = pressures.minOrNull() ?: pressure,
        pressureMax = pressures.maxOrNull() ?: pressure,
        historicalSampleCount = (samples.size - 1).coerceAtLeast(0),
        eventSampleCount = samples.size,
    )
}

public fun InputDiagnostics.mergeForThrottledDisplay(next: InputDiagnostics): InputDiagnostics {
    val combinedSampleCount = eventSampleCount + next.eventSampleCount
    return next.copy(
        pressureMin = minOf(pressureMin, next.pressureMin),
        pressureMax = maxOf(pressureMax, next.pressureMax),
        historicalSampleCount = (combinedSampleCount - 1).coerceAtLeast(0),
        eventSampleCount = combinedSampleCount,
    )
}

public data class AndroidPointerSnapshot(
    val deviceId: Int,
    val source: Int,
    val pointers: List<AndroidPointer>,
    val buttonState: Int? = null,
) {
    public val pointerCount: Int get() = pointers.size
    public fun pointerAt(index: Int): AndroidPointer? = pointers.getOrNull(index)
}

public data class AndroidPointer(
    val pointerId: Int,
    val toolType: Int,
    val pressure: Float,
    val source: Int,
    val historicalSamples: List<InputInkSample> = emptyList(),
)

public enum class PlatformPointerType {
    Touch,
    Mouse,
    Stylus,
    Eraser,
    Unknown,
}

public object AndroidToolTypes {
    public const val Unknown: Int = 0
    public const val Finger: Int = 1
    public const val Stylus: Int = 2
    public const val Mouse: Int = 3
    public const val Eraser: Int = 4
}

public object AndroidInputSources {
    public const val Unknown: Int = 0
    public const val ClassPointer: Int = 0x00000002
    public const val Touchscreen: Int = 0x00001000 or ClassPointer
    public const val Mouse: Int = 0x00002000 or ClassPointer
    public const val Stylus: Int = 0x00004000 or ClassPointer
}

public fun classifyPointerTool(
    composeType: PlatformPointerType,
    androidToolType: Int = AndroidToolTypes.Unknown,
    androidSource: Int = AndroidInputSources.Unknown,
): PointerTool = when {
    androidToolType == AndroidToolTypes.Eraser -> PointerTool.Eraser
    androidToolType == AndroidToolTypes.Stylus -> PointerTool.SPen
    androidToolType == AndroidToolTypes.Mouse -> PointerTool.Mouse
    androidToolType == AndroidToolTypes.Finger -> PointerTool.Finger
    composeType == PlatformPointerType.Eraser -> PointerTool.Eraser
    composeType == PlatformPointerType.Stylus -> PointerTool.SPen
    composeType == PlatformPointerType.Mouse -> PointerTool.Mouse
    androidSource.hasSource(AndroidInputSources.Stylus) -> PointerTool.SPen
    androidSource.hasSource(AndroidInputSources.Mouse) -> PointerTool.Mouse
    else -> PointerTool.Finger
}

public fun describeAndroidSource(source: Int?): String = when {
    source == null -> "source=unknown"
    source.hasSource(AndroidInputSources.Stylus) -> "source=stylus(${source.toHexSource()})"
    source.hasSource(AndroidInputSources.Mouse) -> "source=mouse(${source.toHexSource()})"
    source.hasSource(AndroidInputSources.Touchscreen) -> "source=touchscreen(${source.toHexSource()})"
    else -> "source=${source.toHexSource()}"
}

private fun Int.hasSource(sourceClass: Int): Boolean = sourceClass != AndroidInputSources.Unknown && (this and sourceClass) == sourceClass

private fun Int.toHexSource(): String = "0x${toUInt().toString(16)}"

public fun describeAndroidToolType(toolType: Int?): String = when (toolType) {
    null -> "unknown"
    AndroidToolTypes.Finger -> "finger"
    AndroidToolTypes.Stylus -> "stylus"
    AndroidToolTypes.Mouse -> "mouse"
    AndroidToolTypes.Eraser -> "eraser"
    else -> "unknown($toolType)"
}

public fun describeAndroidButtonState(buttonState: Int?): String = when (buttonState) {
    null -> "unknown"
    0 -> "none"
    else -> "0x${buttonState.toUInt().toString(16)}"
}
