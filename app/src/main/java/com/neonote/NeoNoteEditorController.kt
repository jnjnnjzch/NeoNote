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

private const val DefaultBoxWidth = 960f
private const val DefaultBoxHeight = 220f
private const val DefaultViewportWidthPx = 1280f
private const val MinimumTextBoxWidthDp = 320f
private const val PreferredTextBoxWidthDp = 520f
private const val MaximumTextBoxWidthDp = 760f
private const val MinimumTextBoxHeightDp = 64f
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

    private var activeSelectionGesture: ActiveSelectionGesture? by mutableStateOf(null)
    private var lastInputDiagnosticsUpdateMillis: Long? = null
    private var pendingInputDiagnostics: InputDiagnostics? = null
    private var richContentInteractionRevision by mutableStateOf(0)
    private var displayDensity: Float = 2f
    private var viewportWidthScreenPx: Float = DefaultViewportWidthPx
    private val richContentSessions = mutableMapOf<String, RichContentEditorSession>()
    private val selectedRichContentObjectBlocks = mutableMapOf<String, Int>()

    public val saveStateLabel: String
        get() = when {
            lastPersistedRevision == state.document.revision -> "Saved"
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
        if (undoDocuments.isEmpty()) return
        val previous = undoDocuments.removeLast()
        redoDocuments.addLast(state.document.historySnapshot())
        redoDocuments.trimToHistoryLimit()
        restoreDocumentFromHistory(previous)
    }

    public fun redo() {
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
        state = state.copy(
            currentTool = tool,
            focusedRichContentBoxId = if (tool == EditorTool.Text) state.focusedRichContentBoxId else null,
            selection = if (tool == EditorTool.Selection) state.selection else SelectionState(),
            document = if (canvas == currentCanvas) state.document else state.document.withCanvas(canvas),
        )
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

    public fun resetViewport() { state = state.copy(viewport = ViewportState()) }

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
        val result = documentEngine.execute(DocumentCommand.AddPage(state.document)) as DocumentCommandResult.PageAdded
        state = state.copy(document = result.document)
        switchPage(result.page.id)
    }

    public fun renamePage(pageId: String, title: String) {
        val result = documentEngine.execute(DocumentCommand.RenamePage(state.document, pageId, title))
            as DocumentCommandResult.PageUpdated
        state = state.copy(document = result.document)
    }

    public fun duplicatePage(pageId: String = currentPage.id) {
        val result = documentEngine.execute(DocumentCommand.DuplicatePage(state.document, pageId))
            as DocumentCommandResult.PageAdded
        state = state.copy(document = result.document)
        switchPage(result.page.id)
    }

    public fun deletePage(pageId: String = currentPage.id) {
        val result = documentEngine.execute(DocumentCommand.DeletePage(state.document, pageId))
            as DocumentCommandResult.PageDeleted
        if (result.document == state.document) return
        replaceStateWithoutRecordingHistory {
            state = state.copy(document = result.document, currentPageId = result.nextPageId)
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
        state.focusedRichContentBoxId?.let(::commitRichContentEditing)
        val clearedDocument = state.document.withCanvasForPage(currentPage.id, currentCanvas.setFocusedRichContentBox(null))
        val result = documentEngine.execute(DocumentCommand.SwitchPage(
            state.copy(document = clearedDocument, focusedRichContentBoxId = null, selection = SelectionState()),
            pageId,
        )) as DocumentCommandResult.PageSwitched
        state = result.state
        activeSelectionGesture = null
        inkSession = InkSession.fromCanvas(currentCanvas)
    }

    public fun deleteSelection() {
        if (state.selection.selectedRefs.isEmpty()) return
        applyCanvas(selectionEngine.deleteSelection(currentCanvas, state.selection), clearSelection = true)
    }

    public fun duplicateSelection() {
        if (state.selection.selectedRefs.isEmpty()) return
        val result = selectionEngine.duplicateSelection(currentCanvas, state.selection, idGenerator)
        applyCanvas(result.canvas, selection = result.selection)
    }

    public fun scaleSelection(scaleX: Float, scaleY: Float = scaleX) {
        if (state.selection.selectedRefs.isEmpty()) return
        applyCanvas(selectionEngine.scaleSelection(currentCanvas, state.selection, scaleX, scaleY))
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
        val saved = store.save(state.document)
        lastPersistedRevision = saved.revision
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
        replaceDocument(document.withMeasuredRichContentBoxHeights(), recordHistory = false)
        lastPersistedRevision = document.revision
        persistenceDiagnostics = loaded.diagnostics
        persistenceStatus = "Loaded ${document.id} at revision ${document.revision}" + loaded.diagnostics.toStatusSuffix()
        return loaded
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

    public fun panViewportBy(screenDx: Float, screenDy: Float) {
        state = state.copy(viewport = state.viewport.copy(
            panOffsetX = state.viewport.panOffsetX + screenDx,
            panOffsetY = state.viewport.panOffsetY + screenDy,
        ))
    }

    public fun zoomViewportBy(zoomChange: Float, screenCentroid: CanvasPoint) {
        if (zoomChange == 1f) return
        state = state.copy(viewport = state.viewport.zoomAroundScreenPoint(
            zoomChange, screenCentroid, MinZoomScale, MaxZoomScale,
        ))
    }

    public fun updateViewportMetrics(widthPx: Int, density: Float) {
    if (widthPx > 0) viewportWidthScreenPx = widthPx.toFloat()
    if (density.isFinite() && density > 0f) displayDensity = density
}

public fun focusOrCreateRichContentBox(documentPosition: CanvasPoint) {
    val existing = currentCanvas.topMostObjectAt(documentPosition) as? RichContentBox
    if (existing != null) {
        activateRichContentBox(existing.id)
        return
    }
    if (state.currentTool != EditorTool.Text) return
    val zoom = state.viewport.zoomScale.coerceAtLeast(MinZoomScale)
    val minimumWidth = MinimumTextBoxWidthDp * displayDensity / zoom
    val preferredWidth = PreferredTextBoxWidthDp * displayDensity / zoom
    val maximumWidth = MaximumTextBoxWidthDp * displayDensity / zoom
    val availableWidth = (viewportWidthScreenPx - TextBoxScreenMarginDp * 2f * displayDensity) / zoom
    val width = preferredWidth.coerceIn(minimumWidth, maximumWidth)
        .coerceAtMost(availableWidth.coerceAtLeast(minimumWidth))
    val visibleLeft = -state.viewport.panOffsetX / zoom
    val visibleRight = (viewportWidthScreenPx - state.viewport.panOffsetX) / zoom
    val margin = TextBoxScreenMarginDp * displayDensity / zoom
    val maximumLeft = (visibleRight - width - margin).coerceAtLeast(visibleLeft + margin)
    val position = documentPosition.copy(
        x = documentPosition.x.coerceIn(visibleLeft + margin, maximumLeft),
    )
    val minimumHeight = MinimumTextBoxHeightDp * displayDensity / zoom
    val box = RichContentBox(
        id = nextRichContentBoxId(),
        position = position,
        size = CanvasSize(width, maxOf(DefaultBoxHeight / zoom, minimumHeight)),
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
            EditorTool.Text -> focusRichContentBox(boxId)
            EditorTool.Selection -> selectCanvasObject(boxId)
            EditorTool.Pen, EditorTool.Eraser -> Unit
        }
    }

    public fun focusRichContentBox(boxId: String) {
        if (state.currentTool != EditorTool.Text) return
        state.focusedRichContentBoxId?.takeIf { it != boxId }?.let(::commitRichContentEditing)
        val box = currentCanvas.objects.filterIsInstance<RichContentBox>().firstOrNull { it.id == boxId } ?: return
        if (box.isLocked) return
        editorSessionFor(boxId, box).focus(box)
        state = state.copy(
            document = state.document.withCanvas(currentCanvas.setFocusedRichContentBox(boxId)),
            focusedRichContentBoxId = boxId,
            selection = SelectionState(),
        )
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
        if (hasActiveComposition) session.replaceFromPlatformCompositionFallback(nextText, selectionStart, selectionEnd)
        else session.replaceFromPlatformInput(previousText, nextText, selectionStart, selectionEnd)
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

    public fun focusRichContentTableCell(boxId: String, address: TableCellAddress) {
        if (!canEditRichContent()) return
        focusRichContentBox(boxId)
        richContentBox(boxId)?.let { editorSessionFor(boxId, it).focusTableCell(address) }
        selectedRichContentObjectBlocks.remove(richContentSessionKey(boxId))
        richContentInteractionRevision++
    }

    public fun updateRichContentTableCellFromPlatformInput(boxId: String, address: TableCellAddress, nextText: String) =
        editBox(boxId, keepFocused = true) { it.replaceTableCellParagraphFromPlatformInput(address, nextText) }

    public fun updateRichContentParagraphFromPlatformInput(
        boxId: String,
        blockIndex: Int,
        previousText: String,
        nextText: String,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
        hasActiveComposition: Boolean = false,
    ) = editBox(boxId) { session ->
        if (hasActiveComposition) {
            session.replaceParagraphFromPlatformCompositionFallback(blockIndex, nextText, selectionStart, selectionEnd)
        } else {
            session.replaceFromPlatformParagraphInput(blockIndex, previousText, nextText, selectionStart, selectionEnd)
        }
    }

    public fun updateRichContentFormulaExpression(boxId: String, blockIndex: Int, expression: String) =
        editBox(boxId, keepFocused = true) { it.replaceFormulaExpression(blockIndex, expression) }

    public fun updateRichContentFormula(
        boxId: String,
        blockIndex: Int,
        expression: String? = null,
        displayMode: FormulaDisplayMode? = null,
        numbered: Boolean? = null,
    ) = editBox(boxId, keepFocused = true) { it.updateFormula(blockIndex, expression, displayMode, numbered) }

    public fun toggleRichContentParagraphStyle(
        boxId: String,
        blockIndex: Int,
        style: InlineStyle,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
    ) = editBox(boxId) { it.focusParagraph(blockIndex, selectionStart, selectionEnd); it.toggleStyle(style) }

    public fun toggleRichContentParagraphList(
        boxId: String,
        blockIndex: Int,
        kind: ListKind,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
    ) = editBox(boxId) { it.focusParagraph(blockIndex, selectionStart, selectionEnd); it.toggleList(kind) }

    public fun toggleRichContentStyle(
        boxId: String,
        style: InlineStyle,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
    ) = editBox(boxId) { it.setSelectionFromPlainOffsets(selectionStart, selectionEnd); it.toggleStyle(style) }

    public fun toggleRichContentList(
        boxId: String,
        kind: ListKind,
        selectionStart: Int,
        selectionEnd: Int = selectionStart,
    ) = editBox(boxId) { it.setSelectionFromPlainOffsets(selectionStart, selectionEnd); it.toggleList(kind) }

    public fun toggleRichContentTodoCheckedState(boxId: String, blockIndex: Int) =
        editBox(boxId) { it.toggleTodoCheckedState(blockIndex) }

    public fun toggleActiveRichContentStyle(boxId: String, style: InlineStyle) =
        editBox(boxId, keepFocused = true) { it.toggleStyle(style) }

    public fun toggleActiveRichContentList(boxId: String, kind: ListKind) =
        editBox(boxId, keepFocused = true) { it.toggleList(kind) }

    public fun setActiveRichContentTextColor(boxId: String, colorArgb: Int?) =
        editBox(boxId, keepFocused = true) { it.setTextColor(colorArgb) }

    public fun setActiveRichContentHighlight(boxId: String, colorArgb: Int?) =
        editBox(boxId, keepFocused = true) { it.setHighlightColor(colorArgb) }

    public fun setActiveRichContentFontScale(boxId: String, scale: Float) =
        editBox(boxId, keepFocused = true) { it.setFontScale(scale) }

    public fun setActiveRichContentLink(boxId: String, url: String?) =
        editBox(boxId, keepFocused = true) { it.setLink(url) }

    public fun setActiveParagraphAlignment(boxId: String, alignment: TextAlignment) =
        editBox(boxId, keepFocused = true) { it.setParagraphAlignment(alignment) }

    public fun setActiveHeadingLevel(boxId: String, level: Int) =
        editBox(boxId, keepFocused = true) { it.setHeadingLevel(level) }

    public fun changeActiveParagraphIndent(boxId: String, delta: Int) =
        editBox(boxId, keepFocused = true) { it.changeIndent(delta) }

    public fun insertRichContentTablePlaceholder(boxId: String, rows: Int = 2, columns: Int = 2) =
        editBox(boxId, keepFocused = true) { it.insertTablePlaceholder(rows, columns) }

    public fun insertNestedTable(boxId: String, address: TableCellAddress, rows: Int = 2, columns: Int = 2) =
        editBox(boxId, keepFocused = true) { it.insertNestedTable(address, rows, columns) }

    public fun addActiveRichContentTableRow(boxId: String) {
        val address = (activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell)?.address ?: return
        editBox(boxId, keepFocused = true) { it.addTableRow(address) }
    }

    public fun deleteActiveRichContentTableRow(boxId: String) {
        val address = (activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell)?.address ?: return
        editBox(boxId, keepFocused = true) { it.deleteTableRow(address) }
    }

    public fun addActiveRichContentTableColumn(boxId: String) {
        val address = (activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell)?.address ?: return
        editBox(boxId, keepFocused = true) { it.addTableColumn(address) }
    }

    public fun deleteActiveRichContentTableColumn(boxId: String) {
        val address = (activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell)?.address ?: return
        editBox(boxId, keepFocused = true) { it.deleteTableColumn(address) }
    }

    public fun setActiveRichContentTableColumnWidth(boxId: String, width: Float?) {
        val address = (activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell)?.address ?: return
        editBox(boxId, keepFocused = true) { it.setTableColumnWidth(address, width) }
    }

    public fun insertRichContentFormulaPlaceholder(boxId: String, expression: String = "") =
        editBox(boxId, keepFocused = true) { it.insertBlockFormulaPlaceholder(expression) }

    public fun insertRichContentImagePlaceholder(
        boxId: String,
        assetId: String = "image-placeholder",
        altText: String? = "Image placeholder",
    ) {
        var insertion = 0
        editBox(boxId, keepFocused = true) { session ->
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
    ) = editBox(boxId, keepFocused = true) {
        it.updateImage(blockIndex, replacementAssetId, width = width, height = height,
            rotationDegrees = rotationDegrees, crop = crop, caption = caption)
    }

    public fun deleteRichContentBlock(boxId: String, blockIndex: Int) =
        editBox(boxId, keepFocused = true) { it.deleteBlock(blockIndex) }

    public fun moveRichContentBlock(boxId: String, fromIndex: Int, toIndex: Int) =
        editBox(boxId, keepFocused = true) { it.moveBlock(fromIndex, toIndex) }

    public fun commitRichContentEditing(boxId: String) {
        richContentBox(boxId)?.let { richContentSessions[richContentSessionKey(boxId)]?.blurCommit(it) }
    }

    private fun editBox(
        boxId: String,
        keepFocused: Boolean = false,
        edit: (RichContentEditorSession) -> com.neonote.engine.RichContentEditorEdit,
    ) {
        if (!canEditRichContent()) return
        var updatedBox: RichContentBox? = null
        val canvas = currentCanvas.updateRichContentBox(boxId) { box ->
            val result = edit(editorSessionFor(boxId, box))
            val measured = if (result.box.autoSizeHeight) {
                val predicted = richContentMeasurer.resizeBoxToMeasuredContent(result.box)
                predicted.copy(size = predicted.size.copy(
                    height = maxOf(result.box.size.height, predicted.size.height),
                ))
            } else result.box
            updatedBox = measured
            measured
        }
        updatedBox?.let { editorSessionFor(boxId, it).focus(it) }
        state = state.copy(
            document = state.document.withCanvas(if (keepFocused) canvas.setFocusedRichContentBox(boxId) else canvas),
            focusedRichContentBoxId = if (keepFocused) boxId else state.focusedRichContentBoxId,
        )
        richContentInteractionRevision++
    }

    public fun updateRichContentBoxMeasuredHeight(boxId: String, measuredHeight: Float) {
    val targetHeight = measuredHeight.coerceAtLeast(MinimumTextBoxHeightDp * displayDensity)
    val box = richContentBox(boxId) ?: return
    if (!box.autoSizeHeight || kotlin.math.abs(box.size.height - targetHeight) < 1f) return
    val canvas = currentCanvas.updateRichContentBox(boxId) { current ->
        current.copy(size = current.size.copy(height = targetHeight))
    }
    replaceStateWithoutRecordingHistory {
        state = state.copy(document = state.document.withCanvas(canvas))
    }
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
        val result = selectionEngine.execute(SelectionState(), SelectionCommand.SelectCanvasObject(objectId))
            as SelectionCommandResult.SelectionChanged
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
        val result = selectionEngine.execute(state.selection, SelectionCommand.ReplaceSelection(selection))
            as SelectionCommandResult.SelectionChanged
        state = state.copy(
            selection = result.selection,
            focusedRichContentBoxId = null,
            document = state.document.withCanvas(currentCanvas.setFocusedRichContentBox(null)),
        )
        return result.selection
    }

    private fun beginSelectionGesture(screenPosition: CanvasPoint) {
        val document = screenToDocument(screenPosition)
        if (state.selection.selectedRefs.isNotEmpty() && selectedBounds?.contains(document) == true) {
            activeSelectionGesture = ActiveSelectionGesture.Drag(screenPosition)
            return
        }
        val hit = hitSelectableAt(document)
        if (hit != null) {
            if (hit !in state.selection.selectedRefs) replaceSelection(SelectionState(setOf(hit)))
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
            is ActiveSelectionGesture.Drag, null -> endInkIfActive()
        }
        activeSelectionGesture = null
    }

    private fun cancelActiveInteraction() {
        activeSelectionGesture = null
        cancelInkIfActive()
    }

    private fun replaceSelection(selection: SelectionState) {
        state = state.copy(
            selection = selection,
            focusedRichContentBoxId = null,
            document = state.document.withCanvas(currentCanvas.setFocusedRichContentBox(null)),
        )
    }

    private fun hitSelectableAt(position: CanvasPoint): com.neonote.model.SelectableRef? {
        selectionEngine.hitTestCanvasObjects(currentCanvas, position).firstOrNull()?.let { return CanvasObjectRef(it.id) }
        selectionEngine.hitTestInkStrokes(currentCanvas, position).firstOrNull()?.let { return InkStrokeRef(it.id) }
        return null
    }

    public fun moveSelectedObjectsByScreenDelta(screenDelta: Offset) {
        if (state.selection.selectedRefs.isEmpty()) return
        val delta = screenDeltaToDocumentDelta(screenDelta)
        applyCanvas(selectionEngine.moveSelection(currentCanvas, state.selection, delta.x, delta.y))
    }

    public fun screenToDocument(screenPosition: CanvasPoint): CanvasPoint = state.viewport.screenToDocument(screenPosition)
    public fun documentToScreen(documentPosition: CanvasPoint): CanvasPoint = state.viewport.documentToScreen(documentPosition)
    public fun screenDeltaToDocumentDelta(screenDelta: Offset): Offset = state.viewport.screenDeltaToDocumentDelta(screenDelta)

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
        activeSelectionGesture = null
        richContentInteractionRevision++
        inkSession = InkSession.fromCanvas(currentCanvas)
    }

    private inline fun replaceStateWithoutRecordingHistory(block: () -> Unit) {
        historyMutationInProgress = true
        try { block() } finally { historyMutationInProgress = false }
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
        firstPageId = "page-1",
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

private sealed interface ActiveSelectionGesture {
    data class Lasso(val path: List<CanvasPoint>) : ActiveSelectionGesture
    data class Drag(val lastScreenPosition: CanvasPoint) : ActiveSelectionGesture
}

private class SequentialIdGenerator : IdGenerator {
    private var nextId = 1L
    override fun nextId(prefix: String): String = "$prefix-${nextId++}"
}

private fun PersistenceDiagnostics?.toStatusSuffix(): String = this?.let { " (${it.toDebugSummary()})" }.orEmpty()
