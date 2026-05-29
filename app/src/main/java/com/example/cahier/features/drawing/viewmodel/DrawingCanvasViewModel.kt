/*
 * Copyright 2025 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.cahier.features.drawing.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.StockBrushes
import androidx.ink.brush.Version
import androidx.ink.brush.compose.composeColor
import androidx.ink.brush.compose.copyWithComposeColor
import androidx.ink.brush.compose.createWithComposeColor
import androidx.ink.geometry.AffineTransform
import androidx.ink.geometry.Intersection.intersects
import androidx.ink.geometry.MutableParallelogram
import androidx.ink.geometry.MutableSegment
import androidx.ink.geometry.MutableVec
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.storage.AndroidBrushFamilySerialization
import androidx.ink.strokes.Stroke
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.example.cahier.core.data.CustomBrush
import com.example.cahier.core.data.NotesRepository
import com.example.cahier.core.document.DocumentSerializer
import com.example.cahier.core.document.Block
import com.example.cahier.core.document.DocumentSettings
import com.example.cahier.core.document.ImageBlock
import com.example.cahier.core.document.FormulaBlock
import com.example.cahier.core.document.StrokeAnchor
import com.example.cahier.core.document.StrokeTransform
import com.example.cahier.core.document.TableBlock
import com.example.cahier.core.document.TableCell
import com.example.cahier.core.document.ParagraphNode
import com.example.cahier.core.document.TableNode
import com.example.cahier.core.document.TextContainerBlock
import com.example.cahier.core.document.TextContainerContent
import com.example.cahier.core.document.TicDocument
import com.example.cahier.core.navigation.DrawingCanvasDestination
import com.example.cahier.core.ui.CahierTextureBitmapStore
import com.example.cahier.core.ui.CahierUiState
import com.example.cahier.core.utils.FileHelper
import com.example.cahier.developer.brushdesigner.data.AUTOSAVE_KEY
import com.example.cahier.developer.brushdesigner.data.CustomBrushDao
import com.example.cahier.developer.brushdesigner.data.CustomBrushEntity
import com.example.cahier.features.drawing.CustomBrushes
import com.example.cahier.features.drawing.CanvasTransform
import com.example.cahier.features.drawing.CanvasTransformMapper
import com.example.cahier.features.drawing.InkDebugAggregator
import com.example.cahier.features.drawing.InkDebugMetrics
import com.example.cahier.features.drawing.InkDebugSample
import com.example.cahier.features.drawing.PressureCurveMapper
import com.example.cahier.features.drawing.StressDocumentFactory
import com.example.cahier.features.drawing.StrokeIdMapper
import com.example.cahier.features.drawing.export.ExportComposer
import com.example.cahier.features.drawing.export.TicNoteArchiveWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import javax.inject.Inject

@HiltViewModel
class DrawingCanvasViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val noteRepository: NotesRepository,
    val fileHelper: FileHelper,
    private val imageLoader: ImageLoader,
    private val customBrushDao: CustomBrushDao,
    val textureStore: CahierTextureBitmapStore,
) : ViewModel() {
    data class ExportResult(
        val directory: String,
        val generatedFiles: List<String>
    )

    enum class ImagePasteTarget {
        CANVAS_BLOCK,
        INLINE_TABLE_CELL
    }

    private val _uiState = MutableStateFlow(CahierUiState())
    val uiState: StateFlow<CahierUiState> = _uiState.asStateFlow()

    private val noteId: Long = savedStateHandle[DrawingCanvasDestination.NOTE_ID_ARG] ?: 0L

    private val currentNightMode = AppCompatDelegate.getDefaultNightMode()

    private val heartHighlighter =
        StockBrushes.emojiHighlighter("emoji-heart", showMiniEmojiTrail = true)
    private val poopHighlighter =
        StockBrushes.emojiHighlighter("emoji-poop", showMiniEmojiTrail = true)
    private val starHighlighter =
        StockBrushes.emojiHighlighter("emoji-star", showMiniEmojiTrail = true)
    private val highlighter = StockBrushes.highlighter()

    private val _defaultBrush = MutableStateFlow(
        Brush.createWithComposeColor(
            family = StockBrushes.pressurePen(),
            color = if (currentNightMode == AppCompatDelegate.MODE_NIGHT_YES)
                Color.White else Color.Gray,
            size = 5F,
            epsilon = 0.1F
        )
    )

    private val _selectedBrush = MutableStateFlow(_defaultBrush.value)
    val currentBrush = _selectedBrush.asStateFlow()

    private val _isEraserMode = MutableStateFlow(false)
    val isEraserMode: StateFlow<Boolean> = _isEraserMode.asStateFlow()

    private var previousPoint: MutableVec? = null
    private val eraserPadding = 50f

    private sealed interface DrawingEdit {
        val before: EditSnapshot
        val after: EditSnapshot

        data class DocumentEdit(
            override val before: EditSnapshot,
            override val after: EditSnapshot,
        ) : DrawingEdit

        data class InkEdit(
            override val before: EditSnapshot,
            override val after: EditSnapshot,
        ) : DrawingEdit
    }

    private data class EditSnapshot(
        val document: TicDocument,
        val strokes: List<Stroke>,
    )

    private val undoStack = mutableListOf<DrawingEdit>()
    private val redoStack = mutableListOf<DrawingEdit>()
    private var saveJob: Job? = null
    private var isDirty = false
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _exportedImageUri = MutableStateFlow<Uri?>(null)
    val exportedImageUri: StateFlow<Uri?> = _exportedImageUri.asStateFlow()

    private val _customBrushes = MutableStateFlow<List<CustomBrush>>(emptyList())
    val customBrushes: StateFlow<List<CustomBrush>> = _customBrushes.asStateFlow()
    private val _document = MutableStateFlow(TicDocument())
    val document: StateFlow<TicDocument> = _document.asStateFlow()
    private val _strokeTranslations = MutableStateFlow<Map<Int, Pair<Float, Float>>>(emptyMap())
    val strokeTranslations: StateFlow<Map<Int, Pair<Float, Float>>> = _strokeTranslations.asStateFlow()
    private var focusedBlockId: String? = null
    private val _selectedStrokeIndices = MutableStateFlow<Set<Int>>(emptySet())
    val selectedStrokeIndices: StateFlow<Set<Int>> = _selectedStrokeIndices.asStateFlow()
    private val _selectedTextContainer = MutableStateFlow(false)
    val selectedTextContainer: StateFlow<Boolean> = _selectedTextContainer.asStateFlow()
    private val _hasSelection = MutableStateFlow(false)
    val hasSelection: StateFlow<Boolean> = _hasSelection.asStateFlow()
    private val _inkDebugMetrics = MutableStateFlow(InkDebugMetrics())
    val inkDebugMetrics: StateFlow<InkDebugMetrics> = _inkDebugMetrics.asStateFlow()
    private val inkDebugAggregator = InkDebugAggregator()

    private var isBrushSelectedInSession = false
    private val _lastExportDirectory = MutableStateFlow<String?>(null)
    val lastExportDirectory: StateFlow<String?> = _lastExportDirectory.asStateFlow()
    private val _lastExportResult = MutableStateFlow<ExportResult?>(null)
    val lastExportResult: StateFlow<ExportResult?> = _lastExportResult.asStateFlow()
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()
    private val _stylusWritesByDefault = MutableStateFlow(true)
    val stylusWritesByDefault: StateFlow<Boolean> = _stylusWritesByDefault.asStateFlow()
    private val _fingerPansByDefault = MutableStateFlow(true)
    val fingerPansByDefault: StateFlow<Boolean> = _fingerPansByDefault.asStateFlow()
    private val _pressureCurve = MutableStateFlow(1.0f)
    val pressureCurve: StateFlow<Float> = _pressureCurve.asStateFlow()
    private val _selectionModeEnabled = MutableStateFlow(false)
    val selectionModeEnabled: StateFlow<Boolean> = _selectionModeEnabled.asStateFlow()
    private val _lastPressure = MutableStateFlow(0.5f)
    val lastPressure: StateFlow<Float> = _lastPressure.asStateFlow()
    private val _lastPressureUiSampled = MutableStateFlow(0.5f)
    val lastPressureUiSampled: StateFlow<Float> = _lastPressureUiSampled.asStateFlow()
    private var lastPressureUiSampledAtMs: Long = 0L

    private val currentSnapshot: EditSnapshot
        get() = EditSnapshot(
            document = _document.value,
            strokes = _uiState.value.strokes,
        )

    init {
        viewModelScope.launch {
            noteRepository.getNoteStream(noteId)
                .filterNotNull()
                .collect { note ->
                    if (isDirty) return@collect
                    val parsedDocument = DocumentSerializer.decodeOrNull(note.text) ?: TicDocument()
                    _document.value = parsedDocument
                    _pressureCurve.value = parsedDocument.settings.pressureCurve
                    _stylusWritesByDefault.value = parsedDocument.settings.stylusWritesByDefault
                    _fingerPansByDefault.value = parsedDocument.settings.fingerPansByDefault
                    recomputeStrokeTranslations()
                    if (note.text.isNullOrBlank()) {
                        noteRepository.updateNote(note.copy(text = DocumentSerializer.encode(parsedDocument)))
                    }

                    val initialStrokes = if (note.strokesData != null) {
                        noteRepository.getNoteStrokes(note.id)
                    } else {
                        emptyList()
                    }

                    note.clientBrushFamilyId?.let { id ->
                        if (!isBrushSelectedInSession) {
                            val customBrush = customBrushes.value.find {
                                it.name == id
                            }
                            customBrush?.let {
                                _selectedBrush.value =
                                    _selectedBrush.value.copy(family = it.brushFamily)
                            }
                        }
                    }

                    _uiState.update {
                        it.copy(note = note, strokes = initialStrokes)
                    }
                    updateUndoRedoState()
                }
        }

        loadCustomBrushes()
        viewModelScope.launch {
            while (true) {
                delay(PERIODIC_AUTOSAVE_MS)
                if (isDirty) flushPendingEdits()
            }
        }
    }

    fun addImageWithLocalUri(localUri: Uri?) {
        if (localUri == null) return
        val newImageUri = localUri.toString()
        _uiState.update { it.copy(note = it.note.copy(imageUriList = listOf(newImageUri))) }
        scheduleDebouncedSave()
    }

    private fun dispatchEdit(edit: DrawingEdit, scheduleSave: Boolean = true) {
        applySnapshot(edit.after)
        undoStack.add(edit)
        redoStack.clear()
        updateUndoRedoState()
        if (scheduleSave) scheduleDebouncedSave()
    }

    private fun dispatchDocumentEdit(document: TicDocument) {
        val before = currentSnapshot
        val afterDocument = document.nextRevision().withInkLayerMetadata(before.strokes.size)
        if (before.document == afterDocument) return
        dispatchEdit(
            DrawingEdit.DocumentEdit(
                before = before,
                after = before.copy(document = afterDocument),
            )
        )
    }

    private fun dispatchInkEdit(newStrokes: List<Stroke>) {
        val before = currentSnapshot
        val documentWithStrokeIds = documentForStrokes(
            document = before.document,
            oldStrokes = before.strokes,
            newStrokes = newStrokes,
        ).nextRevision().withInkLayerMetadata(newStrokes.size)
        val after = EditSnapshot(document = documentWithStrokeIds, strokes = newStrokes)
        if (before == after) return
        dispatchEdit(DrawingEdit.InkEdit(before = before, after = after))
    }

    private fun applySnapshot(snapshot: EditSnapshot) {
        _document.value = snapshot.document
        _uiState.update { it.copy(strokes = snapshot.strokes) }
        val maxIndex = snapshot.strokes.lastIndex
        _selectedStrokeIndices.value = _selectedStrokeIndices.value.filter { it in 0..maxIndex }.toSet()
        refreshSelectionState()
        recomputeStrokeTranslations()
    }

    private fun documentForStrokes(
        document: TicDocument,
        oldStrokes: List<Stroke>,
        newStrokes: List<Stroke>,
    ): TicDocument {
        val page = document.pages.firstOrNull() ?: return document
        val oldIds = if (page.strokeIds.size == oldStrokes.size) {
            page.strokeIds
        } else {
            List(oldStrokes.size) { UUID.randomUUID().toString() }
        }
        val remapped = StrokeIdMapper.remapIds(oldStrokes, newStrokes, oldIds)
        val validIds = remapped.toSet()
        val updatedAnchors = page.strokeAnchors.map { anchor ->
            anchor.copy(strokeIds = anchor.strokeIds.filter { it in validIds })
        }.filter { anchor ->
            anchor.strokeIds.isNotEmpty() || (
                anchor.startStrokeIndex != null &&
                    anchor.endStrokeIndexInclusive != null &&
                    anchor.endStrokeIndexInclusive >= anchor.startStrokeIndex
                )
        }
        val updatedTransforms = page.strokeTransforms.filter { it.strokeId in validIds }
        persistDocument(
            current.copy(
                pages = listOf(
                    page.copy(
                        strokeIds = remapped,
                        strokeAnchors = updatedAnchors,
                        strokeTransforms = updatedTransforms
                    )
                )
            )
        )
    }

    private fun TicDocument.nextRevision(): TicDocument = copy(revision = revision + 1L)

    private fun TicDocument.withInkLayerMetadata(strokeCount: Int): TicDocument {
        val page = pages.firstOrNull() ?: return this
        val updatedPage = page.copy(
            inkLayer = page.inkLayer.copy(
                source = "note_strokes_data_v1",
                documentRevision = revision,
                strokeCount = strokeCount,
            )
        )
        return copy(pages = listOf(updatedPage))
    }

    private fun updateUndoRedoState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun undo() {
        val edit = undoStack.removeLastOrNull() ?: return
        applySnapshot(edit.before)
        redoStack.add(edit)
        updateUndoRedoState()
        scheduleDebouncedSave()
    }

    fun redo() {
        val edit = redoStack.removeLastOrNull() ?: return
        applySnapshot(edit.after)
        undoStack.add(edit)
        updateUndoRedoState()
        scheduleDebouncedSave()
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            noteRepository.toggleFavorite(noteId)
        }
    }

    fun processAndAddImageFromPicker(uri: Uri?) {
        viewModelScope.launch {
            processAndAddImage(uri)
        }
    }

    suspend fun processAndAddImage(uri: Uri?) {
        if (uri == null) return
        val localFileUri = fileHelper.copyUriToInternalStorage(uri)
        addImageWithLocalUri(localFileUri)
    }

    fun replaceImage(uri: Uri?) {
        viewModelScope.launch {
            processAndAddImage(uri)
        }
    }

    @SuppressLint("RestrictedApi")
    suspend fun createExportedBitmap(width: Int, height: Int) {
        val backgroundImageUri = _uiState.value.note.imageUriList?.firstOrNull()
        val strokes = _uiState.value.strokes

        val backgroundBitmap = if (backgroundImageUri != null) {
            val request = ImageRequest.Builder(context)
                .data(backgroundImageUri.toUri())
                .allowHardware(false)
                .build()
            imageLoader.execute(request).image?.toBitmap()
        } else {
            null
        }

        val exportBitmap = createBitmap(width, height)
        val canvas = Canvas(exportBitmap)

        val backgroundColor = if (currentNightMode == AppCompatDelegate.MODE_NIGHT_YES) {
            android.graphics.Color.BLACK
        } else {
            android.graphics.Color.WHITE
        }
        canvas.drawColor(backgroundColor)

        backgroundBitmap?.let { bmp ->
            val canvasWidth = width.toFloat()
            val canvasHeight = height.toFloat()
            val bmpWidth = bmp.width.toFloat()
            val bmpHeight = bmp.height.toFloat()

            val scaleX = canvasWidth / bmpWidth
            val scaleY = canvasHeight / bmpHeight

            val scale = maxOf(scaleX, scaleY)

            val dx = (canvasWidth - bmpWidth * scale) / 2f
            val dy = (canvasHeight - bmpHeight * scale) / 2f

            val matrix = android.graphics.Matrix()
            matrix.setScale(scale, scale)
            matrix.postTranslate(dx, dy)

            canvas.drawBitmap(bmp, matrix, null)
        }

        val strokeRenderer = CanvasStrokeRenderer.create(
            forcePathRendering = true,
            textureStore = textureStore
        )
        strokes.forEach { stroke ->
            strokeRenderer.draw(canvas, stroke, android.graphics.Matrix())
        }
        _exportedImageUri.value = fileHelper.saveBitmapToCache(exportBitmap)
    }

    suspend fun saveStrokes() {
        flushPendingEdits()
    }

    private fun scheduleDebouncedSave() {
        isDirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            flushPendingEdits()
        }
    }

    fun flushEdits() {
        viewModelScope.launch { flushPendingEdits() }
    }

    private suspend fun flushPendingEdits() {
        val currentJob = currentCoroutineContext()[Job]
        saveJob?.takeIf { it !== currentJob }?.cancel()
        saveJob = null
        val snapshot = currentSnapshot
        val note = buildNoteForSave(snapshot)
        noteRepository.updateNote(note)
        noteRepository.updateNoteStrokes(noteId, snapshot.strokes, note.clientBrushFamilyId)
        isDirty = false
    }

    private fun buildNoteForSave(snapshot: EditSnapshot) = _uiState.value.note.copy(
        text = DocumentSerializer.encode(snapshot.document),
        clientBrushFamilyId = snapshot.strokes.lastOrNull()?.brush?.family?.let { currentBrushFamily ->
            _customBrushes.value.find { it.brushFamily == currentBrushFamily }?.name
        },
    )

    fun onTitleChanged(newTitle: String) {
        viewModelScope.launch {
            updateNoteTitle(newTitle)
        }
    }

    suspend fun updateNoteTitle(newTitle: String) {
        _uiState.update { it.copy(note = it.note.copy(title = newTitle)) }
        scheduleDebouncedSave()
    }

    @UiThread
    fun onStrokesFinished(finishedStrokes: List<Stroke>) {
        val currentStrokes = _uiState.value.strokes
        val newStrokes = currentStrokes + finishedStrokes
        dispatchInkEdit(newStrokes)
        _inkDebugMetrics.update { it.copy(finalizedStrokeCount = newStrokes.size) }
        anchorNewStrokes(startIndex = currentStrokes.size, count = finishedStrokes.size)
        viewModelScope.launch {
            saveStrokes()
        }
    }

    fun onRawMotionEvent(event: MotionEvent) {
        val now = SystemClock.elapsedRealtime()
        val tilt = event.getAxisValue(MotionEvent.AXIS_TILT).let { axis ->
            if (axis == 0f) null else axis
        }
        val sample = InkDebugSample(
            pressure = event.pressure,
            toolType = toolTypeName(event.getToolType(0)),
            historySize = event.historySize,
            isCancel = event.actionMasked == MotionEvent.ACTION_CANCEL,
            isPalm = event.getToolType(0) == TOOL_TYPE_PALM_COMPAT,
            tiltRadians = tilt
        )
        inkDebugAggregator.ingest(now, sample, _inkDebugMetrics.value)?.let { updated ->
            _inkDebugMetrics.value = updated
        }
        val clampedPressure = event.pressure.coerceIn(0f, 1f)
        _lastPressure.value = clampedPressure
        // Keep UI updates sampled so pressure debug widgets don't recompose on every stylus point.
        if (now - lastPressureUiSampledAtMs >= 80L) {
            _lastPressureUiSampled.value = clampedPressure
            lastPressureUiSampledAtMs = now
        }
    }

    private fun toolTypeName(toolType: Int): String {
        return when (toolType) {
            MotionEvent.TOOL_TYPE_STYLUS -> "stylus"
            MotionEvent.TOOL_TYPE_ERASER -> "eraser"
            MotionEvent.TOOL_TYPE_FINGER -> "finger"
            MotionEvent.TOOL_TYPE_MOUSE -> "mouse"
            TOOL_TYPE_PALM_COMPAT -> "palm"
            else -> "unknown($toolType)"
        }
    }

    fun startErase() {
        previousPoint = null
    }

    fun endErase() {
        previousPoint = null
        scheduleDebouncedSave()
    }


    fun erase(x: Float, y: Float) {
        val strokesBeforeErase = _uiState.value.strokes
        val strokesAfterErase = eraseIntersectingStrokes(
            x, y, strokesBeforeErase
        )

        if (strokesAfterErase.size != strokesBeforeErase.size) {
            dispatchInkEdit(strokesAfterErase)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun eraseIntersectingStrokes(
        currentX: Float,
        currentY: Float,
        currentStrokes: List<Stroke>,
    ): List<Stroke> {
        val prev = previousPoint
        previousPoint = MutableVec(currentX, currentY)

        if (prev == null) return currentStrokes

        val segment = MutableSegment(prev, MutableVec(currentX, currentY))
        val parallelogram = MutableParallelogram()
            .populateFromSegmentAndPadding(segment, eraserPadding)

        val strokesToRemove = currentStrokes.filter { stroke ->
            stroke.shape.intersects(parallelogram, AffineTransform.IDENTITY)
        }

        return if (strokesToRemove.isNotEmpty()) {
            currentStrokes - strokesToRemove.toSet()
        } else {
            currentStrokes
        }
    }

    fun changeBrush(brushFamily: BrushFamily) {
        setEraserMode(false)
        isBrushSelectedInSession = true
        _selectedBrush.update { currentBrush ->
            val newBrush = currentBrush.copy(family = brushFamily)
            val colorToApply = when (newBrush.family) {
                highlighter -> newBrush.composeColor.copy(alpha = HIGHLIGHTER_ALPHA)
                heartHighlighter -> Color(0xFFFF45CA).copy(alpha = HIGHLIGHTER_ALPHA)
                poopHighlighter -> Color(0xFF783013).copy(alpha = HIGHLIGHTER_ALPHA)
                starHighlighter -> Color(0xFFFFE100).copy(alpha = HIGHLIGHTER_ALPHA)
                else -> newBrush.composeColor.copy(alpha = 1f)
            }
            newBrush.copyWithComposeColor(colorToApply)
        }
    }

    fun changeBrushAndSize(brushFamily: BrushFamily, size: Float) {
        isBrushSelectedInSession = true
        _selectedBrush.update { currentBrush ->
            val newBrush = currentBrush.copy(family = brushFamily, size = size)
            val colorToApply = when (newBrush.family) {
                highlighter -> newBrush.composeColor.copy(alpha = HIGHLIGHTER_ALPHA)
                heartHighlighter -> Color(0xFFFF45CA).copy(alpha = HIGHLIGHTER_ALPHA)
                poopHighlighter -> Color(0xFF783013).copy(alpha = HIGHLIGHTER_ALPHA)
                starHighlighter -> Color(0xFFFFE100).copy(alpha = HIGHLIGHTER_ALPHA)
                else -> newBrush.composeColor.copy(alpha = 1f)
            }
            newBrush.copyWithComposeColor(colorToApply)
        }
    }

    fun changeBrushColor(color: Color) {
        isBrushSelectedInSession = true
        _selectedBrush.update { currentBrush ->
            val colorToApply = when (currentBrush.family) {
                heartHighlighter,
                poopHighlighter,
                starHighlighter,
                highlighter,
                    -> color.copy(alpha = HIGHLIGHTER_ALPHA)

                else -> color.copy(alpha = 1f)
            }
            currentBrush.copyWithComposeColor(color = colorToApply)
        }
    }

    fun changeBrushSize(size: Float) {
        isBrushSelectedInSession = true
        _selectedBrush.update { currentBrush ->
            currentBrush.copy(size = size)
        }
    }

    fun setEraserMode(enabled: Boolean) {
        _isEraserMode.update { enabled }
    }

    fun clearStrokes() {
        if (_uiState.value.strokes.isNotEmpty()) {
            dispatchInkEdit(emptyList())
            scheduleDebouncedSave()
        }
    }

    fun clearImages() {
        _uiState.update { it.copy(note = it.note.copy(imageUriList = emptyList())) }
        scheduleDebouncedSave()
    }

    fun clearScreen() {
        clearStrokes()
        clearImages()
    }

    fun handleDroppedUri(uri: Uri, permissions: android.view.DragAndDropPermissions?) {
        viewModelScope.launch {
            try {
                val localUri = fileHelper.copyUriToInternalStorage(uri)
                addImageWithLocalUri(localUri)
            } finally {
                permissions?.release()
            }
        }
    }

    fun getCurrentBrush(): Brush {
        return _selectedBrush.value
    }

    fun getCurrentBrushWithPressureCurve(): Brush {
        val base = _selectedBrush.value
        val mapped = mapPressureToScale(
            pressure = _lastPressure.value,
            curve = _pressureCurve.value
        )
        return base.copy(size = (base.size * mapped).coerceIn(1f, 128f))
    }

    suspend fun saveCurrentBrushToAutosave() {
        withContext(Dispatchers.IO) {
            try {
                val stream = java.io.ByteArrayOutputStream()
                AndroidBrushFamilySerialization.encode(
                    _selectedBrush.value.family,
                    stream,
                    textureStore
                )
                val encodedBrushFamily = stream.toByteArray()
                customBrushDao.saveCustomBrush(
                    CustomBrushEntity(
                        AUTOSAVE_KEY,
                        encodedBrushFamily
                    )
                )
                Log.d(TAG, "Auto saved brush to database successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error auto saving brush to database", e)
            }
        }
    }

    private fun loadCustomBrushes() {
        viewModelScope.launch(Dispatchers.IO) {
            val builtInBrushes = CustomBrushes.getBrushes(context, textureStore)

            val decodedCache = mutableMapOf<String, CustomBrush>()

            customBrushDao.getAllCustomBrushes().collect { dbBrushes ->
                val currentNames = dbBrushes.map { it.name }.toSet()
                decodedCache.keys.retainAll(currentNames)

                val userBrushes = dbBrushes.mapNotNull { entity ->
                    decodedCache[entity.name]?.let { return@mapNotNull it }

                    try {
                        GZIPInputStream(ByteArrayInputStream(entity.brushBytes))
                            .use { gzip ->
                                val rawProtoBytes = gzip.readBytes()
                                val proto = ink.proto.BrushFamily.parseFrom(rawProtoBytes)

                                proto.textureIdToBitmapMap.forEach { (id, byteString) ->
                                    val bitmapBytes = byteString.toByteArray()
                                    val bitmap =
                                        BitmapFactory.decodeByteArray(
                                            bitmapBytes,
                                            0,
                                            bitmapBytes.size
                                        )
                                    if (bitmap != null) {
                                        textureStore.loadTexture(id, bitmap)
                                    }
                                }
                            }

                        ByteArrayInputStream(entity.brushBytes).use { inputStream ->
                            val family =
                                AndroidBrushFamilySerialization.decode(
                                    inputStream,
                                    maxVersion = Version.DEVELOPMENT
                                ) { id, bitmap ->
                                    if (bitmap != null)
                                        textureStore.loadTexture(id, bitmap)
                                    id
                                }
                            CustomBrush(
                                name = entity.name,
                                icon = com.example.cahier.R.drawable.edit_24px,
                                brushFamily = family,
                                isRemovable = true
                            ).also { decodedCache[entity.name] = it }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading textures/brush ${entity.name}", e)
                        null
                    }
                }

                withContext(Dispatchers.Main) {
                    _customBrushes.value = builtInBrushes + userBrushes
                }
            }
        }
    }

    override fun onCleared() {
        flushEdits()
        super.onCleared()
    }

    companion object {
        private const val TAG = "DrawingCanvasViewModel"
        private const val HIGHLIGHTER_ALPHA = 0.3f
        private const val TOOL_TYPE_PALM_COMPAT = 5
        private const val SAVE_DEBOUNCE_MS = 750L
        private const val PERIODIC_AUTOSAVE_MS = 30_000L
    }

    /**
     * Legacy compatibility entrypoint.
     *
     * Standalone TableBlock is deprecated as the primary Normal Mode workflow.
     * Keep this only for compatibility/migration flows until TextContainer inline-table
     * model is fully rolled out.
     */
    fun ensureDefaultTableBlock() {
        val current = _document.value
        val page = current.pages.firstOrNull() ?: return
        if (page.blocks.any { it is TableBlock }) return
        val updated = current.copy(
            pages = listOf(
                page.copy(blocks = page.blocks + TableBlock())
            )
        )
        persistDocument(updated)
    }

    fun ensureDefaultTextContainer() {
        val current = _document.value
        val page = current.pages.firstOrNull() ?: return
        if (page.blocks.any { it is TextContainerBlock }) return
        val updated = current.copy(
            pages = listOf(
                page.copy(
                    blocks = page.blocks + TextContainerBlock(
                        content = TextContainerContent(nodes = listOf(ParagraphNode("")))
                    )
                )
            )
        )
        persistDocument(updated)
    }

    fun updateTextContainerParagraph(text: String) {
        updateFirstTextContainer { container ->
            val nodes = container.content.nodes.toMutableList()
            val firstParagraph = nodes.indexOfFirst { it is ParagraphNode }
            if (firstParagraph >= 0) {
                nodes[firstParagraph] = ParagraphNode(text)
            } else {
                nodes.add(0, ParagraphNode(text))
            }
            container.copy(content = container.content.copy(nodes = nodes))
        }
    }

    fun updateTextContainerParagraphAt(index: Int, text: String) {
        updateFirstTextContainer { container ->
            val nodes = container.content.nodes.toMutableList()
            val paragraphIndexes = nodes.mapIndexedNotNull { i, n -> if (n is ParagraphNode) i else null }
            val targetNodeIndex = paragraphIndexes.getOrNull(index)
            if (targetNodeIndex != null) {
                nodes[targetNodeIndex] = ParagraphNode(text)
            } else {
                nodes.add(ParagraphNode(text))
            }
            container.copy(content = container.content.copy(nodes = nodes))
        }
    }

    fun insertInlineTableInTextContainer(rows: Int = 3, columns: Int = 3) {
        updateFirstTextContainer { container ->
            val nodes = container.content.nodes.toMutableList()
            if (nodes.any { it is TableNode }) return@updateFirstTextContainer container
            if (nodes.isEmpty()) {
                nodes.add(ParagraphNode(""))
            }
            nodes.add(TableNode(rows = rows, columns = columns))
            nodes.add(ParagraphNode(""))
            container.copy(content = container.content.copy(nodes = nodes))
        }
    }

    fun updateInlineTableCell(row: Int, col: Int, text: String) {
        updateFirstTextContainer { container ->
            val tableIndex = container.content.nodes.indexOfFirst { it is TableNode }
            if (tableIndex < 0) return@updateFirstTextContainer container
            val table = container.content.nodes[tableIndex] as TableNode
            val updatedCells = table.cells.mapIndexed { rowIndex, rowCells ->
                rowCells.mapIndexed { colIndex, cell ->
                    if (rowIndex == row && colIndex == col) {
                        val imageUri = extractImageUri(text)
                        val latex = extractLatex(text)
                        cell.copy(
                            text = text,
                            imageUri = imageUri ?: cell.imageUri,
                            latex = latex ?: cell.latex
                        )
                    } else {
                        cell
                    }
                }
            }
            val nodes = container.content.nodes.toMutableList()
            nodes[tableIndex] = table.copy(cells = updatedCells)
            container.copy(content = container.content.copy(nodes = nodes))
        }
    }

    fun appendInlineTableRow() {
        updateFirstTextContainer { container ->
            val tableIndex = container.content.nodes.indexOfFirst { it is TableNode }
            if (tableIndex < 0) return@updateFirstTextContainer container
            val table = container.content.nodes[tableIndex] as TableNode
            val newRow = List(table.columns) { TableCell() }
            val nodes = container.content.nodes.toMutableList()
            nodes[tableIndex] = table.copy(
                rows = table.rows + 1,
                cells = table.cells + listOf(newRow)
            )
            container.copy(content = container.content.copy(nodes = nodes))
        }
    }

    fun toggleInlineTableCellBold(row: Int, col: Int) {
        updateInlineTableCellStyle(row, col) { it.copy(bold = !it.bold) }
    }

    fun toggleInlineTableCellItalic(row: Int, col: Int) {
        updateInlineTableCellStyle(row, col) { it.copy(italic = !it.italic) }
    }

    fun toggleInlineTableCellUnderline(row: Int, col: Int) {
        updateInlineTableCellStyle(row, col) { it.copy(underline = !it.underline) }
    }

    fun moveTextContainerBy(dx: Float, dy: Float) {
        updateFirstTextContainer { container ->
            container.copy(
                x = container.x + dx,
                y = container.y + dy
            )
        }
        recomputeStrokeTranslations()
    }

    fun placePrimaryTextContainerAt(x: Float, y: Float) {
        val current = _document.value
        val page = current.pages.firstOrNull() ?: return
        val idx = page.blocks.indexOfFirst { it is TextContainerBlock }
        if (idx >= 0) {
            val blocks = page.blocks.toMutableList()
            val currentContainer = blocks[idx] as TextContainerBlock
            blocks[idx] = currentContainer.copy(x = x, y = y)
            persistDocument(current.copy(pages = listOf(page.copy(blocks = blocks))))
            recomputeStrokeTranslations()
            return
        }
        val container = TextContainerBlock(
            x = x,
            y = y,
            content = TextContainerContent(nodes = listOf(ParagraphNode("")))
        )
        persistDocument(current.copy(pages = listOf(page.copy(blocks = page.blocks + container))))
        recomputeStrokeTranslations()
    }

    fun updateTableCell(
        row: Int,
        col: Int,
        text: String,
    ) {
        updateFirstTableBlock { table ->
            val updatedCells = table.cells.mapIndexed { rowIndex, rowCells ->
                rowCells.mapIndexed { colIndex, cell ->
                    if (rowIndex == row && colIndex == col) {
                        val imageUri = extractImageUri(text)
                        val latex = extractLatex(text)
                        cell.copy(
                            text = text,
                            imageUri = imageUri ?: cell.imageUri,
                            latex = latex ?: cell.latex
                        )
                    } else {
                        cell
                    }
                }
            }
            table.copy(cells = updatedCells)
        }
    }

    fun attachImageAssetToCell(row: Int, col: Int, assetPath: String) {
        updateFirstTableBlock { table ->
            val updatedCells = table.cells.mapIndexed { rowIndex, rowCells ->
                rowCells.mapIndexed { colIndex, cell ->
                    if (rowIndex == row && colIndex == col) cell.copy(imageUri = assetPath) else cell
                }
            }
            table.copy(cells = updatedCells)
        }
    }

    fun addImageBlock(assetPath: String) {
        val current = _document.value
        val page = current.pages.firstOrNull() ?: return
        val imageBlock = ImageBlock(assetPath = assetPath)
        persistDocument(current.copy(pages = listOf(page.copy(blocks = page.blocks + imageBlock))))
    }

    fun moveImageBlockBy(index: Int, dx: Float, dy: Float) {
        val current = _document.value
        val page = current.pages.firstOrNull() ?: return
        val imageIndexes = page.blocks.mapIndexedNotNull { i, b -> if (b is ImageBlock) i else null }
        val blockIndex = imageIndexes.getOrNull(index) ?: return
        val blocks = page.blocks.toMutableList()
        val image = blocks[blockIndex] as ImageBlock
        blocks[blockIndex] = image.copy(x = image.x + dx, y = image.y + dy)
        persistDocument(current.copy(pages = listOf(page.copy(blocks = blocks))))
    }

    fun resizeImageBlockBy(index: Int, dw: Float, dh: Float) {
        val current = _document.value
        val page = current.pages.firstOrNull() ?: return
        val imageIndexes = page.blocks.mapIndexedNotNull { i, b -> if (b is ImageBlock) i else null }
        val blockIndex = imageIndexes.getOrNull(index) ?: return
        val blocks = page.blocks.toMutableList()
        val image = blocks[blockIndex] as ImageBlock
        blocks[blockIndex] = image.copy(
            width = (image.width + dw).coerceAtLeast(96f),
            height = (image.height + dh).coerceAtLeast(96f)
        )
        persistDocument(current.copy(pages = listOf(page.copy(blocks = blocks))))
    }

    fun attachImageAssetToInlineCell(row: Int, col: Int, assetPath: String) {
        updateFirstTextContainer { container ->
            val tableIndex = container.content.nodes.indexOfFirst { it is TableNode }
            if (tableIndex < 0) return@updateFirstTextContainer container
            val table = container.content.nodes[tableIndex] as TableNode
            val updatedCells = table.cells.mapIndexed { rowIndex, rowCells ->
                rowCells.mapIndexed { colIndex, cell ->
                    if (rowIndex == row && colIndex == col) cell.copy(imageUri = assetPath) else cell
                }
            }
            val nodes = container.content.nodes.toMutableList()
            nodes[tableIndex] = table.copy(cells = updatedCells)
            container.copy(content = container.content.copy(nodes = nodes))
        }
    }

    fun addFormulaBlock(source: String) {
        val trimmed = source.trim()
        if (trimmed.isBlank()) return
        val current = _document.value
        val page = current.pages.firstOrNull() ?: return
        val formulaBlock = FormulaBlock(source = trimmed, rendered = "f(x): $trimmed")
        persistDocument(current.copy(pages = listOf(page.copy(blocks = page.blocks + formulaBlock))))
    }

    fun importImageFromUriToAssets(uriString: String): String? {
        return runCatching {
            val uri = uriString.toUri()
            val input: InputStream = context.contentResolver.openInputStream(uri) ?: return null
            input.use { stream ->
                val dir = File(context.filesDir, "note_assets").apply { mkdirs() }
                val name = "img_${System.currentTimeMillis()}.bin"
                val outFile = File(dir, name)
                FileOutputStream(outFile).use { out -> stream.copyTo(out) }
                outFile.absolutePath
            }
        }.getOrNull()
    }

    fun pasteImageBlockFromClipboard(): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val item = clipboard.primaryClip?.getItemAt(0) ?: return false
        val uri = item.uri ?: return false
        val asset = importImageFromUriToAssets(uri.toString()) ?: return false
        addImageBlock(asset)
        return true
    }

    fun pasteImageFromClipboard(
        target: ImagePasteTarget,
        inlineCell: Pair<Int, Int>? = null
    ): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val item = clipboard.primaryClip?.getItemAt(0) ?: return false
        val uri = item.uri ?: return false
        val asset = importImageFromUriToAssets(uri.toString()) ?: return false
        when {
            target == ImagePasteTarget.INLINE_TABLE_CELL && inlineCell != null -> {
                attachImageAssetToInlineCell(inlineCell.first, inlineCell.second, asset)
            }
            target == ImagePasteTarget.INLINE_TABLE_CELL && inlineCell == null -> {
                _userMessage.value = "Image paste into table cell is not supported until a cell is focused. Inserted as canvas image."
                addImageBlock(asset)
            }
            else -> addImageBlock(asset)
        }
        return true
    }

    fun toggleTableCellBold(row: Int, col: Int) {
        updateFirstTableBlock { table ->
            val updatedCells = table.cells.mapIndexed { rowIndex, rowCells ->
                rowCells.mapIndexed { colIndex, cell ->
                    if (rowIndex == row && colIndex == col) cell.copy(bold = !cell.bold) else cell
                }
            }
            table.copy(cells = updatedCells)
        }
    }

    fun appendTableRow() {
        updateFirstTableBlock { table ->
            val newRow = List(table.columns) { TableCell() }
            table.copy(
                rows = table.rows + 1,
                cells = table.cells + listOf(newRow)
            )
        }
    }

    fun toggleTableCellItalic(row: Int, col: Int) {
        updateFirstTableBlock { table ->
            val updatedCells = table.cells.mapIndexed { rowIndex, rowCells ->
                rowCells.mapIndexed { colIndex, cell ->
                    if (rowIndex == row && colIndex == col) cell.copy(italic = !cell.italic) else cell
                }
            }
            table.copy(cells = updatedCells)
        }
    }

    fun toggleTableCellUnderline(row: Int, col: Int) {
        updateFirstTableBlock { table ->
            val updatedCells = table.cells.mapIndexed { rowIndex, rowCells ->
                rowCells.mapIndexed { colIndex, cell ->
                    if (rowIndex == row && colIndex == col) cell.copy(underline = !cell.underline) else cell
                }
            }
            table.copy(cells = updatedCells)
        }
    }

    private fun updateFirstTableBlock(transform: (TableBlock) -> TableBlock) {
        val current = _document.value
        val page = current.pages.firstOrNull() ?: return
        val idx = page.blocks.indexOfFirst { it is TableBlock }
        if (idx < 0) return
        val target = page.blocks[idx] as TableBlock
        val updatedBlocks = page.blocks.toMutableList()
        updatedBlocks[idx] = transform(target)
        val updated = current.copy(pages = listOf(page.copy(blocks = updatedBlocks)))
        persistDocument(updated)
    }

    private fun updateFirstTextContainer(transform: (TextContainerBlock) -> TextContainerBlock) {
        val current = _document.value
        val page = current.pages.firstOrNull() ?: return
        val idx = page.blocks.indexOfFirst { it is TextContainerBlock }
        if (idx < 0) return
        val target = page.blocks[idx] as TextContainerBlock
        val updatedBlocks = page.blocks.toMutableList()
        updatedBlocks[idx] = transform(target)
        val updated = current.copy(pages = listOf(page.copy(blocks = updatedBlocks)))
        persistDocument(updated)
    }

    private fun updateInlineTableCellStyle(
        row: Int,
        col: Int,
        transform: (TableCell) -> TableCell
    ) {
        updateFirstTextContainer { container ->
            val tableIndex = container.content.nodes.indexOfFirst { it is TableNode }
            if (tableIndex < 0) return@updateFirstTextContainer container
            val table = container.content.nodes[tableIndex] as TableNode
            val updatedCells = table.cells.mapIndexed { rowIndex, rowCells ->
                rowCells.mapIndexed { colIndex, cell ->
                    if (rowIndex == row && colIndex == col) transform(cell) else cell
                }
            }
            val nodes = container.content.nodes.toMutableList()
            nodes[tableIndex] = table.copy(cells = updatedCells)
            container.copy(content = container.content.copy(nodes = nodes))
        }
    }

    private fun persistDocument(document: TicDocument) {
        dispatchDocumentEdit(document)
    }

    fun moveTableBlockBy(dx: Float, dy: Float) {
        updateFirstTableBlock { table ->
            table.copy(
                x = table.x + dx,
                y = table.y + dy
            )
        }
        recomputeStrokeTranslations()
    }

    fun resizeTableBlockBy(dw: Float, dh: Float) {
        updateFirstTableBlock { table ->
            table.copy(
                width = (table.width + dw).coerceAtLeast(320f),
                height = (table.height + dh).coerceAtLeast(160f)
            )
        }
    }

    private fun extractImageUri(text: String): String? {
        val regex = Regex("""!\[[^\]]*]\(([^)]+)\)""")
        return regex.find(text)?.groupValues?.getOrNull(1)
    }

    private fun extractLatex(text: String): String? {
        val block = Regex("""\$\$([\s\S]+?)\$\$""").find(text)?.groupValues?.getOrNull(1)
        if (!block.isNullOrBlank()) return block
        val inline = Regex("""\$([^$\n]+)\$""").find(text)?.groupValues?.getOrNull(1)
        return inline
    }

    fun setFocusedBlockId(blockId: String?) {
        focusedBlockId = blockId
    }

    private fun anchorNewStrokes(startIndex: Int, count: Int) {
        if (count <= 0) return
        val current = _document.value
        val page = current.pages.firstOrNull() ?: return
        val ids = page.strokeIds.drop(startIndex).take(count)
        if (ids.isEmpty()) return
        val target = findAnchorTargetBlock(page.blocks, startIndex, count) ?: return
        val anchor = StrokeAnchor(
            blockId = target.id,
            strokeIds = ids,
            startStrokeIndex = null,
            endStrokeIndexInclusive = null,
            anchorOriginX = target.x,
            anchorOriginY = target.y
        )
        val updated = current.copy(
            pages = listOf(page.copy(strokeAnchors = page.strokeAnchors + anchor))
        )
        persistDocument(updated)
        recomputeStrokeTranslations()
    }

    private fun findAnchorTargetBlock(blocks: List<Block>, startIndex: Int, count: Int): Block? {
        val focused = focusedBlockId?.let { id -> blocks.firstOrNull { it.id == id } }
        if (focused != null) return focused

        val finishedStrokes = _uiState.value.strokes.drop(startIndex).take(count)
        return blocks.firstOrNull { block ->
            finishedStrokes.any { stroke -> strokeIntersectsBlock(stroke, block) }
        }
    }

    private fun strokeIntersectsBlock(stroke: Stroke, block: Block): Boolean {
        val top = MutableSegment(
            MutableVec(block.x, block.y),
            MutableVec(block.x + block.width, block.y)
        )
        val blockBounds = MutableParallelogram().populateFromSegmentAndPadding(
            top,
            maxOf(block.height, 1f)
        )
        return stroke.shape.intersects(blockBounds, AffineTransform.IDENTITY)
    }

    private fun recomputeStrokeTranslations() {
        val page = _document.value.pages.firstOrNull() ?: return
        val blockById = page.blocks.associateBy { it.id }
        val strokeIndexById = page.strokeIds.withIndex().associate { (idx, id) -> id to idx }
        val translations = mutableMapOf<Int, Pair<Float, Float>>()
        page.strokeAnchors.forEach { anchor ->
            val block = blockById[anchor.blockId] ?: return@forEach
            val dx = block.x - anchor.anchorOriginX
            val dy = block.y - anchor.anchorOriginY
            if (anchor.strokeIds.isNotEmpty()) {
                anchor.strokeIds.forEach { strokeId ->
                    val index = strokeIndexById[strokeId] ?: return@forEach
                    translations[index] = dx to dy
                }
            } else if (
                anchor.startStrokeIndex != null &&
                anchor.endStrokeIndexInclusive != null &&
                anchor.endStrokeIndexInclusive >= anchor.startStrokeIndex
            ) {
                for (i in anchor.startStrokeIndex..anchor.endStrokeIndexInclusive) {
                    translations[i] = dx to dy
                }
            }
        }
        page.strokeTransforms.forEach { transform ->
            val index = strokeIndexById[transform.strokeId] ?: return@forEach
            val base = translations[index] ?: (0f to 0f)
            translations[index] = (base.first + transform.translateX) to (base.second + transform.translateY)
        }
        _strokeTranslations.value = translations
    }

    private fun refreshSelectionState() {
        _hasSelection.value = _selectedStrokeIndices.value.isNotEmpty() || _selectedTextContainer.value
    }

    fun setStylusWritesByDefault(enabled: Boolean) {
        _stylusWritesByDefault.value = enabled
        val current = _document.value
        persistDocument(current.copy(settings = current.settings.copy(stylusWritesByDefault = enabled)))
    }

    fun setFingerPansByDefault(enabled: Boolean) {
        _fingerPansByDefault.value = enabled
        val current = _document.value
        persistDocument(current.copy(settings = current.settings.copy(fingerPansByDefault = enabled)))
    }

    fun setPressureCurve(curve: Float) {
        val clamped = curve.coerceIn(0.5f, 2.0f)
        _pressureCurve.value = clamped
        val current = _document.value
        persistDocument(current.copy(settings = current.settings.copy(pressureCurve = clamped)))
    }

    fun setSelectionMode(enabled: Boolean) {
        _selectionModeEnabled.value = enabled
        if (enabled) {
            setEraserMode(false)
        } else {
            clearSelection()
        }
    }

    fun clearSelection() {
        _selectedStrokeIndices.value = emptySet()
        _selectedTextContainer.value = false
        refreshSelectionState()
    }

    fun toggleTextContainerSelection() {
        _selectedTextContainer.value = !_selectedTextContainer.value
        refreshSelectionState()
    }

    fun selectStrokesInScreenRect(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        canvasTransform: CanvasTransform,
        replace: Boolean = false,
    ) {
        val left = minOf(startX, endX)
        val right = maxOf(startX, endX)
        val top = minOf(startY, endY)
        val bottom = maxOf(startY, endY)
        if ((right - left) < 3f || (bottom - top) < 3f) return

        val docLeft = CanvasTransformMapper.screenToCanvasX(left, canvasTransform)
        val docRight = CanvasTransformMapper.screenToCanvasX(right, canvasTransform)
        val docTop = CanvasTransformMapper.screenToCanvasY(top, canvasTransform)
        val docBottom = CanvasTransformMapper.screenToCanvasY(bottom, canvasTransform)

        val segment = MutableSegment(
            MutableVec(docLeft, docTop),
            MutableVec(docRight, docTop)
        )
        val lasso = MutableParallelogram().populateFromSegmentAndPadding(
            segment,
            maxOf(docBottom - docTop, 1f)
        )

        val hit = _uiState.value.strokes.mapIndexedNotNull { index, stroke ->
            if (stroke.shape.intersects(lasso, AffineTransform.IDENTITY)) index else null
        }.toSet()

        _selectedStrokeIndices.value = if (replace) hit else (_selectedStrokeIndices.value + hit)
        refreshSelectionState()
    }

    fun moveSelectionBy(dx: Float, dy: Float) {
        if (_selectedStrokeIndices.value.isEmpty() && !_selectedTextContainer.value) return
        if (_selectedTextContainer.value) {
            moveTextContainerBy(dx, dy)
        }
        if (_selectedStrokeIndices.value.isNotEmpty()) {
            persistStrokeTranslations(_selectedStrokeIndices.value, dx, dy)
        }
    }

    private fun persistStrokeTranslations(indices: Set<Int>, dx: Float, dy: Float) {
        val current = _document.value
        val page = current.pages.firstOrNull() ?: return
        val selectedIds = indices.mapNotNull { page.strokeIds.getOrNull(it) }.toSet()
        if (selectedIds.isEmpty()) return
        val transformsById = page.strokeTransforms.associateBy { it.strokeId }.toMutableMap()
        selectedIds.forEach { strokeId ->
            val previous = transformsById[strokeId] ?: StrokeTransform(strokeId = strokeId)
            transformsById[strokeId] = previous.copy(
                translateX = previous.translateX + dx,
                translateY = previous.translateY + dy
            )
        }
        val updatedTransforms = page.strokeIds.mapNotNull { transformsById[it] }
        persistDocument(current.copy(pages = listOf(page.copy(strokeTransforms = updatedTransforms))))
        recomputeStrokeTranslations()
    }

    fun exportAllFormats() {
        viewModelScope.launch(Dispatchers.IO) {
            val note = _uiState.value.note
            val doc = _document.value
            val now = System.currentTimeMillis()
            val baseDir = File(context.filesDir, "exports/$now").apply { mkdirs() }
            val assetsDir = File(baseDir, "assets").apply { mkdirs() }
            val safeTitle = note.title.ifBlank { "note-$noteId" }.replace("""[^\w\-]+""".toRegex(), "_")
            val imagePath = note.imageUriList?.firstOrNull()
            imagePath?.let {
                runCatching {
                    File(it).takeIf { file -> file.exists() }?.copyTo(
                        target = File(assetsDir, "background.png"),
                        overwrite = true
                    )
                }
            }

            val strokeCount = _uiState.value.strokes.size
            val markdown = ExportComposer.toMarkdown(
                title = safeTitle,
                document = doc,
                strokeCount = strokeCount
            )
            File(baseDir, "$safeTitle.md").writeText(markdown)

            val html = ExportComposer.toHtml(
                title = safeTitle,
                document = doc,
                strokeCount = strokeCount
            )
            File(baseDir, "$safeTitle.html").writeText(html)

            val pdfFile = File(baseDir, "$safeTitle.pdf")
            exportPdf(pdfFile, safeTitle, doc)

            val archiveFile = File(baseDir, "$safeTitle.ticnote")
            val imageAssets = doc.pages.firstOrNull()?.blocks?.filterIsInstance<ImageBlock>()
                ?.mapNotNull { img ->
                    File(img.assetPath).takeIf { it.exists() }?.also { file ->
                        File(assetsDir, file.name).writeBytes(file.readBytes())
                    }
                } ?: emptyList()
            TicNoteArchiveWriter.writeArchive(
                archiveFile = archiveFile,
                documentJson = DocumentSerializer.encode(doc),
                inkStrokesJson = note.strokesData ?: "[]",
                markdownFile = File(baseDir, "$safeTitle.md"),
                htmlFile = File(baseDir, "$safeTitle.html"),
                title = safeTitle,
                finalizedStrokeCount = strokeCount,
                backgroundFile = File(assetsDir, "background.png"),
                imageAssetFiles = imageAssets
            )
            _lastExportDirectory.value = baseDir.absolutePath
            _lastExportResult.value = ExportResult(
                directory = baseDir.absolutePath,
                generatedFiles = listOf(
                    File(baseDir, "$safeTitle.pdf").absolutePath,
                    File(baseDir, "$safeTitle.html").absolutePath,
                    File(baseDir, "$safeTitle.md").absolutePath,
                    archiveFile.absolutePath
                )
            )
        }
    }

    fun clearExportResult() {
        _lastExportResult.value = null
    }

    fun consumeUserMessage() {
        _userMessage.value = null
    }

    fun generateStressDocument(
        tableCount: Int = 10,
        rowsPerTable: Int = 20,
        columnsPerTable: Int = 6,
        imageCount: Int = 50,
    ) {
        val blocks = StressDocumentFactory.createBlocks(
            tableCount = tableCount,
            rowsPerTable = rowsPerTable,
            columnsPerTable = columnsPerTable,
            imageCount = imageCount
        )
        val page = _document.value.pages.firstOrNull() ?: return
        persistDocument(_document.value.copy(pages = listOf(page.copy(blocks = blocks))))
    }

    private fun exportPdf(pdfFile: File, title: String, document: TicDocument) {
        val pdf = PdfDocument()
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(1080, 1920, 1).create())
        val canvas = page.canvas
        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 28f
        }
        val subPaint = Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 20f
        }
        val boxPaint = Paint().apply {
            color = android.graphics.Color.argb(255, 200, 200, 200)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawText(title, 48f, 64f, paint)
        val contentTop = 96f
        val blocks = document.pages.firstOrNull()?.blocks.orEmpty()
        blocks.forEach { block ->
            val left = 48f + block.x
            val top = contentTop + block.y
            val right = left + block.width
            val bottom = top + block.height
            canvas.drawRect(left, top, right, bottom, boxPaint)
            when (block) {
                is TableBlock -> {
                    canvas.drawText("Table ${block.rows}x${block.columns}", left + 8f, top + 24f, subPaint)
                    val preview = block.cells.firstOrNull()?.joinToString(" | ") { it.text.ifBlank { " " } }.orEmpty()
                    canvas.drawText(preview.take(60), left + 8f, top + 48f, subPaint)
                }
                is ImageBlock -> {
                    canvas.drawText("Image: ${File(block.assetPath).name}", left + 8f, top + 24f, subPaint)
                }
                is FormulaBlock -> {
                    canvas.drawText(block.rendered, left + 8f, top + 24f, subPaint)
                }
                else -> {
                    canvas.drawText(block::class.simpleName.orEmpty(), left + 8f, top + 24f, subPaint)
                }
            }
        }
        canvas.drawText("Finalized strokes: ${_uiState.value.strokes.size}", 48f, 1860f, subPaint)
        pdf.finishPage(page)
        pdf.writeTo(FileOutputStream(pdfFile))
        pdf.close()
    }

    fun mapPressureToScale(pressure: Float, curve: Float): Float {
        return PressureCurveMapper.mapPressureToScale(pressure, curve)
    }
}
