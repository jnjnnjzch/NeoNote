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
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.view.KeyEvent as AndroidKeyEvent
import android.view.MotionEvent
import android.view.View
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cahier.R
import com.example.cahier.core.ui.ColorPickerDialog
import com.example.cahier.core.ui.ConfirmationDialog
import com.example.cahier.core.ui.DrawingSurface
import com.example.cahier.core.ui.FocusedFieldEnum
import com.example.cahier.core.ui.LocalTextureStore
import com.example.cahier.core.ui.theme.CahierAppTheme
import com.example.cahier.core.utils.createDropTarget
import com.example.cahier.core.document.TableBlock
import com.example.cahier.core.document.TableNode
import com.example.cahier.core.document.TextContainerBlock
import com.example.cahier.core.document.ParagraphNode
import com.example.cahier.features.drawing.CanvasTransformMapper.docToScreenX
import com.example.cahier.features.drawing.CanvasTransformMapper.docToScreenY
import com.example.cahier.features.drawing.CanvasTransformMapper.screenToDocDelta
import com.example.cahier.features.drawing.viewmodel.DrawingCanvasViewModel


@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3WindowSizeClassApi::class
)
@Composable
fun DrawingCanvas(
    navigateUp: () -> Unit,
    navigateToBrushGraph: () -> Unit,
    modifier: Modifier = Modifier,
    drawingCanvasViewModel: DrawingCanvasViewModel = hiltViewModel(),
) {
    val uiState by drawingCanvasViewModel.uiState.collectAsStateWithLifecycle()
    var showConfirmationDialog by rememberSaveable { mutableStateOf(false) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        DrawingCanvasTopBar(drawingCanvasViewModel)
        DrawingCanvasContent(
            drawingCanvasViewModel = drawingCanvasViewModel,
            imagePickerLauncher = imagePickerLauncher,
            onNavigateUp = navigateUp,
            navigateToBrushGraph = navigateToBrushGraph
        )
    }
}

