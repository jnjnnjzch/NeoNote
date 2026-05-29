package com.neonote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.neonote.engine.CanvasCommand
import com.neonote.engine.CanvasCommandResult
import com.neonote.engine.CanvasEngine
import com.neonote.engine.IdGenerator
import com.neonote.engine.InkCommand
import com.neonote.engine.InkCommandResult
import com.neonote.engine.InkEngine
import com.neonote.engine.InkSession
import com.neonote.engine.InputAction
import com.neonote.engine.InputEvent
import com.neonote.engine.InputInkSample
import com.neonote.engine.InputMode
import com.neonote.engine.InputRouteResult
import com.neonote.engine.InputRouter
import com.neonote.engine.SelectionCommand
import com.neonote.engine.SelectionCommandResult
import com.neonote.engine.SelectionEngine
import com.neonote.input.InputDiagnostics
import com.neonote.model.CanvasObject
import com.neonote.model.CanvasPoint
import com.neonote.model.CanvasSize
import com.neonote.model.EditorState
import com.neonote.model.EditorTool
import com.neonote.model.InfiniteCanvas
import com.neonote.model.InkPoint
import com.neonote.model.InkStroke
import com.neonote.model.InlineText
import com.neonote.model.NeoNoteDocument
import com.neonote.model.NotePage
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.SelectionState

private const val DefaultBoxWidth = 320f
private const val DefaultBoxHeight = 160f
private const val MinZoomScale = 0.25f
private const val MaxZoomScale = 4f

/**
 * Small reducer-style controller for the first v2 interactive vertical slice.
 * Composables translate UI events into these intents; document mutations stay
 * here and flow through the existing engines where appropriate.
 */
