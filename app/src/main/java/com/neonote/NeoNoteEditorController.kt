package com.neonote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.neonote.engine.CanvasCommand
import com.neonote.engine.CanvasCommandResult
import com.neonote.engine.CanvasEngine
import com.neonote.engine.DocumentCommand
import com.neonote.engine.DocumentCommandResult
import com.neonote.engine.DocumentEngine
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
import com.neonote.engine.InlineStyle
import com.neonote.engine.PersistenceDiagnostics
import com.neonote.engine.PersistenceResult
import com.neonote.engine.PersistenceStore
import com.neonote.engine.RichContentEditorSession
import com.neonote.engine.RichContentEngine
import com.neonote.engine.RichContentMeasurer
import com.neonote.engine.SelectionCommand
import com.neonote.engine.SelectionCommandResult
import com.neonote.engine.SelectionEngine
import com.neonote.engine.toPlainText
import com.neonote.input.InputDiagnostics
import com.neonote.input.mergeForThrottledDisplay
import com.neonote.model.CanvasObject
import com.neonote.model.CanvasObjectRef
import com.neonote.model.CanvasPoint
import com.neonote.model.CanvasRect
import com.neonote.model.CanvasSize
import com.neonote.model.EditorState
import com.neonote.model.EditorTool
import com.neonote.model.InfiniteCanvas
import com.neonote.model.InkPoint
import com.neonote.model.InkStroke
import com.neonote.model.InkStrokeRef
import com.neonote.model.ListKind
import com.neonote.model.NeoNoteDocument
import com.neonote.model.NotePage
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.SelectionState

