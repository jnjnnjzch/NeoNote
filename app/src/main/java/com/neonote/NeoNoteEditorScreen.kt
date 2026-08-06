package com.neonote

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.neonote.engine.DocumentEngine
import com.neonote.engine.DocumentSummary
import com.neonote.engine.FileAssetStore
import com.neonote.engine.FileDocumentLibrary
import com.neonote.engine.FileDocumentSearchIndex
import com.neonote.engine.IdGenerator
import com.neonote.engine.NeoNoteArchiveCodec
import com.neonote.model.CanvasPoint
import com.neonote.model.CanvasSize
import com.neonote.model.EditorTool
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val AutoSaveDebounceMillis = 1_200L
private const val MaximumImportedArchiveBytes = 256 * 1024 * 1024

@Composable
internal fun NeoNoteEditorScreen(
    controller: NeoNoteEditorController,
    preferences: NeoNotePreferences,
    onPreferencesChange: (NeoNotePreferences) -> Unit,
) {
    val state = controller.state
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val clipboard = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    val library = remember(context) { FileDocumentLibrary(context.filesDir) }
    val assetStore = remember(context) { FileAssetStore(FileAssetStore.defaultDirectory(context.filesDir)) }
    val archiveCodec = remember(assetStore) { NeoNoteArchiveCodec(assetStore) }
    val documentEngine = remember { DocumentEngine(UuidIdGenerator()) }
    val searchIndex = remember(context) { FileDocumentSearchIndex(context.filesDir) }
    val searchCoordinator = remember(searchIndex, library, controller) {
        DocumentSearchCoordinator(searchIndex, library, controller)
    }
    val defaultImageScreenWidthPx = with(density) { 320.dp.toPx() }
    val defaultImageScreenHeightPx = with(density) { 220.dp.toPx() }
    val maximumImageScreenWidthPx = with(density) { 420.dp.toPx() }
    val maximumImageScreenHeightPx = with(density) { 360.dp.toPx() }

    var libraryReady by rememberSaveable { mutableStateOf(false) }
    var libraryVisible by rememberSaveable { mutableStateOf(false) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var showTrash by rememberSaveable { mutableStateOf(false) }
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    var documents by remember { mutableStateOf<List<DocumentSummary>>(emptyList()) }
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }
    var canvasViewportSize by remember { mutableStateOf(IntSize.Zero) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var toolOptionsVisible by rememberSaveable { mutableStateOf(false) }

    suspend fun addImageFromUri(uri: Uri, preferInline: Boolean) {
        val draft = context.contentResolver.readEditorImageAssetDraft(uri)
        if (draft == null) {
            statusMessage = "Image import failed"
            return
        }
        val reference = assetStore.put(draft)
        val focusedBoxId = controller.state.focusedRichContentBoxId
        if (preferInline && focusedBoxId != null) {
            controller.insertImageAtActiveContext(focusedBoxId, reference.id, reference.fileName ?: "Image")
            statusMessage = "Image inserted"
            return
        }
        val screenSize = preferredFloatingImageScreenSize(
            bytes = draft.bytes,
            viewport = canvasViewportSize,
            defaultWidthPx = defaultImageScreenWidthPx,
            defaultHeightPx = defaultImageScreenHeightPx,
            maximumWidthPx = maximumImageScreenWidthPx,
            maximumHeightPx = maximumImageScreenHeightPx,
        )
        val zoom = controller.state.viewport.zoomScale.coerceAtLeast(0.2f)
        val documentSize = CanvasSize(screenSize.width / zoom, screenSize.height / zoom)
        val center = controller.screenToDocument(
            CanvasPoint(canvasViewportSize.width / 2f, canvasViewportSize.height / 2f),
        )
        val imageId = controller.insertFloatingImage(
            assetId = reference.id,
            position = CanvasPoint(center.x - documentSize.width / 2f, center.y - documentSize.height / 2f),
            size = documentSize,
            altText = reference.fileName,
        )
        controller.setTool(EditorTool.Selection)
        controller.selectCanvasObject(imageId)
        statusMessage = "Image placed · drag to position it"
    }

    suspend fun refreshLibrary() {
        documents = library.list(includeTrash = true)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.neonote"),
    ) { uri ->
        val payload = pendingExport
        pendingExport = null
        if (uri != null && payload != null) coroutineScope.launch {
            val wrote = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(payload) } != null }
                    .getOrDefault(false)
            }
            statusMessage = if (wrote) "Archive exported" else "Export failed"
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) coroutineScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytesLimited(MaximumImportedArchiveBytes) }
                        ?: error("Unable to read archive")
                }
                val imported = archiveCodec.import(bytes)
                library.save(imported.document)
                controller.replaceDocument(imported.document, recordHistory = false)
                refreshLibrary()
                libraryVisible = false
                statusMessage = "Imported ${imported.importedAssetCount} assets"
            }.onFailure { statusMessage = "Import failed: ${it.message ?: "invalid archive"}" }
        }
    }

    val floatingImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) coroutineScope.launch { addImageFromUri(uri, preferInline = false) }
    }

    LaunchedEffect(
        preferences.defaultInkColorArgb,
        preferences.defaultInkWidth,
        preferences.defaultInkOpacity,
        preferences.pressureEnabled,
        preferences.defaultBrush,
        preferences.eraserMode,
    ) {
        controller.setInkColor(preferences.defaultInkColorArgb)
        controller.setInkWidth(preferences.defaultInkWidth)
        controller.setInkOpacity(preferences.defaultInkOpacity)
        controller.setPressureEnabled(preferences.pressureEnabled)
        controller.setInkBrush(preferences.defaultBrush)
        controller.setEraserMode(preferences.eraserMode)
    }

    LaunchedEffect(libraryReady) {
        if (libraryReady) return@LaunchedEffect
        val available = library.list()
        when {
            available.any { it.id == state.document.id } -> controller.loadDocument(library, state.document.id)
            available.isNotEmpty() -> controller.loadDocument(library, available.first().id)
            else -> controller.saveDocument(library)
        }
        refreshLibrary()
        libraryReady = true
    }

    LaunchedEffect(state.document.revision, controller.contentChangeToken, libraryReady) {
        if (!libraryReady) return@LaunchedEffect
        delay(AutoSaveDebounceMillis)
        controller.saveDocument(library)
        refreshLibrary()
    }

    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            delay(3_000L)
            statusMessage = null
        }
    }

    fun createDocument(title: String) {
        coroutineScope.launch {
            controller.saveDocument(library)
            val document = documentEngine.createDocument(
                title = title,
                assetStoreId = "assets-${UUID.randomUUID()}",
            )
            library.save(document)
            controller.replaceDocument(document, recordHistory = false)
            refreshLibrary()
            libraryVisible = false
        }
    }

    fun openDocument(documentId: String) {
        coroutineScope.launch {
            controller.saveDocument(library)
            controller.loadDocument(library, documentId)
            refreshLibrary()
            libraryVisible = false
        }
    }

    fun trashDocument(documentId: String) {
        coroutineScope.launch {
            if (documentId == controller.state.document.id) controller.saveDocument(library)
            if (!library.moveToTrash(documentId)) return@launch
            val active = library.list()
            if (documentId == controller.state.document.id) {
                if (active.isNotEmpty()) {
                    controller.loadDocument(library, active.first().id)
                } else {
                    val replacement = documentEngine.createDocument("Untitled Note", "assets-${UUID.randomUUID()}")
                    library.save(replacement)
                    controller.replaceDocument(replacement, recordHistory = false)
                }
            }
            refreshLibrary()
        }
    }

    fun exportCurrentDocument() {
        coroutineScope.launch {
            controller.saveDocument(library)
            val exportDocument = controller.documentForPersistence()
            pendingExport = archiveCodec.export(exportDocument)
            exportLauncher.launch(exportDocument.title.safeFileName() + ".neonote")
        }
    }

    fun shareCurrentDocument() {
        coroutineScope.launch {
            controller.saveDocument(library)
            val shareDocument = controller.documentForPersistence()
            val archive = archiveCodec.export(shareDocument)
            val directory = context.cacheDir.resolve("shared").also { it.mkdirs() }
            directory.listFiles()?.forEach {
                if (System.currentTimeMillis() - it.lastModified() > 86_400_000L) it.delete()
            }
            val file = directory.resolve(controller.state.document.title.safeFileName() + ".neonote")
            withContext(Dispatchers.IO) { file.writeBytes(archive) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.neonote"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, controller.state.document.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share NeoNote archive"))
        }
    }

    fun canvasInsertionPoint(): CanvasPoint = controller.screenToDocument(
        CanvasPoint(
            if (canvasViewportSize.width > 0) canvasViewportSize.width * 0.42f else 220f,
            if (canvasViewportSize.height > 0) canvasViewportSize.height * 0.28f else 160f,
        ),
    )

    fun beginContentAtInsertionPoint(afterFocus: (String) -> Unit = {}) {
        controller.setTool(EditorTool.Pen)
        controller.createRichContentBox(canvasInsertionPoint(), avoidOverlap = true)
        controller.state.focusedRichContentBoxId?.let(afterFocus)
    }

    BackHandler(
        enabled = libraryVisible || searchVisible || settingsVisible ||
            state.focusedRichContentBoxId != null || state.selection.selectedRefs.isNotEmpty() ||
            state.currentTool == EditorTool.Selection || state.currentTool == EditorTool.Eraser || toolOptionsVisible,
    ) {
        when {
            settingsVisible -> settingsVisible = false
            searchVisible -> searchVisible = false
            libraryVisible -> libraryVisible = false
            toolOptionsVisible -> toolOptionsVisible = false
            else -> controller.handleBack()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || libraryVisible || searchVisible || settingsVisible) return@onPreviewKeyEvent false
                when {
                    event.isCtrlPressed && !event.isShiftPressed && event.key == Key.C &&
                        state.selection.selectedRefs.isNotEmpty() -> {
                        controller.copySelection()
                        true
                    }
                    event.isCtrlPressed && !event.isShiftPressed && event.key == Key.X &&
                        state.selection.selectedRefs.isNotEmpty() -> {
                        controller.cutSelection()
                        true
                    }
                    event.isCtrlPressed && !event.isShiftPressed && event.key == Key.A &&
                        state.focusedRichContentBoxId == null -> {
                        controller.selectAllCanvasContent()
                        true
                    }
                    event.isCtrlPressed && !event.isShiftPressed && event.key == Key.V -> {
                        when {
                            state.focusedRichContentBoxId == null && state.currentTool == EditorTool.Selection && controller.canPasteSelection -> {
                                controller.pasteSelection()
                                true
                            }
                            clipboard.imageUriOrNull() != null -> {
                                val imageUri = requireNotNull(clipboard.imageUriOrNull())
                                coroutineScope.launch {
                                    addImageFromUri(imageUri, preferInline = controller.state.focusedRichContentBoxId != null)
                                }
                                true
                            }
                            state.focusedRichContentBoxId == null -> {
                                val pastedText = clipboard.plainTextOrNull(context)
                                if (pastedText == null) false else {
                                    beginContentAtInsertionPoint { boxId ->
                                        controller.updateRichContentText(boxId, pastedText)
                                    }
                                    true
                                }
                            }
                            else -> false
                        }
                    }
                    state.selection.selectedRefs.isNotEmpty() &&
                        (event.key == Key.Delete || event.key == Key.Backspace) -> {
                        controller.deleteSelection()
                        true
                    }
                    event.isCtrlPressed && !event.isShiftPressed && event.key == Key.D &&
                        state.selection.selectedRefs.isNotEmpty() -> {
                        controller.duplicateSelection()
                        true
                    }
                    event.isCtrlPressed && !event.isShiftPressed && event.key == Key.S -> {
                        coroutineScope.launch { controller.saveDocument(library); refreshLibrary() }
                        true
                    }
                    event.isCtrlPressed && !event.isShiftPressed && event.key == Key.F -> {
                        controller.state.focusedRichContentBoxId?.let(controller::commitRichContentEditing)
                        coroutineScope.launch { controller.saveDocument(library); searchVisible = true }
                        true
                    }
                    event.isCtrlPressed && !event.isShiftPressed && event.key == Key.Z -> { controller.undo(); true }
                    (event.isCtrlPressed && event.key == Key.Y) ||
                        (event.isCtrlPressed && event.isShiftPressed && event.key == Key.Z) -> { controller.redo(); true }
                    event.key == Key.Escape -> controller.handleBack()
                    else -> false
                }
            },
    ) {
        val expandedNavigation = maxWidth >= 720.dp
        val focusedTextEditing = state.currentTool == EditorTool.Text && state.focusedRichContentBoxId != null
        Column(Modifier.fillMaxSize()) {
            NaturalWorkspaceTopBar(
                title = state.document.title,
                favorite = state.document.isFavorite,
                onLibrary = {
                    controller.state.focusedRichContentBoxId?.let(controller::commitRichContentEditing)
                    coroutineScope.launch { controller.saveDocument(library); refreshLibrary(); libraryVisible = true }
                },
                onSearch = {
                    controller.state.focusedRichContentBoxId?.let(controller::commitRichContentEditing)
                    coroutineScope.launch { controller.saveDocument(library); searchVisible = true }
                },
                onTitleChange = controller::renameDocument,
                onFavorite = controller::toggleDocumentFavorite,
                saveLabel = controller.saveStateLabel,
                canUndo = controller.canUndo,
                canRedo = controller.canRedo,
                onUndo = controller::undo,
                onRedo = controller::redo,
                onResetView = controller::resetViewport,
                onExport = ::exportCurrentDocument,
                onShare = ::shareCurrentDocument,
                onSettings = { settingsVisible = true },
                onSaveNow = { coroutineScope.launch { controller.saveDocument(library); refreshLibrary() } },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            IntentNavigation(controller = controller, compact = !expandedNavigation)
            Row(Modifier.fillMaxSize()) {
                if (expandedNavigation) {
                    IntentPageRail(controller)
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (focusedTextEditing) Modifier.imePadding() else Modifier)
                        .onSizeChanged { canvasViewportSize = it },
                ) {
                    InfiniteCanvasViewport(
                        controller = controller,
                        selectionMode = state.currentTool == EditorTool.Selection,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (toolOptionsVisible && state.currentTool != EditorTool.Text && !focusedTextEditing) {
                        NaturalToolContextPanel(
                            controller = controller,
                            preferences = preferences,
                            onPreferencesChange = onPreferencesChange,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 10.dp, bottom = 82.dp)
                                .fillMaxWidth(),
                        )
                    }
                    if (!focusedTextEditing) {
                        NaturalToolDock(
                            selectedTool = state.currentTool,
                            onToolSelected = { tool ->
                                if (tool == state.currentTool && tool != EditorTool.Text) {
                                    toolOptionsVisible = !toolOptionsVisible
                                } else {
                                    controller.setTool(tool)
                                    toolOptionsVisible = false
                                }
                            },
                            onInsertText = { beginContentAtInsertionPoint() },
                            onInsertTable = { beginContentAtInsertionPoint { id -> controller.insertTableAtActiveContext(id) } },
                            onInsertFormula = { beginContentAtInsertionPoint { id -> controller.insertFormulaAtActiveContext(id) } },
                            onInsertImage = { floatingImagePicker.launch("image/*") },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                        )
                    }
                    statusMessage?.let { message ->
                        Text(
                            message,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp)
                                .background(
                                    MaterialTheme.colorScheme.inverseSurface,
                                    androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                )
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }

    if (libraryVisible) {
        NaturalDocumentLibraryDialog(
            documents = documents,
            showTrash = showTrash,
            currentDocumentId = state.document.id,
            onToggleTrash = { showTrash = !showTrash },
            onCreate = ::createDocument,
            onOpen = ::openDocument,
            onTrash = ::trashDocument,
            onRestore = { id -> coroutineScope.launch { library.restore(id); refreshLibrary() } },
            onDeleteForever = { id -> coroutineScope.launch { library.permanentlyDelete(id); refreshLibrary() } },
            onSearchAllNotes = {
                libraryVisible = false
                coroutineScope.launch {
                    controller.saveDocument(library)
                    searchVisible = true
                }
            },
            onImport = {
                importLauncher.launch(
                    arrayOf(
                        "application/vnd.neonote",
                        "application/zip",
                        "application/octet-stream",
                    ),
                )
            },
            onDismiss = { libraryVisible = false },
        )
    }
    if (searchVisible) {
        DocumentSearchDialog(
            searchIndex = searchIndex,
            onOpenResult = { hit ->
                coroutineScope.launch {
                    if (searchCoordinator.open(hit)) {
                        refreshLibrary()
                        searchVisible = false
                    } else {
                        statusMessage = "That note is no longer available"
                    }
                }
            },
            onDismiss = { searchVisible = false },
        )
    }
    if (settingsVisible) {
        NeoNoteSettingsDialog(
            preferences = preferences,
            onPreferencesChange = onPreferencesChange,
            onDismiss = { settingsVisible = false },
        )
    }
}

private fun ClipboardManager?.imageUriOrNull(): Uri? {
    val clip = this?.primaryClip ?: return null
    return (0 until clip.itemCount).firstNotNullOfOrNull { index -> clip.getItemAt(index).uri }
}

private fun ClipboardManager?.plainTextOrNull(context: android.content.Context): String? {
    val clip = this?.primaryClip ?: return null
    val description = clip.description
    if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) &&
        !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) return null
    return (0 until clip.itemCount)
        .firstNotNullOfOrNull { index -> clip.getItemAt(index).coerceToText(context)?.toString() }
        ?.takeIf { it.isNotEmpty() }
}

private data class FloatingImageScreenSize(val width: Float, val height: Float)

private fun preferredFloatingImageScreenSize(
    bytes: ByteArray,
    viewport: IntSize,
    defaultWidthPx: Float,
    defaultHeightPx: Float,
    maximumWidthPx: Float,
    maximumHeightPx: Float,
): FloatingImageScreenSize {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    val sourceWidth = options.outWidth.takeIf { it > 0 }?.toFloat()
    val sourceHeight = options.outHeight.takeIf { it > 0 }?.toFloat()
    val availableWidth = if (viewport.width > 0) min(viewport.width * 0.72f, maximumWidthPx) else defaultWidthPx
    val availableHeight = if (viewport.height > 0) min(viewport.height * 0.58f, maximumHeightPx) else defaultHeightPx
    if (sourceWidth == null || sourceHeight == null) {
        return FloatingImageScreenSize(availableWidth, min(defaultHeightPx, availableHeight))
    }

    var scale = min(availableWidth / sourceWidth, availableHeight / sourceHeight).coerceAtMost(1f)
    var width = sourceWidth * scale
    var height = sourceHeight * scale
    val minimumLongEdge = min(max(defaultWidthPx, defaultHeightPx) * 0.55f, max(availableWidth, availableHeight))
    val currentLongEdge = max(width, height)
    if (currentLongEdge < minimumLongEdge && currentLongEdge > 0f) {
        scale = min(minimumLongEdge / currentLongEdge, min(availableWidth / width, availableHeight / height))
        width *= scale
        height *= scale
    }
    return FloatingImageScreenSize(width.coerceAtLeast(1f), height.coerceAtLeast(1f))
}

private class UuidIdGenerator : IdGenerator {
    override fun nextId(prefix: String): String = "$prefix-${UUID.randomUUID()}"
}

private fun InputStream.readBytesLimited(maximumBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        require(total <= maximumBytes) { "Input exceeds the size limit" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private fun String.safeFileName(): String =
    map { if (it.isLetterOrDigit() || it in "-_. ") it else '_' }
        .joinToString("")
        .trim()
        .ifBlank { "Untitled Note" }
