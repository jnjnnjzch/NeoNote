package com.neonote.engine

import com.neonote.model.CanvasObjectRef
import com.neonote.model.FloatingImage
import com.neonote.model.InfiniteCanvas
import com.neonote.model.InkLayer
import com.neonote.model.InkStroke
import com.neonote.model.InkStrokeRef
import com.neonote.model.RichContentBox
import com.neonote.model.SelectionState

/**
 * Maintains a mixed selection of handwriting strokes and canvas objects.
 */
public class SelectionEngine {
    public fun select(
        canvas: InfiniteCanvas,
        objectIds: Set<String> = emptySet(),
        strokeIds: Set<String> = emptySet(),
    ): SelectionState {
        val availableObjectIds = canvas.objects.mapTo(mutableSetOf()) { it.id }
        val availableStrokeIds = canvas.inkLayer.strokes.mapTo(mutableSetOf()) { it.id }
        val refs = objectIds.intersect(availableObjectIds).map(::CanvasObjectRef) +
            strokeIds.intersect(availableStrokeIds).map(::InkStrokeRef)
        return SelectionState(selectedRefs = refs.toSet())
    }

    public fun execute(selection: SelectionState, command: SelectionCommand): SelectionCommandResult = when (command) {
        is SelectionCommand.SelectInkStroke -> SelectionCommandResult.SelectionChanged(
            selection = selection.copy(selectedRefs = selection.selectedRefs + InkStrokeRef(command.strokeId)),
        )
        is SelectionCommand.SelectCanvasObject -> SelectionCommandResult.SelectionChanged(
            selection = selection.copy(selectedRefs = selection.selectedRefs + CanvasObjectRef(command.objectId)),
        )
        is SelectionCommand.ReplaceSelection -> SelectionCommandResult.SelectionChanged(selection = command.selection)
        is SelectionCommand.ToggleInkStroke -> SelectionCommandResult.SelectionChanged(
            selection = selection.copy(selectedRefs = selection.selectedRefs.toggle(InkStrokeRef(command.strokeId))),
        )
        is SelectionCommand.ToggleCanvasObject -> SelectionCommandResult.SelectionChanged(
            selection = selection.copy(selectedRefs = selection.selectedRefs.toggle(CanvasObjectRef(command.objectId))),
        )
        SelectionCommand.Clear -> SelectionCommandResult.SelectionChanged(selection = SelectionState())
    }

    public fun selectBoth(
        inkStrokeIds: Set<String>,
        canvasObjectIds: Set<String>,
    ): SelectionCommandResult.SelectionChanged = SelectionCommandResult.SelectionChanged(
        selection = SelectionState(
            selectedRefs = inkStrokeIds.map(::InkStrokeRef).toSet() + canvasObjectIds.map(::CanvasObjectRef),
        ),
    )

    public fun moveSelection(
        canvas: InfiniteCanvas,
        selection: SelectionState,
        dx: Float,
        dy: Float,
    ): InfiniteCanvas {
        val movedObjects = canvas.objects.map { canvasObject ->
            if (!selection.isObjectSelected(canvasObject.id)) return@map canvasObject

            when (canvasObject) {
                is RichContentBox -> canvasObject.copy(
                    position = canvasObject.position.copy(
                        x = canvasObject.position.x + dx,
                        y = canvasObject.position.y + dy,
                    ),
                )
                is FloatingImage -> canvasObject.copy(
                    position = canvasObject.position.copy(
                        x = canvasObject.position.x + dx,
                        y = canvasObject.position.y + dy,
                    ),
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

    private fun <T> Set<T>.toggle(ref: T): Set<T> = if (ref in this) this - ref else this + ref
}

public sealed interface SelectionCommand {
    public data class SelectInkStroke(val strokeId: String) : SelectionCommand
    public data class SelectCanvasObject(val objectId: String) : SelectionCommand
    public data class ToggleInkStroke(val strokeId: String) : SelectionCommand
    public data class ToggleCanvasObject(val objectId: String) : SelectionCommand
    public data class ReplaceSelection(val selection: SelectionState) : SelectionCommand
    public data object Clear : SelectionCommand
}

public sealed interface SelectionCommandResult {
    public data class SelectionChanged(val selection: SelectionState) : SelectionCommandResult
}
