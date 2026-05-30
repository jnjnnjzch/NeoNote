package com.neonote.engine

import com.neonote.model.CanvasObject
import com.neonote.model.CanvasObjectRef
import com.neonote.model.CanvasPoint
import com.neonote.model.CanvasRect
import com.neonote.model.FloatingImage
import com.neonote.model.InfiniteCanvas
import com.neonote.model.InkLayer
import com.neonote.model.InkPoint
import com.neonote.model.InkStroke
import com.neonote.model.InkStrokeRef
import com.neonote.model.RichContentBox
import com.neonote.model.SelectionState
import kotlin.math.hypot

/**
 * Maintains a mixed selection of handwriting strokes and canvas objects.
 */
public class SelectionEngine {

    public fun hitTestCanvasObject(canvasObject: CanvasObject, point: CanvasPoint): Boolean =
        canvasObject.bounds.contains(point)

    public fun hitTestCanvasObjects(canvas: InfiniteCanvas, point: CanvasPoint): List<CanvasObject> =
        canvas.objects
            .filter { hitTestCanvasObject(it, point) }
            .sortedWith(compareByDescending<CanvasObject> { it.zIndex }.thenByDescending { it.id })

    public fun hitTestInkStroke(
        stroke: InkStroke,
        point: CanvasPoint,
        tolerance: Float = DefaultStrokeHitTolerance,
    ): Boolean {
        if (stroke.points.isEmpty()) return false
        if (stroke.points.size == 1) {
            val onlyPoint = stroke.points.single().toCanvasPoint()
            return onlyPoint.distanceTo(point) <= tolerance
        }

        return stroke.points.zipWithNext().any { (start, end) ->
            point.distanceToSegment(start.toCanvasPoint(), end.toCanvasPoint()) <= tolerance
        }
    }

    public fun hitTestInkStrokes(
        canvas: InfiniteCanvas,
        point: CanvasPoint,
        tolerance: Float = DefaultStrokeHitTolerance,
    ): List<InkStroke> = canvas.inkLayer.strokes.filter { hitTestInkStroke(it, point, tolerance) }

    public fun selectWithLasso(
        canvas: InfiniteCanvas,
        lassoPath: List<CanvasPoint>,
        strokeTolerance: Float = DefaultStrokeHitTolerance,
    ): SelectionState {
        if (lassoPath.size < 2) return SelectionState()

        val selectedObjectIds = canvas.objects
            .filter { lassoIntersectsRect(lassoPath, it.bounds) }
            .mapTo(mutableSetOf()) { it.id }
        val selectedStrokeIds = canvas.inkLayer.strokes
            .filter { lassoIntersectsStroke(lassoPath, it, strokeTolerance) }
            .mapTo(mutableSetOf()) { it.id }

        return select(canvas, objectIds = selectedObjectIds, strokeIds = selectedStrokeIds)
    }

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

    public fun selectedBounds(canvas: InfiniteCanvas, selection: SelectionState): CanvasRect? {
        val objectBounds = canvas.objects
            .filter { selection.isObjectSelected(it.id) }
            .map { it.bounds }
        val strokeBounds = canvas.inkLayer.strokes
            .filter { selection.isStrokeSelected(it.id) }
            .mapNotNull { it.boundsOrNull() }
        return (objectBounds + strokeBounds).unionOrNull()
    }

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



    private fun lassoIntersectsStroke(
        lassoPath: List<CanvasPoint>,
        stroke: InkStroke,
        tolerance: Float,
    ): Boolean {
        val strokePoints = stroke.points.map { it.toCanvasPoint() }
        if (strokePoints.isEmpty()) return false
        if (strokePoints.any { point -> point.isInsidePolygon(lassoPath) || nearPolyline(point, lassoPath, tolerance) }) {
            return true
        }
        return strokePoints.zipWithNext().any { (start, end) ->
            lassoSegments(lassoPath).any { (lassoStart, lassoEnd) ->
                segmentsIntersect(start, end, lassoStart, lassoEnd) ||
                    start.distanceToSegment(lassoStart, lassoEnd) <= tolerance ||
                    end.distanceToSegment(lassoStart, lassoEnd) <= tolerance ||
                    lassoStart.distanceToSegment(start, end) <= tolerance ||
                    lassoEnd.distanceToSegment(start, end) <= tolerance
            }
        }
    }

