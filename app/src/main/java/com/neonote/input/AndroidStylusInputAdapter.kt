package com.neonote.input

import android.view.MotionEvent
import com.neonote.engine.InputEvent
import com.neonote.engine.InputInkSample
import com.neonote.engine.InputPointer
import com.neonote.engine.PointerEventType
import com.neonote.engine.PointerTool
import com.neonote.model.CanvasPoint

public object AndroidStylusInputAdapter {
    public fun isStylusOrEraser(event: MotionEvent): Boolean =
        event.primaryStylusPointerIndex() != null

    public fun toInputEvent(event: MotionEvent): InputEvent? {
        val pointerIndex = event.primaryStylusPointerIndex() ?: return null
        val eventType = event.toPointerEventType() ?: return null
        val currentPressure = event.getPressure(pointerIndex)

        val historicalSamples = if (eventType == PointerEventType.Move) {
            event.historicalInkSamples(pointerIndex)
        } else {
            emptyList()
        }

        return InputEvent(
            type = eventType,
            pointers = listOf(
                InputPointer(
                    id = event.getPointerId(pointerIndex),
                    position = CanvasPoint(
                        x = event.getX(pointerIndex),
                        y = event.getY(pointerIndex),
                    ),
                    tool = PointerTool.SPen,
                    pressure = currentPressure,
                    rawPressure = currentPressure,
                    historicalSamples = historicalSamples,
                ),
            ),
        )
    }

    public fun toDiagnostics(event: MotionEvent): InputDiagnostics {
        val pointerIndex = event.primaryStylusPointerIndex()
            ?: return InputDiagnostics(
                tool = PointerTool.Finger,
                pressure = 1f,
                pointerCount = event.pointerCount,
                deviceId = event.deviceId,
                source = event.source,
                sourceDescription = describeAndroidSource(event.source),
            )

        val currentPressure = event.getPressure(pointerIndex)
        val currentSample = InputInkSample(
            position = CanvasPoint(
                x = event.getX(pointerIndex),
                y = event.getY(pointerIndex),
            ),
            pressure = currentPressure,
            rawPressure = currentPressure,
        )

        return InputDiagnostics(
            tool = PointerTool.SPen,
            pressure = currentPressure,
            pointerCount = event.pointerCount,
            deviceId = event.deviceId,
            source = event.source,
            sourceDescription = describeAndroidSource(event.source),
        ).withPressureSamples(event.historicalInkSamples(pointerIndex) + currentSample)
    }

    private fun MotionEvent.primaryStylusPointerIndex(): Int? {
        val actionPointerIndex = actionIndex
        if (actionPointerIndex in 0 until pointerCount && isStylusTool(actionPointerIndex)) {
            return actionPointerIndex
        }

        for (index in 0 until pointerCount) {
            if (isStylusTool(index)) return index
        }

        return null
    }

    private fun MotionEvent.isStylusTool(pointerIndex: Int): Boolean {
        val toolType = getToolType(pointerIndex)
        return toolType == MotionEvent.TOOL_TYPE_STYLUS ||
            toolType == MotionEvent.TOOL_TYPE_ERASER
    }

    private fun MotionEvent.toPointerEventType(): PointerEventType? =
        when (actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> PointerEventType.Down

            MotionEvent.ACTION_MOVE -> PointerEventType.Move

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> PointerEventType.Up

            MotionEvent.ACTION_CANCEL -> PointerEventType.Cancel

            else -> null
        }

    private fun MotionEvent.historicalInkSamples(pointerIndex: Int): List<InputInkSample> =
        List(historySize) { historyIndex ->
            val pressure = getHistoricalPressure(pointerIndex, historyIndex)
            InputInkSample(
                position = CanvasPoint(
                    x = getHistoricalX(pointerIndex, historyIndex),
                    y = getHistoricalY(pointerIndex, historyIndex),
                ),
                pressure = pressure,
                rawPressure = pressure,
            )
        }
}