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
import com.example.cahier.core.document.DocumentSettings
import com.example.cahier.core.document.ImageBlock
import com.example.cahier.core.document.StrokeAnchor
import com.example.cahier.core.document.TableBlock
import com.example.cahier.core.document.TableCell
import com.example.cahier.core.document.TicDocument
import com.example.cahier.core.navigation.DrawingCanvasDestination
import com.example.cahier.core.ui.CahierTextureBitmapStore
import com.example.cahier.core.ui.CahierUiState
import com.example.cahier.core.utils.FileHelper
import com.example.cahier.developer.brushdesigner.data.AUTOSAVE_KEY
import com.example.cahier.developer.brushdesigner.data.CustomBrushDao
import com.example.cahier.developer.brushdesigner.data.CustomBrushEntity
import com.example.cahier.features.drawing.CustomBrushes
import com.example.cahier.features.drawing.InkDebugAggregator
import com.example.cahier.features.drawing.InkDebugMetrics
import com.example.cahier.features.drawing.InkDebugSample
import com.example.cahier.features.drawing.PressureCurveMapper
import com.example.cahier.features.drawing.StrokeIdMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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

    private val history = mutableListOf<List<Stroke>>()
    private var historyIndex = -1
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
    private val _inkDebugMetrics = MutableStateFlow(InkDebugMetrics())
    val inkDebugMetrics: StateFlow<InkDebugMetrics> = _inkDebugMetrics.asStateFlow()
    private val inkDebugAggregator = InkDebugAggregator()

    private var isBrushSelectedInSession = false
    private val _lastExportDirectory = MutableStateFlow<String?>(null)
    val lastExportDirectory: StateFlow<String?> = _lastExportDirectory.asStateFlow()
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

    init {
        viewModelScope.launch {
            noteRepository.getNoteStream(noteId)
                .filterNotNull()
                .collect { note ->
                    val parsedDocument = DocumentSerializer.decodeOrNull(note.text) ?: TicDocument()
                    _document.value = parsedDocument
                    _pressureCurve.value = parsedDocument.settings.pressureCurve
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
                    if (history.isEmpty()) {
                        history.clear()
                        history.add(initialStrokes)
                        historyIndex = 0
                        updateUndoRedoState()
                    } else {
                        if (historyIndex >= 0 && historyIndex < history.size) {
                            _uiState.update { it.copy(strokes = history[historyIndex]) }
                        }
                        updateUndoRedoState()
                    }
                }
        }

        loadCustomBrushes()
    }

    fun addImageWithLocalUri(localUri: Uri?) {
        if (localUri == null) return
        val newImageUri = localUri.toString()
        val updatedNote = _uiState.value.note.copy(imageUriList = listOf(newImageUri))
        viewModelScope.launch {
            noteRepository.updateNote(updatedNote)
        }
    }

    private fun updateStrokes(newStrokes: List<Stroke>) {
        val oldStrokes = _uiState.value.strokes
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }
        history.add(newStrokes)
        historyIndex++

        syncStrokeIds(oldStrokes = oldStrokes, newStrokes = newStrokes)
        _uiState.update { it.copy(strokes = newStrokes) }
        updateUndoRedoState()
        recomputeStrokeTranslations()
    }

    private fun syncStrokeIds(oldStrokes: List<Stroke>, newStrokes: List<Stroke>) {
        val current = _document.value
        val page = current.pages.firstOrNull() ?: return
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
        persistDocument(
            current.copy(
                pages = listOf(
                    page.copy(
                        strokeIds = remapped,
                        strokeAnchors = updatedAnchors
                    )
                )
            )
        )
    }

    private fun updateUndoRedoState() {
        _canUndo.value = historyIndex > 0
        _canRedo.value = historyIndex < history.size - 1
    }

    fun undo() {
        if (canUndo.value) {
            historyIndex--
            _uiState.update { it.copy(strokes = history[historyIndex]) }
            updateUndoRedoState()
            viewModelScope.launch { saveStrokes() }
        }
    }

    fun redo() {
        if (canRedo.value) {
            historyIndex++
            _uiState.update { it.copy(strokes = history[historyIndex]) }
            updateUndoRedoState()
            viewModelScope.launch { saveStrokes() }
        }
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
        if (historyIndex >= 0 && historyIndex < history.size) {
            val strokesToSave = history[historyIndex]
            val currentBrushFamily = strokesToSave.lastOrNull()?.brush?.family
            val clientBrushFamilyId = _customBrushes.value
                .find { it.brushFamily == currentBrushFamily }?.name
            noteRepository.updateNoteStrokes(noteId, strokesToSave, clientBrushFamilyId)
        } else if (history.isEmpty()) {
            noteRepository.updateNoteStrokes(noteId, emptyList(), null)
        }
    }

    fun onTitleChanged(newTitle: String) {
        viewModelScope.launch {
            updateNoteTitle(newTitle)
        }
    }

    suspend fun updateNoteTitle(newTitle: String) {
        val updatedNote = _uiState.value.note.copy(title = newTitle)
        noteRepository.updateNote(updatedNote)
    }

    @UiThread
    fun onStrokesFinished(finishedStrokes: List<Stroke>) {
        val currentStrokes = history.getOrElse(historyIndex) { emptyList() }
        val startIndex = currentStrokes.size
        val newStrokes = currentStrokes + finishedStrokes
        updateStrokes(newStrokes)
        _inkDebugMetrics.update { it.copy(finalizedStrokeCount = newStrokes.size) }
        anchorNewStrokes(startIndex, finishedStrokes.size)
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
        _lastPressure.value = event.pressure.coerceIn(0f, 1f)
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
        viewModelScope.launch { saveStrokes() }
    }


    fun erase(x: Float, y: Float) {
        val strokesBeforeErase = history.getOrElse(historyIndex) { emptyList() }
        val strokesAfterErase = eraseIntersectingStrokes(
            x, y, strokesBeforeErase
        )

        if (strokesAfterErase.size != strokesBeforeErase.size) {
            updateStrokes(strokesAfterErase)
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
            updateStrokes(emptyList())
            viewModelScope.launch { saveStrokes() }
        }
    }

    fun clearImages() {
        val updatedNote = _uiState.value.note.copy(imageUriList = emptyList())
        viewModelScope.launch {
            noteRepository.updateNote(updatedNote)
        }
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
        super.onCleared()
        viewModelScope.launch {
            saveStrokes()
        }
    }

    companion object {
        private const val TAG = "DrawingCanvasViewModel"
        private const val HIGHLIGHTER_ALPHA = 0.3f
        private const val TOOL_TYPE_PALM_COMPAT = 5
    }

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

    private fun persistDocument(document: TicDocument) {
        _document.value = document
        viewModelScope.launch {
            val note = _uiState.value.note
            noteRepository.updateNote(note.copy(text = DocumentSerializer.encode(document)))
        }
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

    private fun anchorNewStrokes(startIndex: Int, count: Int) {
        if (count <= 0) return
        val current = _document.value
        val page = current.pages.firstOrNull() ?: return
        val table = page.blocks.filterIsInstance<TableBlock>().firstOrNull() ?: return
        val ids = page.strokeIds.drop(startIndex).take(count)
        val anchor = StrokeAnchor(
            blockId = table.id,
            strokeIds = ids,
            startStrokeIndex = startIndex,
            endStrokeIndexInclusive = startIndex + count - 1,
            anchorOriginX = table.x,
            anchorOriginY = table.y
        )
        val updated = current.copy(
            pages = listOf(page.copy(strokeAnchors = page.strokeAnchors + anchor))
        )
        persistDocument(updated)
        recomputeStrokeTranslations()
    }

    private fun recomputeStrokeTranslations() {
        val page = _document.value.pages.firstOrNull() ?: return
        val tableById = page.blocks.filterIsInstance<TableBlock>().associateBy { it.id }
        val strokeIndexById = page.strokeIds.withIndex().associate { (idx, id) -> id to idx }
        val translations = mutableMapOf<Int, Pair<Float, Float>>()
        page.strokeAnchors.forEach { anchor ->
            val table = tableById[anchor.blockId] ?: return@forEach
            val dx = table.x - anchor.anchorOriginX
            val dy = table.y - anchor.anchorOriginY
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
        _strokeTranslations.value = translations
    }

    fun setStylusWritesByDefault(enabled: Boolean) {
        _stylusWritesByDefault.value = enabled
    }

    fun setFingerPansByDefault(enabled: Boolean) {
        _fingerPansByDefault.value = enabled
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
        }
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

            val markdown = buildString {
                append("# ")
                append(safeTitle)
                append("\n\n")
                append("## Tables\n\n")
            doc.pages.firstOrNull()?.blocks?.filterIsInstance<TableBlock>()?.forEach { table ->
                table.cells.forEach { row ->
                    append("| ")
                        append(row.joinToString(" | ") { it.text.ifBlank { " " } })
                        append(" |\n")
                }
                append("\n")
            }
            doc.pages.firstOrNull()?.blocks?.filterIsInstance<ImageBlock>()?.forEachIndexed { idx, img ->
                append("![image-$idx](assets/")
                append(File(img.assetPath).name)
                append(")\n")
            }
            append("## Ink\n\n")
                append("Finalized stroke count: ${_uiState.value.strokes.size}\n")
            }
            File(baseDir, "$safeTitle.md").writeText(markdown)

            val html = """
                <html><body>
                <h1>$safeTitle</h1>
                <h2>Tables</h2>
                ${doc.pages.firstOrNull()?.blocks?.filterIsInstance<TableBlock>()?.joinToString("\n") { table ->
                    val rows = table.cells.joinToString("\n") { row ->
                        "<tr>${row.joinToString("") { "<td>${it.text}</td>" }}</tr>"
                    }
                    "<table border=\"1\" cellspacing=\"0\" cellpadding=\"4\">$rows</table>"
                } ?: ""}
                ${doc.pages.firstOrNull()?.blocks?.filterIsInstance<ImageBlock>()?.joinToString("\n") { img ->
                    "<img src=\"assets/${File(img.assetPath).name}\" width=\"${img.width}\" height=\"${img.height}\" />"
                } ?: ""}
                <h2>Ink</h2>
                <p>Finalized stroke count: ${_uiState.value.strokes.size}</p>
                </body></html>
            """.trimIndent()
            File(baseDir, "$safeTitle.html").writeText(html)

            val pdfFile = File(baseDir, "$safeTitle.pdf")
            exportPdf(pdfFile, safeTitle, doc)

            val archiveFile = File(baseDir, "$safeTitle.ticnote")
            ZipOutputStream(FileOutputStream(archiveFile)).use { zip ->
                zip.putNextEntry(ZipEntry("document.json"))
                zip.write(DocumentSerializer.encode(doc).toByteArray())
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("strokes-count.txt"))
                zip.write(_uiState.value.strokes.size.toString().toByteArray())
                zip.closeEntry()

                File(baseDir, "$safeTitle.md").takeIf { it.exists() }?.let { file ->
                    zip.putNextEntry(ZipEntry(file.name))
                    zip.write(file.readBytes())
                    zip.closeEntry()
                }
                File(baseDir, "$safeTitle.html").takeIf { it.exists() }?.let { file ->
                    zip.putNextEntry(ZipEntry(file.name))
                    zip.write(file.readBytes())
                    zip.closeEntry()
                }
                File(assetsDir, "background.png").takeIf { it.exists() }?.let { file ->
                    zip.putNextEntry(ZipEntry("assets/background.png"))
                    zip.write(file.readBytes())
                    zip.closeEntry()
                }
                doc.pages.firstOrNull()?.blocks?.filterIsInstance<ImageBlock>()?.forEach { img ->
                    File(img.assetPath).takeIf { it.exists() }?.let { file ->
                        val assetName = file.name
                        File(assetsDir, assetName).writeBytes(file.readBytes())
                        zip.putNextEntry(ZipEntry("assets/$assetName"))
                        zip.write(file.readBytes())
                        zip.closeEntry()
                    }
                }
            }
            _lastExportDirectory.value = baseDir.absolutePath
        }
    }

    fun generateStressDocument(
        tableCount: Int = 24,
        rowsPerTable: Int = 40,
        columnsPerTable: Int = 6,
    ) {
        val tables = (0 until tableCount).map { i ->
            TableBlock(
                x = 64f + (i % 3) * 720f,
                y = 64f + (i / 3) * 540f,
                width = 680f,
                height = 480f,
                rows = rowsPerTable,
                columns = columnsPerTable,
                cells = List(rowsPerTable) { r ->
                    List(columnsPerTable) { c ->
                        TableCell(text = "R${r + 1}C${c + 1}")
                    }
                }
            )
        }
        val page = _document.value.pages.firstOrNull() ?: return
        persistDocument(_document.value.copy(pages = listOf(page.copy(blocks = tables))))
    }

    private fun exportPdf(pdfFile: File, title: String, document: TicDocument) {
        val pdf = PdfDocument()
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(1080, 1920, 1).create())
        val canvas = page.canvas
        val paint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 28f
        }
        canvas.drawText(title, 64f, 80f, paint)
        canvas.drawText("Tables:", 64f, 140f, paint)
        var y = 190f
        document.pages.firstOrNull()?.blocks?.filterIsInstance<TableBlock>()?.forEachIndexed { idx, table ->
            canvas.drawText("Table ${idx + 1} (${table.rows}x${table.columns})", 64f, y, paint)
            y += 40f
            table.cells.forEach { row ->
                canvas.drawText(row.joinToString(" | ") { it.text.ifBlank { " " } }, 80f, y, paint)
                y += 34f
            }
            y += 20f
        }
        canvas.drawText("Finalized strokes: ${_uiState.value.strokes.size}", 64f, y + 40f, paint)
        pdf.finishPage(page)
        pdf.writeTo(FileOutputStream(pdfFile))
        pdf.close()
    }

    fun mapPressureToScale(pressure: Float, curve: Float): Float {
        return PressureCurveMapper.mapPressureToScale(pressure, curve)
    }
}
