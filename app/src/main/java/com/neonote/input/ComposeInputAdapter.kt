package com.neonote.input

import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import com.neonote.engine.InputEvent
import com.neonote.engine.InputPointer
import com.neonote.engine.PointerEventType
import com.neonote.model.CanvasPoint

/**
 * Android/Compose-facing adapter for the pure InputRouter model.
 *
 * This class only translates platform pointer facts into engine input events and
 * diagnostic metadata. It intentionally does not keep viewport/camera state and
 * does not perform editor business actions.
 */
public class ComposeInputAdapter {
    public fun toInputEvent(
        type: PointerEventType,
        changes: List<PointerInputChange>,
        platformSnapshot: AndroidPointerSnapshot? = null,
        targetObjectId: String? = null,
        targetStrokeId: String? = null,
    ): InputEvent? {
        val activeChanges = changes.filter { change ->
            type == PointerEventType.Up || type == PointerEventType.Cancel || change.pressed || change.previousPressed
        }
        val pointers = activeChanges.mapIndexed { index, change ->
            val platformPointer = platformSnapshot?.pointerAt(index)
            change.toInputPointer(platformPointer, platformSnapshot?.source ?: AndroidInputSources.Unknown)
        }
        if (pointers.isEmpty()) return null
        return InputEvent(
            type = type,
            pointers = pointers,
            targetObjectId = targetObjectId,
            targetStrokeId = targetStrokeId,
        )
    }

    public fun diagnostics(
        event: PointerEvent,
        platformSnapshot: AndroidPointerSnapshot? = null,
    ): InputDiagnostics {
        val pointerCount = platformSnapshot?.pointerCount ?: event.changes.count { it.pressed || it.previousPressed }.coerceAtLeast(1)
        val primaryChange = event.changes.firstOrNull()
        val primaryPlatformPointer = platformSnapshot?.pointerAt(0)
        val source = platformSnapshot?.source ?: AndroidInputSources.Unknown
        val tool = classifyPointerTool(
            composeType = primaryChange?.type.toPlatformPointerType(),
            androidToolType = primaryPlatformPointer?.toolType ?: AndroidToolTypes.Unknown,
            androidSource = source,
        )
        return InputDiagnostics(
            tool = tool,
            pressure = primaryPlatformPointer?.pressure ?: 1f,
            pointerCount = pointerCount,
            deviceId = platformSnapshot?.deviceId,
            source = platformSnapshot?.source,
            sourceDescription = describeAndroidSource(platformSnapshot?.source),
        )
    }

    private fun PointerInputChange.toInputPointer(platformPointer: AndroidPointer?, source: Int): InputPointer = InputPointer(
        id = id.value.toInt(),
        position = CanvasPoint(position.x, position.y),
        tool = classifyPointerTool(
            composeType = type.toPlatformPointerType(),
            androidToolType = platformPointer?.toolType ?: AndroidToolTypes.Unknown,
            androidSource = source,
        ),
        pressure = platformPointer?.pressure ?: 1f,
    )
}

public fun MotionEvent.toAndroidPointerSnapshot(): AndroidPointerSnapshot {
    val eventSource = source
    return AndroidPointerSnapshot(
        deviceId = deviceId,
        source = eventSource,
        pointers = List(pointerCount) { index ->
            AndroidPointer(
                pointerId = getPointerId(index),
                toolType = getToolType(index),
                pressure = getPressure(index),
                source = eventSource,
            )
        },
    )
}

private fun PointerType?.toPlatformPointerType(): PlatformPointerType = when (this) {
    PointerType.Touch -> PlatformPointerType.Touch
    PointerType.Mouse -> PlatformPointerType.Mouse
    PointerType.Stylus -> PlatformPointerType.Stylus
    else -> PlatformPointerType.Unknown
}

@Suppress("unused")
private fun platformConstantsMatchAndroid(): Boolean =
    AndroidToolTypes.Finger == MotionEvent.TOOL_TYPE_FINGER &&
        AndroidToolTypes.Stylus == MotionEvent.TOOL_TYPE_STYLUS &&
        AndroidToolTypes.Mouse == MotionEvent.TOOL_TYPE_MOUSE &&
        AndroidToolTypes.Eraser == MotionEvent.TOOL_TYPE_ERASER &&
        AndroidInputSources.Touchscreen == InputDevice.SOURCE_TOUCHSCREEN &&
        AndroidInputSources.Mouse == InputDevice.SOURCE_MOUSE &&
        AndroidInputSources.Stylus == InputDevice.SOURCE_STYLUS
