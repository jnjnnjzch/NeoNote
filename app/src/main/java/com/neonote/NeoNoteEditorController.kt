package com.neonote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.neonote.engine.CanvasCommand
import com.neonote.engine.CanvasCommandResult
import com.neonote.engine.CanvasEngine
import com.neonote.engine.InputAction
import com.neonote.engine.InputEvent
import com.neonote.engine.InputMode
import com.neonote.engine.InputRouteResult
import com.neonote.engine.InputRouter
import com.neonote.engine.SelectionCommand
import com.neonote.engine.SelectionCommandResult
import com.neonote.engine.SelectionEngine
import com.neonote.model.CanvasObject
import com.neonote.model.CanvasPoint
import com.neonote.model.CanvasSize
import com.neonote.model.EditorState
import com.neonote.model.EditorTool
import com.neonote.model.InfiniteCanvas
import com.neonote.model.InlineText
import com.neonote.model.NeoNoteDocument
import com.neonote.model.NotePage
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.SelectionState
import com.neonote.model.ViewportState
import com.neonote.input.InputDiagnostics

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
) {
    public var state: EditorState by mutableStateOf(initialState)
        private set

    public var inputDiagnostics: InputDiagnostics by mutableStateOf(InputDiagnostics())
        private set

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
            is InputAction.PanBy -> panViewportBy(action.dx, action.dy)
            is InputAction.CreateOrFocusRichContentBox -> focusOrCreateRichContentBox(screenToDocument(action.position))
            is InputAction.FocusExisting -> action.objectId?.let(::focusRichContentBox)
            else -> Unit
        }
        return result
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
        val oldViewport = state.viewport
        val oldZoom = oldViewport.zoomScale
        val newZoom = (oldZoom * zoomChange).coerceIn(MinZoomScale, MaxZoomScale)
        if (newZoom == oldZoom) return

        val scaleChange = newZoom / oldZoom
        val newPanX = screenCentroid.x - (screenCentroid.x - oldViewport.panOffsetX) * scaleChange
        val newPanY = screenCentroid.y - (screenCentroid.y - oldViewport.panOffsetY) * scaleChange
        state = state.copy(
            viewport = oldViewport.copy(
                panOffsetX = newPanX,
                panOffsetY = newPanY,
                zoomScale = newZoom,
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
        val zoom = state.viewport.zoomScale
        val dx = screenDelta.x / zoom
        val dy = screenDelta.y / zoom
        val movedCanvas = selectionEngine.moveSelection(currentCanvas, state.selection, dx = dx, dy = dy)
        state = state.copy(document = state.document.withCanvas(movedCanvas))
    }

    public fun screenToDocument(screenPosition: CanvasPoint): CanvasPoint = state.viewport.screenToDocument(screenPosition)

    public fun documentToScreen(documentPosition: CanvasPoint): CanvasPoint = state.viewport.documentToScreen(documentPosition)

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

public fun ViewportState.screenToDocument(screenPosition: CanvasPoint): CanvasPoint = CanvasPoint(
    x = (screenPosition.x - panOffsetX) / zoomScale,
    y = (screenPosition.y - panOffsetY) / zoomScale,
)

public fun ViewportState.documentToScreen(documentPosition: CanvasPoint): CanvasPoint = CanvasPoint(
    x = documentPosition.x * zoomScale + panOffsetX,
    y = documentPosition.y * zoomScale + panOffsetY,
)

public fun InfiniteCanvas.topMostObjectAt(position: CanvasPoint): CanvasObject? = objects
    .sortedWith(compareBy<CanvasObject> { it.zIndex }.thenBy { it.id })
    .lastOrNull { it.bounds.contains(position) }

private fun InfiniteCanvas.setFocusedRichContentBox(focusedId: String?): InfiniteCanvas = copy(
    objects = objects.map { canvasObject ->
        if (canvasObject is RichContentBox) canvasObject.copy(isFocused = canvasObject.id == focusedId) else canvasObject
    },
)