    private fun lassoIntersectsRect(lassoPath: List<CanvasPoint>, rect: CanvasRect): Boolean {
        val corners = listOf(
            CanvasPoint(rect.left, rect.top),
            CanvasPoint(rect.right, rect.top),
            CanvasPoint(rect.right, rect.bottom),
            CanvasPoint(rect.left, rect.bottom),
        )
        if (corners.any { it.isInsidePolygon(lassoPath) }) return true
        if (lassoPath.any { rect.contains(it) }) return true

        val rectEdges = corners.zipWithNext() + (corners.last() to corners.first())
        return lassoSegments(lassoPath).any { (lassoStart, lassoEnd) ->
            rectEdges.any { (rectStart, rectEnd) -> segmentsIntersect(lassoStart, lassoEnd, rectStart, rectEnd) }
        }
    }

    private fun lassoSegments(lassoPath: List<CanvasPoint>): List<Pair<CanvasPoint, CanvasPoint>> =
        lassoPath.zipWithNext() + (lassoPath.last() to lassoPath.first())

    private fun nearPolyline(point: CanvasPoint, polyline: List<CanvasPoint>, tolerance: Float): Boolean =
        polyline.zipWithNext().any { (start, end) -> point.distanceToSegment(start, end) <= tolerance }

    private fun InkPoint.toCanvasPoint(): CanvasPoint = CanvasPoint(x = x, y = y)

    private fun CanvasPoint.distanceTo(other: CanvasPoint): Float = hypot(x - other.x, y - other.y)

    private fun CanvasPoint.distanceToSegment(start: CanvasPoint, end: CanvasPoint): Float {
        val dx = end.x - start.x
        val dy = end.y - start.y
        if (dx == 0f && dy == 0f) return distanceTo(start)
        val t = (((x - start.x) * dx + (y - start.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        val projection = CanvasPoint(x = start.x + t * dx, y = start.y + t * dy)
        return distanceTo(projection)
    }

    private fun CanvasPoint.isInsidePolygon(polygon: List<CanvasPoint>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            val crosses = (pi.y > y) != (pj.y > y) &&
                x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y) + pi.x
            if (crosses) inside = !inside
            j = i
        }
        return inside
    }

    private fun segmentsIntersect(a: CanvasPoint, b: CanvasPoint, c: CanvasPoint, d: CanvasPoint): Boolean {
        val o1 = orientation(a, b, c)
        val o2 = orientation(a, b, d)
        val o3 = orientation(c, d, a)
        val o4 = orientation(c, d, b)

        if (o1 == 0f && c.isOnSegment(a, b)) return true
        if (o2 == 0f && d.isOnSegment(a, b)) return true
        if (o3 == 0f && a.isOnSegment(c, d)) return true
        if (o4 == 0f && b.isOnSegment(c, d)) return true

        return (o1 > 0f) != (o2 > 0f) && (o3 > 0f) != (o4 > 0f)
    }

    private fun orientation(a: CanvasPoint, b: CanvasPoint, c: CanvasPoint): Float {
        val value = (b.y - a.y) * (c.x - b.x) - (b.x - a.x) * (c.y - b.y)
        return if (kotlin.math.abs(value) < GeometryEpsilon) 0f else value
    }

    private fun CanvasPoint.isOnSegment(start: CanvasPoint, end: CanvasPoint): Boolean =
        x in minOf(start.x, end.x)..maxOf(start.x, end.x) &&
            y in minOf(start.y, end.y)..maxOf(start.y, end.y)

    public companion object {
        public const val DefaultStrokeHitTolerance: Float = 6f
        private const val GeometryEpsilon: Float = 0.0001f
    }

    private fun InkStroke.boundsOrNull(): CanvasRect? {
        if (points.isEmpty()) return null
        return CanvasRect(
            left = points.minOf { it.x },
            top = points.minOf { it.y },
            right = points.maxOf { it.x },
            bottom = points.maxOf { it.y },
        )
    }

    private fun List<CanvasRect>.unionOrNull(): CanvasRect? {
        if (isEmpty()) return null
        return CanvasRect(
            left = minOf { it.left },
            top = minOf { it.top },
            right = maxOf { it.right },
            bottom = maxOf { it.bottom },
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
