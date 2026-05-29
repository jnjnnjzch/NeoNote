package com.neonote.input

import com.neonote.model.InfiniteCanvas
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox

class InkEngine {
    private val _events = mutableListOf<InputEvent>()
    val events: List<InputEvent> get() = _events

    fun handle(event: InputEvent): RouteResult.Ink {
        _events += event
        return RouteResult.Ink(event)
    }
}

class InputRouter(
    private val inkEngine: InkEngine,
) {
    fun route(event: InputEvent): RouteResult = when {
        event.hasSpenPointer -> inkEngine.handle(event)
        event is InputEvent.Tap && event.isBlankCanvasTap -> RouteResult.CreateOrFocusRichContentBox(
            canvas = event.canvas.ensureRichContentBoxAt(event.x, event.y),
            x = event.x,
            y = event.y,
        )
        event.touchPointerCount >= 2 -> RouteResult.Zoom(event)
        event.touchPointerCount == 1 -> RouteResult.Pan(event)
        else -> RouteResult.Ignored(event)
    }

    private fun InfiniteCanvas.ensureRichContentBoxAt(x: Float, y: Float): InfiniteCanvas {
        val existingBox = objects.filterIsInstance<RichContentBox>().firstOrNull()
        if (existingBox != null) return copy(
            objects = objects.map { canvasObject ->
                if (canvasObject.id == existingBox.id) existingBox.copy(isFocused = true) else canvasObject
            },
        )

        return copy(
            objects = objects + RichContentBox(
                id = "rich-content-${objects.size + 1}",
                content = RichContent(),
                x = x,
                y = y,
                isFocused = true,
            ),
        )
    }
}

sealed interface InputEvent {
    val pointers: List<InputPointer>

    data class PointerMove(
        override val pointers: List<InputPointer>,
    ) : InputEvent

    data class Tap(
        override val pointers: List<InputPointer>,
        val x: Float,
        val y: Float,
        val canvas: InfiniteCanvas,
        val targetObjectId: String? = null,
        val targetStrokeId: String? = null,
    ) : InputEvent
}

data class InputPointer(
    val id: Int,
    val x: Float,
    val y: Float,
    val tool: PointerTool,
)

enum class PointerTool {
    SPEN,
    TOUCH,
}

sealed interface RouteResult {
    val event: InputEvent?

    data class Ink(override val event: InputEvent) : RouteResult

    data class Pan(override val event: InputEvent) : RouteResult

    data class Zoom(override val event: InputEvent) : RouteResult

    data class CreateOrFocusRichContentBox(
        val canvas: InfiniteCanvas,
        val x: Float,
        val y: Float,
    ) : RouteResult {
        override val event: InputEvent? = null
        val box: RichContentBox = canvas.objects.filterIsInstance<RichContentBox>().first { it.isFocused }
    }

    data class Ignored(override val event: InputEvent) : RouteResult
}

private val InputEvent.hasSpenPointer: Boolean
    get() = pointers.any { it.tool == PointerTool.SPEN }

private val InputEvent.touchPointerCount: Int
    get() = pointers.count { it.tool == PointerTool.TOUCH }

private val InputEvent.Tap.isBlankCanvasTap: Boolean
    get() = targetObjectId == null && targetStrokeId == null && touchPointerCount == 1
