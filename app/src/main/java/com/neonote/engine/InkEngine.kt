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
    private val pressureNormalizer: InkPressureNormalizer = InkPressureNormalizer(),
    private val smoother: InkStrokeSmoother = InkStrokeSmoother(),
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
        val activeStroke = InkStroke(id = strokeId, points = listOf(point.prepareForStroke(previous = null)))
        return InkCommandResult.StrokeBegun(session = session.copy(activeStroke = activeStroke), stroke = activeStroke)
    }

    public fun appendPoint(session: InkSession, point: InkPoint): InkCommandResult.PointAppended {
        val stroke = requireNotNull(session.activeStroke) { "No active stroke" }
        val updatedStroke = stroke.copy(points = stroke.points + point.prepareForStroke(previous = stroke.points.lastOrNull()))
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

    private fun InkPoint.prepareForStroke(previous: InkPoint?): InkPoint = smoother.smooth(
        previous = previous,
        current = pressureNormalizer.normalize(this),
    )
}

/**
 * Converts platform pressure into the canonical 0..1 range while keeping the
 * original hardware reading on InkPoint.rawPressure for diagnostics or future
 * brushes.
 */
public class InkPressureNormalizer(
    private val fallbackPressure: Float = 0.5f,
) {
    public fun normalize(point: InkPoint): InkPoint {
        val rawPressure = point.rawPressure ?: point.pressure
        val normalized = when {
            rawPressure.isNaN() -> fallbackPressure
            rawPressure < 0f -> fallbackPressure
            else -> rawPressure.coerceIn(0f, 1f)
        }
        return point.copy(pressure = normalized, rawPressure = rawPressure)
    }
}

/**
 * Small, pure stabilizer for ink points. It nudges subsequent samples toward a
 * weighted average of the previous stabilized sample and the current sample.
 */
public class InkStrokeSmoother(
    smoothing: Float = DefaultSmoothing,
) {
    private val smoothing: Float = smoothing.coerceIn(0f, 1f)

    public fun smooth(previous: InkPoint?, current: InkPoint): InkPoint {
        if (previous == null || smoothing == 0f) return current
        val currentWeight = 1f - smoothing
        return current.copy(
            x = lerp(previous.x, current.x, currentWeight),
            y = lerp(previous.y, current.y, currentWeight),
            pressure = lerp(previous.pressure, current.pressure, currentWeight).coerceIn(0f, 1f),
        )
    }

    public companion object {
        public const val DefaultSmoothing: Float = 0.35f
    }
}

public object InkStrokeWidthMapper {
    public const val MinStrokeWidth: Float = 1.5f
    public const val MaxStrokeWidth: Float = 6f

    public fun widthForPressure(pressure: Float): Float {
        val normalizedPressure = when {
            pressure.isNaN() -> 0.5f
            else -> pressure.coerceIn(0f, 1f)
        }
        return lerp(MinStrokeWidth, MaxStrokeWidth, normalizedPressure)
    }

    public fun widthForSegment(start: InkPoint, end: InkPoint): Float =
        widthForPressure((start.pressure + end.pressure) / 2f)
}

private fun lerp(start: Float, stop: Float, amount: Float): Float = start + (stop - start) * amount

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
