/*
 *
 *  * Copyright 2025 Google LLC. All rights reserved.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.example.cahier.features.drawing

import android.content.ClipData
import android.content.ClipDescription
import android.net.Uri
import android.view.KeyEvent as AndroidKeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cahier.R
import com.example.cahier.core.ui.ColorPickerDialog
import com.example.cahier.core.ui.ConfirmationDialog
import com.example.cahier.core.ui.DrawingInputRoute
import com.example.cahier.core.ui.DrawingInputRouter
import com.example.cahier.core.ui.DrawingInputRoutingConfig
import com.example.cahier.core.ui.DrawingSurface
import com.example.cahier.core.ui.FocusedFieldEnum
import com.example.cahier.core.ui.LocalTextureStore
import com.example.cahier.core.ui.theme.NeoNoteVisualTokens
import com.example.cahier.core.ui.theme.CahierAppTheme
import com.example.cahier.core.utils.createDropTarget
import com.example.cahier.core.document.TableBlock
import com.example.cahier.core.document.TableNode
import com.example.cahier.core.document.TextContainerBlock
import com.example.cahier.core.document.ParagraphNode
import com.example.cahier.core.document.ImageBlock
import com.example.cahier.core.document.FormulaBlock
import com.example.cahier.features.drawing.CanvasTransformMapper.docToScreenX
import com.example.cahier.features.drawing.CanvasTransformMapper.docToScreenY
import com.example.cahier.features.drawing.CanvasTransformMapper.screenToCanvasX
import com.example.cahier.features.drawing.CanvasTransformMapper.screenToCanvasY
import com.example.cahier.features.drawing.CanvasTransformMapper.screenToDocDelta
import com.example.cahier.features.home.AppMode
import com.example.cahier.features.drawing.viewmodel.DrawingCanvasViewModel
import coil3.compose.AsyncImage
import kotlin.math.hypot

internal data class FingerTapThresholds(
    val touchSlop: Float,
    val maxDurationMillis: Long = ViewConfiguration.getTapTimeout().toLong()
)

internal class FingerTapGestureTracker(
    private val thresholds: FingerTapThresholds
) {
    private var downX = 0f
    private var downY = 0f
    private var downTimeMillis = 0L
    private var tapCandidate = false

    fun onDown(x: Float, y: Float, eventTimeMillis: Long) {
        downX = x
        downY = y
        downTimeMillis = eventTimeMillis
        tapCandidate = true
    }

    fun onMove(x: Float, y: Float) {
        if (hypot(x - downX, y - downY) > thresholds.touchSlop) {
            tapCandidate = false
        }
    }

    fun onMultiPointerGesture() {
        tapCandidate = false
    }

    fun onUp(x: Float, y: Float, eventTimeMillis: Long): Boolean {
        onMove(x, y)
        val isTap = tapCandidate && eventTimeMillis - downTimeMillis <= thresholds.maxDurationMillis
        tapCandidate = false
        return isTap
    }

    fun onCancel() {
        tapCandidate = false
    }
}

internal fun routeForDrawingCanvasFingerEvent(
    event: MotionEvent,
    config: DrawingInputRoutingConfig
): DrawingInputRoute = DrawingInputRouter.routeFor(event, config)

internal sealed interface FingerTapTextTarget {
    data object FocusExisting : FingerTapTextTarget
    data class PlaceAt(val docX: Float, val docY: Float) : FingerTapTextTarget
}

internal fun resolveFingerTapTextTarget(
    sx: Float,
    sy: Float,
    textContainer: TextContainerBlock?,
    canvasTransform: CanvasTransform
): FingerTapTextTarget {
    val container = textContainer
    if (container != null) {
        val left = docToScreenX(container.x, canvasTransform)
        val top = docToScreenY(container.y, canvasTransform)
        val right = left + (container.width * canvasTransform.scale)
        val bottom = top + (container.height * canvasTransform.scale)
        if (sx in left..right && sy in top..bottom) {
            return FingerTapTextTarget.FocusExisting
        }
    }
    return FingerTapTextTarget.PlaceAt(
        docX = screenToCanvasX(sx, canvasTransform),
        docY = screenToCanvasY(sy, canvasTransform)
    )
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3WindowSizeClassApi::class
)
@Composable
fun DrawingCanvas(
    navigateUp: () -> Unit,
    navigateToBrushGraph: () -> Unit,
    appMode: AppMode,
    modifier: Modifier = Modifier,
    drawingCanvasViewModel: DrawingCanvasViewModel = hiltViewModel(),
) {
    val uiState by drawingCanvasViewModel.uiState.collectAsStateWithLifecycle()
    val exportResult by drawingCanvasViewModel.lastExportResult.collectAsStateWithLifecycle()
    val userMessage by drawingCanvasViewModel.userMessage.collectAsStateWithLifecycle()
    var showConfirmationDialog by rememberSaveable { mutableStateOf(false) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var resetViewNonce by rememberSaveable { mutableStateOf(0) }
    var inlineFocusedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var formulaDialogOpen by rememberSaveable { mutableStateOf(false) }
    var formulaSource by rememberSaveable { mutableStateOf("") }
    var exportDialogOpen by rememberSaveable { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            if (uiState.note.imageUriList?.isNotEmpty() == true) {
                pendingImageUri = it
                showConfirmationDialog = true
            } else {
                drawingCanvasViewModel.processAndAddImageFromPicker(it)
            }
        }
    }

    if (showConfirmationDialog) {
        ConfirmationDialog(
            onConfirm = {
                drawingCanvasViewModel.replaceImage(pendingImageUri)
                showConfirmationDialog = false
                pendingImageUri = null
            },
            onDismiss = {
                showConfirmationDialog = false
                pendingImageUri = null
            },
            title = stringResource(R.string.replace_image_title),
            text = stringResource(R.string.replace_image_text)
        )
    }
    LaunchedEffect(Unit) {
        drawingCanvasViewModel.ensureDefaultTextContainer()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, drawingCanvasViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                drawingCanvasViewModel.flushEdits()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            drawingCanvasViewModel.flushEdits()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeoNoteVisualTokens.paperBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (
                    native.action == AndroidKeyEvent.ACTION_DOWN &&
                    native.keyCode == AndroidKeyEvent.KEYCODE_V &&
                    native.isCtrlPressed
                ) {
                    val target =
                        if (inlineFocusedCell != null) DrawingCanvasViewModel.ImagePasteTarget.INLINE_TABLE_CELL
                        else DrawingCanvasViewModel.ImagePasteTarget.CANVAS_BLOCK
                    drawingCanvasViewModel.pasteImageFromClipboard(target, inlineFocusedCell)
                    true
                } else {
                    false
                }
            }
    ) {
        DrawingCanvasTopBar(
            drawingCanvasViewModel = drawingCanvasViewModel,
            appMode = appMode,
            onNavigateUp = {
                drawingCanvasViewModel.flushEdits()
                navigateUp()
            },
            onResetView = { resetViewNonce++ },
            onFormulaClick = { formulaDialogOpen = true },
            onExportClick = { exportDialogOpen = true },
            onPasteImage = {
                val target =
                    if (inlineFocusedCell != null) DrawingCanvasViewModel.ImagePasteTarget.INLINE_TABLE_CELL
                    else DrawingCanvasViewModel.ImagePasteTarget.CANVAS_BLOCK
                drawingCanvasViewModel.pasteImageFromClipboard(target, inlineFocusedCell)
            }
        )
        DrawingCanvasContent(
            drawingCanvasViewModel = drawingCanvasViewModel,
            imagePickerLauncher = imagePickerLauncher,
            onNavigateUp = {
                drawingCanvasViewModel.flushEdits()
                navigateUp()
            },
            navigateToBrushGraph = navigateToBrushGraph,
            appMode = appMode,
            resetViewNonce = resetViewNonce,
            onInlineTableCellFocused = { inlineFocusedCell = it }
        )
    }

    if (formulaDialogOpen) {
        AlertDialog(
            onDismissRequest = { formulaDialogOpen = false },
            title = { Text("Insert Formula") },
            text = {
                OutlinedTextField(
                    value = formulaSource,
                    onValueChange = { formulaSource = it },
                    label = { Text("LaTeX source") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    drawingCanvasViewModel.addFormulaBlock(formulaSource)
                    formulaSource = ""
                    formulaDialogOpen = false
                }) { Text("Insert") }
            },
            dismissButton = {
                TextButton(onClick = { formulaDialogOpen = false }) { Text("Cancel") }
            }
        )
    }

    if (exportDialogOpen) {
        AlertDialog(
            onDismissRequest = { exportDialogOpen = false },
            title = { Text("Export Note") },
            text = {
                Column {
                    Text("Formats:")
                    Text("- PDF")
                    Text("- HTML")
                    Text("- Markdown")
                    Text("- .ticnote")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    drawingCanvasViewModel.exportAllFormats()
                    exportDialogOpen = false
                }) { Text("Export") }
            },
            dismissButton = {
                TextButton(onClick = { exportDialogOpen = false }) { Text("Cancel") }
            }
        )
    }

    exportResult?.let { result ->
        ExportResultDialog(
            result = result,
            onDismiss = drawingCanvasViewModel::clearExportResult
        )
    }

    userMessage?.let { message ->
        ConfirmationDialog(
            title = "NeoNote",
            text = message,
            onConfirm = drawingCanvasViewModel::consumeUserMessage,
            onDismiss = drawingCanvasViewModel::consumeUserMessage
        )
    }
}

@Composable
fun NormalEditorScreen(
    navigateUp: () -> Unit,
    navigateToBrushGraph: () -> Unit,
    modifier: Modifier = Modifier,
    drawingCanvasViewModel: DrawingCanvasViewModel = hiltViewModel(),
) {
    DrawingCanvas(
        navigateUp = navigateUp,
        navigateToBrushGraph = navigateToBrushGraph,
        appMode = AppMode.NORMAL,
        modifier = modifier,
        drawingCanvasViewModel = drawingCanvasViewModel
    )
}

@Composable
private fun DrawingCanvasTopBar(
    drawingCanvasViewModel: DrawingCanvasViewModel,
    appMode: AppMode,
    onNavigateUp: () -> Unit,
    onResetView: () -> Unit,
    onFormulaClick: () -> Unit,
    onExportClick: () -> Unit,
    onPasteImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by drawingCanvasViewModel.uiState.collectAsStateWithLifecycle()
    val isEraserMode by drawingCanvasViewModel.isEraserMode.collectAsStateWithLifecycle()
    val selectionMode by drawingCanvasViewModel.selectionModeEnabled.collectAsStateWithLifecycle()
    var moreExpanded by rememberSaveable { mutableStateOf(false) }
    var titleState by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(uiState.note.title))
    }
    var focusedFieldEnum by rememberSaveable { mutableStateOf(FocusedFieldEnum.None) }
    val titleFocusRequester = remember { FocusRequester() }

    LaunchedEffect(focusedFieldEnum) {
        if (focusedFieldEnum == FocusedFieldEnum.Title) {
            titleFocusRequester.requestFocus()
        }
    }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(NeoNoteVisualTokens.mutedToolbarBackground)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.arrow_back_24px),
                    contentDescription = "Back"
                )
            }
            TextField(
                value = titleState,
                onValueChange = { newTitle ->
                    titleState = newTitle
                    drawingCanvasViewModel.onTitleChanged(newTitle.text)
                },
                placeholder = { Text(text = stringResource(R.string.drawing_title)) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(titleFocusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            focusedFieldEnum = FocusedFieldEnum.Title
                        }
                    },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { })
            )
            TextButton(onClick = drawingCanvasViewModel::undo) { Text("Undo") }
            TextButton(onClick = drawingCanvasViewModel::redo) { Text("Redo") }
            TextButton(onClick = onExportClick) { Text("Export") }
            TextButton(onClick = { moreExpanded = true }) { Text("More") }
            DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Reset View") },
                    onClick = {
                        moreExpanded = false
                        onResetView()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Paste") },
                    onClick = {
                        moreExpanded = false
                        onPasteImage()
                    }
                )
                if (appMode == AppMode.DEBUG) {
                    DropdownMenuItem(
                        text = { Text("Debug: Brush Graph") },
                        onClick = { moreExpanded = false }
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { drawingCanvasViewModel.setEraserMode(!isEraserMode) }) {
                Text(if (isEraserMode) "Pen" else "Eraser")
            }
            TextButton(onClick = { drawingCanvasViewModel.setSelectionMode(!selectionMode) }) {
                Text("Select/Lasso")
            }
            if (selectionMode) {
                TextButton(onClick = drawingCanvasViewModel::toggleTextContainerSelection) {
                    Text("Select Text")
                }
                TextButton(onClick = drawingCanvasViewModel::clearSelection) { Text("Clear Selection") }
            }
            TextButton(onClick = { /* text entry tool reserved */ }) { Text("Text") }
            TextButton(onClick = { drawingCanvasViewModel.insertInlineTableInTextContainer() }) { Text("Table") }
            TextButton(onClick = onPasteImage) { Text("Image") }
            TextButton(onClick = onFormulaClick) { Text("Formula") }
        }
        if (appMode == AppMode.DEBUG) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { drawingCanvasViewModel.setStylusWritesByDefault(true) }) {
                    Text("Handwriting")
                }
                TextButton(onClick = { drawingCanvasViewModel.setFingerPansByDefault(true) }) {
                    Text("Move Canvas")
                }
            }
        }
    }
}

