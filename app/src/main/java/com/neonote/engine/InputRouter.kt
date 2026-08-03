package com.neonote.engine

import com.neonote.model.CanvasPoint
import com.neonote.model.InfiniteCanvas
import kotlin.math.hypot

/**
 * Pure pointer-event router. Stylus input is kept independent from touch
 * navigation so a user can rest a hand, pan with a finger, and write or erase
 * with the S Pen without switching the entire canvas into a fragile gesture
 * mode.
 */
public class InputRouter(
    private val tapSlop: Float = DefaultTapSlop,
) {
    private var activeFingerDown: PendingFingerDown? = null

    public fun route(canvas: InfiniteCanvas, event: InputEvent, mode: InputMode = InputMode.Write): InputRouteResult {
        val hasPhysicalEraser = event.pointers.any { it.tool == PointerTool.Eraser }
        if (hasPhysicalEraser) {
            activeFingerDown = null
            return InputRouteResult(canvas = canvas, action = event.toEraseAction())
        }

        if (mode == InputMode.Selection) {
            return routeSelectionMode(canvas, event)
        }

        val hasStylus = event.pointers.any { it.tool == PointerTool.SPen }
        if (mode == InputMode.Erase && hasStylus) {
            activeFingerDown = null
            return InputRouteResult(canvas = canvas, action = event.toEraseAction())
        }

        if (hasStylus) {
            activeFingerDown = null
            return InputRouteResult(canvas = canvas, action = event.toInkAction())
        }

        val surfacePointers = event.pointers.filter {
            it.tool == PointerTool.Finger || it.tool == PointerTool.Mouse
        }
        val touchPointers = surfacePointers.filter { it.tool == PointerTool.Finger }
        if (touchPointers.size >= 2) {
            activeFingerDown = null
            return InputRouteResult(canvas = canvas, action = InputAction.Zoom(centroid = touchPointers.centroid()))
        }

        val primary = surfacePointers.singleOrNull()
        if (mode == InputMode.Navigate || mode == InputMode.Erase) {
            return routeNavigationMode(canvas, event, primary)
        }

        return when (event.type) {
            PointerEventType.Down -> routeFingerDown(canvas, primary)
            PointerEventType.Move -> routeFingerMove(canvas, primary)
            PointerEventType.Up -> routeFingerUp(canvas, event, primary)
            PointerEventType.Cancel -> {
                activeFingerDown = null
                InputRouteResult(canvas = canvas, action = InputAction.CancelInteraction)
            }
        }
    }

    private fun routeFingerDown(
        canvas: InfiniteCanvas,
        pointer: InputPointer?,
    ): InputRouteResult {
        if (pointer == null) return InputRouteResult(canvas = canvas, action = InputAction.Ignored)
        activeFingerDown = PendingFingerDown(pointer.id, pointer.position)
        return InputRouteResult(canvas = canvas, action = InputAction.PendingTap(pointer.position))
    }

    private fun routeFingerMove(
        canvas: InfiniteCanvas,
        pointer: InputPointer?,
    ): InputRouteResult {
        if (pointer == null) return InputRouteResult(canvas = canvas, action = InputAction.Ignored)
        val pending = activeFingerDown
        if (pending != null && pointer.id == pending.pointerId) {
            val totalDx = pointer.position.x - pending.start.x
            val totalDy = pointer.position.y - pending.start.y
            if (pending.hasExceededTapSlop || hypot(totalDx, totalDy) > tapSlop) {
                val previous = if (pending.hasExceededTapSlop) pending.last else pending.start
                val dx = pointer.position.x - previous.x
                val dy = pointer.position.y - previous.y
                activeFingerDown = pending.copy(hasExceededTapSlop = true, last = pointer.position)
                return InputRouteResult(canvas = canvas, action = InputAction.PanBy(dx = dx, dy = dy))
            }
            return InputRouteResult(canvas = canvas, action = InputAction.PendingTap(pending.start))
        }
        return InputRouteResult(canvas = canvas, action = InputAction.Ignored)
    }

    private fun routeFingerUp(
        canvas: InfiniteCanvas,
        event: InputEvent,
        pointer: InputPointer?,
    ): InputRouteResult {
        val pending = activeFingerDown
        activeFingerDown = null
        if (pointer == null || pending == null || pointer.id != pending.pointerId) {
            return InputRouteResult(canvas = canvas, action = InputAction.Ignored)
        }
        if (pending.hasExceededTapSlop) {
            return InputRouteResult(canvas = canvas, action = InputAction.EndInteraction)
        }
        if (event.targetObjectId == null && event.targetStrokeId == null) {
            return InputRouteResult(
                canvas = canvas,
                action = InputAction.CreateOrFocusRichContentBox(pointer.position),
            )
        }
        return InputRouteResult(canvas = canvas, action = InputAction.FocusExisting(event.targetObjectId))
    }

    private fun routeNavigationMode(
        canvas: InfiniteCanvas,
        event: InputEvent,
        pointer: InputPointer?,
    ): InputRouteResult = when (event.type) {
        PointerEventType.Down -> routeFingerDown(canvas, pointer)
        PointerEventType.Move -> routeFingerMove(canvas, pointer)
        PointerEventType.Up -> {
            val pending = activeFingerDown
            activeFingerDown = null
            when {
                pointer == null || pending == null || pointer.id != pending.pointerId ->
                    InputRouteResult(canvas = canvas, action = InputAction.Ignored)
                pending.hasExceededTapSlop ->
                    InputRouteResult(canvas = canvas, action = InputAction.EndInteraction)
                else -> InputRouteResult(canvas = canvas, action = InputAction.Ignored)
            }
        }
        PointerEventType.Cancel -> {
            activeFingerDown = null
            InputRouteResult(canvas = canvas, action = InputAction.CancelInteraction)
        }
    }

    private fun routeSelectionMode(canvas: InfiniteCanvas, event: InputEvent): InputRouteResult = InputRouteResult(
        canvas = canvas,
        action = when (event.type) {
            PointerEventType.Down -> InputAction.BeginSelectionGesture(event.primaryPosition)
            PointerEventType.Move -> InputAction.UpdateSelectionGesture(event.primaryPosition)
            PointerEventType.Up -> InputAction.EndInteraction
            PointerEventType.Cancel -> InputAction.CancelInteraction
        },
    )

    private fun InputEvent.toInkAction(): InputAction = when (type) {
        PointerEventType.Down -> InputAction.BeginInk(primaryPosition, primaryPressure, primaryRawPressure)
        PointerEventType.Move -> InputAction.ContinueInk(primaryInkSamples)
        PointerEventType.Up -> InputAction.EndInteraction
        PointerEventType.Cancel -> InputAction.CancelInteraction
    }

    private fun InputEvent.toEraseAction(): InputAction = when (type) {
        PointerEventType.Down,
        PointerEventType.Move -> InputAction.EraseAt(primaryInkSamples.map { it.position })
        PointerEventType.Up -> InputAction.EndInteraction
        PointerEventType.Cancel -> InputAction.CancelInteraction
    }

    private data class PendingFingerDown(
        val pointerId: Int,
        val start: CanvasPoint,
        val last: CanvasPoint = start,
        val hasExceededTapSlop: Boolean = false,
    )

    public companion object {
        public const val DefaultTapSlop: Float = 8f
    }
}

