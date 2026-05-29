package com.neonote.engine

import com.neonote.model.CanvasObject
import com.neonote.model.CanvasPosition
import com.neonote.model.FloatingImage
import com.neonote.model.InfiniteCanvas
import com.neonote.model.RichContentBox

/**
 * Pure operations for adding, moving, and deleting objects on an infinite canvas.
 */
public class CanvasEngine {
    public fun execute(canvas: InfiniteCanvas, command: CanvasCommand): CanvasCommandResult = when (command) {
        is CanvasCommand.AddObject -> addObject(canvas, command.canvasObject)
        is CanvasCommand.MoveObject -> moveObject(canvas, command.objectId, command.position)
        is CanvasCommand.DeleteObject -> deleteObject(canvas, command.objectId)
    }

    public fun addObject(canvas: InfiniteCanvas, canvasObject: CanvasObject): CanvasCommandResult.ObjectAdded =
        CanvasCommandResult.ObjectAdded(canvas = canvas.copy(objects = canvas.objects + canvasObject), canvasObject = canvasObject)

    public fun moveObject(canvas: InfiniteCanvas, objectId: String, position: CanvasPosition): CanvasCommandResult.ObjectMoved {
        require(canvas.objects.any { it.id == objectId }) { "Unknown canvas object id: $objectId" }
        val movedObjects = canvas.objects.map { canvasObject ->
            if (canvasObject.id == objectId) canvasObject.withPosition(position) else canvasObject
        }
        return CanvasCommandResult.ObjectMoved(canvas = canvas.copy(objects = movedObjects), objectId = objectId, position = position)
    }

    public fun deleteObject(canvas: InfiniteCanvas, objectId: String): CanvasCommandResult.ObjectDeleted =
        CanvasCommandResult.ObjectDeleted(
            canvas = canvas.copy(objects = canvas.objects.filterNot { it.id == objectId }),
            objectId = objectId,
        )

    private fun CanvasObject.withPosition(position: CanvasPosition): CanvasObject = when (this) {
        is RichContentBox -> copy(position = position)
        is FloatingImage -> copy(position = position)
    }
}


public sealed interface CanvasCommand {
    public data class AddObject(val canvasObject: CanvasObject) : CanvasCommand
    public data class MoveObject(val objectId: String, val position: CanvasPosition) : CanvasCommand
    public data class DeleteObject(val objectId: String) : CanvasCommand
}

public sealed interface CanvasCommandResult {
    public data class ObjectAdded(val canvas: InfiniteCanvas, val canvasObject: CanvasObject) : CanvasCommandResult
    public data class ObjectMoved(val canvas: InfiniteCanvas, val objectId: String, val position: CanvasPosition) : CanvasCommandResult
    public data class ObjectDeleted(val canvas: InfiniteCanvas, val objectId: String) : CanvasCommandResult
}
