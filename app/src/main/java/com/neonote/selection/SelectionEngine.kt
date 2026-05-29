package com.neonote.selection

import com.neonote.model.FloatingImage
import com.neonote.model.InfiniteCanvas
import com.neonote.model.InkLayer
import com.neonote.model.InkStroke
import com.neonote.model.RichContentBox

/**
 * Tracks mixed selections across floating canvas objects and handwriting strokes.
 */
data class SelectionState(
    val selectedObjectIds: Set<String> = emptySet(),
    val selectedStrokeIds: Set<String> = emptySet(),
) {
    fun isObjectSelected(id: String): Boolean = id in selectedObjectIds

    fun isStrokeSelected(id: String): Boolean = id in selectedStrokeIds
}

class SelectionEngine {
    fun select(
        canvas: InfiniteCanvas,
        objectIds: Set<String> = emptySet(),
        strokeIds: Set<String> = emptySet(),
    ): SelectionState {
        val availableObjectIds = canvas.objects.mapTo(mutableSetOf()) { it.id }
        val availableStrokeIds = canvas.inkLayer.strokes.mapTo(mutableSetOf()) { it.id }

        return SelectionState(
            selectedObjectIds = objectIds.intersect(availableObjectIds),
            selectedStrokeIds = strokeIds.intersect(availableStrokeIds),
        )
    }

    fun moveSelection(
        canvas: InfiniteCanvas,
        selection: SelectionState,
        dx: Float,
        dy: Float,
    ): InfiniteCanvas {
        val movedObjects = canvas.objects.map { canvasObject ->
            if (!selection.isObjectSelected(canvasObject.id)) return@map canvasObject

            when (canvasObject) {
                is RichContentBox -> canvasObject.copy(
                    x = canvasObject.x + dx,
                    y = canvasObject.y + dy,
                )
                is FloatingImage -> canvasObject.copy(
                    x = canvasObject.x + dx,
                    y = canvasObject.y + dy,
                )
            }
        }
        val movedStrokes = canvas.inkLayer.strokes.map { stroke ->
            if (selection.isStrokeSelected(stroke.id)) stroke.translate(dx, dy) else stroke
        }

        return canvas.copy(
            objects = movedObjects,
            inkLayer = InkLayer(strokes = movedStrokes),
        )
    }

    private fun InkStroke.translate(dx: Float, dy: Float): InkStroke = copy(
        points = points.map { point -> point.copy(x = point.x + dx, y = point.y + dy) },
    )
}