public class NeoNoteEditorController(
    initialState: EditorState = createTestEditorState(),
    private val canvasEngine: CanvasEngine = CanvasEngine(),
    private val selectionEngine: SelectionEngine = SelectionEngine(),
    private val inkEngine: InkEngine = InkEngine(SequentialIdGenerator()),
) {
    public var state: EditorState by mutableStateOf(initialState)
        private set

    public var inputDiagnostics: InputDiagnostics by mutableStateOf(InputDiagnostics())
        private set

    public var inkSession: InkSession by mutableStateOf(InkSession.fromCanvas(currentCanvas))
        private set

    public val activeInkStroke: InkStroke?
        get() = inkSession.activeStroke

    public val currentCanvas: InfiniteCanvas
        get() = currentPage.canvas

    private val currentPage: NotePage
        get() = state.document.pages.first { it.id == state.currentPageId }


    public fun updateInputDiagnostics(diagnostics: InputDiagnostics) {
        inputDiagnostics = diagnostics
    }

    public fun routeInputEvent(
        router: InputRouter,
        event: InputEvent,
        mode: InputMode = InputMode.Write,
    ): InputRouteResult {
        val result = router.route(canvas = currentCanvas, event = event, mode = mode)
        when (val action = result.action) {
            is InputAction.BeginInk -> beginInk(action.position, action.pressure, action.rawPressure)
            is InputAction.ContinueInk -> continueInk(action.samples)
            InputAction.EndInteraction -> endInkIfActive()
            InputAction.CancelInteraction -> cancelInkIfActive()
            is InputAction.PanBy -> panViewportBy(action.dx, action.dy)
            is InputAction.CreateOrFocusRichContentBox -> focusOrCreateRichContentBox(screenToDocument(action.position))
            is InputAction.FocusExisting -> action.objectId?.let(::focusRichContentBox)
            else -> Unit
        }
        return result
    }


    private fun beginInk(screenPosition: CanvasPoint, pressure: Float, rawPressure: Float?) {
        val point = screenPosition.toInkPoint(pressure, rawPressure)
        val result = inkEngine.execute(
            session = InkSession.fromCanvas(currentCanvas),
            command = InkCommand.BeginStroke(point),
        ) as InkCommandResult.StrokeBegun
        inkSession = result.session
    }

    private fun continueInk(samples: List<InputInkSample>) {
        if (inkSession.activeStroke == null) return
        samples.forEach { sample ->
            val result = inkEngine.execute(
                session = inkSession,
                command = InkCommand.AppendPoint(sample.position.toInkPoint(sample.pressure, sample.rawPressure)),
            ) as InkCommandResult.PointAppended
            inkSession = result.session
        }
    }

    private fun endInkIfActive() {
        if (inkSession.activeStroke == null) return
        val result = inkEngine.execute(inkSession, InkCommand.EndStroke) as InkCommandResult.StrokeEnded
        inkSession = result.session
        commitInkLayer(result.session.inkLayer)
    }

    private fun cancelInkIfActive() {
        if (inkSession.activeStroke == null) return
        val result = inkEngine.execute(inkSession, InkCommand.CancelStroke) as InkCommandResult.StrokeCancelled
        inkSession = result.session
    }

    private fun CanvasPoint.toInkPoint(pressure: Float, rawPressure: Float?): InkPoint {
        val documentPosition = screenToDocument(this)
        return InkPoint(
            x = documentPosition.x,
            y = documentPosition.y,
            pressure = pressure,
            rawPressure = rawPressure,
        )
    }

    private fun commitInkLayer(inkLayer: com.neonote.model.InkLayer) {
        val updatedCanvas = currentCanvas.copy(inkLayer = inkLayer)
        state = state.copy(document = state.document.withCanvas(updatedCanvas))
    }

    public fun setSelectionMode(enabled: Boolean) {
        state = state.copy(
            currentTool = if (enabled) EditorTool.Selection else EditorTool.Text,
            focusedRichContentBoxId = if (enabled) null else state.focusedRichContentBoxId,
            selection = if (enabled) state.selection else SelectionState(),
            document = state.document.withCanvas(currentCanvas.setFocusedRichContentBox(null)),
        )
    }

    public fun panViewportBy(screenDx: Float, screenDy: Float) {
        val viewport = state.viewport
        state = state.copy(
            viewport = viewport.copy(
                panOffsetX = viewport.panOffsetX + screenDx,
                panOffsetY = viewport.panOffsetY + screenDy,
            ),
        )
    }

    public fun zoomViewportBy(zoomChange: Float, screenCentroid: CanvasPoint) {
        if (zoomChange == 1f) return
        state = state.copy(
            viewport = state.viewport.zoomAroundScreenPoint(
                zoomChange = zoomChange,
                screenCentroid = screenCentroid,
                minZoomScale = MinZoomScale,
                maxZoomScale = MaxZoomScale,
            ),
        )
    }

    public fun focusOrCreateRichContentBox(documentPosition: CanvasPoint) {
        val existing = currentCanvas.topMostObjectAt(documentPosition) as? RichContentBox
        if (existing != null) {
            focusRichContentBox(existing.id)
            return
        }

        val box = RichContentBox(
            id = nextRichContentBoxId(),
            position = documentPosition,
            size = CanvasSize(DefaultBoxWidth, DefaultBoxHeight),
            zIndex = (currentCanvas.objects.maxOfOrNull { it.zIndex } ?: 0) + 1,
            content = RichContent(blocks = listOf(ParagraphNode(inlines = listOf(InlineText(""))))),
            isFocused = true,
        )
        val added = canvasEngine.execute(currentCanvas, CanvasCommand.AddObject(box)) as CanvasCommandResult.ObjectAdded
        state = state.copy(
            document = state.document.withCanvas(added.canvas.setFocusedRichContentBox(box.id)),
            focusedRichContentBoxId = box.id,
            selection = SelectionState(),
            currentTool = EditorTool.Text,
        )
    }

    public fun focusRichContentBox(boxId: String) {
        state = state.copy(
            document = state.document.withCanvas(currentCanvas.setFocusedRichContentBox(boxId)),
            focusedRichContentBoxId = boxId,
            selection = SelectionState(),
            currentTool = EditorTool.Text,
        )
    }

    public fun updateRichContentText(boxId: String, text: String) {
        val updatedCanvas = currentCanvas.copy(
            objects = currentCanvas.objects.map { canvasObject ->
                if (canvasObject.id == boxId && canvasObject is RichContentBox) {
                    canvasObject.copy(content = RichContent(blocks = listOf(ParagraphNode(inlines = listOf(InlineText(text))))))
                } else {
                    canvasObject
                }
            },
        )
        state = state.copy(document = state.document.withCanvas(updatedCanvas))
    }

    public fun selectCanvasObject(objectId: String) {
        val result = selectionEngine.execute(
            SelectionState(),
            SelectionCommand.SelectCanvasObject(objectId),
        ) as SelectionCommandResult.SelectionChanged
        state = state.copy(
            selection = result.selection,
            focusedRichContentBoxId = null,
            document = state.document.withCanvas(currentCanvas.setFocusedRichContentBox(null)),
        )
    }

    public fun clearSelection() {
        val result = selectionEngine.execute(state.selection, SelectionCommand.Clear) as SelectionCommandResult.SelectionChanged
        state = state.copy(selection = result.selection)
    }

    public fun moveSelectedObjectsByScreenDelta(screenDelta: Offset) {
        if (state.selection.selectedRefs.isEmpty()) return
        val documentDelta = screenDeltaToDocumentDelta(screenDelta)
        val movedCanvas = selectionEngine.moveSelection(
            canvas = currentCanvas,
            selection = state.selection,
            dx = documentDelta.x,
            dy = documentDelta.y,
        )
        state = state.copy(document = state.document.withCanvas(movedCanvas))
    }

    public fun screenToDocument(screenPosition: CanvasPoint): CanvasPoint = state.viewport.screenToDocument(screenPosition)

    public fun documentToScreen(documentPosition: CanvasPoint): CanvasPoint = state.viewport.documentToScreen(documentPosition)

    public fun screenDeltaToDocumentDelta(screenDelta: Offset): Offset = state.viewport.screenDeltaToDocumentDelta(screenDelta)

    private fun nextRichContentBoxId(): String {
        val next = currentCanvas.objects
            .filterIsInstance<RichContentBox>()
            .mapNotNull { it.id.removePrefix("rich-content-").toIntOrNull() }
            .maxOrNull()
            ?.plus(1) ?: 1
        return "rich-content-$next"
    }

    private fun NeoNoteDocument.withCanvas(canvas: InfiniteCanvas): NeoNoteDocument = copy(
        pages = pages.map { page -> if (page.id == state.currentPageId) page.copy(canvas = canvas) else page },
        revision = revision + 1,
    )
}

public fun createTestEditorState(): EditorState = EditorState(
    document = NeoNoteDocument(
        id = "test-document-v2",
        title = "NeoNote v2 Test Document",
        assetStoreId = "test-assets",
        pages = listOf(NotePage(id = "test-page-1", canvas = InfiniteCanvas())),
    ),
    currentTool = EditorTool.Text,
)


public fun InfiniteCanvas.topMostObjectAt(position: CanvasPoint): CanvasObject? = objects
    .sortedWith(compareBy<CanvasObject> { it.zIndex }.thenBy { it.id })
    .lastOrNull { it.bounds.contains(position) }

private fun InfiniteCanvas.setFocusedRichContentBox(focusedId: String?): InfiniteCanvas = copy(
    objects = objects.map { canvasObject ->
        if (canvasObject is RichContentBox) canvasObject.copy(isFocused = canvasObject.id == focusedId) else canvasObject
    },
)


private class SequentialIdGenerator : IdGenerator {
    private var nextId = 1

    override fun nextId(prefix: String): String = "$prefix-${nextId++}"
}
