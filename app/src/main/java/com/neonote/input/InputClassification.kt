package com.neonote.input

import com.neonote.engine.InputInkSample
import com.neonote.engine.PointerTool

public data class InputDiagnostics(
    val tool: PointerTool = PointerTool.Finger,
    val pressure: Float = 1f,
    val pointerCount: Int = 0,
    val deviceId: Int? = null,
    val source: Int? = null,
    val sourceDescription: String = "source=unknown",
) {
    public fun asToolbarText(): String = buildString {
        append("tool=").append(tool)
        append(" pressure=").append("%.2f".format(pressure))
        append(" pointers=").append(pointerCount)
        append(" device=").append(deviceId?.toString() ?: "unknown")
        append(' ')
        append(sourceDescription)
    }
}

public data class AndroidPointerSnapshot(
    val deviceId: Int,
    val source: Int,
    val pointers: List<AndroidPointer>,
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
    androidToolType == AndroidToolTypes.Stylus || androidToolType == AndroidToolTypes.Eraser -> PointerTool.SPen
    androidToolType == AndroidToolTypes.Mouse -> PointerTool.Mouse
    androidToolType == AndroidToolTypes.Finger -> PointerTool.Finger
    composeType == PlatformPointerType.Stylus || composeType == PlatformPointerType.Eraser -> PointerTool.SPen
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
