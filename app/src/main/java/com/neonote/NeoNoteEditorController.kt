package com.neonote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.neonote.engine.ActiveRichContentTarget
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
import com.neonote.engine.ParagraphTextSelection
import com.neonote.engine.RichContentEditorSession
import com.neonote.engine.RichContentEngine
import com.neonote.engine.RichContentMeasurer
import com.neonote.engine.RichContentTree
import com.neonote.engine.TypingStyle
import com.neonote.engine.hasMeaningfulContent
import com.neonote.engine.normalizedForCommit
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
import com.neonote.model.EraserMode
import com.neonote.model.FloatingImage
import com.neonote.model.FormulaDisplayMode
import com.neonote.model.ImageCrop
import com.neonote.model.InfiniteCanvas
import com.neonote.model.InkBrush
import com.neonote.model.InkPoint
import com.neonote.model.InkStroke
import com.neonote.model.InkStrokeRef
import com.neonote.model.InkToolSettings
import com.neonote.model.ListKind
import com.neonote.model.NeoNoteDocument
import com.neonote.model.NotePage
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.SelectionState
import com.neonote.model.TableCellAddress
import com.neonote.model.TextAlignment
import com.neonote.model.TextSelection
import com.neonote.model.ViewportState
import java.util.ArrayDeque

private const val DefaultBoxWidth = 320f
private const val DefaultBoxHeight = 48f
private const val DefaultViewportWidthPx = 1280f
private const val MinimumTextBoxWidthDp = 280f
private const val PreferredTextBoxWidthDp = 520f
private const val MaximumTextBoxWidthDp = 760f
private const val MinimumTextBoxHeightDp = 44f
private const val TextBoxScreenMarginDp = 20f
private const val DefaultImageWidth = 320f
private const val DefaultImageHeight = 220f
private const val MinZoomScale = 0.2f
private const val MaxZoomScale = 6f
private const val DefaultInputDiagnosticsThrottleMillis = 32L
private const val HistoryLimit = 150
private const val MaximumDocumentTitleLength = 120

/**
 * Product editor reducer. Compose sends user intents here; all persisted
 * mutations flow through pure document/content/ink/selection engines.
 */
