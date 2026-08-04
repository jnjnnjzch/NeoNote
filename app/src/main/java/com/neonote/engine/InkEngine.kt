package com.neonote.engine

import com.neonote.model.InfiniteCanvas
import com.neonote.model.InkLayer
import com.neonote.model.InkPoint
import com.neonote.model.InkStroke
import com.neonote.model.InkStrokeStyle
import kotlin.math.sqrt

/** Builds styled pressure-aware strokes and performs vector-safe erasing. */
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
            style = command.style,
        )
        is InkCommand.AppendPoint -> appendPoint(session, command.point)
        is InkCommand.AppendPoints -> appendPoints(session, command.points)
        InkCommand.EndStroke -> endStroke(session)
        InkCommand.CancelStroke -> InkCommandResult.StrokeCancelled(session.copy(activeStroke = null))
        is InkCommand.EraseWholeStrokes -> eraseWholeStrokes(session, command.points, command.radius)
        is InkCommand.EraseSegments -> eraseSegments(session, command.points, command.radius)
    }

    public fun beginStroke(
        session: InkSession,
        point: InkPoint,
        strokeId: String = idGenerator.nextId("stroke"),
        style: InkStrokeStyle = InkStrokeStyle(),
    ): InkCommandResult.StrokeBegun {
        require(session.activeStroke == null) { "A stroke is already active" }
        val prepared = point.prepareForStroke(previous = null, pressureEnabled = style.pressureEnabled)
        val stroke = InkStroke(strokeId, listOf(prepared), style.normalized())
        return InkCommandResult.StrokeBegun(session.copy(activeStroke = stroke), stroke)
    }

    public fun appendPoint(session: InkSession, point: InkPoint): InkCommandResult.PointAppended =
        appendPoints(session, listOf(point))

    public fun appendPoints(session: InkSession, points: List<InkPoint>): InkCommandResult.PointAppended {
        require(points.isNotEmpty()) { "No points to append" }
        val stroke = requireNotNull(session.activeStroke) { "No active stroke" }
        val updatedPoints = points.fold(stroke.points) { accumulated, point ->
            accumulated + point.prepareForStroke(accumulated.lastOrNull(), stroke.style.pressureEnabled)
        }
        val updatedStroke = stroke.copy(points = updatedPoints)
        return InkCommandResult.PointAppended(session.copy(activeStroke = updatedStroke), updatedStroke)
    }

    public fun endStroke(session: InkSession): InkCommandResult.StrokeEnded {
        val stroke = requireNotNull(session.activeStroke) { "No active stroke" }
        val layer = session.inkLayer.copy(strokes = session.inkLayer.strokes + stroke)
        return InkCommandResult.StrokeEnded(session.copy(inkLayer = layer, activeStroke = null), stroke)
    }

    public fun eraseWholeStrokes(
        session: InkSession,
        points: List<InkPoint>,
        radius: Float,
    ): InkCommandResult.InkErased {
        if (points.isEmpty() || session.inkLayer.strokes.isEmpty()) {
            return InkCommandResult.InkErased(session, emptySet())
        }
        val safeRadius = radius.coerceAtLeast(0.5f)
        val removed = session.inkLayer.strokes.filter { stroke ->
            stroke.points.any { strokePoint -> points.any { it.distanceTo(strokePoint) <= safeRadius } }
        }.mapTo(mutableSetOf(), InkStroke::id)
        val layer = session.inkLayer.copy(strokes = session.inkLayer.strokes.filterNot { it.id in removed })
        return InkCommandResult.InkErased(session.copy(inkLayer = layer), removed)
    }

    /**
     * Removes only touched vector sections. Untouched runs become independent
     * strokes with stable style and fresh ids, so later lasso/undo operations
     * remain well-defined.
     */
    public fun eraseSegments(
        session: InkSession,
        points: List<InkPoint>,
        radius: Float,
    ): InkCommandResult.InkErased {
        if (points.isEmpty() || session.inkLayer.strokes.isEmpty()) {
            return InkCommandResult.InkErased(session, emptySet())
        }
        val safeRadius = radius.coerceAtLeast(0.5f)
        val changedIds = mutableSetOf<String>()
        val nextStrokes = buildList {
            session.inkLayer.strokes.forEach { stroke ->
                val keep = stroke.points.map { strokePoint ->
                    points.none { eraser -> eraser.distanceTo(strokePoint) <= safeRadius }
                }
                if (keep.all { it }) {
                    add(stroke)
                    return@forEach
                }
                changedIds += stroke.id
                contiguousRuns(stroke.points, keep).forEachIndexed { runIndex, run ->
                    if (run.size >= 2) {
                        add(stroke.copy(id = idGenerator.nextId("${stroke.id}-part-$runIndex"), points = run))
                    }
                }
            }
        }
        return InkCommandResult.InkErased(
            session.copy(inkLayer = session.inkLayer.copy(strokes = nextStrokes)),
            changedIds,
        )
    }

    private fun InkPoint.prepareForStroke(previous: InkPoint?, pressureEnabled: Boolean): InkPoint {
        val normalized = pressureNormalizer.normalize(this)
        val pressureAdjusted = if (pressureEnabled) normalized else normalized.copy(pressure = 1f)
        return smoother.smooth(previous, pressureAdjusted)
    }
}