@Composable
private fun ExportResultDialog(
    result: DrawingCanvasViewModel.ExportResult,
    onDismiss: () -> Unit,
) {
    Surface(
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Export completed", style = MaterialTheme.typography.titleMedium)
            Text("Folder:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            Text(result.directory, style = MaterialTheme.typography.bodySmall)
            Text("Generated files:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            result.generatedFiles.forEach { file ->
                Text(file, style = MaterialTheme.typography.bodySmall)
            }
            Row(modifier = Modifier.padding(top = 12.dp)) {
                OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

@OptIn(
    ExperimentalMaterial3WindowSizeClassApi::class,
    ExperimentalFoundationApi::class
)
@Composable
private fun DrawingCanvasContent(
    drawingCanvasViewModel: DrawingCanvasViewModel,
    imagePickerLauncher: ActivityResultLauncher<PickVisualMediaRequest>,
    onNavigateUp: () -> Unit,
    navigateToBrushGraph: () -> Unit,
    appMode: AppMode,
    resetViewNonce: Int,
    onInlineTableCellFocused: (Pair<Int, Int>?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current as ComponentActivity
    val windowSizeClass = calculateWindowSizeClass(activity)
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

    val canUndo by drawingCanvasViewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by drawingCanvasViewModel.canRedo.collectAsStateWithLifecycle()
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    var brushesMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var sizeMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val customBrushes by drawingCanvasViewModel.customBrushes.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        DrawingSurfaceWithTarget(
            drawingCanvasViewModel,
            appMode = appMode,
            resetViewNonce = resetViewNonce,
            onInlineTableCellFocused = onInlineTableCellFocused,
            modifier = Modifier.fillMaxSize()
        )

        val toolboxModifier = if (isCompact) {
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
        } else {
            Modifier
                .align(Alignment.TopCenter)
                .padding(8.dp)
        }

        DrawingToolbox(
            isVertical = isCompact,
            modifier = toolboxModifier,
            drawingCanvasViewModel = drawingCanvasViewModel,
            imagePickerLauncher = imagePickerLauncher,
            canUndo = canUndo,
            canRedo = canRedo,
            onUndo = drawingCanvasViewModel::undo,
            onRedo = drawingCanvasViewModel::redo,
            onExit = onNavigateUp,
            onEditActiveBrush = navigateToBrushGraph,
            onColorPickerClick = { showColorPicker = true },
        )

        BrushesDropdownMenu(
            expanded = brushesMenuExpanded,
            onDismissRequest = { brushesMenuExpanded = false },
            onBrushChange = { newBrush ->
                drawingCanvasViewModel.changeBrush(newBrush)
                brushesMenuExpanded = false
            },
            customBrushes = customBrushes
        )

        SizeDropdownMenu(
            expanded = sizeMenuExpanded,
            onDismissRequest = { sizeMenuExpanded = false },
            onSizeChange = { newSize ->
                drawingCanvasViewModel.changeBrushSize(newSize)
                sizeMenuExpanded = false
            }
        )

        ColorPickerDialog(
            showDialog = showColorPicker,
            onDismissRequest = { showColorPicker = false },
            onColorSelected = { color ->
                drawingCanvasViewModel.changeBrushColor(color)
                showColorPicker = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawingSurfaceWithTarget(
    drawingCanvasViewModel: DrawingCanvasViewModel,
    appMode: AppMode,
    resetViewNonce: Int,
    onInlineTableCellFocused: (Pair<Int, Int>?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by drawingCanvasViewModel.uiState.collectAsStateWithLifecycle()
    val exportedUri by drawingCanvasViewModel.exportedImageUri.collectAsStateWithLifecycle()
    val isEraserMode by drawingCanvasViewModel.isEraserMode.collectAsStateWithLifecycle()
    val isSelectionMode by drawingCanvasViewModel.selectionModeEnabled.collectAsStateWithLifecycle()
    val hasSelection by drawingCanvasViewModel.hasSelection.collectAsStateWithLifecycle()
    val textContainerSelected by drawingCanvasViewModel.selectedTextContainer.collectAsStateWithLifecycle()
    val strokeTranslations by drawingCanvasViewModel.strokeTranslations.collectAsStateWithLifecycle()
    val stylusWritesByDefault by drawingCanvasViewModel.stylusWritesByDefault.collectAsStateWithLifecycle()
    val fingerPanZoomEnabled by drawingCanvasViewModel.fingerPansByDefault.collectAsStateWithLifecycle()
    val strokes = remember { mutableStateListOf<Stroke>() }
    val textureStore = LocalTextureStore.current
    val cacheGen by textureStore.generation.collectAsState()
    val canvasStrokeRenderer = remember(cacheGen) {
        CanvasStrokeRenderer.create(textureStore = textureStore)
    }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val view = LocalView.current
    val activity = LocalActivity.current as ComponentActivity
    var canvasTransform by remember { mutableStateOf(CanvasTransform()) }
    var lastFingerX by remember { mutableStateOf(0f) }
    var lastFingerY by remember { mutableStateOf(0f) }
    var lastFingerDistance by remember { mutableStateOf(0f) }
    var lastCentroid by remember { mutableStateOf(GesturePoint(0f, 0f)) }
    val page = drawingCanvasViewModel.document.collectAsStateWithLifecycle().value.pages.firstOrNull()
    val tableBlock = page?.blocks?.filterIsInstance<TableBlock>()?.firstOrNull()
    val textContainer = page?.blocks?.filterIsInstance<TextContainerBlock>()?.firstOrNull()
    val imageBlocks = page?.blocks?.filterIsInstance<ImageBlock>().orEmpty()
    val formulaBlocks = page?.blocks?.filterIsInstance<FormulaBlock>().orEmpty()
    var selectedInlineCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var selectedImageIndex by remember { mutableStateOf<Int?>(null) }
    var requestTextFocusNonce by remember { mutableStateOf(0) }
    val inputRoutingConfig = DrawingInputRoutingConfig(
        stylusWritesByDefault = stylusWritesByDefault,
        fingerPansByDefault = fingerPanZoomEnabled,
        isSelectionMode = isSelectionMode,
        isEraserMode = isEraserMode
    )
    val fingerTapTracker = remember(view.context) {
        FingerTapGestureTracker(
            FingerTapThresholds(
                touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop.toFloat()
            )
        )
    }

    fun handleFingerTap(sx: Float, sy: Float) {
        when (val target = resolveFingerTapTextTarget(sx, sy, textContainer, canvasTransform)) {
            FingerTapTextTarget.FocusExisting -> requestTextFocusNonce++
            is FingerTapTextTarget.PlaceAt -> {
                drawingCanvasViewModel.placePrimaryTextContainerAt(target.docX, target.docY)
                requestTextFocusNonce++
            }
        }
    }

    val dropTarget = remember {
        createDropTarget(activity) { uri, permissions ->
            drawingCanvasViewModel.handleDroppedUri(uri, permissions)
        }
    }

    LaunchedEffect(uiState.strokes) {
        if (strokes != uiState.strokes) {
            strokes.clear()
            strokes.addAll(uiState.strokes)
        }
    }

    LaunchedEffect(
        uiState.strokes,
        uiState.note.imageUriList,
        canvasSize
    )
    {
        if (canvasSize != IntSize.Zero) {
            drawingCanvasViewModel.createExportedBitmap(
                canvasSize.width,
                canvasSize.height
            )
        }
    }
    LaunchedEffect(resetViewNonce) {
        canvasTransform = CanvasTransform()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInteropFilter { event ->
                val route = routeForDrawingCanvasFingerEvent(event, inputRoutingConfig)
                if (route != DrawingInputRoute.FingerPanZoom || selectedImageIndex != null) {
                    return@pointerInteropFilter false
                }

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastFingerX = event.x
                        lastFingerY = event.y
                        fingerTapTracker.onDown(event.x, event.y, event.eventTime)
                        true
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        fingerTapTracker.onMultiPointerGesture()
                        if (event.pointerCount >= 2) {
                            val dx = event.getX(0) - event.getX(1)
                            val dy = event.getY(0) - event.getY(1)
                            lastFingerDistance = hypot(dx, dy)
                            lastCentroid = GesturePoint(
                                x = (event.getX(0) + event.getX(1)) / 2f,
                                y = (event.getY(0) + event.getY(1)) / 2f
                            )
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (event.pointerCount >= 2) {
                            fingerTapTracker.onMultiPointerGesture()
                            val dx = event.getX(0) - event.getX(1)
                            val dy = event.getY(0) - event.getY(1)
                            val distance = hypot(dx, dy)
                            val centroid = GesturePoint(
                                x = (event.getX(0) + event.getX(1)) / 2f,
                                y = (event.getY(0) + event.getY(1)) / 2f
                            )
                            canvasTransform = CanvasTransformGesture.applyTwoFingerPinchPan(
                                current = canvasTransform,
                                previousCentroid = lastCentroid,
                                currentCentroid = centroid,
                                previousDistance = lastFingerDistance,
                                currentDistance = distance
                            )
                            lastFingerDistance = distance
                            lastCentroid = centroid
                        } else {
                            fingerTapTracker.onMove(event.x, event.y)
                            val dx = event.x - lastFingerX
                            val dy = event.y - lastFingerY
                            canvasTransform = CanvasTransformGesture.applyOneFingerPan(
                                current = canvasTransform,
                                dxScreen = dx,
                                dyScreen = dy
                            )
                            lastFingerX = event.x
                            lastFingerY = event.y
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (fingerTapTracker.onUp(event.x, event.y, event.eventTime)) {
                            handleFingerTap(event.x, event.y)
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        fingerTapTracker.onCancel()
                        true
                    }
                    else -> false
                }
            }
            .onSizeChanged { canvasSize = it }
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    event.mimeTypes().any { it.startsWith("image/") }
                },
                target = dropTarget
            )
    ) {
        DrawingSurface(
            strokes = strokes,
            strokeTranslations = strokeTranslations,
            canvasTransform = canvasTransform,
            canvasStrokeRenderer = canvasStrokeRenderer,
            onStrokesFinished = { newStrokes ->
                strokes.addAll(newStrokes)
                drawingCanvasViewModel.onStrokesFinished(newStrokes)
            },
            onErase = drawingCanvasViewModel::erase,
            onEraseStart = drawingCanvasViewModel::startErase,
            onEraseEnd = drawingCanvasViewModel::endErase,
            onStartDrag = {
                exportedUri?.let { uri ->
                    val clipData = ClipData(
                        ClipDescription(
                            "Image",
                            arrayOf(
                                "image/png"
                            )
                        ),
                        ClipData.Item(uri)
                    )
                    val dragShadowBuilder = View.DragShadowBuilder(view)
                    // While Jetpack Compose offers the `dragAndDropSource`
                    // modifier, a custom implementation using the Android View
                    // system's `startDragAndDrop` is necessary here. This is
                    // because the Ink API's drawing gestures conflict with the
                    // long-press-to-drag gesture when using the standard Compose
                    // modifier, preventing drag detection. This approach allows
                    // for a custom gesture detector to coexist with the Ink API
                    // and manually initiate the drag for seamless interoperability.
                    view.startDragAndDrop(
                        clipData,
                        dragShadowBuilder,
                        null,
                        View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ
                    )
                }
            },
            currentBrush = drawingCanvasViewModel.getCurrentBrushWithPressureCurve(),
            onGetNextBrush = drawingCanvasViewModel::getCurrentBrushWithPressureCurve,
            isEraserMode = isEraserMode,
            isSelectionMode = isSelectionMode,
            hasSelection = hasSelection,
            backgroundImageUri = uiState.note.imageUriList?.firstOrNull(),
            onSelectionLasso = { start, end ->
                drawingCanvasViewModel.selectStrokesInScreenRect(
                    startX = start.x,
                    startY = start.y,
                    endX = end.x,
                    endY = end.y,
                    canvasTransform = canvasTransform,
                    replace = false
                )
            },
            onMoveSelection = { dxScreen, dyScreen ->
                drawingCanvasViewModel.moveSelectionBy(
                    dx = screenToDocDelta(dxScreen, canvasTransform),
                    dy = screenToDocDelta(dyScreen, canvasTransform)
                )
            },
            onRawMotionEvent = drawingCanvasViewModel::onRawMotionEvent,
            inputRoutingConfig = inputRoutingConfig,
            onFingerTap = ::handleFingerTap,
            modifier = Modifier.fillMaxSize()
        )

        if (appMode == AppMode.DEBUG) {
            val metrics by drawingCanvasViewModel.inkDebugMetrics.collectAsStateWithLifecycle()
            val pressure by drawingCanvasViewModel.lastPressureUiSampled.collectAsStateWithLifecycle()
            val curve by drawingCanvasViewModel.pressureCurve.collectAsStateWithLifecycle()
            val brush by drawingCanvasViewModel.currentBrush.collectAsStateWithLifecycle()
            val scale = drawingCanvasViewModel.mapPressureToScale(pressure, curve)
            InkDebugOverlay(
                metrics = metrics,
                modifier = Modifier.align(Alignment.TopStart)
            )
            PressureTestPanel(
                baseSize = brush.size,
                pressure = pressure,
                curve = curve,
                scale = scale,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }

        if (textContainer == null) tableBlock?.let { table ->
            TableBlockEditor(
                table = table,
                onCellChange = drawingCanvasViewModel::updateTableCell,
                onSelectCell = { _, _ -> },
                onPasteImage = {
                    drawingCanvasViewModel.pasteImageFromClipboard(
                        target = DrawingCanvasViewModel.ImagePasteTarget.CANVAS_BLOCK
                    )
                },
                onToggleBold = drawingCanvasViewModel::toggleTableCellBold,
                onToggleItalic = drawingCanvasViewModel::toggleTableCellItalic,
                onToggleUnderline = drawingCanvasViewModel::toggleTableCellUnderline,
                onAppendRow = drawingCanvasViewModel::appendTableRow,
                onMove = drawingCanvasViewModel::moveTableBlockBy,
                onResize = drawingCanvasViewModel::resizeTableBlockBy,
                canvasTransform = canvasTransform,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            docToScreenX(table.x, canvasTransform).toInt(),
                            docToScreenY(table.y, canvasTransform).toInt()
                        )
                    }
            )
        }

        textContainer?.let { container ->
            val paragraphs = container.content.nodes.filterIsInstance<ParagraphNode>()
            val tableNode = container.content.nodes.filterIsInstance<TableNode>().firstOrNull()
            TextContainerEditor(
                paragraphBefore = paragraphs.getOrNull(0)?.text.orEmpty(),
                paragraphAfter = paragraphs.getOrNull(1)?.text.orEmpty(),
                tableNode = tableNode,
                canvasTransform = canvasTransform,
                onParagraphBeforeChange = { drawingCanvasViewModel.updateTextContainerParagraphAt(0, it) },
                onParagraphAfterChange = { drawingCanvasViewModel.updateTextContainerParagraphAt(1, it) },
                onInsertTable = drawingCanvasViewModel::insertInlineTableInTextContainer,
                onInlineTableCellChange = drawingCanvasViewModel::updateInlineTableCell,
                onInlineCellFocused = {
                    selectedInlineCell = it
                    onInlineTableCellFocused(it)
                },
                onInlineTableAppendRow = drawingCanvasViewModel::appendInlineTableRow,
                onContainerFocused = { focused ->
                    if (focused) drawingCanvasViewModel.setFocusedBlockId(container.id)
                },
                onInlineToggleBold = drawingCanvasViewModel::toggleInlineTableCellBold,
                onInlineToggleItalic = drawingCanvasViewModel::toggleInlineTableCellItalic,
                onInlineToggleUnderline = drawingCanvasViewModel::toggleInlineTableCellUnderline,
                onMove = drawingCanvasViewModel::moveTextContainerBy,
                isSelected = textContainerSelected,
                requestFocusNonce = requestTextFocusNonce,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            docToScreenX(container.x, canvasTransform).toInt(),
                            docToScreenY(container.y, canvasTransform).toInt()
                        )
                    }
                    .width((container.width * canvasTransform.scale).dp)
                    .height((container.height * canvasTransform.scale).dp)
            )
        }

        imageBlocks.forEachIndexed { index, image ->
            val isSelectedImage = selectedImageIndex == index
            AsyncImage(
                model = drawingCanvasViewModel.imageModelForBlock(image),
                contentDescription = "Image block $index",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            docToScreenX(image.x, canvasTransform).toInt(),
                            docToScreenY(image.y, canvasTransform).toInt()
                        )
                    }
                    .width((image.width * canvasTransform.scale).dp)
                    .height((image.height * canvasTransform.scale).dp)
                    .border(
                        if (isSelectedImage) 2.dp else 1.dp,
                        if (isSelectedImage) NeoNoteVisualTokens.selectedBorder else NeoNoteVisualTokens.subtleBorder
                    )
                    .pointerInput(index) {
                        detectTapGestures(onTap = { selectedImageIndex = index })
                    }
                    .pointerInput(index, isSelectedImage) {
                        if (isSelectedImage) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                drawingCanvasViewModel.moveImageBlockBy(
                                    index = index,
                                    dx = screenToDocDelta(dragAmount.x, canvasTransform),
                                    dy = screenToDocDelta(dragAmount.y, canvasTransform)
                                )
                            }
                        }
                    }
            )
            if (isSelectedImage) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                docToScreenX(image.x + image.width, canvasTransform).toInt() - 16,
                                docToScreenY(image.y + image.height, canvasTransform).toInt() - 16
                            )
                        }
                        .width(20.dp)
                        .height(20.dp)
                        .background(NeoNoteVisualTokens.activeCellOutline)
                        .pointerInput(index) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                drawingCanvasViewModel.resizeImageBlockBy(
                                    index = index,
                                    dw = screenToDocDelta(dragAmount.x, canvasTransform),
                                    dh = screenToDocDelta(dragAmount.y, canvasTransform)
                                )
                            }
                        }
                )
            }
        }

        formulaBlocks.forEachIndexed { index, formula ->
            Surface(
                color = NeoNoteVisualTokens.containerSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, NeoNoteVisualTokens.subtleBorder),
                modifier = Modifier
                    .offset {
                        IntOffset(
                            docToScreenX(formula.x, canvasTransform).toInt(),
                            docToScreenY(formula.y, canvasTransform).toInt()
                        )
                    }
                    .width((formula.width * canvasTransform.scale).dp)
                    .height((formula.height * canvasTransform.scale).dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "plain formula preview",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeoNoteVisualTokens.secondaryText
                    )
                    Text(
                        text = formula.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeoNoteVisualTokens.secondaryText
                    )
                }
            }
        }
        if (appMode == AppMode.DEBUG) {
            CanvasVerificationPanel(
                transform = canvasTransform,
                textContainer = textContainer,
                imageBlocks = imageBlocks,
                formulaBlocks = formulaBlocks,
                selectedInlineCell = selectedInlineCell,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun TextContainerEditor(
    paragraphBefore: String,
    paragraphAfter: String,
    tableNode: TableNode?,
    canvasTransform: CanvasTransform,
    onParagraphBeforeChange: (String) -> Unit,
    onParagraphAfterChange: (String) -> Unit,
    onInsertTable: () -> Unit,
    onInlineTableCellChange: (Int, Int, String) -> Unit,
    onInlineCellFocused: (Pair<Int, Int>?) -> Unit,
    onInlineTableAppendRow: () -> Unit,
    onContainerFocused: (Boolean) -> Unit = {},
    onInlineToggleBold: (Int, Int) -> Unit,
    onInlineToggleItalic: (Int, Int) -> Unit,
    onInlineToggleUnderline: (Int, Int) -> Unit,
    onMove: (Float, Float) -> Unit,
    isSelected: Boolean,
    requestFocusNonce: Int,
    modifier: Modifier = Modifier,
) {
            var containerFocused by remember { mutableStateOf(false) }
            val paragraphBeforeFocusRequester = remember { FocusRequester() }
            LaunchedEffect(requestFocusNonce) {
                if (requestFocusNonce > 0) {
                    paragraphBeforeFocusRequester.requestFocus()
                }
            }
            Surface(
                modifier = modifier
                    .pointerInput(Unit) {
                        if (isSelected) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onMove(
                                    screenToDocDelta(dragAmount.x, canvasTransform),
                                    screenToDocDelta(dragAmount.y, canvasTransform)
                                )
                            }
                        }
                    },
        color = NeoNoteVisualTokens.containerSurface,
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(
            width = if (containerFocused || isSelected) 1.4.dp else 0.8.dp,
            color = if (containerFocused || isSelected) NeoNoteVisualTokens.selectedBorder else NeoNoteVisualTokens.subtleBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .testTag("text-container-editor")
        ) {
            TextField(
                value = paragraphBefore,
                onValueChange = onParagraphBeforeChange,
                placeholder = { Text("Type your note…") },
                singleLine = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(paragraphBeforeFocusRequester)
                    .onFocusChanged {
                        containerFocused = it.isFocused || containerFocused
                        if (it.isFocused) onContainerFocused(true)
                    }
                    .testTag("tc-paragraph-before")
            )
            if (tableNode == null) {
                TextButton(onClick = onInsertTable, modifier = Modifier.testTag("btn-insert-inline-table")) {
                    Text("Insert Table")
                }
            } else {
                InlineTableNodeEditor(
                    table = tableNode,
                    onCellChange = onInlineTableCellChange,
                    onSelectCell = { r, c ->
                        onContainerFocused(true)
                        onInlineCellFocused(r to c)
                    },
                    onToggleBold = onInlineToggleBold,
                    onToggleItalic = onInlineToggleItalic,
                    onToggleUnderline = onInlineToggleUnderline,
                    onAppendRow = onInlineTableAppendRow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                )
            }
            TextField(
                value = paragraphAfter,
                onValueChange = onParagraphAfterChange,
                placeholder = { Text("Continue notes…") },
                singleLine = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        containerFocused = it.isFocused || containerFocused
                        if (it.isFocused) onContainerFocused(true)
                    }
                    .testTag("tc-paragraph-after")
            )
        }
    }
}

