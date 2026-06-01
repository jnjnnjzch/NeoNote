package com.neonote

import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neonote.engine.InputMode
import com.neonote.engine.JsonFilePersistenceStore
import com.neonote.engine.InputRouter
import com.neonote.engine.InlineStyle
import com.neonote.engine.PointerEventType
import com.neonote.engine.toPlainText
import com.neonote.input.AndroidPointerSnapshot
import com.neonote.input.AndroidStylusInputAdapter
import com.neonote.input.ComposeInputAdapter
import com.neonote.input.InputDiagnostics
import com.neonote.input.describeAndroidSource
import com.neonote.input.toAndroidPointerSnapshot
import com.neonote.input.withPressureSamples
import com.neonote.model.CanvasObject
import com.neonote.model.CanvasPoint
import com.neonote.model.CanvasRect
import com.neonote.model.InlineLineBreak
import com.neonote.model.InlineText
import com.neonote.model.ListKind
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContentBox
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NeoNoteApp() }
    }
}

@Composable
public fun NeoNoteApp(controller: NeoNoteEditorController? = null) {
    val editorController = controller ?: viewModel<NeoNoteEditorViewModel>().controller

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF8FAFC)) {
            NeoNoteEditorScreen(controller = editorController)
        }
    }
}

@Composable
private fun NeoNoteEditorScreen(controller: NeoNoteEditorController) {
    val state = controller.state
    val selectionMode = state.currentTool == com.neonote.model.EditorTool.Selection
    val context = LocalContext.current
    val persistenceStore = remember(context) { JsonFilePersistenceStore(context.filesDir.resolve("documents")) }
    val coroutineScope = rememberCoroutineScope()
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
            onPreviousPage = controller::switchToPreviousPage,
            onNextPage = controller::switchToNextPage,
            onAddPage = controller::addPage,
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

@Composable
private fun EditorToolbar(
    title: String,
    selectionMode: Boolean,
    viewportLabel: String,
    diagnosticsLabel: String,
    persistenceStatus: String,
    pageLabel: String,
    canGoToPreviousPage: Boolean,
    canGoToNextPage: Boolean,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onAddPage: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
            Text(text = viewportLabel, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
            if (diagnosticsLabel.isNotBlank()) {
                Text(text = diagnosticsLabel, style = MaterialTheme.typography.bodySmall, color = Color(0xFF475569))
            }
            Text(text = persistenceStatus, style = MaterialTheme.typography.bodySmall, color = Color(0xFF0369A1))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onPreviousPage, enabled = canGoToPreviousPage) {
                Text("Prev")
            }
            Text(
                text = pageLabel,
                modifier = Modifier.padding(horizontal = 8.dp),
                color = Color(0xFF334155),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onNextPage, enabled = canGoToNextPage) {
                Text("Next")
            }
            Button(onClick = onAddPage, modifier = Modifier.padding(start = 8.dp)) {
                Text("Add Page")
            }
        }
        Button(onClick = onSave, modifier = Modifier.padding(start = 8.dp)) {
            Text("Save")
        }
        Button(onClick = onLoad, modifier = Modifier.padding(start = 8.dp)) {
            Text("Load")
        }
        Button(onClick = onToggleSelectionMode, modifier = Modifier.padding(start = 8.dp)) {
            Text(if (selectionMode) "Selection: ON" else "Selection: OFF")
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun InfiniteCanvasViewport(
    controller: NeoNoteEditorController,
    selectionMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val router = remember { InputRouter() }
    val inputAdapter = remember { ComposeInputAdapter() }
    val platformSnapshotStore = remember { PlatformSnapshotStore() }
    Box(
        modifier = modifier
            .background(Color(0xFFEFF6FF))
            .pointerInteropFilter { motionEvent ->
                if (AndroidStylusInputAdapter.isStylusOrEraser(motionEvent)) {
                    val inputEvent = AndroidStylusInputAdapter.toInputEvent(motionEvent)
                    controller.updateInputDiagnostics(
                        diagnostics = AndroidStylusInputAdapter.toDiagnostics(motionEvent),
                        eventTimeMillis = motionEvent.eventTime,
                        force = motionEvent.actionMasked != MotionEvent.ACTION_MOVE,
                    )

                    if (inputEvent != null) {
                        controller.routeInputEvent(
                            router = router,
                            event = inputEvent,
                            mode = if (selectionMode) InputMode.Selection else InputMode.Write,
                        )
                    }

                    true
                } else {
                    platformSnapshotStore.latest = motionEvent.toAndroidPointerSnapshot()
                    false
                }
            }
            .pointerInput(selectionMode) {
                handleCanvasPointerInput(
                    router = router,
                    inputAdapter = inputAdapter,
                    platformSnapshotProvider = { platformSnapshotStore.latest },
                    controller = controller,
                    selectionMode = selectionMode,
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = controller.state.viewport.panOffsetX
                    translationY = controller.state.viewport.panOffsetY
                    scaleX = controller.state.viewport.zoomScale
                    scaleY = controller.state.viewport.zoomScale
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            InkLayerView(
                pageId = controller.state.currentPageId,
                inkLayer = controller.currentCanvas.inkLayer,
                activeStroke = controller.activeInkStroke,
                modifier = Modifier.fillMaxSize(),
            )
            controller.currentCanvas.objects.forEach { canvasObject ->
                CanvasObjectView(
                    canvasObject = canvasObject,
                    selected = controller.state.selection.isObjectSelected(canvasObject.id),
                    selectionMode = selectionMode,
                    controller = controller,
                )
            }
            SelectionOverlay(
                activeLassoPath = controller.activeLassoPath,
                selectedBounds = controller.selectedBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = "Tap blank canvas to create text · Drag blank canvas to pan · Pinch to zoom",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.88f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            color = Color(0xFF334155),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private class PlatformSnapshotStore {
    var latest: AndroidPointerSnapshot? = null
}

@Composable
private fun SelectionOverlay(
    activeLassoPath: List<CanvasPoint>,
    selectedBounds: CanvasRect?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        selectedBounds?.let { bounds ->
            drawRect(
                color = Color(0xFF2563EB),
                topLeft = Offset(bounds.left, bounds.top),
                size = Size(
                    width = bounds.right - bounds.left,
                    height = bounds.bottom - bounds.top,
                ),
                style = Stroke(width = 2f),
            )
        }
        activeLassoPath.zipWithNext { start, end ->
            drawLine(
                color = Color(0xFF2563EB),
                start = Offset(start.x, start.y),
                end = Offset(end.x, end.y),
                strokeWidth = 2f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun CanvasObjectView(
    canvasObject: CanvasObject,
    selected: Boolean,
    selectionMode: Boolean,
    controller: NeoNoteEditorController,
) {
    when (canvasObject) {
        is RichContentBox -> RichContentBoxView(
            box = canvasObject,
            selected = selected,
            selectionMode = selectionMode,
            controller = controller,
        )
        else -> Unit
    }
}

@Composable
private fun RichContentBoxView(
    box: RichContentBox,
    selected: Boolean,
    selectionMode: Boolean,
    controller: NeoNoteEditorController,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val clipboardManager = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(box.isFocused, selectionMode, selected) {
        if (selectionMode || selected) {
            focusManager.clearFocus()
        } else if (box.isFocused) {
            focusRequester.requestFocus()
        }
    }
    val borderColor = when {
        selected -> Color(0xFF2563EB)
        box.isFocused -> Color(0xFF7C3AED)
        else -> Color(0xFFE2E8F0)
    }
    val borderWidth = when {
        selected || box.isFocused -> 2.dp
        else -> 1.dp
    }
    val modifier = Modifier
        .offset { IntOffset(box.position.x.roundToInt(), box.position.y.roundToInt()) }
        .size(
            width = with(density) { box.size.width.toDp() },
            height = with(density) { box.size.height.toDp() },
        )
        .clip(RoundedCornerShape(14.dp))
        .background(Color.White)
        .border(width = borderWidth, color = borderColor, shape = RoundedCornerShape(14.dp))

    val contentModifier = modifier
        .padding(8.dp)
        .then(
            if (selectionMode) {
                Modifier.pointerInput(box.id, selectionMode) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        controller.activateRichContentBox(box.id)
                    }
                }
            } else {
                Modifier
            },
        )

    var platformTextFieldValue by remember(box.id) { mutableStateOf(TextFieldValue(box.toPlainText())) }
    val modelText = box.toPlainText()
    LaunchedEffect(modelText, box.isFocused) {
        if (!box.isFocused && platformTextFieldValue.text != modelText) {
            platformTextFieldValue = TextFieldValue(modelText)
        }
    }

    Box(modifier = contentModifier) {
        if (!box.isFocused) {
            RichContentDisplay(
                box = box,
                selectionMode = selectionMode,
                selected = selected,
                onFocus = { controller.activateRichContentBox(box.id) },
                onToggleTodoChecked = { blockIndex ->
                    controller.toggleRichContentTodoCheckedState(boxId = box.id, blockIndex = blockIndex)
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            TextField(
                value = platformTextFieldValue,
                onValueChange = { nextValue ->
                    val previousValue = platformTextFieldValue
                    platformTextFieldValue = nextValue
                    controller.updateRichContentFromPlatformInput(
                        boxId = box.id,
                        previousText = previousValue.text,
                        nextText = nextValue.text,
                        selectionStart = nextValue.selection.start,
                        selectionEnd = nextValue.selection.end,
                        hasActiveComposition = nextValue.composition != null,
                    )
                },
                enabled = !selectionMode && !selected,
                singleLine = false,
                minLines = 1,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { keyEvent ->
                        val style = keyEvent.richContentShortcutStyle()
                        val listKind = keyEvent.richContentShortcutListKind()
                        val pastedText = keyEvent.richContentPlainTextPaste(clipboardManager, context)
                        when {
                            pastedText != null && box.isFocused -> {
                                val selectionStart = minOf(platformTextFieldValue.selection.start, platformTextFieldValue.selection.end)
                                val selectionEnd = maxOf(platformTextFieldValue.selection.start, platformTextFieldValue.selection.end)
                                val nextText = platformTextFieldValue.text.replaceRange(selectionStart, selectionEnd, pastedText)
                                val nextCursor = selectionStart + pastedText.length
                                val previousValue = platformTextFieldValue
                                platformTextFieldValue = TextFieldValue(
                                    text = nextText,
                                    selection = TextRange(nextCursor),
                                )
                                controller.updateRichContentFromPlatformInput(
                                    boxId = box.id,
                                    previousText = previousValue.text,
                                    nextText = nextText,
                                    selectionStart = nextCursor,
                                    selectionEnd = nextCursor,
                                    hasActiveComposition = false,
                                )
                                true
                            }
                            style != null && box.isFocused -> {
                                controller.toggleRichContentStyle(
                                    boxId = box.id,
                                    style = style,
                                    selectionStart = platformTextFieldValue.selection.start,
                                    selectionEnd = platformTextFieldValue.selection.end,
                                )
                                true
                            }
                            listKind != null && box.isFocused -> {
                                controller.toggleRichContentList(
                                    boxId = box.id,
                                    kind = listKind,
                                    selectionStart = platformTextFieldValue.selection.start,
                                    selectionEnd = platformTextFieldValue.selection.end,
                                )
                                true
                            }
                            else -> false
                        }
                    }
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && !selectionMode && !selected && !box.isFocused) {
                            controller.activateRichContentBox(box.id)
                        } else if (!focusState.isFocused && box.isFocused) {
                            controller.commitRichContentEditing(box.id)
                        }
                    },
                placeholder = { Text("Start typing…") },
            )
        }
    }
}

@Composable
private fun RichContentDisplay(
    box: RichContentBox,
    selectionMode: Boolean,
    selected: Boolean,
    onFocus: () -> Unit,
    onToggleTodoChecked: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusModifier = if (!selectionMode && !selected) {
        Modifier.clickable(onClick = onFocus)
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .then(focusModifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (box.content.blocks.isEmpty()) {
            Text("Start typing…", color = Color(0xFF94A3B8))
        }
        var numberedIndex = 0
        var previousNumbered = false
        box.content.blocks.forEachIndexed { blockIndex, block ->
            val paragraph = block as? ParagraphNode ?: return@forEachIndexed
            val metadata = paragraph.listMetadata
            val markerNumber = if (metadata?.kind == ListKind.Numbered) {
                numberedIndex = if (previousNumbered) numberedIndex + 1 else 1
                previousNumbered = true
                numberedIndex
            } else {
                previousNumbered = false
                numberedIndex = 0
                0
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (metadata?.kind) {
                    ListKind.Bullet -> Text("•", modifier = Modifier.padding(end = 8.dp), color = Color(0xFF334155))
                    ListKind.Numbered -> Text("$markerNumber.", modifier = Modifier.padding(end = 8.dp), color = Color(0xFF334155))
                    ListKind.Todo -> Checkbox(
                        checked = metadata.checked,
                        onCheckedChange = { onToggleTodoChecked(blockIndex) },
                        enabled = !selectionMode && !selected,
                    )
                    null -> Unit
                }
                Text(text = paragraph.displayText(), color = Color(0xFF0F172A))
            }
        }
    }
}

private fun ParagraphNode.displayText(): String = inlines.joinToString("") { inline ->
    when (inline) {
        is InlineText -> inline.text
        InlineLineBreak -> "\n"
        else -> ""
    }
}


private fun androidx.compose.ui.input.key.KeyEvent.richContentPlainTextPaste(
    clipboardManager: ClipboardManager?,
    context: android.content.Context,
): String? {
    if (type != KeyEventType.KeyDown || !isCtrlPressed || isShiftPressed || key != Key.V) return null
    val clip = clipboardManager?.primaryClip ?: return null
    val description = clip.description
    if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) || clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.replace("\r\n", "\n")?.replace('\r', '\n')
}

private fun androidx.compose.ui.input.key.KeyEvent.richContentShortcutStyle(): InlineStyle? {
    if (type != KeyEventType.KeyDown || !isCtrlPressed || isShiftPressed) return null
    return when (key) {
        Key.B -> InlineStyle.Bold
        Key.I -> InlineStyle.Italic
        Key.U -> InlineStyle.Underline
        else -> null
    }
}

private fun androidx.compose.ui.input.key.KeyEvent.richContentShortcutListKind(): ListKind? {
    if (type != KeyEventType.KeyDown || !isCtrlPressed || !isShiftPressed) return null
    return when (key) {
        Key.B -> ListKind.Bullet
        Key.N -> ListKind.Numbered
        Key.T -> ListKind.Todo
        else -> null
    }
}

private suspend fun PointerInputScope.handleCanvasPointerInput(
    router: InputRouter,
    inputAdapter: ComposeInputAdapter,
    platformSnapshotProvider: () -> AndroidPointerSnapshot?,
    controller: NeoNoteEditorController,
    selectionMode: Boolean,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        if (down.isConsumed) return@awaitEachGesture

        routePointerEvent(
            router = router,
            inputAdapter = inputAdapter,
            controller = controller,
            type = PointerEventType.Down,
            changes = listOf(down),
            platformSnapshot = platformSnapshotProvider(),
            selectionMode = selectionMode,
        )

        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Final)
            val platformSnapshot = platformSnapshotProvider()
            if (event.changes.any { it.isConsumed }) return@awaitEachGesture

            val pressedChanges = event.changes.filter { it.pressed }
            if (pressedChanges.size >= 2) {
                val zoomChange = event.calculateZoom()
                val centroid = event.calculateCentroid(useCurrent = true)
                routePointerEvent(
                    router = router,
                    inputAdapter = inputAdapter,
                    controller = controller,
                    type = PointerEventType.Move,
                    changes = pressedChanges,
                    platformSnapshot = platformSnapshot,
                    selectionMode = selectionMode,
                )
                controller.zoomViewportBy(zoomChange, CanvasPoint(centroid.x, centroid.y))
                event.changes.forEach { it.consume() }
                continue
            }

            val primary = event.changes.firstOrNull() ?: return@awaitEachGesture
            if (primary.changedToUpIgnoreConsumed()) {
                routePointerEvent(
                    router = router,
                    inputAdapter = inputAdapter,
                    controller = controller,
                    type = PointerEventType.Up,
                    changes = listOf(primary),
                    platformSnapshot = platformSnapshot,
                    selectionMode = selectionMode,
                )
                return@awaitEachGesture
            }

            if (primary.pressed && primary.positionChange() != Offset.Zero) {
                routePointerEvent(
                    router = router,
                    inputAdapter = inputAdapter,
                    controller = controller,
                    type = PointerEventType.Move,
                    changes = listOf(primary),
                    platformSnapshot = platformSnapshot,
                    selectionMode = selectionMode,
                )
            }
        }
    }
}

private fun routePointerEvent(
    router: InputRouter,
    inputAdapter: ComposeInputAdapter,
    controller: NeoNoteEditorController,
    type: PointerEventType,
    changes: List<PointerInputChange>,
    platformSnapshot: AndroidPointerSnapshot?,
    selectionMode: Boolean,
) {
    val inputEvent = inputAdapter.toInputEvent(
        type = type,
        changes = changes,
        platformSnapshot = platformSnapshot,
    ) ?: return
    controller.updateInputDiagnostics(
        InputDiagnostics(
            tool = inputEvent.pointers.first().tool,
            pressure = inputEvent.primaryPressure,
            pointerCount = inputEvent.pointers.size,
            deviceId = platformSnapshot?.deviceId,
            source = platformSnapshot?.source,
            sourceDescription = describeAndroidSource(platformSnapshot?.source),
        ).withPressureSamples(inputEvent.primaryInkSamples),
        force = type != PointerEventType.Move,
    )
    controller.routeInputEvent(
        router = router,
        event = inputEvent,
        mode = if (selectionMode) InputMode.Selection else InputMode.Write,
    )
}