public class InkPressureNormalizer(private val fallbackPressure: Float = 0.5f) {
    public fun normalize(point: InkPoint): InkPoint {
        val raw = point.rawPressure ?: point.pressure
        val normalized = when {
            raw.isNaN() || raw < 0f -> fallbackPressure
            else -> raw.coerceIn(0f, 1f)
        }
        return point.copy(pressure = normalized, rawPressure = raw)
    }
}

public class InkStrokeSmoother(smoothing: Float = DefaultSmoothing) {
    private val smoothing: Float = smoothing.coerceIn(0f, 1f)

    public fun smooth(previous: InkPoint?, current: InkPoint): InkPoint =
        smoothWithDiagnostics(previous, current).point

    public fun smoothWithDiagnostics(previous: InkPoint?, current: InkPoint): InkSmoothingResult {
        if (previous == null || smoothing == 0f) {
            return InkSmoothingResult(
                current,
                InkSmoothingDiagnostics.from(current, current, smoothing, didSmooth = false),
            )
        }
        val weight = 1f - smoothing
        val smoothed = current.copy(
            x = lerp(previous.x, current.x, weight),
            y = lerp(previous.y, current.y, weight),
        )
        return InkSmoothingResult(
            smoothed,
            InkSmoothingDiagnostics.from(current, smoothed, smoothing, didSmooth = true),
        )
    }

    public companion object { public const val DefaultSmoothing: Float = 0.35f }
}

public data class InkSmoothingResult(val point: InkPoint, val diagnostics: InkSmoothingDiagnostics)

public data class InkSmoothingDiagnostics(
    val rawX: Float,
    val rawY: Float,
    val smoothedX: Float,
    val smoothedY: Float,
    val deltaX: Float,
    val deltaY: Float,
    val deltaDistance: Float,
    val smoothing: Float,
    val didSmooth: Boolean,
) {
    public companion object {
        public fun from(raw: InkPoint, smoothed: InkPoint, smoothing: Float, didSmooth: Boolean): InkSmoothingDiagnostics {
            val dx = smoothed.x - raw.x
            val dy = smoothed.y - raw.y
            return InkSmoothingDiagnostics(
                raw.x, raw.y, smoothed.x, smoothed.y,
                dx, dy, sqrt(dx * dx + dy * dy), smoothing, didSmooth,
            )
        }
    }
}

public object InkStrokeWidthMapper {
    public const val MinStrokeWidth: Float = 1.5f
    public const val MaxStrokeWidth: Float = 6f

    public fun widthForPressure(pressure: Float): Float {
        val normalized = if (pressure.isNaN()) 0.5f else pressure.coerceIn(0f, 1f)
        return lerp(MinStrokeWidth, MaxStrokeWidth, normalized)
    }

    public fun widthForPressure(pressure: Float, style: InkStrokeStyle): Float {
        val pressureFactor = if (style.pressureEnabled) {
            val normalized = if (pressure.isNaN()) 0.5f else pressure.coerceIn(0f, 1f)
            0.45f + normalized * 0.85f
        } else {
            1f
        }
        return style.normalized().baseWidth * pressureFactor
    }

    public fun widthForSegment(start: InkPoint, end: InkPoint): Float =
        widthForPressure((start.pressure + end.pressure) / 2f)

    public fun widthForSegment(start: InkPoint, end: InkPoint, style: InkStrokeStyle): Float =
        widthForPressure((start.pressure + end.pressure) / 2f, style)
}

public data class InkSession(
    val inkLayer: InkLayer = InkLayer(),
    val activeStroke: InkStroke? = null,
) {
    public companion object {
        public fun fromCanvas(canvas: InfiniteCanvas): InkSession = InkSession(canvas.inkLayer)
    }
}

public sealed interface InkCommand {
    public data class BeginStroke(
        val point: InkPoint,
        val strokeId: String? = null,
        val style: InkStrokeStyle = InkStrokeStyle(),
    ) : InkCommand
    public data class AppendPoint(val point: InkPoint) : InkCommand
    public data class AppendPoints(val points: List<InkPoint>) : InkCommand
    public data object EndStroke : InkCommand
    public data object CancelStroke : InkCommand
    public data class EraseWholeStrokes(val points: List<InkPoint>, val radius: Float) : InkCommand
    public data class EraseSegments(val points: List<InkPoint>, val radius: Float) : InkCommand
}

public sealed interface InkCommandResult {
    public data class StrokeBegun(val session: InkSession, val stroke: InkStroke) : InkCommandResult
    public data class PointAppended(val session: InkSession, val stroke: InkStroke) : InkCommandResult
    public data class StrokeEnded(val session: InkSession, val stroke: InkStroke) : InkCommandResult
    public data class StrokeCancelled(val session: InkSession) : InkCommandResult
    public data class InkErased(val session: InkSession, val changedStrokeIds: Set<String>) : InkCommandResult
}

private fun contiguousRuns(points: List<InkPoint>, keep: List<Boolean>): List<List<InkPoint>> {
    val runs = mutableListOf<MutableList<InkPoint>>()
    var current: MutableList<InkPoint>? = null
    points.forEachIndexed { index, point ->
        if (keep.getOrElse(index) { false }) {
            val run = current ?: mutableListOf<InkPoint>().also { current = it; runs += it }
            run += point
        } else {
            current = null
        }
    }
    return runs
}

private fun InkPoint.distanceTo(other: InkPoint): Float {
    val dx = x - other.x
    val dy = y - other.y
    return sqrt(dx * dx + dy * dy)
}

private fun lerp(start: Float, stop: Float, amount: Float): Float = start + (stop - start) * amount
