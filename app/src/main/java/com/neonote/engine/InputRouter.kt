package com.neonote.engine

import com.neonote.model.CanvasPosition

/**
 * Maps low-level pointer events to editor-intent commands without touching UI code.
 */
public class InputRouter {
    public fun route(event: InputEvent): InputRouteResult = when (event) {
        is InputEvent.PointerDown -> routePointerDown(event)
        is InputEvent.PointerMove -> routePointerMove(event)
        is InputEvent.PointerUp -> InputRouteResult(InputAction.EndInteraction)
        is InputEvent.TapBlankCanvas -> InputRouteResult(InputAction.CreateOrFocusTextBox(event.position))
    }

    private fun routePointerDown(event: InputEvent.PointerDown): InputRouteResult = when (event.source) {
        InputSource.SPen -> InputRouteResult(InputAction.BeginWriting(event.position, event.pressure))
        InputSource.Finger -> InputRouteResult(InputAction.BeginPan(event.position))
        InputSource.Mouse -> InputRouteResult(InputAction.SelectAt(event.position))
    }

    private fun routePointerMove(event: InputEvent.PointerMove): InputRouteResult = when {
        event.pointers >= 2 -> InputRouteResult(InputAction.Zoom(event.scaleFactor, event.centroid))
        event.source == InputSource.SPen -> InputRouteResult(InputAction.ContinueWriting(event.position, event.pressure))
        event.source == InputSource.Finger -> InputRouteResult(InputAction.PanBy(event.deltaX, event.deltaY))
        else -> InputRouteResult(InputAction.MoveCursor(event.position))
    }
}

public enum class InputSource {
    SPen,
    Finger,
    Mouse,
}

public sealed interface InputEvent {
    public val source: InputSource

    public data class PointerDown(
        override val source: InputSource,
        val position: CanvasPosition,
        val pressure: Float = 1f,
    ) : InputEvent

    public data class PointerMove(
        override val source: InputSource,
        val position: CanvasPosition,
        val deltaX: Float = 0f,
        val deltaY: Float = 0f,
        val pressure: Float = 1f,
        val pointers: Int = 1,
        val scaleFactor: Float = 1f,
        val centroid: CanvasPosition = position,
    ) : InputEvent

    public data class PointerUp(
        override val source: InputSource,
        val position: CanvasPosition,
    ) : InputEvent

    public data class TapBlankCanvas(
        override val source: InputSource,
        val position: CanvasPosition,
    ) : InputEvent
}

public data class InputRouteResult(
    val action: InputAction,
)

public sealed interface InputAction {
    public data class BeginWriting(val position: CanvasPosition, val pressure: Float) : InputAction
    public data class ContinueWriting(val position: CanvasPosition, val pressure: Float) : InputAction
    public data class BeginPan(val position: CanvasPosition) : InputAction
    public data class PanBy(val deltaX: Float, val deltaY: Float) : InputAction
    public data class Zoom(val scaleFactor: Float, val centroid: CanvasPosition) : InputAction
    public data class CreateOrFocusTextBox(val position: CanvasPosition) : InputAction
    public data class SelectAt(val position: CanvasPosition) : InputAction
    public data class MoveCursor(val position: CanvasPosition) : InputAction
    public data object EndInteraction : InputAction
}