public enum class InputMode {
    /** Finger taps create text boxes; stylus input writes. */
    Write,

    /** Finger input only navigates; stylus input writes. */
    Navigate,

    Selection,
    Erase,
}

public enum class PointerEventType {
    Down,
    Move,
    Up,
    Cancel,
}

public enum class PointerTool {
    SPen,
    Eraser,
    Finger,
    Mouse,
}

public data class InputPointer(
    val id: Int,
    val position: CanvasPoint,
    val tool: PointerTool,
    val pressure: Float = 1f,
    val rawPressure: Float? = null,
    val historicalSamples: List<InputInkSample> = emptyList(),
)

public data class InputInkSample(
    val position: CanvasPoint,
    val pressure: Float,
    val rawPressure: Float? = null,
)

public data class InputEvent(
    val type: PointerEventType,
    val pointers: List<InputPointer>,
    val targetObjectId: String? = null,
    val targetStrokeId: String? = null,
) {
    init {
        require(pointers.isNotEmpty()) { "InputEvent requires at least one pointer" }
    }

    val primaryPosition: CanvasPoint get() = pointers.first().position
    val primaryPressure: Float get() = pointers.first().pressure
    val primaryRawPressure: Float? get() = pointers.first().rawPressure
    val primaryInkSamples: List<InputInkSample>
        get() {
            val primary = pointers.first()
            return primary.historicalSamples + InputInkSample(primary.position, primary.pressure, primary.rawPressure)
        }
}

public data class InputRouteResult(
    val canvas: InfiniteCanvas,
    val action: InputAction,
)

public sealed interface InputAction {
    public data class BeginInk(val position: CanvasPoint, val pressure: Float, val rawPressure: Float? = null) : InputAction
    public data class ContinueInk(val samples: List<InputInkSample>) : InputAction
    public data class EraseAt(val positions: List<CanvasPoint>) : InputAction
    public data class PendingTap(val position: CanvasPoint) : InputAction
    public data class PanBy(val dx: Float, val dy: Float) : InputAction
    public data class Zoom(val centroid: CanvasPoint) : InputAction
    public data class CreateOrFocusRichContentBox(val position: CanvasPoint) : InputAction
    public data class FocusExisting(val objectId: String?) : InputAction
    public data class BeginSelectionGesture(val position: CanvasPoint) : InputAction
    public data class UpdateSelectionGesture(val position: CanvasPoint) : InputAction
    public data object EndInteraction : InputAction
    public data object CancelInteraction : InputAction
    public data object Ignored : InputAction
}

private fun List<InputPointer>.centroid(): CanvasPoint = CanvasPoint(
    x = sumOf { it.position.x.toDouble() }.toFloat() / size,
    y = sumOf { it.position.y.toDouble() }.toFloat() / size,
)
