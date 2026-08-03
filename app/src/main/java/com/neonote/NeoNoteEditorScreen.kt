package com.neonote

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.neonote.engine.JsonFilePersistenceStore
import com.neonote.model.EditorTool
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val AutoSaveDebounceMillis = 1_200L

@Composable
internal fun NeoNoteEditorScreen(controller: NeoNoteEditorController) {
    val state = controller.state
    val selectionMode = state.currentTool == EditorTool.Selection
    val context = LocalContext.current
    val persistenceStore = remember(context) {
        JsonFilePersistenceStore(context.filesDir.resolve("documents"))
    }
    val coroutineScope = rememberCoroutineScope()
    var restoreCompleted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(controller, persistenceStore, restoreCompleted) {
        if (!restoreCompleted) {
            controller.loadDocument(persistenceStore)
            restoreCompleted = true
        }
    }

    LaunchedEffect(state.document, restoreCompleted) {
        if (!restoreCompleted) return@LaunchedEffect
        delay(AutoSaveDebounceMillis)
        controller.saveDocument(persistenceStore)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        EditorToolbar(
            title = state.document.title,
            selectionMode = selectionMode,
            viewportLabel = "pan=(${state.viewport.panOffsetX.roundToInt()}, ${state.viewport.panOffsetY.roundToInt()}) zoom=${"%.2f".format(state.viewport.zoomScale)}x",
            diagnosticsLabel = if (BuildConfig.DEBUG) controller.inputDiagnostics.asToolbarText() else "",
            persistenceStatus = controller.persistenceStatus,
            pageLabel = "Page ${controller.currentPageNumber} / ${controller.pageCount}",
            canGoToPreviousPage = controller.canSwitchToPreviousPage,
            canGoToNextPage = controller.canSwitchToNextPage,
            canUndo = controller.canUndo,
            canRedo = controller.canRedo,
            onPreviousPage = controller::switchToPreviousPage,
            onNextPage = controller::switchToNextPage,
            onAddPage = controller::addPage,
            onUndo = controller::undo,
            onRedo = controller::redo,
            onToggleSelectionMode = { controller.setSelectionMode(!selectionMode) },
            onSave = { coroutineScope.launch { controller.saveDocument(persistenceStore) } },
            onLoad = { coroutineScope.launch { controller.loadDocument(persistenceStore) } },
        )
        InfiniteCanvasViewport(
            controller = controller,
            selectionMode = selectionMode,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
