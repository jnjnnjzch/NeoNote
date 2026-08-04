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

/** Mixed selection and geometry transforms for handwriting and canvas objects. */
public class SelectionEngine {
    public fun hitTestCanvasObject(canvasObject: CanvasObject, point: CanvasPoint): Boolean =
        canvasObject.bounds.contains(point)

    public fun hitTestCanvasObjects(canvas: InfiniteCanvas, point: CanvasPoint): List<CanvasObject> =
        canvas.objects.filter { hitTestCanvasObject(it, point) }
            .sortedWith(compareByDescending<CanvasObject> { it.zIndex }.thenByDescending { it.id })

    public fun hitTestInkStroke(
        stroke: InkStroke,
        point: CanvasPoint,
        tolerance: Float = DefaultStrokeHitTolerance,
    ): Boolean {
        if (stroke.points.isEmpty()) return false
        if (stroke.points.size == 1) return stroke.points.single().toCanvasPoint().distanceTo(point) <= tolerance
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
        val objects = canvas.objects.filter { lassoIntersectsRect(lassoPath, it.bounds) }.mapTo(mutableSetOf()) { it.id }
        val strokes = canvas.inkLayer.strokes.filter { lassoIntersectsStroke(lassoPath, it, strokeTolerance) }
            .mapTo(mutableSetOf()) { it.id }
        return select(canvas, objects, strokes)
    }

    public fun select(
        canvas: InfiniteCanvas,
        objectIds: Set<String> = emptySet(),
        strokeIds: Set<String> = emptySet(),
    ): SelectionState {
        val availableObjects = canvas.objects.mapTo(mutableSetOf(), CanvasObject::id)
        val availableStrokes = canvas.inkLayer.strokes.mapTo(mutableSetOf(), InkStroke::id)
        return SelectionState(
            selectedRefs = objectIds.intersect(availableObjects).map(::CanvasObjectRef).toSet() +
                strokeIds.intersect(availableStrokes).map(::InkStrokeRef),
        )
    }

    public fun execute(selection: SelectionState, command: SelectionCommand): SelectionCommandResult = when (command) {
        is SelectionCommand.SelectInkStroke -> changed(selection.copy(selectedRefs = selection.selectedRefs + InkStrokeRef(command.strokeId)))
        is SelectionCommand.SelectCanvasObject -> changed(selection.copy(selectedRefs = selection.selectedRefs + CanvasObjectRef(command.objectId)))
        is SelectionCommand.ReplaceSelection -> changed(command.selection)
        is SelectionCommand.ToggleInkStroke -> changed(selection.copy(selectedRefs = selection.selectedRefs.toggle(InkStrokeRef(command.strokeId))))
        is SelectionCommand.ToggleCanvasObject -> changed(selection.copy(selectedRefs = selection.selectedRefs.toggle(CanvasObjectRef(command.objectId))))
        SelectionCommand.Clear -> changed(SelectionState())
    }

    public fun selectBoth(
        inkStrokeIds: Set<String>,
        canvasObjectIds: Set<String>,
    ): SelectionCommandResult.SelectionChanged = changed(
        SelectionState(inkStrokeIds.map(::InkStrokeRef).toSet() + canvasObjectIds.map(::CanvasObjectRef)),
    )

    public fun selectedBounds(canvas: InfiniteCanvas, selection: SelectionState): CanvasRect? {
        val objectBounds = canvas.objects.filter { selection.isObjectSelected(it.id) }.map(CanvasObject::bounds)
        val strokeBounds = canvas.inkLayer.strokes.filter { selection.isStrokeSelected(it.id) }.mapNotNull { it.boundsOrNull() }
        return (objectBounds + strokeBounds).unionOrNull()
    }

    public fun moveSelection(
        canvas: InfiniteCanvas,
        selection: SelectionState,
        dx: Float,
        dy: Float,
    ): InfiniteCanvas = transformSelection(canvas, selection) { point ->
        CanvasPoint(point.x + dx, point.y + dy)
    }

    public fun scaleSelection(
        canvas: InfiniteCanvas,
        selection: SelectionState,
        scaleX: Float,
        scaleY: Float = scaleX,
        anchor: CanvasPoint = selectedBounds(canvas, selection)?.center ?: CanvasPoint.Zero,
    ): InfiniteCanvas {
        val sx = scaleX.coerceIn(0.05f, 20f)
        val sy = scaleY.coerceIn(0.05f, 20f)
        return transformSelection(canvas, selection) { point ->
            CanvasPoint(
                anchor.x + (point.x - anchor.x) * sx,
                anchor.y + (point.y - anchor.y) * sy,
            )
        }
    }

