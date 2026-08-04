package com.neonote

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.neonote.engine.AssetDraft
import com.neonote.engine.DocumentEngine
import com.neonote.engine.DocumentSummary
import com.neonote.engine.FileAssetStore
import com.neonote.engine.FileDocumentLibrary
import com.neonote.engine.IdGenerator
import com.neonote.engine.NeoNoteArchiveCodec
import com.neonote.model.CanvasPoint
import com.neonote.model.EditorTool
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val AutoSaveDebounceMillis = 1_200L
private const val MaximumImportedArchiveBytes = 256 * 1024 * 1024
private const val MaximumImportedImageBytes = 64 * 1024 * 1024

@Composable
internal fun NeoNoteEditorScreen(
    controller: NeoNoteEditorController,
    preferences: NeoNotePreferences,
    onPreferencesChange: (NeoNotePreferences) -> Unit,
) {
    val state = controller.state
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val library = remember(context) { FileDocumentLibrary(context.filesDir) }
    val assetStore = remember(context) { FileAssetStore(FileAssetStore.defaultDirectory(context.filesDir)) }
    val archiveCodec = remember(assetStore) { NeoNoteArchiveCodec(assetStore) }
    val documentEngine = remember { DocumentEngine(UuidIdGenerator()) }

    var libraryReady by rememberSaveable { mutableStateOf(false) }
    var libraryVisible by rememberSaveable { mutableStateOf(false) }
    var showTrash by rememberSaveable { mutableStateOf(false) }
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    var documents by remember { mutableStateOf<List<DocumentSummary>>(emptyList()) }
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }
    var canvasViewportSize by remember { mutableStateOf(IntSize.Zero) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

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
        if (uri != null) coroutineScope.launch {
            val draft = context.contentResolver.readWorkspaceImageDraft(uri)
            if (draft == null) {
                statusMessage = "Image import failed"
                return@launch
            }
            val reference = assetStore.put(draft)
            val center = controller.screenToDocument(CanvasPoint(
                canvasViewportSize.width / 2f,
                canvasViewportSize.height / 2f,
            ))
            controller.insertFloatingImage(reference.id, center, altText = reference.fileName)
            statusMessage = "Image added to canvas"
        }
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

    LaunchedEffect(state.document, libraryReady) {
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
            pendingExport = archiveCodec.export(controller.state.document)
            exportLauncher.launch(controller.state.document.title.safeFileName() + ".neonote")
        }
    }

    fun shareCurrentDocument() {
        coroutineScope.launch {
            controller.saveDocument(library)
            val archive = archiveCodec.export(controller.state.document)
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

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        val expandedNavigation = maxWidth >= 720.dp
        Column(Modifier.fillMaxSize()) {
            WorkspaceTopBar(
                title = state.document.title,
                favorite = state.document.isFavorite,
                onLibrary = { libraryVisible = true },
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Row(Modifier.fillMaxSize()) {
                if (expandedNavigation) {
                    PageRail(
                        pages = state.document.pages,
                        currentPageId = state.currentPageId,
                        onPageSelected = controller::switchPage,
                        onRename = controller::renamePage,
                        onDuplicate = controller::duplicatePage,
                        onDelete = controller::deletePage,
                        onMove = controller::movePage,
                        onAddPage = controller::addPage,
                    )
                    Box(
                        Modifier.fillMaxHeight().width(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    )
                }
                Box(
                    modifier = Modifier.fillMaxSize().onSizeChanged { canvasViewportSize = it },
                ) {
                    InfiniteCanvasViewport(
                        controller = controller,
                        selectionMode = state.currentTool == EditorTool.Selection,
                        modifier = Modifier.fillMaxSize(),
                    )
                    CanvasMetaChip(
                        controller.currentPageNumber,
                        controller.pageCount,
                        (state.viewport.zoomScale * 100f).roundToInt(),
                        Modifier.align(Alignment.TopEnd).padding(16.dp),
                    )
                    ToolContextPanel(
                        controller = controller,
                        preferences = preferences,
                        onPreferencesChange = onPreferencesChange,
                        onInsertFloatingImage = { floatingImagePicker.launch("image/*") },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 98.dp).fillMaxWidth(0.94f),
                    )
                    ToolDock(
                        selectedTool = state.currentTool,
                        onToolSelected = controller::setTool,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                    )
                    if (!expandedNavigation) {
                        CompactPageSwitcher(
                            controller.currentPageNumber,
                            controller.pageCount,
                            controller.canSwitchToPreviousPage,
                            controller.canSwitchToNextPage,
                            controller::switchToPreviousPage,
                            controller::switchToNextPage,
                            controller::addPage,
                            Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 96.dp),
                        )
                    }
                    statusMessage?.let { message ->
                        Text(
                            message,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
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
        DocumentLibraryDialog(
            documents = documents,
            showTrash = showTrash,
            currentDocumentId = state.document.id,
            onToggleTrash = { showTrash = !showTrash },
            onCreate = ::createDocument,
            onOpen = ::openDocument,
            onTrash = ::trashDocument,
            onRestore = { id -> coroutineScope.launch { library.restore(id); refreshLibrary() } },
            onDeleteForever = { id -> coroutineScope.launch { library.permanentlyDelete(id); refreshLibrary() } },
            onImport = {
                importLauncher.launch(arrayOf(
                    "application/vnd.neonote",
                    "application/zip",
                    "application/octet-stream",
                ))
            },
            onDismiss = { libraryVisible = false },
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

private class UuidIdGenerator : IdGenerator {
    override fun nextId(prefix: String): String = "$prefix-${UUID.randomUUID()}"
}

private suspend fun android.content.ContentResolver.readWorkspaceImageDraft(uri: Uri): AssetDraft? =
    withContext(Dispatchers.IO) {
        runCatching {
            val bytes = openInputStream(uri)?.use { it.readBytesLimited(MaximumImportedImageBytes) }
                ?: return@runCatching null
            if (bytes.isEmpty()) return@runCatching null
            AssetDraft(
                mediaType = getType(uri) ?: "application/octet-stream",
                bytes = bytes,
                fileName = displayName(uri),
            )
        }.getOrNull()
    }

private fun android.content.ContentResolver.displayName(uri: Uri): String? =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0) cursor.getString(index) else null
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