public class NeoNoteEditorController(
    initialState: EditorState = createInitialEditorState(),
    private val canvasEngine: CanvasEngine = CanvasEngine(),
    private val selectionEngine: SelectionEngine = SelectionEngine(),
    private val idGenerator: IdGenerator = SequentialIdGenerator(),
    private val inkEngine: InkEngine = InkEngine(idGenerator),
    private val richContentEngine: RichContentEngine = RichContentEngine(),
    private val richContentMeasurer: RichContentMeasurer = RichContentMeasurer(),
    private val documentEngine: DocumentEngine = DocumentEngine(idGenerator),
    private val inputDiagnosticsThrottleMillis: Long = DefaultInputDiagnosticsThrottleMillis,
    private val diagnosticsClockMillis: () -> Long = { System.currentTimeMillis() },
) {
    private val undoDocuments = ArrayDeque<NeoNoteDocument>()
    private val redoDocuments = ArrayDeque<NeoNoteDocument>()
    private var historyMutationInProgress = false
    private var stateBacking by mutableStateOf(initialState.ensurePage())

    public var state: EditorState
        get() = stateBacking
        private set(value) {
            val previous = stateBacking.document.historySnapshot()
            val next = value.document.historySnapshot()
            if (!historyMutationInProgress && !previous.hasSameHistoryContentAs(next)) {
                undoDocuments.addLast(previous)
                undoDocuments.trimToHistoryLimit()
                redoDocuments.clear()
            }
            stateBacking = value
        }

    public var inputDiagnostics: InputDiagnostics by mutableStateOf(InputDiagnostics())
        private set
    public var inkSession: InkSession by mutableStateOf(InkSession.fromCanvas(currentCanvas))
        private set
    public var persistenceStatus: String by mutableStateOf("Not saved")
        private set
    public var persistenceDiagnostics: PersistenceDiagnostics? by mutableStateOf(null)
        private set
    private var lastPersistedRevision: Long? by mutableStateOf(null)
    private var lastPersistedContentToken: Long? by mutableStateOf(null)
    public var contentChangeToken: Long by mutableStateOf(0L)
        private set

    private var activeSelectionGesture: ActiveSelectionGesture? by mutableStateOf(null)
    private var selectionTransformBaseline: NeoNoteDocument? = null
    private var lastInputDiagnosticsUpdateMillis: Long? = null
    private var pendingInputDiagnostics: InputDiagnostics? = null
    private var richContentInteractionRevision by mutableStateOf(0)
    private var displayDensity: Float = 1f
    private var viewportWidthScreenPx: Float = DefaultViewportWidthPx
    private var viewportHeightScreenPx: Float = 800f
    private var hasViewportMetrics: Boolean = false
    private val richContentSessions = mutableMapOf<String, RichContentEditorSession>()
    private val selectedRichContentObjectBlocks = mutableMapOf<String, Int>()
    private val activeRichContentGestures = mutableSetOf<String>()
    private val draftRichContentBoxes = mutableSetOf<String>()
    private val richContentEditBaselines = mutableMapOf<String, NeoNoteDocument>()
    private val pageViewports = mutableMapOf<String, ViewportState>()
    private var canvasClipboard: CanvasClipboard? = null
    public var searchHighlightedObjectId: String? by mutableStateOf(null)
        private set

    public val saveStateLabel: String
        get() = when {
            lastPersistedRevision == state.document.revision && lastPersistedContentToken == contentChangeToken -> "Saved"
            lastPersistedRevision == null -> "Saving locally"
            else -> "Saving…"
        }
    public val activeInkStroke: InkStroke? get() = inkSession.activeStroke
    public val activeLassoPath: List<CanvasPoint>
        get() = (activeSelectionGesture as? ActiveSelectionGesture.Lasso)?.path.orEmpty()
    public val selectedBounds: CanvasRect? get() = selectionEngine.selectedBounds(currentCanvas, state.selection)
    public val currentCanvas: InfiniteCanvas get() = currentPage.canvas
    public val currentPageIndex: Int
        get() = state.document.pages.indexOfFirst { it.id == state.currentPageId }.coerceAtLeast(0)
    public val currentPageNumber: Int get() = currentPageIndex + 1
    public val pageCount: Int get() = state.document.pages.size
    public val canSwitchToPreviousPage: Boolean get() = currentPageIndex > 0
    public val canSwitchToNextPage: Boolean get() = currentPageIndex < pageCount - 1
    public val canUndo: Boolean get() = undoDocuments.isNotEmpty()
    public val canRedo: Boolean get() = redoDocuments.isNotEmpty()
    private val currentPage: NotePage get() = state.document.pages[currentPageIndex]

    public fun undo() {
        state.focusedRichContentBoxId?.let(::commitRichContentEditing)
        if (undoDocuments.isEmpty()) return
        val previous = undoDocuments.removeLast()
        redoDocuments.addLast(state.document.historySnapshot())
        redoDocuments.trimToHistoryLimit()
        restoreDocumentFromHistory(previous)
    }

    public fun redo() {
        state.focusedRichContentBoxId?.let(::commitRichContentEditing)
        if (redoDocuments.isEmpty()) return
        val next = redoDocuments.removeLast()
        undoDocuments.addLast(state.document.historySnapshot())
        undoDocuments.trimToHistoryLimit()
        restoreDocumentFromHistory(next)
    }

    public fun setTool(tool: EditorTool) {
        if (state.currentTool == tool) return
        if (tool != EditorTool.Text) state.focusedRichContentBoxId?.let(::commitRichContentEditing)
        cancelInkIfActive()
        val canvas = if (tool == EditorTool.Text) currentCanvas else currentCanvas.setFocusedRichContentBox(null)
        replaceStateWithoutRecordingHistory {
            state = state.copy(
                currentTool = tool,
                focusedRichContentBoxId = if (tool == EditorTool.Text) state.focusedRichContentBoxId else null,
                selection = if (tool == EditorTool.Selection) state.selection else SelectionState(),
                document = if (canvas == currentCanvas) state.document else state.document.withCanvasTransient(canvas),
            )
        }
    }

    public fun setInkColor(colorArgb: Int) {
        state = state.copy(inkSettings = state.inkSettings.copy(colorArgb = colorArgb).normalized())
    }

    public fun setInkWidth(width: Float) {
        state = state.copy(inkSettings = state.inkSettings.copy(width = width).normalized())
    }

    public fun setInkOpacity(opacity: Float) {
        state = state.copy(inkSettings = state.inkSettings.copy(opacity = opacity).normalized())
    }

    public fun setInkBrush(brush: InkBrush) {
        state = state.copy(inkSettings = state.inkSettings.copy(
            brush = brush,
            opacity = if (brush == InkBrush.Highlighter) 0.35f else state.inkSettings.opacity,
            width = if (brush == InkBrush.Highlighter) state.inkSettings.width.coerceAtLeast(12f) else state.inkSettings.width,
        ).normalized())
    }

    public fun setPressureEnabled(enabled: Boolean) {
        state = state.copy(inkSettings = state.inkSettings.copy(pressureEnabled = enabled))
    }

    public fun setEraserMode(mode: EraserMode) {
        state = state.copy(eraserMode = mode)
    }

    public fun resetViewport() {
        val next = ViewportState()
        state.currentPageId?.let { pageViewports[it] = next }
        replaceStateWithoutRecordingHistory { state = state.copy(viewport = next) }
    }

    public fun renameDocument(title: String) {
        val next = title.take(MaximumDocumentTitleLength)
        if (next == state.document.title) return
        val result = documentEngine.execute(DocumentCommand.RenameDocument(state.document, next))
            as DocumentCommandResult.DocumentUpdated
        state = state.copy(document = result.document)
    }

    public fun toggleDocumentFavorite() {
        val result = documentEngine.execute(DocumentCommand.ToggleFavorite(state.document))
            as DocumentCommandResult.DocumentUpdated
        state = state.copy(document = result.document)
    }

    public fun addPage() {
        state.focusedRichContentBoxId?.let(::commitRichContentEditing)
        val result = documentEngine.execute(
            DocumentCommand.AddPage(state.document, sectionId = currentSectionId),
        ) as DocumentCommandResult.PageAdded
        state = state.copy(document = result.document)
        pageViewports[result.page.id] = ViewportState()
        switchPage(result.page.id)
    }

    public fun renamePage(pageId: String, title: String) {
        val result = documentEngine.execute(DocumentCommand.RenamePage(state.document, pageId, title))
            as DocumentCommandResult.PageUpdated
        state = state.copy(document = result.document)
    }

    public fun duplicatePage(pageId: String = currentPage.id) {
        state.focusedRichContentBoxId?.let(::commitRichContentEditing)
        val result = documentEngine.execute(DocumentCommand.DuplicatePage(state.document, pageId))
            as DocumentCommandResult.PageAdded
        state = state.copy(document = result.document)
        pageViewports[result.page.id] = ViewportState()
        switchPage(result.page.id)
    }

    public fun deletePage(pageId: String = currentPage.id) {
        state.focusedRichContentBoxId?.let(::commitRichContentEditing)
        val baseline = state.document.historySnapshot()
        val result = documentEngine.execute(DocumentCommand.DeletePage(state.document, pageId))
            as DocumentCommandResult.PageDeleted
        if (result.document == state.document) return
        pushUndoSnapshot(baseline)
        pageViewports.remove(pageId)
        val nextViewport = pageViewports[result.nextPageId] ?: ViewportState()
        replaceStateWithoutRecordingHistory {
            state = state.copy(document = result.document, currentPageId = result.nextPageId, viewport = nextViewport)
        }
        resetTransientEditingState()
    }

    public fun movePage(fromIndex: Int, toIndex: Int) {
        val result = documentEngine.execute(DocumentCommand.MovePage(state.document, fromIndex, toIndex))
            as DocumentCommandResult.PagesReordered
        state = state.copy(document = result.document)
    }

    public fun switchToPreviousPage() {
        if (canSwitchToPreviousPage) switchPage(state.document.pages[currentPageIndex - 1].id)
    }

    public fun switchToNextPage() {
        if (canSwitchToNextPage) switchPage(state.document.pages[currentPageIndex + 1].id)
    }

    public fun switchPage(pageId: String) {
        if (pageId == state.currentPageId) return
        clearSearchHighlight()
        state.focusedRichContentBoxId?.let(::commitRichContentEditing)
        val previousPageId = state.currentPageId
        if (previousPageId != null) pageViewports[previousPageId] = state.viewport
        val clearedDocument = state.document.withCanvasForPageTransient(currentPage.id, currentCanvas.setFocusedRichContentBox(null))
        val result = documentEngine.execute(DocumentCommand.SwitchPage(
            state.copy(document = clearedDocument, focusedRichContentBoxId = null, selection = SelectionState()),
            pageId,
        )) as DocumentCommandResult.PageSwitched
        replaceStateWithoutRecordingHistory {
            state = result.state.copy(viewport = pageViewports[pageId] ?: ViewportState())
        }
        activeSelectionGesture = null
        inkSession = InkSession.fromCanvas(currentCanvas)
    }

    public fun revealSearchTarget(objectId: String?, targetX: Float?, targetY: Float?) {
        val objectValue = objectId?.let { id -> currentCanvas.objects.firstOrNull { it.id == id } }
        val center = objectValue?.bounds?.center ?: if (targetX != null && targetY != null) CanvasPoint(targetX, targetY) else null
        searchHighlightedObjectId = objectValue?.id
        if (center == null) return
        val zoom = state.viewport.zoomScale.takeIf { it in 0.55f..2.5f } ?: 1f
        val viewport = ViewportState(
            panOffsetX = viewportWidthScreenPx / 2f - center.x * zoom,
            panOffsetY = viewportHeightScreenPx * 0.46f - center.y * zoom,
            zoomScale = zoom,
        )
        replaceStateWithoutRecordingHistory { state = state.copy(viewport = viewport) }
        state.currentPageId?.let { pageViewports[it] = viewport }
    }

    public fun clearSearchHighlight() {
        searchHighlightedObjectId = null
    }

    public fun deleteSelection() {
        if (state.selection.selectedRefs.isEmpty()) return
        applyCanvas(selectionEngine.deleteSelection(currentCanvas, state.selection), clearSelection = true)
    }

    public fun selectAllCanvasContent() {
        state.focusedRichContentBoxId?.let(::commitRichContentEditing)
        val refs = currentCanvas.objects.map { CanvasObjectRef(it.id) } +
            currentCanvas.inkLayer.strokes.map { InkStrokeRef(it.id) }
        if (refs.isEmpty()) return
        replaceStateWithoutRecordingHistory {
            state = state.copy(currentTool = EditorTool.Selection, selection = SelectionState(refs.toSet()))
        }
    }

    public fun duplicateSelection() {
        if (state.selection.selectedRefs.isEmpty()) return
        val result = selectionEngine.duplicateSelection(currentCanvas, state.selection, idGenerator)
        applyCanvas(result.canvas, selection = result.selection)
    }

    public val canPasteSelection: Boolean get() = canvasClipboard != null

    public fun copySelection() {
        if (state.selection.selectedRefs.isEmpty()) return
        val objects = currentCanvas.objects.filter { state.selection.isObjectSelected(it.id) }
            .map { objectValue -> if (objectValue is RichContentBox) objectValue.copy(isFocused = false) else objectValue }
        val strokes = currentCanvas.inkLayer.strokes.filter { state.selection.isStrokeSelected(it.id) }
        val bounds = selectionEngine.selectedBounds(currentCanvas, state.selection) ?: return
        canvasClipboard = CanvasClipboard(objects, strokes, bounds)
    }

    public fun cutSelection() {
        if (state.selection.selectedRefs.isEmpty()) return
        copySelection()
        deleteSelection()
    }

    public fun pasteSelection() {
        val clipboard = canvasClipboard ?: return
        state.focusedRichContentBoxId?.let(::commitRichContentEditing)
        val center = screenToDocument(CanvasPoint(viewportWidthScreenPx / 2f, viewportHeightScreenPx / 2f))
        val dx = center.x - clipboard.bounds.center.x
        val dy = center.y - clipboard.bounds.center.y
        var nextZ = (currentCanvas.objects.maxOfOrNull { it.zIndex } ?: 0) + 1
        val objects = clipboard.objects.map { objectValue ->
            val position = CanvasPoint(objectValue.position.x + dx, objectValue.position.y + dy)
            when (objectValue) {
                is RichContentBox -> objectValue.copy(
                    id = idGenerator.nextId("rich-content"),
                    position = position,
                    zIndex = nextZ++,
                    isFocused = false,
                )
                is FloatingImage -> objectValue.copy(
                    id = idGenerator.nextId("image"),
                    position = position,
                    zIndex = nextZ++,
                )
            }
        }
        val strokes = clipboard.strokes.map { stroke ->
            stroke.copy(
                id = idGenerator.nextId("stroke"),
                points = stroke.points.map { point -> point.copy(x = point.x + dx, y = point.y + dy) },
            )
        }
        val nextCanvas = currentCanvas.copy(
            objects = currentCanvas.objects + objects,
            inkLayer = currentCanvas.inkLayer.copy(strokes = currentCanvas.inkLayer.strokes + strokes),
        )
        val nextSelection = SelectionState(
            objects.map { CanvasObjectRef(it.id) }.toSet() + strokes.map { InkStrokeRef(it.id) },
        )
        applyCanvas(nextCanvas, selection = nextSelection)
        replaceStateWithoutRecordingHistory { state = state.copy(currentTool = EditorTool.Selection) }
    }

    public val selectionHasLockedObjects: Boolean
        get() = currentCanvas.objects.any { objectValue ->
            state.selection.isObjectSelected(objectValue.id) && objectValue.isLockedForEditing()
        }

    public val selectionCanTransform: Boolean
        get() = !selectionHasLockedObjects && state.selection.selectedRefs.isNotEmpty()

    public val selectionCanDelete: Boolean
        get() = state.selection.selectedStrokeIds.isNotEmpty() || currentCanvas.objects.any { objectValue ->
            state.selection.isObjectSelected(objectValue.id) && !objectValue.isLockedForEditing()
        }

    public val singleSelectedFloatingImage: FloatingImage?
        get() {
            val refs = state.selection.selectedRefs
            if (refs.size != 1) return null
            val id = (refs.firstOrNull() as? CanvasObjectRef)?.objectId ?: return null
            return currentCanvas.objects.filterIsInstance<FloatingImage>().firstOrNull { it.id == id }
        }

    public val selectionResizesTextWidthOnly: Boolean
        get() {
            val refs = state.selection.selectedRefs
            if (refs.size != 1 || refs.firstOrNull() !is CanvasObjectRef) return false
            val id = (refs.first() as CanvasObjectRef).objectId
            return currentCanvas.objects.any { it is RichContentBox && it.id == id }
        }

    public fun beginSelectionTransform() {
        if (!selectionCanTransform || selectionTransformBaseline != null) return
        state.focusedRichContentBoxId?.let(::commitRichContentEditing)
        selectionTransformBaseline = state.document.historySnapshot()
    }

    public fun resizeSelectionDuringTransform(scaleX: Float, scaleY: Float = scaleX) {
        if (!selectionCanTransform) return
        if (selectionTransformBaseline == null) beginSelectionTransform()
        val effectiveY = if (selectionResizesTextWidthOnly) 1f else scaleY
        val canvas = selectionEngine.scaleSelection(currentCanvas, state.selection, scaleX, effectiveY)
        applyCanvasTransient(canvas)
    }

    public fun endSelectionTransform() {
        val baseline = selectionTransformBaseline ?: return
        selectionTransformBaseline = null
        if (baseline.hasSameHistoryContentAs(state.document)) return
        pushUndoSnapshot(baseline)
        replaceStateWithoutRecordingHistory {
            state = state.copy(document = state.document.touchPage(currentPage.id))
        }
        contentChangeToken++
    }

    public fun cancelSelectionTransform() {
        val baseline = selectionTransformBaseline ?: return
        selectionTransformBaseline = null
        val pageId = state.currentPageId
        replaceStateWithoutRecordingHistory {
            state = state.copy(document = baseline, currentPageId = pageId, focusedRichContentBoxId = null)
        }
        inkSession = InkSession.fromCanvas(currentCanvas)
    }

    public fun scaleSelection(scaleX: Float, scaleY: Float = scaleX) {
        if (state.selection.selectedRefs.isEmpty()) return
        beginSelectionTransform()
        resizeSelectionDuringTransform(scaleX, scaleY)
        endSelectionTransform()
    }

    public fun bringSelectionToFront() {
        applyCanvas(selectionEngine.bringSelectionToFront(currentCanvas, state.selection))
    }

    public fun sendSelectionToBack() {
        applyCanvas(selectionEngine.sendSelectionToBack(currentCanvas, state.selection))
    }

    public fun setSelectionLocked(locked: Boolean) {
        applyCanvas(selectionEngine.setSelectionLocked(currentCanvas, state.selection, locked))
    }

    public suspend fun saveDocument(store: PersistenceStore): PersistenceResult.Saved {
        val saved = store.save(documentForPersistence())
        lastPersistedRevision = saved.revision
        lastPersistedContentToken = contentChangeToken
        persistenceDiagnostics = saved.diagnostics
        persistenceStatus = "Saved ${saved.documentId} at revision ${saved.revision}" + saved.diagnostics.toStatusSuffix()
        return saved
    }

    public suspend fun loadDocument(
        store: PersistenceStore,
        documentId: String = state.document.id,
    ): PersistenceResult.Loaded {
        val loaded = store.load(documentId)
        val document = loaded.document ?: run {
            lastPersistedRevision = null
            persistenceStatus = "No local document found for $documentId"
            return loaded
        }
        val rebuildStartNanos = System.nanoTime()
        val measuredDocument = document.withMeasuredRichContentBoxHeights()
        replaceDocument(measuredDocument, recordHistory = false)
        val diagnostics = loaded.diagnostics?.copy(
            cacheRebuildTimeMillis = (System.nanoTime() - rebuildStartNanos) / 1_000_000.0,
        )
        val result = loaded.copy(diagnostics = diagnostics)
        lastPersistedRevision = document.revision
        contentChangeToken = 0L
        lastPersistedContentToken = 0L
        persistenceDiagnostics = diagnostics
        persistenceStatus = "Loaded ${document.id} at revision ${document.revision}" + diagnostics.toStatusSuffix()
        return result
    }

    public fun replaceDocument(document: NeoNoteDocument, recordHistory: Boolean = true) {
        val action = {
            state = state.copy(
                document = document.ensurePageDocument(),
                currentPageId = document.pages.firstOrNull()?.id,
                focusedRichContentBoxId = null,
                selection = SelectionState(),
                viewport = ViewportState(),
            )
        }
        if (recordHistory) action() else replaceStateWithoutRecordingHistory(action)
        undoDocuments.clear()
        redoDocuments.clear()
        pageViewports.clear()
        contentChangeToken = 0L
        lastPersistedContentToken = null
        resetTransientEditingState()
    }

    public fun updateInputDiagnostics(
        diagnostics: InputDiagnostics,
        eventTimeMillis: Long = diagnosticsClockMillis(),
        force: Boolean = false,
    ): Boolean {
        val pending = pendingInputDiagnostics?.mergeForThrottledDisplay(diagnostics) ?: diagnostics
        val last = lastInputDiagnosticsUpdateMillis
        val publish = force || inputDiagnosticsThrottleMillis <= 0L || last == null ||
            eventTimeMillis - last >= inputDiagnosticsThrottleMillis || diagnostics.tool != inputDiagnostics.tool
        return if (publish) {
            inputDiagnostics = pending
            pendingInputDiagnostics = null
            lastInputDiagnosticsUpdateMillis = eventTimeMillis
            true
        } else {
            pendingInputDiagnostics = pending
            false
        }
    }

    public fun routeInputEvent(
        router: InputRouter,
        event: InputEvent,
        mode: InputMode = InputMode.Write,
    ): InputRouteResult {
        clearSearchHighlight()
        val result = router.route(currentCanvas, event, mode)
        when (val action = result.action) {
            is InputAction.BeginInk -> beginInk(action.position, action.pressure, action.rawPressure)
            is InputAction.ContinueInk -> continueInk(action.samples)
            is InputAction.EraseAt -> eraseInkAtScreenPositions(action.positions)
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
        prepareFocusedContentForIndependentCanvasEdit()
        val command = InkCommand.BeginStroke(
            point = screenPosition.toInkPoint(pressure, rawPressure),
            style = state.inkSettings.toStrokeStyle(),
        )
        inkSession = (inkEngine.execute(InkSession.fromCanvas(currentCanvas), command) as InkCommandResult.StrokeBegun).session
    }

    private fun continueInk(samples: List<InputInkSample>) {
        if (inkSession.activeStroke == null || samples.isEmpty()) return
        val points = samples.map { it.position.toInkPoint(it.pressure, it.rawPressure) }
        inkSession = (inkEngine.execute(inkSession, InkCommand.AppendPoints(points)) as InkCommandResult.PointAppended).session
    }

    private fun endInkIfActive() {
        if (inkSession.activeStroke == null) return
        val result = inkEngine.execute(inkSession, InkCommand.EndStroke) as InkCommandResult.StrokeEnded
        inkSession = result.session
        commitInkLayer(result.session.inkLayer)
        state.focusedRichContentBoxId?.let { focusedId ->
            richContentEditBaselines[richContentSessionKey(focusedId)] = documentForPersistence().historySnapshot()
        }
    }

    private fun prepareFocusedContentForIndependentCanvasEdit() {
        val focusedId = state.focusedRichContentBoxId ?: return
        val box = richContentBox(focusedId)
        if (box?.normalizedForCommit()?.hasMeaningfulContent() == true) {
            checkpointRichContentEditing(focusedId)
        } else {
            commitRichContentEditing(focusedId)
        }
    }

    private fun cancelInkIfActive() {
        if (inkSession.activeStroke == null) return
        inkSession = (inkEngine.execute(inkSession, InkCommand.CancelStroke) as InkCommandResult.StrokeCancelled).session
    }

    private fun eraseInkAtScreenPositions(screenPositions: List<CanvasPoint>) {
        if (screenPositions.isEmpty()) return
        val points = screenPositions.map { screen ->
            val document = screenToDocument(screen)
            InkPoint(document.x, document.y)
        }
        val radius = 14f / state.viewport.zoomScale.coerceAtLeast(MinZoomScale)
        val command = when (state.eraserMode) {
            EraserMode.Stroke -> InkCommand.EraseWholeStrokes(points, radius)
            EraserMode.Segment -> InkCommand.EraseSegments(points, radius)
        }
        val result = inkEngine.execute(InkSession.fromCanvas(currentCanvas), command) as InkCommandResult.InkErased
        if (result.session.inkLayer == currentCanvas.inkLayer) return
        inkSession = result.session
        commitInkLayer(result.session.inkLayer)
    }

    private fun CanvasPoint.toInkPoint(pressure: Float, rawPressure: Float?): InkPoint {
        val point = screenToDocument(this)
        return InkPoint(point.x, point.y, pressure, rawPressure)
    }

    private fun commitInkLayer(layer: com.neonote.model.InkLayer) {
        applyCanvas(currentCanvas.copy(inkLayer = layer))
    }

    public fun setSelectionMode(enabled: Boolean) {
        setTool(if (enabled) EditorTool.Selection else EditorTool.Pen)
    }

    public fun handleBack(): Boolean = when {
        state.focusedRichContentBoxId != null -> {
            finishRichContentEditing(state.focusedRichContentBoxId!!)
            true
        }
        state.selection.selectedRefs.isNotEmpty() -> {
            clearSelection()
            setTool(EditorTool.Pen)
            true
        }
        state.currentTool == EditorTool.Selection || state.currentTool == EditorTool.Eraser -> {
            setTool(EditorTool.Pen)
            true
        }
        else -> false
    }

    public fun panViewportBy(screenDx: Float, screenDy: Float) {
        val next = state.viewport.copy(
            panOffsetX = state.viewport.panOffsetX + screenDx,
            panOffsetY = state.viewport.panOffsetY + screenDy,
        )
        state.currentPageId?.let { pageViewports[it] = next }
        replaceStateWithoutRecordingHistory { state = state.copy(viewport = next) }
    }

    public fun zoomViewportBy(zoomChange: Float, screenCentroid: CanvasPoint) {
        if (zoomChange == 1f) return
        val next = state.viewport.zoomAroundScreenPoint(zoomChange, screenCentroid, MinZoomScale, MaxZoomScale)
        state.currentPageId?.let { pageViewports[it] = next }
        replaceStateWithoutRecordingHistory { state = state.copy(viewport = next) }
    }

    public fun updateViewportMetrics(widthPx: Int, heightPx: Int, density: Float) {
        if (widthPx > 0) viewportWidthScreenPx = widthPx.toFloat()
        if (heightPx > 0) viewportHeightScreenPx = heightPx.toFloat()
        if (density.isFinite() && density > 0f) displayDensity = density
        hasViewportMetrics = widthPx > 0 && density.isFinite() && density > 0f
    }

    public fun focusOrCreateRichContentBox(documentPosition: CanvasPoint) {
        val existing = currentCanvas.topMostObjectAt(documentPosition) as? RichContentBox
        if (existing != null) {
            focusRichContentBox(existing.id)
            return
        }
        createRichContentBox(documentPosition)
    }

    public fun createRichContentBox(documentPosition: CanvasPoint, avoidOverlap: Boolean = false) {
        state.focusedRichContentBoxId?.let(::commitRichContentEditing)
        val zoom = state.viewport.zoomScale.coerceAtLeast(MinZoomScale)
        val width = if (hasViewportMetrics) {
            val minimumWidth = MinimumTextBoxWidthDp * displayDensity / zoom
            val preferredWidth = PreferredTextBoxWidthDp * displayDensity / zoom
            val maximumWidth = MaximumTextBoxWidthDp * displayDensity / zoom
            val availableWidth = (viewportWidthScreenPx - TextBoxScreenMarginDp * 2f * displayDensity) / zoom
            preferredWidth.coerceIn(minimumWidth, maximumWidth).coerceAtMost(availableWidth.coerceAtLeast(minimumWidth))
        } else DefaultBoxWidth
        val height = if (hasViewportMetrics) MinimumTextBoxHeightDp * displayDensity / zoom else DefaultBoxHeight
        val marginPx = TextBoxScreenMarginDp * displayDensity
        val requestedScreen = documentToScreen(documentPosition)
        val adjustedScreenX = if (hasViewportMetrics) {
            requestedScreen.x.coerceIn(marginPx, (viewportWidthScreenPx - marginPx - width * zoom).coerceAtLeast(marginPx))
        } else requestedScreen.x
        val adjustedPosition = if (adjustedScreenX == requestedScreen.x) documentPosition
        else screenToDocument(CanvasPoint(adjustedScreenX, requestedScreen.y))
        val placement = if (avoidOverlap) findOpenRichContentPosition(adjustedPosition, width, height) else adjustedPosition
        val baseline = state.document.historySnapshot()
        val box = RichContentBox(
            id = nextRichContentBoxId(),
            position = placement,
            size = CanvasSize(width, height),
            zIndex = (currentCanvas.objects.maxOfOrNull { it.zIndex } ?: 0) + 1,
            content = RichContent(),
            isFocused = true,
        )
        val added = canvasEngine.execute(currentCanvas, CanvasCommand.AddObject(box)) as CanvasCommandResult.ObjectAdded
        val key = richContentSessionKey(box.id)
        draftRichContentBoxes += key
        richContentEditBaselines[key] = baseline
        replaceStateWithoutRecordingHistory {
            state = state.copy(
                document = state.document.withCanvasTransient(added.canvas.setFocusedRichContentBox(box.id)),
                focusedRichContentBoxId = box.id,
                selection = SelectionState(),
                currentTool = EditorTool.Text,
            )
        }
        editorSessionFor(box.id, box)
    }

    private fun findOpenRichContentPosition(origin: CanvasPoint, width: Float, height: Float): CanvasPoint {
        val gap = 28f / state.viewport.zoomScale.coerceAtLeast(MinZoomScale)
        var candidate = origin
        repeat(12) { attempt ->
            val bounds = CanvasRect(candidate.x, candidate.y, candidate.x + width, candidate.y + height)
            if (currentCanvas.objects.none { objectValue ->
                    val existing = objectValue.bounds
                    existing.right >= bounds.left && existing.left <= bounds.right &&
                        existing.bottom >= bounds.top && existing.top <= bounds.bottom
                }
            ) return candidate
            candidate = if (attempt % 3 == 2) {
                CanvasPoint(origin.x + gap * ((attempt / 3) + 1), origin.y)
            } else {
                CanvasPoint(candidate.x, candidate.y + height + gap)
            }
        }
        return candidate
    }

    public fun insertFloatingImage(
        assetId: String,
        position: CanvasPoint,
        size: CanvasSize = CanvasSize(DefaultImageWidth, DefaultImageHeight),
        altText: String? = null,
    ): String {
        val image = FloatingImage(
            id = idGenerator.nextId("image"),
            position = position,
            size = size,
            zIndex = (currentCanvas.objects.maxOfOrNull { it.zIndex } ?: 0) + 1,
            assetId = assetId,
            altText = altText,
        )
        applyCanvas(currentCanvas.copy(objects = currentCanvas.objects + image))
        return image.id
    }

    public fun updateFloatingImage(
        imageId: String,
        size: CanvasSize? = null,
        rotationDegrees: Float? = null,
        crop: ImageCrop? = null,
        replacementAssetId: String? = null,
    ) {
        val canvas = currentCanvas.copy(objects = currentCanvas.objects.map { objectValue ->
            if (objectValue !is FloatingImage || objectValue.id != imageId || objectValue.isLocked) objectValue
            else objectValue.copy(
                size = size ?: objectValue.size,
                rotationDegrees = rotationDegrees ?: objectValue.rotationDegrees,
                crop = (crop ?: objectValue.crop).normalized(),
                assetId = replacementAssetId ?: objectValue.assetId,
            )
        })
        applyCanvas(canvas)
    }

    public fun activateRichContentBox(boxId: String) {
        when (state.currentTool) {
            EditorTool.Selection -> selectCanvasObject(boxId)
            EditorTool.Eraser -> Unit
            EditorTool.Text, EditorTool.Pen -> focusRichContentBox(boxId)
        }
    }

    public fun focusRichContentBox(boxId: String) {
        if (state.currentTool == EditorTool.Eraser) return
        state.focusedRichContentBoxId?.takeIf { it != boxId }?.let(::commitRichContentEditing)
        val box = currentCanvas.objects.filterIsInstance<RichContentBox>().firstOrNull { it.id == boxId } ?: return
        if (box.isLocked) return
        val key = richContentSessionKey(boxId)
        richContentEditBaselines.putIfAbsent(key, state.document.historySnapshot())
        editorSessionFor(boxId, box).focus(box)
        replaceStateWithoutRecordingHistory {
            state = state.copy(
                currentTool = EditorTool.Text,
                document = state.document.withCanvasTransient(currentCanvas.setFocusedRichContentBox(boxId)),
                focusedRichContentBoxId = boxId,
                selection = SelectionState(),
            )
        }
    }

    public fun updateRichContentText(boxId: String, text: String) {
        val previous = richContentSessions[richContentSessionKey(boxId)]?.localEditableBuffer
            ?: richContentBox(boxId)?.toPlainText().orEmpty()
        updateRichContentFromPlatformInput(boxId, previous, text, text.length)
    }

    public fun updateRichContentFromPlatformInput(
        boxId: String,
        previousText: String,
        nextText: String,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
        hasActiveComposition: Boolean = false,
    ) = editBox(boxId) { session ->
        // Composition updates are still ordinary local text diffs. Replacing the whole
        // paragraph here would destroy inline styles while a Chinese/Japanese IME is open.
        session.replaceFromPlatformInput(previousText, nextText, selectionStart, selectionEnd)
    }

    /** Commit the work accumulated so far without leaving the editor. */
    private fun checkpointRichContentEditing(boxId: String): Boolean {
        val pageId = state.currentPageId ?: return false
        val key = richContentSessionKey(boxId)
        val baseline = richContentEditBaselines[key] ?: state.document.historySnapshot()
        val persisted = documentForPersistence().historySnapshot()
        val changed = !baseline.hasSameHistoryContentAs(persisted)
        if (changed) {
            pushUndoSnapshot(baseline)
            replaceStateWithoutRecordingHistory {
                state = state.copy(document = state.document.touchPage(pageId))
            }
        }
        richContentEditBaselines[key] = documentForPersistence().historySnapshot()
        return changed
    }

    private fun structuralEditBox(
        boxId: String,
        keepFocused: Boolean = true,
        edit: (RichContentEditorSession) -> com.neonote.engine.RichContentEditorEdit,
    ) {
        checkpointRichContentEditing(boxId)
        editBox(boxId, keepFocused, edit)
        checkpointRichContentEditing(boxId)
    }

    public fun beginRichContentGesture(boxId: String) {
        val key = richContentSessionKey(boxId)
        if (!activeRichContentGestures.add(key)) return
        checkpointRichContentEditing(boxId)
    }

    public fun endRichContentGesture(boxId: String) {
        val key = richContentSessionKey(boxId)
        if (!activeRichContentGestures.remove(key)) return
        if (checkpointRichContentEditing(boxId)) contentChangeToken++
    }

    public fun cancelRichContentGesture(boxId: String) {
        val key = richContentSessionKey(boxId)
        activeRichContentGestures.remove(key)
        val baseline = richContentEditBaselines[key] ?: return
        val pageId = state.currentPageId
        val restored = if (pageId == null) baseline else {
            val page = baseline.pages.firstOrNull { it.id == pageId }
            if (page == null) baseline else baseline.withCanvasForPageTransient(pageId, page.canvas.setFocusedRichContentBox(boxId))
        }
        replaceStateWithoutRecordingHistory {
            state = state.copy(document = restored, currentPageId = pageId, focusedRichContentBoxId = boxId)
        }
        richContentSessions.remove(key)
        richContentBox(boxId)?.let { editorSessionFor(boxId, it) }
        richContentInteractionRevision++
    }

    public fun activeRichContentBlockIndex(boxId: String): Int? {
        richContentInteractionRevision
        return richContentSessions[richContentSessionKey(boxId)]?.activeBlockIndex
    }

    public fun activeRichContentFormulaBlockIndex(boxId: String): Int? {
        richContentInteractionRevision
        return richContentSessions[richContentSessionKey(boxId)]?.activeFormulaBlockIndex
    }

    public fun activeRichContentTarget(boxId: String): ActiveRichContentTarget? {
        richContentInteractionRevision
        return richContentSessions[richContentSessionKey(boxId)]?.activeTarget
    }

    public fun activeRichContentTypingStyle(boxId: String): TypingStyle? {
        richContentInteractionRevision
        return richContentSessions[richContentSessionKey(boxId)]?.contextualTypingStyle()
    }

    public fun activeRichContentListKind(boxId: String): ListKind? {
        richContentInteractionRevision
        return richContentSessions[richContentSessionKey(boxId)]?.activeListKind()
    }

    public fun activeRichContentTableDimensions(boxId: String): Pair<Int, Int>? {
        richContentInteractionRevision
        return richContentSessions[richContentSessionKey(boxId)]?.activeTableDimensions()
    }

    public fun activeRichContentSuggestedColumnWidth(boxId: String): Float? {
        val address = (activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell)?.address ?: return null
        val table = richContentBox(boxId)?.content?.let { RichContentTree.table(it, address) } ?: return null
        return table.suggestedEditorColumnWidth(address.path.last().columnIndex)
    }


    public fun activeRichContentPlainSelection(boxId: String): ParagraphTextSelection? {
        richContentInteractionRevision
        return richContentSessions[richContentSessionKey(boxId)]?.unifiedPlainSelection()
    }

    public fun activeRichContentParagraphSelection(boxId: String, blockIndex: Int): ParagraphTextSelection? {
        richContentInteractionRevision
        return richContentSessions[richContentSessionKey(boxId)]?.paragraphPlatformSelection(blockIndex)
    }

    public fun activeRichContentTableCellSelection(boxId: String, address: TableCellAddress): ParagraphTextSelection? {
        richContentInteractionRevision
        return richContentSessions[richContentSessionKey(boxId)]?.tableCellPlatformSelection(address)
    }

    public fun selectedRichContentObjectBlockIndex(boxId: String): Int? =
        selectedRichContentObjectBlocks[richContentSessionKey(boxId)]

    public fun selectRichContentObjectBlock(boxId: String, blockIndex: Int) {
        if (!canEditRichContent()) return
        focusRichContentBox(boxId)
        selectedRichContentObjectBlocks[richContentSessionKey(boxId)] = blockIndex
        richContentInteractionRevision++
    }

    public fun focusRichContentFormulaBlock(boxId: String, blockIndex: Int) {
        if (!canEditRichContent()) return
        focusRichContentBox(boxId)
        richContentBox(boxId)?.let { editorSessionFor(boxId, it).focusFormulaBlock(blockIndex) }
        selectedRichContentObjectBlocks.remove(richContentSessionKey(boxId))
        richContentInteractionRevision++
    }

    public fun blurRichContentFormulaBlock(boxId: String, blockIndex: Int) {
        richContentSessions[richContentSessionKey(boxId)]?.blurFormulaBlock(blockIndex)
        richContentInteractionRevision++
    }

    public fun focusRichContentParagraph(
        boxId: String,
        blockIndex: Int,
        selectionStart: Int = 0,
        selectionEnd: Int = selectionStart,
    ) {
        if (!canEditRichContent()) return
        focusRichContentBox(boxId)
        richContentBox(boxId)?.let { editorSessionFor(boxId, it).focusParagraph(blockIndex, selectionStart, selectionEnd) }
        selectedRichContentObjectBlocks.remove(richContentSessionKey(boxId))
        richContentInteractionRevision++
    }

    public fun focusRichContentTableCell(
        boxId: String,
        address: TableCellAddress,
        selectionStart: Int = 0,
        selectionEnd: Int = selectionStart,
    ) {
        if (!canEditRichContent()) return
        focusRichContentBox(boxId)
        richContentBox(boxId)?.let { editorSessionFor(boxId, it).focusTableCell(address, selectionStart, selectionEnd) }
        selectedRichContentObjectBlocks.remove(richContentSessionKey(boxId))
        richContentInteractionRevision++
    }

    public fun updateRichContentTableCellFromPlatformInput(
        boxId: String,
        address: TableCellAddress,
        previousText: String,
        nextText: String,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
    ) = editBox(boxId, keepFocused = true) {
        it.replaceTableCellParagraphFromPlatformInput(address, previousText, nextText, selectionStart, selectionEnd)
    }

    public fun updateRichContentParagraphFromPlatformInput(
        boxId: String,
        blockIndex: Int,
        previousText: String,
        nextText: String,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
        hasActiveComposition: Boolean = false,
    ) = editBox(boxId) { session ->
        session.replaceFromPlatformParagraphInput(blockIndex, previousText, nextText, selectionStart, selectionEnd)
    }

    public fun updateRichContentFormulaExpression(boxId: String, blockIndex: Int, expression: String) =
        editBox(boxId, keepFocused = true) { it.replaceFormulaExpression(blockIndex, expression) }

    public fun continueAfterRichContentFormula(boxId: String, blockIndex: Int) =
        structuralEditBox(boxId) { it.continueAfterFormula(blockIndex) }

    public fun continueAfterNestedContentBlock(boxId: String, address: TableCellAddress) =
        structuralEditBox(boxId) { it.continueAfterNestedBlock(address) }

    public fun updateRichContentFormula(
        boxId: String,
        blockIndex: Int,
        expression: String? = null,
        displayMode: FormulaDisplayMode? = null,
        numbered: Boolean? = null,
    ) {
        val edit: (RichContentEditorSession) -> com.neonote.engine.RichContentEditorEdit = {
            it.updateFormula(blockIndex, expression, displayMode, numbered)
        }
        if (expression != null && displayMode == null && numbered == null) editBox(boxId, true, edit)
        else structuralEditBox(boxId, edit = edit)
    }

    public fun toggleRichContentParagraphStyle(
        boxId: String,
        blockIndex: Int,
        style: InlineStyle,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
    ) = structuralEditBox(boxId) { it.focusParagraph(blockIndex, selectionStart, selectionEnd); it.toggleStyle(style) }

    public fun toggleRichContentParagraphList(
        boxId: String,
        blockIndex: Int,
        kind: ListKind,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
    ) = structuralEditBox(boxId) { it.focusParagraph(blockIndex, selectionStart, selectionEnd); it.toggleList(kind) }

    public fun toggleRichContentStyle(
        boxId: String,
        style: InlineStyle,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
    ) = structuralEditBox(boxId) { it.setSelectionFromPlainOffsets(selectionStart, selectionEnd); it.toggleStyle(style) }

    public fun toggleRichContentList(
        boxId: String,
        kind: ListKind,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
    ) = structuralEditBox(boxId) { it.setSelectionFromPlainOffsets(selectionStart, selectionEnd); it.toggleList(kind) }

    public fun toggleRichContentTodoCheckedState(boxId: String, blockIndex: Int) =
        structuralEditBox(boxId) { it.toggleTodoCheckedState(blockIndex) }

    public fun toggleActiveRichContentTodoCheckedState(boxId: String) =
        structuralEditBox(boxId) { it.toggleActiveTodoCheckedState() }

    public fun toggleRichContentTableCellTodoCheckedState(boxId: String, address: TableCellAddress) =
        structuralEditBox(boxId) { session ->
            session.focusTableCell(address)
            session.toggleActiveTodoCheckedState()
        }

    public fun toggleActiveRichContentStyle(boxId: String, style: InlineStyle) =
        structuralEditBox(boxId) { it.toggleStyle(style) }

    public fun toggleActiveRichContentList(boxId: String, kind: ListKind) =
        structuralEditBox(boxId) { it.toggleList(kind) }

    public fun setActiveRichContentTextColor(boxId: String, colorArgb: Int?) =
        structuralEditBox(boxId) { it.setTextColor(colorArgb) }

    public fun setActiveRichContentHighlight(boxId: String, colorArgb: Int?) =
        structuralEditBox(boxId) { it.setHighlightColor(colorArgb) }

    public fun setActiveRichContentFontScale(boxId: String, scale: Float) =
        structuralEditBox(boxId) { it.setFontScale(scale) }

    public fun setActiveRichContentLink(boxId: String, url: String?) =
        structuralEditBox(boxId) { it.setLink(url) }

    public fun setActiveParagraphAlignment(boxId: String, alignment: TextAlignment) =
        structuralEditBox(boxId) { it.setParagraphAlignment(alignment) }

    public fun setActiveHeadingLevel(boxId: String, level: Int) =
        structuralEditBox(boxId) { it.setHeadingLevel(level) }

    public fun changeActiveParagraphIndent(boxId: String, delta: Int) =
        structuralEditBox(boxId) { it.changeIndent(delta) }

    public fun insertActiveRichContentLineBreak(boxId: String) =
        structuralEditBox(boxId) { it.insertLineBreak() }

    public fun moveActiveRichContentTableCell(boxId: String, forward: Boolean) =
        structuralEditBox(boxId) { it.moveTableCellFocus(forward) }

    public fun insertRichContentTablePlaceholder(boxId: String, rows: Int = 2, columns: Int = 2) =
        structuralEditBox(boxId) { it.insertTablePlaceholder(rows, columns) }

    public fun insertNestedTable(boxId: String, address: TableCellAddress, rows: Int = 2, columns: Int = 2) =
        structuralEditBox(boxId) { it.insertNestedTable(address, rows, columns) }

    public fun insertTableAtActiveContext(boxId: String, rows: Int = 2, columns: Int = 2) {
        val target = activeRichContentTarget(boxId)
        if (target is ActiveRichContentTarget.TableCell) structuralEditBox(boxId) { it.insertNestedTable(target.address, rows, columns) }
        else structuralEditBox(boxId) { it.insertTablePlaceholder(rows, columns) }
    }

    public fun insertFormulaAtActiveContext(boxId: String, expression: String = "") {
        val target = activeRichContentTarget(boxId)
        if (target is ActiveRichContentTarget.TableCell) structuralEditBox(boxId) { it.insertFormulaInCell(target.address, expression) }
        else structuralEditBox(boxId) { it.insertBlockFormulaPlaceholder(expression) }
    }

    public fun insertImageAtActiveContext(boxId: String, assetId: String, altText: String? = null) {
        val target = activeRichContentTarget(boxId)
        if (target is ActiveRichContentTarget.TableCell) structuralEditBox(boxId) { it.insertImageInCell(target.address, assetId, altText) }
        else insertRichContentImagePlaceholder(boxId, assetId, altText)
    }

    public fun updateNestedFormula(
        boxId: String,
        address: TableCellAddress,
        expression: String? = null,
        displayMode: FormulaDisplayMode? = null,
        numbered: Boolean? = null,
    ) {
        val edit: (RichContentEditorSession) -> com.neonote.engine.RichContentEditorEdit = {
            it.updateNestedFormula(address, expression, displayMode, numbered)
        }
        if (expression != null && displayMode == null && numbered == null) editBox(boxId, true, edit)
        else structuralEditBox(boxId, edit = edit)
    }

    public fun updateNestedImage(
        boxId: String,
        address: TableCellAddress,
        width: Float? = null,
        height: Float? = null,
        rotationDegrees: Float? = null,
        crop: ImageCrop? = null,
        caption: String? = null,
        replacementAssetId: String? = null,
    ) {
        val edit: (RichContentEditorSession) -> com.neonote.engine.RichContentEditorEdit = {
            it.updateNestedImage(address, width, height, rotationDegrees, crop, caption, replacementAssetId)
        }
        val gestureActive = richContentSessionKey(boxId) in activeRichContentGestures
        if ((caption != null && width == null && height == null && rotationDegrees == null && crop == null && replacementAssetId == null) || gestureActive) {
            editBox(boxId, true, edit)
        } else structuralEditBox(boxId, edit = edit)
    }

    public fun deleteNestedBlock(boxId: String, address: TableCellAddress) =
        structuralEditBox(boxId) { it.deleteNestedBlock(address) }

    public fun deleteActiveRichContentTable(boxId: String) {
        val address = (activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell)?.address ?: return
        structuralEditBox(boxId) { it.deleteTable(address) }
    }

    public fun addActiveRichContentTableRow(boxId: String, after: Boolean = true) {
        val address = (activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell)?.address ?: return
        structuralEditBox(boxId) { it.addTableRow(address, after) }
    }

    public fun deleteActiveRichContentTableRow(boxId: String) {
        val address = (activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell)?.address ?: return
        structuralEditBox(boxId) { it.deleteTableRow(address) }
    }

    public fun addActiveRichContentTableColumn(boxId: String, after: Boolean = true) {
        val address = (activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell)?.address ?: return
        structuralEditBox(boxId) { it.addTableColumn(address, after) }
    }

    public fun deleteActiveRichContentTableColumn(boxId: String) {
        val address = (activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell)?.address ?: return
        structuralEditBox(boxId) { it.deleteTableColumn(address) }
    }

    public fun setActiveRichContentTableColumnWidth(boxId: String, width: Float?) {
        val address = (activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell)?.address ?: return
        if (richContentSessionKey(boxId) in activeRichContentGestures) {
            editBox(boxId, keepFocused = true) { it.setTableColumnWidth(address, width) }
        } else {
            structuralEditBox(boxId) { it.setTableColumnWidth(address, width) }
        }
    }

    public fun insertRichContentFormulaPlaceholder(boxId: String, expression: String = "") =
        editBox(boxId, keepFocused = true) { it.insertBlockFormulaPlaceholder(expression) }

    public fun insertRichContentImagePlaceholder(
        boxId: String,
        assetId: String = "image-placeholder",
        altText: String? = "Image placeholder",
    ) {
        var insertion = 0
        structuralEditBox(boxId) { session ->
            insertion = session.activeBlockInsertionIndex()
            session.insertBlockImagePlaceholder(assetId, altText)
        }
        selectedRichContentObjectBlocks[richContentSessionKey(boxId)] = insertion
    }

    public fun updateRichContentImage(
        boxId: String,
        blockIndex: Int,
        width: Float? = null,
        height: Float? = null,
        rotationDegrees: Float? = null,
        crop: ImageCrop? = null,
        caption: String? = null,
        replacementAssetId: String? = null,
    ) {
        val edit: (RichContentEditorSession) -> com.neonote.engine.RichContentEditorEdit = {
            it.updateImage(blockIndex, replacementAssetId, width = width, height = height,
                rotationDegrees = rotationDegrees, crop = crop, caption = caption)
        }
        val gestureActive = richContentSessionKey(boxId) in activeRichContentGestures
        if ((caption != null && width == null && height == null && rotationDegrees == null && crop == null && replacementAssetId == null) || gestureActive) {
            editBox(boxId, true, edit)
        } else structuralEditBox(boxId, edit = edit)
    }

    public fun deleteRichContentBlock(boxId: String, blockIndex: Int) =
        structuralEditBox(boxId) { it.deleteBlock(blockIndex) }

    public fun moveRichContentBlock(boxId: String, fromIndex: Int, toIndex: Int) =
        structuralEditBox(boxId) { it.moveBlock(fromIndex, toIndex) }

    public fun finishRichContentEditing(boxId: String) {
        commitRichContentEditing(boxId)
        replaceStateWithoutRecordingHistory {
            state = state.copy(currentTool = EditorTool.Pen, focusedRichContentBoxId = null, selection = SelectionState())
        }
    }

    public fun commitRichContentEditing(boxId: String) {
        val pageId = state.currentPageId ?: return
        val key = richContentSessionKey(boxId)
        val current = richContentBox(boxId)
        current?.let { richContentSessions[key]?.blurCommit(it) }
        val normalized = current?.normalizedForCommit()
        val keep = normalized?.hasMeaningfulContent() == true
        val nextObjects = currentCanvas.objects.mapNotNull { objectValue ->
            when {
                objectValue !is RichContentBox || objectValue.id != boxId -> objectValue
                keep -> normalized
                else -> null
            }
        }
        val nextCanvas = currentCanvas.copy(objects = nextObjects).setFocusedRichContentBox(null)
        val baseline = richContentEditBaselines.remove(key)
        val transientDocument = state.document.withCanvasForPageTransient(pageId, nextCanvas)
        val changed = baseline != null && !baseline.hasSameHistoryContentAs(transientDocument.historySnapshot())
        val finalDocument = if (changed) transientDocument.touchPage(pageId) else transientDocument
        if (changed && baseline != null) pushUndoSnapshot(baseline)
        draftRichContentBoxes.remove(key)
        richContentSessions.remove(key)
        selectedRichContentObjectBlocks.remove(key)
        replaceStateWithoutRecordingHistory {
            val wasFocused = state.focusedRichContentBoxId == boxId
            state = state.copy(
                document = finalDocument,
                currentTool = if (wasFocused && state.currentTool == EditorTool.Text) EditorTool.Pen else state.currentTool,
                focusedRichContentBoxId = state.focusedRichContentBoxId.takeUnless { it == boxId },
            )
        }
        richContentInteractionRevision++
    }

    private fun editBox(
        boxId: String,
        keepFocused: Boolean = false,
        edit: (RichContentEditorSession) -> com.neonote.engine.RichContentEditorEdit,
    ) {
        if (!canEditRichContent()) return
        val key = richContentSessionKey(boxId)
        richContentEditBaselines.putIfAbsent(key, state.document.historySnapshot())
        var updatedBox: RichContentBox? = null
        val previousCanvas = currentCanvas
        val canvas = previousCanvas.updateRichContentBox(boxId) { box ->
            val result = edit(editorSessionFor(boxId, box))
            val measured = if (result.box.autoSizeHeight) {
                val resized = richContentMeasurer.resizeBoxToMeasuredContent(result.box)
                resized.copy(size = resized.size.copy(height = maxOf(MinimumTextBoxHeightDp * displayDensity, resized.size.height)))
            } else result.box
            updatedBox = measured
            measured
        }
        updatedBox?.let { editorSessionFor(boxId, it).focus(it) }
        replaceStateWithoutRecordingHistory {
            state = state.copy(
                document = state.document.withCanvasTransient(if (keepFocused) canvas.setFocusedRichContentBox(boxId) else canvas),
                focusedRichContentBoxId = if (keepFocused) boxId else state.focusedRichContentBoxId,
            )
        }
        if (canvas != previousCanvas && key !in activeRichContentGestures) contentChangeToken++
        richContentInteractionRevision++
    }

    public fun moveFocusedRichContentBoxByScreenDelta(boxId: String, screenDelta: Offset) {
        if (state.focusedRichContentBoxId != boxId) return
        richContentEditBaselines.putIfAbsent(richContentSessionKey(boxId), state.document.historySnapshot())
        val delta = screenDeltaToDocumentDelta(screenDelta)
        val previousCanvas = currentCanvas
        val canvas = previousCanvas.updateRichContentBox(boxId) { box ->
            if (box.isLocked) box else box.copy(position = CanvasPoint(box.position.x + delta.x, box.position.y + delta.y))
        }
        replaceStateWithoutRecordingHistory { state = state.copy(document = state.document.withCanvasTransient(canvas)) }
        if (canvas != previousCanvas && richContentSessionKey(boxId) !in activeRichContentGestures) contentChangeToken++
    }

    public fun resizeFocusedRichContentBoxWidthByScreenDelta(boxId: String, screenDx: Float) {
        if (state.focusedRichContentBoxId != boxId) return
        richContentEditBaselines.putIfAbsent(richContentSessionKey(boxId), state.document.historySnapshot())
        val dx = screenDeltaToDocumentDelta(Offset(screenDx, 0f)).x
        val zoom = state.viewport.zoomScale.coerceAtLeast(MinZoomScale)
        val min = MinimumTextBoxWidthDp * displayDensity / zoom
        val max = MaximumTextBoxWidthDp * displayDensity / zoom
        val previousCanvas = currentCanvas
        val canvas = previousCanvas.updateRichContentBox(boxId) { box ->
            if (box.isLocked) box else box.copy(size = box.size.copy(width = (box.size.width + dx).coerceIn(min, max)))
        }
        replaceStateWithoutRecordingHistory { state = state.copy(document = state.document.withCanvasTransient(canvas)) }
        if (canvas != previousCanvas && richContentSessionKey(boxId) !in activeRichContentGestures) contentChangeToken++
    }

    public fun updateRichContentBoxMeasuredHeight(boxId: String, measuredHeight: Float) {
        val targetHeight = measuredHeight.coerceAtLeast(MinimumTextBoxHeightDp * displayDensity)
        val box = richContentBox(boxId) ?: return
        if (!box.autoSizeHeight || kotlin.math.abs(box.size.height - targetHeight) < 1f) return
        val canvas = currentCanvas.updateRichContentBox(boxId) { current -> current.copy(size = current.size.copy(height = targetHeight)) }
        replaceStateWithoutRecordingHistory { state = state.copy(document = state.document.withCanvasTransient(canvas)) }
    }

    private fun editorSessionFor(boxId: String, box: RichContentBox): RichContentEditorSession =
        richContentSessions.getOrPut(richContentSessionKey(boxId)) {
            RichContentEditorSession(box, engine = richContentEngine)
        }.also { it.focus(box) }

    private fun richContentBox(boxId: String): RichContentBox? =
        currentCanvas.objects.filterIsInstance<RichContentBox>().firstOrNull { it.id == boxId }

    private fun richContentSessionKey(boxId: String): String = "${state.currentPageId}:$boxId"
    private fun canEditRichContent(): Boolean = state.currentTool == EditorTool.Text && state.selection.selectedRefs.isEmpty()

    public fun selectCanvasObject(objectId: String) {
        state.focusedRichContentBoxId?.let(::commitRichContentEditing)
        val result = selectionEngine.execute(SelectionState(), SelectionCommand.SelectCanvasObject(objectId))
            as SelectionCommandResult.SelectionChanged
        replaceStateWithoutRecordingHistory {
            state = state.copy(
                selection = result.selection,
                focusedRichContentBoxId = null,
                document = state.document.withCanvasTransient(currentCanvas.setFocusedRichContentBox(null)),
            )
        }
    }

    public fun clearSelection() {
        val result = selectionEngine.execute(state.selection, SelectionCommand.Clear) as SelectionCommandResult.SelectionChanged
        replaceStateWithoutRecordingHistory { state = state.copy(selection = result.selection) }
    }

    public fun selectWithLasso(documentPath: List<CanvasPoint>): SelectionState {
        val selection = selectionEngine.selectWithLasso(currentCanvas, documentPath)
        val result = selectionEngine.execute(state.selection, SelectionCommand.ReplaceSelection(selection))
            as SelectionCommandResult.SelectionChanged
        replaceStateWithoutRecordingHistory {
            state = state.copy(
                selection = result.selection,
                focusedRichContentBoxId = null,
                document = state.document.withCanvasTransient(currentCanvas.setFocusedRichContentBox(null)),
            )
        }
        return result.selection
    }

    private fun beginSelectionGesture(screenPosition: CanvasPoint) {
        val document = screenToDocument(screenPosition)
        if (state.selection.selectedRefs.isNotEmpty() && selectedBounds?.contains(document) == true) {
            beginSelectionTransform()
            activeSelectionGesture = ActiveSelectionGesture.Drag(screenPosition)
            return
        }
        val hit = hitSelectableAt(document)
        if (hit != null) {
            if (hit !in state.selection.selectedRefs) replaceSelection(SelectionState(setOf(hit)))
            beginSelectionTransform()
            activeSelectionGesture = ActiveSelectionGesture.Drag(screenPosition)
        } else {
            activeSelectionGesture = ActiveSelectionGesture.Lasso(listOf(document))
        }
    }

    private fun updateSelectionGesture(screenPosition: CanvasPoint) {
        when (val gesture = activeSelectionGesture) {
            is ActiveSelectionGesture.Drag -> {
                moveSelectedObjectsByScreenDelta(Offset(
                    screenPosition.x - gesture.lastScreenPosition.x,
                    screenPosition.y - gesture.lastScreenPosition.y,
                ))
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
            is ActiveSelectionGesture.Lasso -> if (gesture.path.size < 2) clearSelection() else selectWithLasso(gesture.path)
            is ActiveSelectionGesture.Drag -> endSelectionTransform()
            null -> endInkIfActive()
        }
        activeSelectionGesture = null
    }

    private fun cancelActiveInteraction() {
        if (activeSelectionGesture is ActiveSelectionGesture.Drag) cancelSelectionTransform()
        activeSelectionGesture = null
        cancelInkIfActive()
    }

    private fun replaceSelection(selection: SelectionState) {
        replaceStateWithoutRecordingHistory {
            state = state.copy(
                selection = selection,
                focusedRichContentBoxId = null,
                document = state.document.withCanvasTransient(currentCanvas.setFocusedRichContentBox(null)),
            )
        }
    }

    private fun hitSelectableAt(position: CanvasPoint): com.neonote.model.SelectableRef? {
        selectionEngine.hitTestCanvasObjects(currentCanvas, position).firstOrNull()?.let { return CanvasObjectRef(it.id) }
        selectionEngine.hitTestInkStrokes(currentCanvas, position).firstOrNull()?.let { return InkStrokeRef(it.id) }
        return null
    }

    public fun moveSelectedObjectsByScreenDelta(screenDelta: Offset) {
        if (state.selection.selectedRefs.isEmpty()) return
        if (selectionTransformBaseline == null) beginSelectionTransform()
        val delta = screenDeltaToDocumentDelta(screenDelta)
        applyCanvasTransient(selectionEngine.moveSelection(currentCanvas, state.selection, delta.x, delta.y))
    }

    public fun screenToDocument(screenPosition: CanvasPoint): CanvasPoint = state.viewport.screenToDocument(screenPosition)
    public fun documentToScreen(documentPosition: CanvasPoint): CanvasPoint = state.viewport.documentToScreen(documentPosition)
    public fun screenDeltaToDocumentDelta(screenDelta: Offset): Offset = state.viewport.screenDeltaToDocumentDelta(screenDelta)

    private fun applyCanvasTransient(canvas: InfiniteCanvas) {
        if (canvas == currentCanvas) return
        replaceStateWithoutRecordingHistory {
            state = state.copy(document = state.document.withCanvasTransient(canvas))
        }
        inkSession = InkSession.fromCanvas(canvas)
    }

    private fun applyCanvas(
        canvas: InfiniteCanvas,
        clearSelection: Boolean = false,
        selection: SelectionState = state.selection,
    ) {
        if (canvas == currentCanvas && (!clearSelection || state.selection.selectedRefs.isEmpty())) return
        state = state.copy(
            document = state.document.withCanvas(canvas),
            selection = if (clearSelection) SelectionState() else selection,
            focusedRichContentBoxId = if (clearSelection) null else state.focusedRichContentBoxId,
        )
        inkSession = InkSession.fromCanvas(canvas)
    }

    private fun restoreDocumentFromHistory(document: NeoNoteDocument) {
        val measured = document.withMeasuredRichContentBoxHeights().ensurePageDocument()
        val pageId = state.currentPageId.takeIf { id -> measured.pages.any { it.id == id } } ?: measured.pages.first().id
        replaceStateWithoutRecordingHistory {
            state = state.copy(document = measured, currentPageId = pageId, focusedRichContentBoxId = null, selection = SelectionState())
        }
        resetTransientEditingState()
    }

    private fun resetTransientEditingState() {
        richContentSessions.clear()
        selectedRichContentObjectBlocks.clear()
        draftRichContentBoxes.clear()
        richContentEditBaselines.clear()
        activeSelectionGesture = null
        selectionTransformBaseline = null
        activeRichContentGestures.clear()
        searchHighlightedObjectId = null
        richContentInteractionRevision++
        inkSession = InkSession.fromCanvas(currentCanvas)
    }

    private inline fun replaceStateWithoutRecordingHistory(block: () -> Unit) {
        historyMutationInProgress = true
        try { block() } finally { historyMutationInProgress = false }
    }

    public fun documentForPersistence(): NeoNoteDocument = state.document.copy(
        pages = state.document.pages.map { page ->
            page.copy(canvas = page.canvas.copy(objects = page.canvas.objects.mapNotNull { objectValue ->
                if (objectValue !is RichContentBox) return@mapNotNull objectValue
                val key = "${page.id}:${objectValue.id}"
                val normalized = objectValue.normalizedForCommit()
                when {
                    key in draftRichContentBoxes && !normalized.hasMeaningfulContent() -> null
                    normalized.hasMeaningfulContent() -> normalized
                    else -> null
                }
            }))
        },
    )

    internal fun applyDocumentStructureChange(document: NeoNoteDocument, preferredPageId: String? = state.currentPageId) {
        state.focusedRichContentBoxId?.let(::commitRichContentEditing)
        val next = document.ensurePageDocument()
        if (state.document.hasSameHistoryContentAs(next)) return
        pushUndoSnapshot(state.document)
        val pageId = preferredPageId?.takeIf { id -> next.pages.any { it.id == id } } ?: next.pages.first().id
        replaceStateWithoutRecordingHistory {
            state = state.copy(document = next, currentPageId = pageId, focusedRichContentBoxId = null,
                selection = SelectionState(), viewport = pageViewports[pageId] ?: ViewportState())
        }
        resetTransientEditingState()
    }

    private fun pushUndoSnapshot(document: NeoNoteDocument) {
        val snapshot = document.historySnapshot()
        if (undoDocuments.lastOrNull()?.hasSameHistoryContentAs(snapshot) == true) return
        undoDocuments.addLast(snapshot)
        undoDocuments.trimToHistoryLimit()
        redoDocuments.clear()
    }

    private fun NeoNoteDocument.withCanvasTransient(canvas: InfiniteCanvas): NeoNoteDocument =
        withCanvasForPageTransient(state.currentPageId, canvas)

    private fun NeoNoteDocument.withCanvasForPageTransient(pageId: String?, canvas: InfiniteCanvas): NeoNoteDocument {
        val page = pages.firstOrNull { it.id == pageId } ?: return this
        if (page.canvas == canvas) return this
        return copy(pages = pages.map { current -> if (current.id == page.id) current.copy(canvas = canvas) else current })
    }

    private fun NeoNoteDocument.touchPage(pageId: String): NeoNoteDocument {
        val now = System.currentTimeMillis()
        return copy(
            pages = pages.map { page -> if (page.id == pageId) page.copy(updatedAtEpochMillis = now) else page },
            revision = revision + 1,
            updatedAtEpochMillis = now,
        )
    }

    private fun nextRichContentBoxId(): String = idGenerator.nextId("rich-content")

    private fun NeoNoteDocument.withMeasuredRichContentBoxHeights(): NeoNoteDocument = copy(pages = pages.map { page ->
        page.copy(canvas = page.canvas.copy(objects = page.canvas.objects.map { objectValue ->
            if (objectValue is RichContentBox && objectValue.autoSizeHeight) {
                val measured = richContentMeasurer.resizeBoxToMeasuredContent(objectValue)
                measured.copy(size = measured.size.copy(
                    height = maxOf(objectValue.size.height, measured.size.height),
                ))
            } else objectValue
        }))
    })

    private fun NeoNoteDocument.withCanvas(canvas: InfiniteCanvas): NeoNoteDocument =
        withCanvasForPage(state.currentPageId, canvas)

    private fun NeoNoteDocument.withCanvasForPage(pageId: String?, canvas: InfiniteCanvas): NeoNoteDocument {
        val page = pages.firstOrNull { it.id == pageId } ?: return this
        if (page.canvas == canvas) return this
        val now = System.currentTimeMillis()
        return copy(
            pages = pages.map { current -> if (current.id == page.id) current.copy(canvas = canvas, updatedAtEpochMillis = now) else current },
            revision = revision + 1,
            updatedAtEpochMillis = now,
        )
    }

    private fun EditorState.ensurePage(): EditorState {
        val document = document.ensurePageDocument()
        return copy(document = document, currentPageId = currentPageId?.takeIf { id -> document.pages.any { it.id == id } } ?: document.pages.first().id)
    }

    private fun NeoNoteDocument.ensurePageDocument(): NeoNoteDocument = if (pages.isNotEmpty()) this else copy(
        pages = listOf(NotePage(id = "page-1", title = "Page 1")),
    )

    private fun NeoNoteDocument.historySnapshot(): NeoNoteDocument = copy(
        pages = pages.map { page -> page.copy(canvas = page.canvas.copy(objects = page.canvas.objects.map { objectValue ->
            if (objectValue is RichContentBox) objectValue.copy(isFocused = false) else objectValue
        })) },
    )

    private fun NeoNoteDocument.hasSameHistoryContentAs(other: NeoNoteDocument): Boolean =
        copy(revision = 0L, updatedAtEpochMillis = 0L) == other.copy(revision = 0L, updatedAtEpochMillis = 0L)

    private fun ArrayDeque<NeoNoteDocument>.trimToHistoryLimit() {
        while (size > HistoryLimit) removeFirst()
    }
}

public fun createInitialEditorState(): EditorState {
    val generator = SequentialIdGenerator()
    val document = DocumentEngine(generator).createDocument(
        title = "Untitled Note",
        assetStoreId = "local-assets",
        documentId = "default-document",
        firstPageId = "page-initial",
        firstSectionId = "section-initial",
    )
    return EditorState(document = document, currentTool = EditorTool.Pen)
}

public fun createTestEditorState(): EditorState = EditorState(
    document = NeoNoteDocument(
        id = "test-document-v2",
        title = "NeoNote v2 Test Document",
        assetStoreId = "test-assets",
        pages = listOf(NotePage(id = "test-page-1")),
    ),
    currentTool = EditorTool.Text,
)

private fun InfiniteCanvas.updateRichContentBox(boxId: String, edit: (RichContentBox) -> RichContentBox): InfiniteCanvas =
    copy(objects = objects.map { objectValue -> if (objectValue is RichContentBox && objectValue.id == boxId) edit(objectValue) else objectValue })

public fun InfiniteCanvas.topMostObjectAt(position: CanvasPoint): CanvasObject? =
    objects.sortedWith(compareBy<CanvasObject> { it.zIndex }.thenBy { it.id }).lastOrNull { it.bounds.contains(position) }

private fun InfiniteCanvas.setFocusedRichContentBox(focusedId: String?): InfiniteCanvas =
    copy(objects = objects.map { objectValue ->
        if (objectValue is RichContentBox) objectValue.copy(isFocused = objectValue.id == focusedId) else objectValue
    })

private fun CanvasObject.isLockedForEditing(): Boolean = when (this) {
    is RichContentBox -> isLocked
    is FloatingImage -> isLocked
}

private data class CanvasClipboard(
    val objects: List<CanvasObject>,
    val strokes: List<InkStroke>,
    val bounds: CanvasRect,
)

private sealed interface ActiveSelectionGesture {
    data class Lasso(val path: List<CanvasPoint>) : ActiveSelectionGesture
    data class Drag(val lastScreenPosition: CanvasPoint) : ActiveSelectionGesture
}

private class SequentialIdGenerator : IdGenerator {
    private var nextId = 1L
    override fun nextId(prefix: String): String = "$prefix-${nextId++}"
}

private fun PersistenceDiagnostics?.toStatusSuffix(): String = this?.let { " (${it.toDebugSummary()})" }.orEmpty()
