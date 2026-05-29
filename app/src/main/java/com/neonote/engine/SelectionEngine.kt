package com.neonote.engine

/**
 * Maintains a mixed selection of handwriting strokes and canvas objects.
 */
public class SelectionEngine {
    public fun execute(selection: SelectionState, command: SelectionCommand): SelectionCommandResult = when (command) {
        is SelectionCommand.SelectInkStroke -> SelectionCommandResult.SelectionChanged(
            selection = selection.copy(inkStrokeIds = selection.inkStrokeIds + command.strokeId),
        )
        is SelectionCommand.SelectCanvasObject -> SelectionCommandResult.SelectionChanged(
            selection = selection.copy(canvasObjectIds = selection.canvasObjectIds + command.objectId),
        )
        is SelectionCommand.ReplaceSelection -> SelectionCommandResult.SelectionChanged(selection = command.selection)
        is SelectionCommand.ToggleInkStroke -> SelectionCommandResult.SelectionChanged(
            selection = selection.copy(inkStrokeIds = selection.inkStrokeIds.toggle(command.strokeId)),
        )
        is SelectionCommand.ToggleCanvasObject -> SelectionCommandResult.SelectionChanged(
            selection = selection.copy(canvasObjectIds = selection.canvasObjectIds.toggle(command.objectId)),
        )
        SelectionCommand.Clear -> SelectionCommandResult.SelectionChanged(selection = SelectionState())
    }

    public fun selectBoth(
        inkStrokeIds: Set<String>,
        canvasObjectIds: Set<String>,
    ): SelectionCommandResult.SelectionChanged = SelectionCommandResult.SelectionChanged(
        selection = SelectionState(inkStrokeIds = inkStrokeIds, canvasObjectIds = canvasObjectIds),
    )

    private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id
}

public data class SelectionState(
    val inkStrokeIds: Set<String> = emptySet(),
    val canvasObjectIds: Set<String> = emptySet(),
)

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