@Composable
private fun InlineTableNodeEditor(
    table: TableNode,
    onCellChange: (Int, Int, String) -> Unit,
    onSelectCell: (Int, Int) -> Unit,
    onToggleBold: (Int, Int) -> Unit,
    onToggleItalic: (Int, Int) -> Unit,
    onToggleUnderline: (Int, Int) -> Unit,
    onAppendRow: () -> Unit,
    forcedActiveCell: Pair<Int, Int>? = null,
    modifier: Modifier = Modifier,
) {
    val focusRequesters = remember(table.rows, table.columns) {
        List(table.rows * table.columns) { FocusRequester() }
    }
    var activeCell by remember(forcedActiveCell) { mutableStateOf<Pair<Int, Int>?>(forcedActiveCell) }
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        table.cells.forEachIndexed { row, rowCells ->
            Row {
                rowCells.forEachIndexed { col, cell ->
                    val index = row * table.columns + col
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(3.dp)
                            .border(
                                width = if (activeCell == row to col) 1.4.dp else 0.8.dp,
                                color = if (activeCell == row to col) NeoNoteVisualTokens.activeCellOutline
                                else NeoNoteVisualTokens.tableGridLine
                            )
                    ) {
                        TextField(
                            value = cell.text,
                            onValueChange = { onCellChange(row, col, it) },
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (cell.italic) FontStyle.Italic else FontStyle.Normal,
                                textDecoration = if (cell.underline) TextDecoration.Underline else TextDecoration.None
                            ),
                            singleLine = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("table-cell-$row-$col")
                                .focusRequester(focusRequesters[index])
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        activeCell = row to col
                                        onSelectCell(row, col)
                                    }
                                }
                                .onPreviewKeyEvent { event ->
                                    val nativeEvent = event.nativeKeyEvent
                                    if (
                                        nativeEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                                        nativeEvent.keyCode == AndroidKeyEvent.KEYCODE_TAB
                                    ) {
                                        val isLast = row == table.rows - 1 && col == table.columns - 1
                                        if (isLast) {
                                            onAppendRow()
                                        } else {
                                            val next = (index + 1).coerceAtMost(focusRequesters.lastIndex)
                                            focusRequesters[next].requestFocus()
                                        }
                                        true
                                    } else if (
                                        nativeEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                                        nativeEvent.keyCode == AndroidKeyEvent.KEYCODE_B &&
                                        nativeEvent.isCtrlPressed
                                    ) {
                                        onToggleBold(row, col)
                                        true
                                    } else if (
                                        nativeEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                                        nativeEvent.keyCode == AndroidKeyEvent.KEYCODE_I &&
                                        nativeEvent.isCtrlPressed
                                    ) {
                                        onToggleItalic(row, col)
                                        true
                                    } else if (
                                        nativeEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                                        nativeEvent.keyCode == AndroidKeyEvent.KEYCODE_U &&
                                        nativeEvent.isCtrlPressed
                                    ) {
                                        onToggleUnderline(row, col)
                                        true
                                    } else {
                                        false
                                    }
                                }
                        )
                        cell.latex?.takeIf { it.isNotBlank() }?.let { formula ->
                            Text(
                                text = "plain formula preview: $formula",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeoNoteVisualTokens.secondaryText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PressureTestPanel(
    baseSize: Float,
    pressure: Float,
    curve: Float,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    Text(
        text = buildString {
            append("pressure test\n")
            append("pressure=")
            append(String.format("%.3f", pressure))
            append(" curve=")
            append(String.format("%.2f", curve))
            append('\n')
            append("baseSize=")
            append(String.format("%.2f", baseSize))
            append(" scale=")
            append(String.format("%.2f", scale))
            append(" outSize=")
            append(String.format("%.2f", baseSize * scale))
        },
        modifier = modifier
            .background(Color(0xCC102020))
            .padding(8.dp),
        color = Color.White,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun CanvasVerificationPanel(
    transform: CanvasTransform,
    textContainer: TextContainerBlock?,
    imageBlocks: List<ImageBlock>,
    formulaBlocks: List<FormulaBlock>,
    selectedInlineCell: Pair<Int, Int>?,
    modifier: Modifier = Modifier,
) {
    Text(
        text = buildString {
            append("canvas verify\n")
            append("scale=").append(String.format("%.2f", transform.scale))
            append(" pan=(").append(String.format("%.1f", transform.panX))
            append(", ").append(String.format("%.1f", transform.panY)).append(")\n")
            textContainer?.let {
                append("text=(").append(String.format("%.1f", it.x)).append(", ")
                    .append(String.format("%.1f", it.y)).append(")\n")
            }
            imageBlocks.firstOrNull()?.let {
                append("image0=(").append(String.format("%.1f", it.x)).append(", ")
                    .append(String.format("%.1f", it.y)).append(")\n")
            }
            formulaBlocks.firstOrNull()?.let {
                append("formula0=(").append(String.format("%.1f", it.x)).append(", ")
                    .append(String.format("%.1f", it.y)).append(")\n")
            }
            append("inlineCell=").append(selectedInlineCell?.toString() ?: "none")
        },
        modifier = modifier
            .background(Color(0xCC111111))
            .padding(8.dp),
        color = Color.White,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
internal fun TableBlockEditor(
    table: TableBlock,
    onCellChange: (Int, Int, String) -> Unit,
    onSelectCell: (Int, Int) -> Unit,
    onPasteImage: () -> Unit,
    onToggleBold: (Int, Int) -> Unit,
    onToggleItalic: (Int, Int) -> Unit,
    onToggleUnderline: (Int, Int) -> Unit,
    onAppendRow: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
    canvasTransform: CanvasTransform,
    modifier: Modifier = Modifier,
) {
    // Legacy compatibility editor for standalone TableBlock.
    // Deprecated for Normal Mode after OneNote-logic reset; keep for document compatibility.
    val focusRequesters = remember(table.rows, table.columns) {
        List(table.rows * table.columns) { FocusRequester() }
    }
    Surface(
        modifier = modifier
            .width((table.width * canvasTransform.scale).dp)
            .height((table.height * canvasTransform.scale).dp)
            .pointerInput(table.id) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onMove(
                        screenToDocDelta(dragAmount.x, canvasTransform),
                        screenToDocDelta(dragAmount.y, canvasTransform)
                    )
                }
            },
        color = Color(0xCC202020),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row {
                TextButton(onClick = onPasteImage) { Text("Paste Img") }
            }
            table.cells.forEachIndexed { row, rowCells ->
                Row {
                    rowCells.forEachIndexed { col, cell ->
                        val index = row * table.columns + col
                        TextField(
                            value = cell.text,
                            onValueChange = { onCellChange(row, col, it) },
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White,
                                fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (cell.italic) FontStyle.Italic else FontStyle.Normal,
                                textDecoration = if (cell.underline) TextDecoration.Underline else TextDecoration.None
                            ),
                            singleLine = false,
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp)
                                .border(1.dp, Color(0x55FFFFFF))
                                .testTag("table-cell-$row-$col")
                                .focusRequester(focusRequesters[index])
                                .onFocusChanged { if (it.isFocused) onSelectCell(row, col) }
                                .onPreviewKeyEvent { event ->
                                    val nativeEvent = event.nativeKeyEvent
                                    if (
                                        nativeEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                                        nativeEvent.keyCode == AndroidKeyEvent.KEYCODE_TAB
                                    ) {
                                        val isLast = row == table.rows - 1 && col == table.columns - 1
                                        if (isLast) {
                                            onAppendRow()
                                        } else {
                                            val next = (index + 1).coerceAtMost(focusRequesters.lastIndex)
                                            focusRequesters[next].requestFocus()
                                        }
                                        true
                                    } else if (
                                        nativeEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                                        nativeEvent.keyCode == AndroidKeyEvent.KEYCODE_B &&
                                        nativeEvent.isCtrlPressed
                                    ) {
                                        onToggleBold(row, col)
                                        true
                                    } else if (
                                        nativeEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                                        nativeEvent.keyCode == AndroidKeyEvent.KEYCODE_I &&
                                        nativeEvent.isCtrlPressed
                                    ) {
                                        onToggleItalic(row, col)
                                        true
                                    } else if (
                                        nativeEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                                        nativeEvent.keyCode == AndroidKeyEvent.KEYCODE_U &&
                                        nativeEvent.isCtrlPressed
                                    ) {
                                        onToggleUnderline(row, col)
                                        true
                                    } else {
                                        false
                                    }
                                }
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .width(18.dp)
                    .height(18.dp)
                    .background(Color(0xFF66D9EF))
                    .pointerInput(table.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onResize(
                                screenToDocDelta(dragAmount.x, canvasTransform),
                                screenToDocDelta(dragAmount.y, canvasTransform)
                            )
                        }
                    }
            )
        }
    }
}

@Composable
private fun InkDebugOverlay(
    metrics: InkDebugMetrics,
    modifier: Modifier = Modifier,
) {
    Text(
        text = buildString {
            append("pressure=")
            append(String.format("%.2f", metrics.pressure))
            append("  tool=")
            append(metrics.toolType)
            append("  points=")
            append(metrics.pointCount)
            append('\n')
            append("tilt=")
            append(metrics.tiltRadians?.let { String.format("%.3f", it) } ?: "n/a")
            append(" rad  ")
            append("rate=")
            append(metrics.eventRateHz)
            append("Hz  finalized=")
            append(metrics.finalizedStrokeCount)
            append("  cancel=")
            append(metrics.cancelEventCount)
            append("  palm=")
            append(metrics.palmEventCount)
        },
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xCC111111))
            .padding(8.dp),
        color = Color.White,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall
    )
}

@Preview(showBackground = true)
@Composable
fun DrawingCanvasPreview() {
    CahierAppTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = "Drawing Title",
                    onValueChange = { },
                    placeholder = { Text(text = stringResource(R.string.drawing_title)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Drawing Surface")
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium
                        )
                ) {
                    Text("Toolbox Placeholder", modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Normal Editor Empty")
@Composable
private fun NormalEditorEmptyPreview() {
    CahierAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NeoNoteVisualTokens.paperBackground)
                .padding(16.dp)
        )
    }
}

@Preview(showBackground = true, name = "TextContainer Paragraph")
@Composable
private fun TextContainerParagraphPreview() {
    CahierAppTheme {
        TextContainerEditor(
            paragraphBefore = "Research notes for seminar.",
            paragraphAfter = "",
            tableNode = null,
            canvasTransform = CanvasTransform(),
            onParagraphBeforeChange = {},
            onParagraphAfterChange = {},
            onInsertTable = {},
            onInlineTableCellChange = { _, _, _ -> },
            onInlineCellFocused = {},
            onInlineTableAppendRow = {},
            onContainerFocused = {},
            onInlineToggleBold = { _, _ -> },
            onInlineToggleItalic = { _, _ -> },
            onInlineToggleUnderline = { _, _ -> },
            onMove = { _, _ -> },
            isSelected = false,
            requestFocusNonce = 0,
            modifier = Modifier
                .padding(20.dp)
                .width(360.dp)
                .height(240.dp)
        )
    }
}

@Preview(showBackground = true, name = "TextContainer Inline Table")
@Composable
private fun TextContainerInlineTablePreview() {
    CahierAppTheme {
        val table = TableNode(
            rows = 2,
            columns = 3,
            cells = listOf(
                listOf(
                    com.example.cahier.core.document.TableCell(text = "Topic"),
                    com.example.cahier.core.document.TableCell(text = "Claim"),
                    com.example.cahier.core.document.TableCell(text = "Evidence")
                ),
                listOf(
                    com.example.cahier.core.document.TableCell(text = "Hydrology"),
                    com.example.cahier.core.document.TableCell(text = "Rainfall rises"),
                    com.example.cahier.core.document.TableCell(text = "Field notebook 03")
                )
            )
        )
        TextContainerEditor(
            paragraphBefore = "Draft outline:",
            paragraphAfter = "Add final inference below.",
            tableNode = table,
            canvasTransform = CanvasTransform(),
            onParagraphBeforeChange = {},
            onParagraphAfterChange = {},
            onInsertTable = {},
            onInlineTableCellChange = { _, _, _ -> },
            onInlineCellFocused = {},
            onInlineTableAppendRow = {},
            onContainerFocused = {},
            onInlineToggleBold = { _, _ -> },
            onInlineToggleItalic = { _, _ -> },
            onInlineToggleUnderline = { _, _ -> },
            onMove = { _, _ -> },
            isSelected = false,
            requestFocusNonce = 0,
            modifier = Modifier
                .padding(20.dp)
                .width(440.dp)
                .height(320.dp)
        )
    }
}

@Preview(showBackground = true, name = "Active Cell")
@Composable
private fun ActiveCellPreview() {
    CahierAppTheme {
        val table = TableNode(
            rows = 2,
            columns = 2,
            cells = listOf(
                listOf(
                    com.example.cahier.core.document.TableCell(text = "A1"),
                    com.example.cahier.core.document.TableCell(text = "B1")
                ),
                listOf(
                    com.example.cahier.core.document.TableCell(text = "A2"),
                    com.example.cahier.core.document.TableCell(text = "B2")
                )
            )
        )
        InlineTableNodeEditor(
            table = table,
            onCellChange = { _, _, _ -> },
            onSelectCell = { _, _ -> },
            onToggleBold = { _, _ -> },
            onToggleItalic = { _, _ -> },
            onToggleUnderline = { _, _ -> },
            onAppendRow = {},
            forcedActiveCell = 0 to 1,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, name = "Selected TextContainer")
@Composable
private fun SelectedTextContainerPreview() {
    CahierAppTheme {
        Surface(
            color = NeoNoteVisualTokens.containerSurface,
            border = androidx.compose.foundation.BorderStroke(1.4.dp, NeoNoteVisualTokens.selectedBorder),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .padding(20.dp)
                .width(420.dp)
                .height(220.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Selected note container", color = NeoNoteVisualTokens.normalText)
                Text("Drag to move on canvas", color = NeoNoteVisualTokens.secondaryText)
            }
        }
    }
}