private const val DefaultBoxWidth = 320f
private const val DefaultBoxHeight = 160f
private const val MinZoomScale = 0.25f
private const val MaxZoomScale = 4f
private const val DefaultInputDiagnosticsThrottleMillis = 32L

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
    private val richContentEngine: RichContentEngine = RichContentEngine(),
    private val richContentMeasurer: RichContentMeasurer = RichContentMeasurer(),
    private val documentEngine: DocumentEngine = DocumentEngine(SequentialIdGenerator()),
    private val inputDiagnosticsThrottleMillis: Long = DefaultInputDiagnosticsThrottleMillis,
    private val diagnosticsClockMillis: () -> Long = { System.currentTimeMillis() },
) {
    public var state: EditorState by mutableStateOf(initialState)
        private set

    public var inputDiagnostics: InputDiagnostics by mutableStateOf(InputDiagnostics())
        private set

    public var inkSession: InkSession by mutableStateOf(InkSession.fromCanvas(currentCanvas))
        private set

    public var persistenceStatus: String by mutableStateOf("Not saved")
        private set

    public var persistenceDiagnostics: PersistenceDiagnostics? by mutableStateOf(null)
        private set

    public val activeInkStroke: InkStroke?
        get() = inkSession.activeStroke

    public val activeLassoPath: List<CanvasPoint>
        get() = (activeSelectionGesture as? ActiveSelectionGesture.Lasso)?.path.orEmpty()

    public val selectedBounds: CanvasRect?
        get() = selectionEngine.selectedBounds(currentCanvas, state.selection)

    public val currentCanvas: InfiniteCanvas
        get() = currentPage.canvas

    public val currentPageIndex: Int
        get() = state.document.pages.indexOfFirst { it.id == state.currentPageId }.coerceAtLeast(0)

    public val currentPageNumber: Int
        get() = currentPageIndex + 1

    public val pageCount: Int
        get() = state.document.pages.size

    public val canSwitchToPreviousPage: Boolean
        get() = currentPageIndex > 0

    public val canSwitchToNextPage: Boolean
        get() = currentPageIndex < pageCount - 1

    private var activeSelectionGesture: ActiveSelectionGesture? by mutableStateOf(null)
    private var lastInputDiagnosticsUpdateMillis: Long? = null
    private var pendingInputDiagnostics: InputDiagnostics? = null
    private val richContentSessions: MutableMap<String, RichContentEditorSession> = mutableMapOf()

    private val currentPage: NotePage
        get() = state.document.pages.first { it.id == state.currentPageId }

    public suspend fun saveDocument(store: PersistenceStore): PersistenceResult.Saved {
        val saved = store.save(state.document)
        persistenceDiagnostics = saved.diagnostics
        persistenceStatus = "Saved ${saved.documentId} at revision ${saved.revision}" + saved.diagnostics.toStatusSuffix()
        return saved
    }

    public suspend fun loadDocument(
        store: PersistenceStore,
        documentId: String = state.document.id,
    ): PersistenceResult.Loaded {
        val loaded = store.load(documentId)
        val document = loaded.document
        if (document == null) {
            persistenceDiagnostics = null
            persistenceStatus = "No local document found for $documentId"
            return loaded
        }

        val nextPageId = document.pages.firstOrNull()?.id
        state = state.copy(
            document = document.withMeasuredRichContentBoxHeights(),
            currentPageId = nextPageId,
            focusedRichContentBoxId = null,
            selection = SelectionState(),
        )
        val cacheRebuildTimeMillis = elapsedMillis {
            inkSession = document.pages.firstOrNull()?.canvas?.let(InkSession::fromCanvas) ?: InkSession()
        }
        val diagnostics = loaded.diagnostics?.copy(cacheRebuildTimeMillis = cacheRebuildTimeMillis)
        persistenceDiagnostics = diagnostics
        persistenceStatus = "Loaded ${document.id} at revision ${document.revision}" + diagnostics.toStatusSuffix()
        return loaded.copy(diagnostics = diagnostics)
    }

    /**
     * Updates toolbar-only input diagnostics at a bounded cadence.
     *
     * The pending diagnostics are merged so the toolbar can still show the latest
     * tool and pressure plus a pressure range/sample count for throttled events.
     * This method is intentionally separate from [routeInputEvent], so throttling
     * Compose state writes cannot drop ink samples or alter pressure routing.
     */
    public fun updateInputDiagnostics(
        diagnostics: InputDiagnostics,
        eventTimeMillis: Long = diagnosticsClockMillis(),
        force: Boolean = false,
    ): Boolean {
        val pendingDiagnostics = pendingInputDiagnostics?.mergeForThrottledDisplay(diagnostics) ?: diagnostics
        val lastUpdateMillis = lastInputDiagnosticsUpdateMillis
        val shouldPublish = force ||
            inputDiagnosticsThrottleMillis <= 0L ||
            lastUpdateMillis == null ||
            eventTimeMillis - lastUpdateMillis >= inputDiagnosticsThrottleMillis ||
            diagnostics.tool != inputDiagnostics.tool

        return if (shouldPublish) {
            inputDiagnostics = pendingDiagnostics
            pendingInputDiagnostics = null
            lastInputDiagnosticsUpdateMillis = eventTimeMillis
            true
        } else {
            pendingInputDiagnostics = pendingDiagnostics
            false
        }
    }

    public fun addPage() {
        val result = documentEngine.execute(DocumentCommand.AddPage(state.document)) as DocumentCommandResult.PageAdded
        state = state.copy(document = result.document)
        switchPage(result.page.id)
    }

    public fun switchToPreviousPage() {
        if (!canSwitchToPreviousPage) return
        switchPage(state.document.pages[currentPageIndex - 1].id)
    }

    public fun switchToNextPage() {
        if (!canSwitchToNextPage) return
        switchPage(state.document.pages[currentPageIndex + 1].id)
    }

    public fun switchPage(pageId: String) {
        if (pageId == state.currentPageId) return
        state.focusedRichContentBoxId?.let(::commitRichContentEditing)
        val documentWithClearedFocus = state.document.withCanvasForPage(
            pageId = currentPage.id,
            canvas = currentCanvas.setFocusedRichContentBox(null),
        )
        val clearedState = state.copy(
            document = documentWithClearedFocus,
            focusedRichContentBoxId = null,
            selection = SelectionState(),
        )
        val result = documentEngine.execute(DocumentCommand.SwitchPage(clearedState, pageId)) as DocumentCommandResult.PageSwitched
        state = result.state
        activeSelectionGesture = null
        inkSession = InkSession.fromCanvas(currentCanvas)
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
            is InputAction.PanBy -> panViewportBy(action.dx, action.dy)
            is InputAction.CreateOrFocusRichContentBox -> focusOrCreateRichContentBox(screenToDocument(action.position))
            is InputAction.FocusExisting -> action.objectId?.let(::activateRichContentBox)
            is InputAction.BeginSelectionGesture -> beginSelectionGesture(action.position)
            is InputAction.UpdateSelectionGesture -> updateSelectionGesture(action.position)
            InputAction.EndInteraction -> endActiveInteraction()
            InputAction.CancelInteraction -> cancelActiveInteraction()
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
        val points = samples.map { sample -> sample.position.toInkPoint(sample.pressure, sample.rawPressure) }
        val result = inkEngine.execute(
            session = inkSession,
            command = InkCommand.AppendPoints(points),
        ) as InkCommandResult.PointAppended
        inkSession = result.session
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
        if (enabled) state.focusedRichContentBoxId?.let(::commitRichContentEditing)
        state = state.copy(
            currentTool = if (enabled) EditorTool.Selection else EditorTool.Pen,
            focusedRichContentBoxId = null,
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
            activateRichContentBox(existing.id)
            return
        }

        val box = RichContentBox(
            id = nextRichContentBoxId(),
            position = documentPosition,
            size = CanvasSize(DefaultBoxWidth, DefaultBoxHeight),
            zIndex = (currentCanvas.objects.maxOfOrNull { it.zIndex } ?: 0) + 1,
            content = RichContent(),
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

    /**
     * User intent for tapping an existing floating text box. Selection mode
     * selects the canvas object only; text mode enters editing and allows the
     * platform keyboard to appear.
     */
    public fun activateRichContentBox(boxId: String) {
        if (state.currentTool == EditorTool.Selection) {
            selectCanvasObject(boxId)
        } else {
            focusRichContentBox(boxId)
        }
    }

    public fun focusRichContentBox(boxId: String) {
        if (state.currentTool == EditorTool.Selection) return
        if (state.focusedRichContentBoxId != null && state.focusedRichContentBoxId != boxId) {
            commitRichContentEditing(state.focusedRichContentBoxId!!)
        }
        val box = currentCanvas.objects.filterIsInstance<RichContentBox>().firstOrNull { it.id == boxId } ?: return
        editorSessionFor(boxId, box).focus(box)
        state = state.copy(
            document = state.document.withCanvas(currentCanvas.setFocusedRichContentBox(boxId)),
            focusedRichContentBoxId = boxId,
            selection = SelectionState(),
            currentTool = EditorTool.Text,
        )
    }

    public fun updateRichContentText(boxId: String, text: String) {
        val previousText = richContentSessions[boxId]?.localEditableBuffer
            ?: currentCanvas.objects.filterIsInstance<RichContentBox>().firstOrNull { it.id == boxId }?.toPlainText()
            ?: ""
        updateRichContentFromPlatformInput(
            boxId = boxId,
            previousText = previousText,
            nextText = text,
            selectionStart = text.length,
            selectionEnd = text.length,
            hasActiveComposition = false,
        )
    }

    public fun updateRichContentFromPlatformInput(
        boxId: String,
        previousText: String,
        nextText: String,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
        hasActiveComposition: Boolean = false,
    ) {
        if (state.currentTool == EditorTool.Selection || state.selection.selectedRefs.isNotEmpty()) return

        val updatedCanvas = currentCanvas.updateRichContentBox(boxId) { box ->
            val session = editorSessionFor(boxId, box)
            val edit = if (hasActiveComposition) {
                session.replaceFromPlatformCompositionFallback(
                    nextText = nextText,
                    selectionStartPlainOffset = selectionStart,
                    selectionEndPlainOffset = selectionEnd,
                )
            } else {
                session.replaceFromPlatformInput(
                    previousText = previousText,
                    nextText = nextText,
                    selectionStartPlainOffset = selectionStart,
                    selectionEndPlainOffset = selectionEnd,
                )
            }
            richContentMeasurer.resizeBoxToMeasuredContent(edit.box)
        }
        state = state.copy(document = state.document.withCanvas(updatedCanvas))
    }

    public fun activeRichContentBlockIndex(boxId: String): Int? =
        richContentSessions[richContentSessionKey(boxId)]?.activeBlockIndex

    public fun focusRichContentParagraph(
        boxId: String,
        blockIndex: Int,
        selectionStart: Int = 0,
        selectionEnd: Int = selectionStart,
    ) {
        if (state.currentTool == EditorTool.Selection || state.selection.selectedRefs.isNotEmpty()) return
        focusRichContentBox(boxId)
        val box = currentCanvas.objects.filterIsInstance<RichContentBox>().firstOrNull { it.id == boxId } ?: return
        editorSessionFor(boxId, box).focusParagraph(blockIndex, selectionStart, selectionEnd)
    }

    public fun updateRichContentParagraphFromPlatformInput(
        boxId: String,
        blockIndex: Int,
        previousText: String,
        nextText: String,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
        hasActiveComposition: Boolean = false,
    ) {
        if (state.currentTool == EditorTool.Selection || state.selection.selectedRefs.isNotEmpty()) return

        val updatedCanvas = currentCanvas.updateRichContentBox(boxId) { box ->
            val session = editorSessionFor(boxId, box)
            val edit = if (hasActiveComposition) {
                session.replaceParagraphFromPlatformCompositionFallback(
                    blockIndex = blockIndex,
                    nextText = nextText,
                    selectionStartOffset = selectionStart,
                    selectionEndOffset = selectionEnd,
                )
            } else {
                session.replaceFromPlatformParagraphInput(
                    blockIndex = blockIndex,
                    previousText = previousText,
                    nextText = nextText,
                    selectionStartOffset = selectionStart,
                    selectionEndOffset = selectionEnd,
                )
            }
            richContentMeasurer.resizeBoxToMeasuredContent(edit.box)
        }
        state = state.copy(document = state.document.withCanvas(updatedCanvas))
    }

    public fun toggleRichContentParagraphStyle(
        boxId: String,
        blockIndex: Int,
        style: InlineStyle,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
    ) {
        if (state.currentTool == EditorTool.Selection || state.selection.selectedRefs.isNotEmpty()) return

        val updatedCanvas = currentCanvas.updateRichContentBox(boxId) { box ->
            val session = editorSessionFor(boxId, box)
            session.focusParagraph(blockIndex, selectionStart, selectionEnd)
            richContentMeasurer.resizeBoxToMeasuredContent(session.toggleStyle(style).box)
        }
        state = state.copy(document = state.document.withCanvas(updatedCanvas))
    }

    public fun toggleRichContentParagraphList(
        boxId: String,
        blockIndex: Int,
        kind: ListKind,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
    ) {
        if (state.currentTool == EditorTool.Selection || state.selection.selectedRefs.isNotEmpty()) return

        val updatedCanvas = currentCanvas.updateRichContentBox(boxId) { box ->
            val session = editorSessionFor(boxId, box)
            session.focusParagraph(blockIndex, selectionStart, selectionEnd)
            richContentMeasurer.resizeBoxToMeasuredContent(session.toggleList(kind).box)
        }
        state = state.copy(document = state.document.withCanvas(updatedCanvas))
    }

    public fun toggleRichContentStyle(
        boxId: String,
        style: InlineStyle,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
    ) {
        if (state.currentTool == EditorTool.Selection || state.selection.selectedRefs.isNotEmpty()) return

        val updatedCanvas = currentCanvas.updateRichContentBox(boxId) { box ->
            val session = editorSessionFor(boxId, box)
            session.setSelectionFromPlainOffsets(selectionStart, selectionEnd)
            richContentMeasurer.resizeBoxToMeasuredContent(session.toggleStyle(style).box)
        }
        state = state.copy(document = state.document.withCanvas(updatedCanvas))
    }

    public fun toggleRichContentList(
        boxId: String,
        kind: ListKind,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
    ) {
        if (state.currentTool == EditorTool.Selection || state.selection.selectedRefs.isNotEmpty()) return

        val updatedCanvas = currentCanvas.updateRichContentBox(boxId) { box ->
            val session = editorSessionFor(boxId, box)
            session.setSelectionFromPlainOffsets(selectionStart, selectionEnd)
            richContentMeasurer.resizeBoxToMeasuredContent(session.toggleList(kind).box)
        }
        state = state.copy(document = state.document.withCanvas(updatedCanvas))
    }

    public fun toggleRichContentTodoCheckedState(boxId: String, blockIndex: Int) {
        val updatedCanvas = currentCanvas.updateRichContentBox(boxId) { box ->
            val session = editorSessionFor(boxId, box)
            richContentMeasurer.resizeBoxToMeasuredContent(session.toggleTodoCheckedState(blockIndex).box)
        }
        state = state.copy(document = state.document.withCanvas(updatedCanvas))
    }

    public fun toggleActiveRichContentStyle(boxId: String, style: InlineStyle) {
        if (state.currentTool == EditorTool.Selection || state.selection.selectedRefs.isNotEmpty()) return

        val updatedCanvas = currentCanvas.updateRichContentBox(boxId) { box ->
            val session = editorSessionFor(boxId, box)
            richContentMeasurer.resizeBoxToMeasuredContent(session.toggleStyle(style).box)
        }
        state = state.copy(
            document = state.document.withCanvas(updatedCanvas.setFocusedRichContentBox(boxId)),
            focusedRichContentBoxId = boxId,
            currentTool = EditorTool.Text,
        )
    }

    public fun toggleActiveRichContentList(boxId: String, kind: ListKind) {
        if (state.currentTool == EditorTool.Selection || state.selection.selectedRefs.isNotEmpty()) return

        val updatedCanvas = currentCanvas.updateRichContentBox(boxId) { box ->
            val session = editorSessionFor(boxId, box)
            richContentMeasurer.resizeBoxToMeasuredContent(session.toggleList(kind).box)
        }
        state = state.copy(
            document = state.document.withCanvas(updatedCanvas.setFocusedRichContentBox(boxId)),
            focusedRichContentBoxId = boxId,
            currentTool = EditorTool.Text,
        )
    }

    public fun insertRichContentTablePlaceholder(boxId: String, rows: Int = 2, columns: Int = 2) {
        if (state.currentTool == EditorTool.Selection || state.selection.selectedRefs.isNotEmpty()) return

        val updatedCanvas = currentCanvas.updateRichContentBox(boxId) { box ->
            val session = editorSessionFor(boxId, box)
            richContentMeasurer.resizeBoxToMeasuredContent(session.insertTablePlaceholder(rows = rows, columns = columns).box)
        }
        state = state.copy(
            document = state.document.withCanvas(updatedCanvas.setFocusedRichContentBox(boxId)),
            focusedRichContentBoxId = boxId,
            currentTool = EditorTool.Text,
        )
    }

    public fun insertRichContentFormulaPlaceholder(boxId: String, expression: String = "") {
        if (state.currentTool == EditorTool.Selection || state.selection.selectedRefs.isNotEmpty()) return

        val updatedCanvas = currentCanvas.updateRichContentBox(boxId) { box ->
            val session = editorSessionFor(boxId, box)
            richContentMeasurer.resizeBoxToMeasuredContent(session.insertBlockFormulaPlaceholder(expression = expression).box)
        }
        state = state.copy(
            document = state.document.withCanvas(updatedCanvas.setFocusedRichContentBox(boxId)),
            focusedRichContentBoxId = boxId,
            currentTool = EditorTool.Text,
        )
    }

    public fun commitRichContentEditing(boxId: String) {
        val box = currentCanvas.objects.filterIsInstance<RichContentBox>().firstOrNull { it.id == boxId } ?: return
        richContentSessions[richContentSessionKey(boxId)]?.blurCommit(box)
    }

    private fun editorSessionFor(boxId: String, box: RichContentBox): RichContentEditorSession =
        richContentSessions.getOrPut(richContentSessionKey(boxId)) {
            RichContentEditorSession(initialBox = box, engine = richContentEngine)
        }.also { it.focus(box) }

    private fun richContentSessionKey(boxId: String): String = "${state.currentPageId}:$boxId"

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

    public fun selectWithLasso(documentPath: List<CanvasPoint>): SelectionState {
        val selection = selectionEngine.selectWithLasso(currentCanvas, documentPath)
        val result = selectionEngine.execute(
            state.selection,
            SelectionCommand.ReplaceSelection(selection),
        ) as SelectionCommandResult.SelectionChanged
        state = state.copy(
            selection = result.selection,
            focusedRichContentBoxId = null,
            document = state.document.withCanvas(currentCanvas.setFocusedRichContentBox(null)),
        )
        return result.selection
    }

    private fun beginSelectionGesture(screenPosition: CanvasPoint) {
        val documentPosition = screenToDocument(screenPosition)
        if (selectionHitBoundsContains(documentPosition)) {
            activeSelectionGesture = ActiveSelectionGesture.Drag(lastScreenPosition = screenPosition)
            return
        }

        val hitRef = hitSelectableAt(documentPosition)
        if (hitRef != null) {
            if (hitRef !in state.selection.selectedRefs) {
                replaceSelection(SelectionState(selectedRefs = setOf(hitRef)))
            }
            activeSelectionGesture = ActiveSelectionGesture.Drag(lastScreenPosition = screenPosition)
        } else {
            activeSelectionGesture = ActiveSelectionGesture.Lasso(path = listOf(documentPosition))
        }
    }

    private fun updateSelectionGesture(screenPosition: CanvasPoint) {
        when (val gesture = activeSelectionGesture) {
            is ActiveSelectionGesture.Drag -> {
                val screenDelta = Offset(
                    x = screenPosition.x - gesture.lastScreenPosition.x,
                    y = screenPosition.y - gesture.lastScreenPosition.y,
                )
                moveSelectedObjectsByScreenDelta(screenDelta)
                activeSelectionGesture = gesture.copy(lastScreenPosition = screenPosition)
            }
            is ActiveSelectionGesture.Lasso -> {
                val path = gesture.path + screenToDocument(screenPosition)
                activeSelectionGesture = gesture.copy(path = path)
                selectWithLasso(path)
            }
            null -> Unit
        }
    }

    private fun endActiveInteraction() {
        when (val gesture = activeSelectionGesture) {
            is ActiveSelectionGesture.Lasso -> {
                if (gesture.path.size < 2) {
                    clearSelection()
                } else {
                    selectWithLasso(gesture.path)
                }
            }
            is ActiveSelectionGesture.Drag,
            null -> endInkIfActive()
        }
        activeSelectionGesture = null
    }

    private fun cancelActiveInteraction() {
        activeSelectionGesture = null
        cancelInkIfActive()
    }

    private fun replaceSelection(selection: SelectionState) {
        val result = selectionEngine.execute(
            state.selection,
            SelectionCommand.ReplaceSelection(selection),
        ) as SelectionCommandResult.SelectionChanged
        state = state.copy(
            selection = result.selection,
            focusedRichContentBoxId = null,
            document = state.document.withCanvas(currentCanvas.setFocusedRichContentBox(null)),
        )
    }

    private fun selectionHitBoundsContains(documentPosition: CanvasPoint): Boolean =
        state.selection.selectedRefs.isNotEmpty() && selectedBounds?.contains(documentPosition) == true

    private fun hitSelectableAt(documentPosition: CanvasPoint): com.neonote.model.SelectableRef? {
        val objectHit = selectionEngine.hitTestCanvasObjects(currentCanvas, documentPosition).firstOrNull()
        if (objectHit != null) return CanvasObjectRef(objectHit.id)
        val strokeHit = selectionEngine.hitTestInkStrokes(currentCanvas, documentPosition).firstOrNull()
        if (strokeHit != null) return InkStrokeRef(strokeHit.id)
        return null
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

    private fun NeoNoteDocument.withMeasuredRichContentBoxHeights(): NeoNoteDocument = copy(
        pages = pages.map { page ->
            page.copy(
                canvas = page.canvas.copy(
                    objects = page.canvas.objects.map { canvasObject ->
                        if (canvasObject is RichContentBox) {
                            richContentMeasurer.resizeBoxToMeasuredContent(canvasObject)
                        } else {
                            canvasObject
                        }
                    },
                ),
            )
        },
    )

    private fun NeoNoteDocument.withCanvas(canvas: InfiniteCanvas): NeoNoteDocument = withCanvasForPage(
        pageId = state.currentPageId,
        canvas = canvas,
    )

    private fun NeoNoteDocument.withCanvasForPage(pageId: String?, canvas: InfiniteCanvas): NeoNoteDocument = copy(
        pages = pages.map { page -> if (page.id == pageId) page.copy(canvas = canvas) else page },
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

private fun InfiniteCanvas.updateRichContentBox(
    boxId: String,
    edit: (RichContentBox) -> RichContentBox,
): InfiniteCanvas = copy(
    objects = objects.map { canvasObject ->
        if (canvasObject.id == boxId && canvasObject is RichContentBox) edit(canvasObject) else canvasObject
    },
)

public fun InfiniteCanvas.topMostObjectAt(position: CanvasPoint): CanvasObject? = objects
    .sortedWith(compareBy<CanvasObject> { it.zIndex }.thenBy { it.id })
    .lastOrNull { it.bounds.contains(position) }

private fun InfiniteCanvas.setFocusedRichContentBox(focusedId: String?): InfiniteCanvas = copy(
    objects = objects.map { canvasObject ->
        if (canvasObject is RichContentBox) canvasObject.copy(isFocused = canvasObject.id == focusedId) else canvasObject
    },
)


private sealed interface ActiveSelectionGesture {
    data class Lasso(val path: List<CanvasPoint>) : ActiveSelectionGesture
    data class Drag(val lastScreenPosition: CanvasPoint) : ActiveSelectionGesture
}

private class SequentialIdGenerator : IdGenerator {
    private var nextId = 1

    override fun nextId(prefix: String): String = "$prefix-${nextId++}"
}

private fun PersistenceDiagnostics?.toStatusSuffix(): String = this?.let { " (${it.toDebugSummary()})" }.orEmpty()

private fun elapsedMillis(block: () -> Unit): Double {
    val startNanos = System.nanoTime()
    block()
    return (System.nanoTime() - startNanos) / 1_000_000.0
}