    public fun resizeSelectedObjects(
        canvas: InfiniteCanvas,
        selection: SelectionState,
        widthScale: Float,
        heightScale: Float,
        anchor: CanvasPoint = selectedBounds(canvas, selection)?.let { CanvasPoint(it.left, it.top) } ?: CanvasPoint.Zero,
    ): InfiniteCanvas = scaleSelection(canvas, selection, widthScale, heightScale, anchor)

    public fun deleteSelection(canvas: InfiniteCanvas, selection: SelectionState): InfiniteCanvas = canvas.copy(
        objects = canvas.objects.filterNot { selection.isObjectSelected(it.id) },
        inkLayer = canvas.inkLayer.copy(strokes = canvas.inkLayer.strokes.filterNot { selection.isStrokeSelected(it.id) }),
    )

    public fun duplicateSelection(
        canvas: InfiniteCanvas,
        selection: SelectionState,
        idGenerator: IdGenerator,
        offsetX: Float = 24f,
        offsetY: Float = 24f,
    ): SelectionTransformResult {
        val maxZ = canvas.objects.maxOfOrNull(CanvasObject::zIndex) ?: 0
        var z = maxZ + 1
        val copies = canvas.objects.filter { selection.isObjectSelected(it.id) }.map { objectValue ->
            val position = CanvasPoint(objectValue.position.x + offsetX, objectValue.position.y + offsetY)
            when (objectValue) {
                is RichContentBox -> objectValue.copy(
                    id = idGenerator.nextId("rich-content"),
                    position = position,
                    zIndex = z++,
                    isFocused = false,
                )
                is FloatingImage -> objectValue.copy(
                    id = idGenerator.nextId("image"),
                    position = position,
                    zIndex = z++,
                )
            }
        }
        val strokeCopies = canvas.inkLayer.strokes.filter { selection.isStrokeSelected(it.id) }.map { stroke ->
            stroke.copy(
                id = idGenerator.nextId("stroke"),
                points = stroke.points.map { it.copy(x = it.x + offsetX, y = it.y + offsetY) },
            )
        }
        val nextCanvas = canvas.copy(
            objects = canvas.objects + copies,
            inkLayer = canvas.inkLayer.copy(strokes = canvas.inkLayer.strokes + strokeCopies),
        )
        val nextSelection = SelectionState(
            copies.map { CanvasObjectRef(it.id) }.toSet() + strokeCopies.map { InkStrokeRef(it.id) },
        )
        return SelectionTransformResult(nextCanvas, nextSelection)
    }

    public fun bringSelectionToFront(canvas: InfiniteCanvas, selection: SelectionState): InfiniteCanvas {
        var nextZ = (canvas.objects.maxOfOrNull(CanvasObject::zIndex) ?: 0) + 1
        return canvas.copy(objects = canvas.objects.map { objectValue ->
            if (!selection.isObjectSelected(objectValue.id)) objectValue
            else when (objectValue) {
                is RichContentBox -> objectValue.copy(zIndex = nextZ++)
                is FloatingImage -> objectValue.copy(zIndex = nextZ++)
            }
        })
    }

    public fun sendSelectionToBack(canvas: InfiniteCanvas, selection: SelectionState): InfiniteCanvas {
        var nextZ = (canvas.objects.minOfOrNull(CanvasObject::zIndex) ?: 0) - selection.selectedRefs.size
        return canvas.copy(objects = canvas.objects.map { objectValue ->
            if (!selection.isObjectSelected(objectValue.id)) objectValue
            else when (objectValue) {
                is RichContentBox -> objectValue.copy(zIndex = nextZ++)
                is FloatingImage -> objectValue.copy(zIndex = nextZ++)
            }
        })
    }

    public fun setSelectionLocked(canvas: InfiniteCanvas, selection: SelectionState, locked: Boolean): InfiniteCanvas =
        canvas.copy(objects = canvas.objects.map { objectValue ->
            if (!selection.isObjectSelected(objectValue.id)) objectValue
            else when (objectValue) {
                is RichContentBox -> objectValue.copy(isLocked = locked)
                is FloatingImage -> objectValue.copy(isLocked = locked)
            }
        })