@Composable
private fun DrawingCanvasTopBar(
    drawingCanvasViewModel: DrawingCanvasViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by drawingCanvasViewModel.uiState.collectAsStateWithLifecycle()
    val isEraserMode by drawingCanvasViewModel.isEraserMode.collectAsStateWithLifecycle()
    val selectionMode by drawingCanvasViewModel.selectionModeEnabled.collectAsStateWithLifecycle()
    val stylusWrites by drawingCanvasViewModel.stylusWritesByDefault.collectAsStateWithLifecycle()
    val fingerPans by drawingCanvasViewModel.fingerPansByDefault.collectAsStateWithLifecycle()
    val pressureCurve by drawingCanvasViewModel.pressureCurve.collectAsStateWithLifecycle()
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
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            TextButton(onClick = drawingCanvasViewModel::exportAllFormats) { Text("Export") }
            TextButton(onClick = drawingCanvasViewModel::generateStressDocument) { Text("Stress") }
            TextButton(onClick = { drawingCanvasViewModel.setEraserMode(!isEraserMode) }) {
                Text(if (isEraserMode) "Pen" else "Eraser")
            }
            TextButton(onClick = { drawingCanvasViewModel.setSelectionMode(!selectionMode) }) {
                Text(if (selectionMode) "Select:On" else "Select:Off")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { drawingCanvasViewModel.setStylusWritesByDefault(!stylusWrites) }) {
                Text(if (stylusWrites) "Stylus:Write" else "Stylus:Tool")
            }
            TextButton(onClick = { drawingCanvasViewModel.setFingerPansByDefault(!fingerPans) }) {
                Text(if (fingerPans) "Finger:Pan" else "Finger:Touch")
            }
            Text(
                text = "Pressure ${String.format("%.2f", pressureCurve)}",
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = pressureCurve,
                onValueChange = drawingCanvasViewModel::setPressureCurve,
                valueRange = 0.5f..2.0f,
                modifier = Modifier.weight(1f)
            )
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
    modifier: Modifier = Modifier,
) {
    val uiState by drawingCanvasViewModel.uiState.collectAsStateWithLifecycle()
    val exportedUri by drawingCanvasViewModel.exportedImageUri.collectAsStateWithLifecycle()
    val currentBrush by drawingCanvasViewModel.currentBrush.collectAsStateWithLifecycle()
    val pressureCurve by drawingCanvasViewModel.pressureCurve.collectAsStateWithLifecycle()
    val lastPressure by drawingCanvasViewModel.lastPressureUiSampled.collectAsStateWithLifecycle()
    val isEraserMode by drawingCanvasViewModel.isEraserMode.collectAsStateWithLifecycle()
    val isSelectionMode by drawingCanvasViewModel.selectionModeEnabled.collectAsStateWithLifecycle()
    val strokeTranslations by drawingCanvasViewModel.strokeTranslations.collectAsStateWithLifecycle()
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
    val tableBlock = drawingCanvasViewModel.document.collectAsStateWithLifecycle().value
        .pages
        .firstOrNull()
        ?.blocks
        ?.filterIsInstance<TableBlock>()
        ?.firstOrNull()
    val textContainer = drawingCanvasViewModel.document.collectAsStateWithLifecycle().value
        .pages
        .firstOrNull()
        ?.blocks
        ?.filterIsInstance<TextContainerBlock>()
        ?.firstOrNull()
    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val context = LocalContext.current

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInteropFilter { event ->
                if (!fingerPanZoomEnabled) return@pointerInteropFilter false
                val toolType = event.getToolType(0)
                val isFinger = toolType == MotionEvent.TOOL_TYPE_FINGER
                if (!isFinger) return@pointerInteropFilter false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastFingerX = event.x
                        lastFingerY = event.y
                        true
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        if (event.pointerCount >= 2) {
                            val dx = event.getX(0) - event.getX(1)
                            val dy = event.getY(0) - event.getY(1)
                            lastFingerDistance = kotlin.math.sqrt(dx * dx + dy * dy)
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (event.pointerCount >= 2) {
                            val dx = event.getX(0) - event.getX(1)
                            val dy = event.getY(0) - event.getY(1)
                            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                            if (lastFingerDistance > 0f) {
                                val ratio = (distance / lastFingerDistance).coerceIn(0.5f, 2.0f)
                                val newScale = (canvasTransform.scale * ratio).coerceIn(0.5f, 4.0f)
                                canvasTransform = canvasTransform.copy(scale = newScale)
                            }
                            lastFingerDistance = distance
                        } else {
                            val dx = event.x - lastFingerX
                            val dy = event.y - lastFingerY
                            canvasTransform = canvasTransform.copy(
                                panX = canvasTransform.panX + dx,
                                panY = canvasTransform.panY + dy
                            )
                            lastFingerX = event.x
                            lastFingerY = event.y
                        }
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
            backgroundImageUri = uiState.note.imageUriList?.firstOrNull(),
            onRawMotionEvent = drawingCanvasViewModel::onRawMotionEvent,
            modifier = Modifier.fillMaxSize()
        )

        InkDebugOverlay(
            metrics = drawingCanvasViewModel.inkDebugMetrics.collectAsStateWithLifecycle().value,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        )

        PressureTestPanel(
            baseSize = currentBrush.size,
            pressure = lastPressure,
            curve = pressureCurve,
            scale = drawingCanvasViewModel.mapPressureToScale(lastPressure, pressureCurve),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        )

        if (textContainer == null) tableBlock?.let { table ->
            TableBlockEditor(
                table = table,
                onCellChange = drawingCanvasViewModel::updateTableCell,
                onSelectCell = { r, c -> selectedCell = r to c },
                onPasteImage = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip
                    val uri = clip?.getItemAt(0)?.uri?.toString()
                    if (uri != null) {
                        val asset = drawingCanvasViewModel.importImageFromUriToAssets(uri)
                        if (asset != null) {
                            val target = selectedCell
                            if (target != null) {
                                drawingCanvasViewModel.attachImageAssetToCell(target.first, target.second, asset)
                            } else {
                                drawingCanvasViewModel.addImageBlock(asset)
                            }
                        }
                    }
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
                onInlineTableAppendRow = drawingCanvasViewModel::appendInlineTableRow,
                onInlineToggleBold = drawingCanvasViewModel::toggleInlineTableCellBold,
                onInlineToggleItalic = drawingCanvasViewModel::toggleInlineTableCellItalic,
                onInlineToggleUnderline = drawingCanvasViewModel::toggleInlineTableCellUnderline,
                onMove = drawingCanvasViewModel::moveTextContainerBy,
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
    onInlineTableAppendRow: () -> Unit,
    onInlineToggleBold: (Int, Int) -> Unit,
    onInlineToggleItalic: (Int, Int) -> Unit,
    onInlineToggleUnderline: (Int, Int) -> Unit,
    onMove: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onMove(
                        screenToDocDelta(dragAmount.x, canvasTransform),
                        screenToDocDelta(dragAmount.y, canvasTransform)
                    )
                }
            },
        color = Color(0xFFFCFCFB),
        tonalElevation = 1.dp
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
                    onSelectCell = { _, _ -> },
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
    modifier: Modifier = Modifier,
) {
    val focusRequesters = remember(table.rows, table.columns) {
        List(table.rows * table.columns) { FocusRequester() }
    }
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        table.cells.forEachIndexed { row, rowCells ->
            Row {
                rowCells.forEachIndexed { col, cell ->
                    val index = row * table.columns + col
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
                            .weight(1f)
                            .padding(2.dp)
                            .border(1.dp, Color(0xFFD8DDE3))
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
