package com.neonote.engine

import com.neonote.model.InfiniteCanvas
import com.neonote.model.InkLayer
import com.neonote.model.InkPoint
import com.neonote.model.InkStroke

/**
 * Builds pressure-aware ink strokes before committing them to an InkLayer.
 */
public class InkEngine(
    private val idGenerator: IdGenerator,
) {
    public fun execute(session: InkSession, command: InkCommand): InkCommandResult = when (command) {
        is InkCommand.BeginStroke -> beginStroke(
            session = session,
            point = command.point,
            strokeId = command.strokeId ?: idGenerator.nextId("stroke"),
        )
        is InkCommand.AppendPoint -> appendPoint(session, command.point)
        InkCommand.EndStroke -> endStroke(session)
        InkCommand.CancelStroke -> InkCommandResult.StrokeCancelled(session = session.copy(activeStroke = null))
    }

    public fun beginStroke(
        session: InkSession,
        point: InkPoint,
        strokeId: String = idGenerator.nextId("stroke"),
    ): InkCommandResult.StrokeBegun {
        require(session.activeStroke == null) { "A stroke is already active" }
        val activeStroke = InkStroke(id = strokeId, points = listOf(point))
        return InkCommandResult.StrokeBegun(session = session.copy(activeStroke = activeStroke), stroke = activeStroke)
    }

    public fun appendPoint(session: InkSession, point: InkPoint): InkCommandResult.PointAppended {
        val stroke = requireNotNull(session.activeStroke) { "No active stroke" }
        val updatedStroke = stroke.copy(points = stroke.points + point)
        return InkCommandResult.PointAppended(session = session.copy(activeStroke = updatedStroke), stroke = updatedStroke)
    }

    public fun endStroke(session: InkSession): InkCommandResult.StrokeEnded {
        val stroke = requireNotNull(session.activeStroke) { "No active stroke" }
        val updatedLayer = session.inkLayer.copy(strokes = session.inkLayer.strokes + stroke)
        return InkCommandResult.StrokeEnded(
            session = session.copy(inkLayer = updatedLayer, activeStroke = null),
            stroke = stroke,
        )
    }
}

public data class InkSession(
    val inkLayer: InkLayer = InkLayer(),
    val activeStroke: InkStroke? = null,
) {
    public companion object {
        public fun fromCanvas(canvas: InfiniteCanvas): InkSession = InkSession(inkLayer = canvas.inkLayer)
    }
}

public sealed interface InkCommand {
    public data class BeginStroke(val point: InkPoint, val strokeId: String? = null) : InkCommand
    public data class AppendPoint(val point: InkPoint) : InkCommand
    public data object EndStroke : InkCommand
    public data object CancelStroke : InkCommand
}

public sealed interface InkCommandResult {
    public data class StrokeBegun(val session: InkSession, val stroke: InkStroke) : InkCommandResult
    public data class PointAppended(val session: InkSession, val stroke: InkStroke) : InkCommandResult
    public data class StrokeEnded(val session: InkSession, val stroke: InkStroke) : InkCommandResult
    public data class StrokeCancelled(val session: InkSession) : InkCommandResult
}