    private fun transformSelection(
        canvas: InfiniteCanvas,
        selection: SelectionState,
        transform: (CanvasPoint) -> CanvasPoint,
    ): InfiniteCanvas {
        val objects = canvas.objects.map { objectValue ->
            if (!selection.isObjectSelected(objectValue.id) || objectValue.isLocked()) return@map objectValue
            val oldTopLeft = objectValue.position
            val oldBottomRight = CanvasPoint(oldTopLeft.x + objectValue.size.width, oldTopLeft.y + objectValue.size.height)
            val newTopLeft = transform(oldTopLeft)
            val newBottomRight = transform(oldBottomRight)
            val width = kotlin.math.abs(newBottomRight.x - newTopLeft.x).coerceAtLeast(24f)
            val height = kotlin.math.abs(newBottomRight.y - newTopLeft.y).coerceAtLeast(24f)
            val position = CanvasPoint(minOf(newTopLeft.x, newBottomRight.x), minOf(newTopLeft.y, newBottomRight.y))
            when (objectValue) {
                is RichContentBox -> objectValue.copy(position = position, size = objectValue.size.copy(width = width, height = height))
                is FloatingImage -> objectValue.copy(position = position, size = objectValue.size.copy(width = width, height = height))
            }
        }
        val strokes = canvas.inkLayer.strokes.map { stroke ->
            if (!selection.isStrokeSelected(stroke.id)) stroke
            else stroke.copy(points = stroke.points.map { point ->
                transform(CanvasPoint(point.x, point.y)).let { point.copy(x = it.x, y = it.y) }
            })
        }
        return canvas.copy(objects = objects, inkLayer = InkLayer(strokes))
    }

    private fun CanvasObject.isLocked(): Boolean = when (this) {
        is RichContentBox -> isLocked
        is FloatingImage -> isLocked
    }

    private fun lassoIntersectsStroke(lassoPath: List<CanvasPoint>, stroke: InkStroke, tolerance: Float): Boolean {
        val points = stroke.points.map { it.toCanvasPoint() }
        if (points.isEmpty()) return false
        if (points.any { it.isInsidePolygon(lassoPath) || nearPolyline(it, lassoPath, tolerance) }) return true
        return points.zipWithNext().any { (start, end) ->
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
            CanvasPoint(rect.left, rect.top), CanvasPoint(rect.right, rect.top),
            CanvasPoint(rect.right, rect.bottom), CanvasPoint(rect.left, rect.bottom),
        )
        if (corners.any { it.isInsidePolygon(lassoPath) } || lassoPath.any(rect::contains)) return true
        val edges = corners.zipWithNext() + (corners.last() to corners.first())
        return lassoSegments(lassoPath).any { (a, b) -> edges.any { (c, d) -> segmentsIntersect(a, b, c, d) } }
    }

    private fun lassoSegments(path: List<CanvasPoint>): List<Pair<CanvasPoint, CanvasPoint>> =
        path.zipWithNext() + (path.last() to path.first())

    private fun nearPolyline(point: CanvasPoint, polyline: List<CanvasPoint>, tolerance: Float): Boolean =
        polyline.zipWithNext().any { (start, end) -> point.distanceToSegment(start, end) <= tolerance }

    private fun InkPoint.toCanvasPoint(): CanvasPoint = CanvasPoint(x, y)
    private fun CanvasPoint.distanceTo(other: CanvasPoint): Float = hypot(x - other.x, y - other.y)

    private fun CanvasPoint.distanceToSegment(start: CanvasPoint, end: CanvasPoint): Float {
        val dx = end.x - start.x
        val dy = end.y - start.y
        if (dx == 0f && dy == 0f) return distanceTo(start)
        val t = (((x - start.x) * dx + (y - start.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        return distanceTo(CanvasPoint(start.x + t * dx, start.y + t * dy))
    }

    private fun CanvasPoint.isInsidePolygon(polygon: List<CanvasPoint>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            val crosses = (pi.y > y) != (pj.y > y) && x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y) + pi.x
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
        x in minOf(start.x, end.x)..maxOf(start.x, end.x) && y in minOf(start.y, end.y)..maxOf(start.y, end.y)

    private fun InkStroke.boundsOrNull(): CanvasRect? = if (points.isEmpty()) null else CanvasRect(
        points.minOf(InkPoint::x), points.minOf(InkPoint::y), points.maxOf(InkPoint::x), points.maxOf(InkPoint::y),
    )

    private fun List<CanvasRect>.unionOrNull(): CanvasRect? = if (isEmpty()) null else CanvasRect(
        minOf(CanvasRect::left), minOf(CanvasRect::top), maxOf(CanvasRect::right), maxOf(CanvasRect::bottom),
    )

    private fun <T> Set<T>.toggle(ref: T): Set<T> = if (ref in this) this - ref else this + ref
    private fun changed(selection: SelectionState): SelectionCommandResult.SelectionChanged =
        SelectionCommandResult.SelectionChanged(selection)

    public companion object {
        public const val DefaultStrokeHitTolerance: Float = 6f
        private const val GeometryEpsilon: Float = 0.0001f
    }
}

public data class SelectionTransformResult(val canvas: InfiniteCanvas, val selection: SelectionState)

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
