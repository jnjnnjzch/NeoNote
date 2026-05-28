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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
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
    val document by drawingCanvasViewModel.document.collectAsStateWithLifecycle()
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
    LaunchedEffect(document.pages.size) {
        drawingCanvasViewModel.ensureDefaultTableBlock()
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

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
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
        TextButton(onClick = drawingCanvasViewModel::exportAllFormats) {
            Text("Export")
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
    val isEraserMode by drawingCanvasViewModel.isEraserMode.collectAsStateWithLifecycle()
    val strokeTranslations by drawingCanvasViewModel.strokeTranslations.collectAsStateWithLifecycle()
    val strokes = remember { mutableStateListOf<Stroke>() }
    val textureStore = LocalTextureStore.current
    val cacheGen by textureStore.generation.collectAsState()
    val canvasStrokeRenderer = remember(cacheGen) {
        CanvasStrokeRenderer.create(textureStore = textureStore)
    }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val view = LocalView.current
    val activity = LocalActivity.current as ComponentActivity
    val tableBlock = drawingCanvasViewModel.document.collectAsStateWithLifecycle().value
        .pages
        .firstOrNull()
        ?.blocks
        ?.filterIsInstance<TableBlock>()
        ?.firstOrNull()

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
            currentBrush = currentBrush,
            onGetNextBrush = drawingCanvasViewModel::getCurrentBrush,
            isEraserMode = isEraserMode,
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

        tableBlock?.let { table ->
            TableBlockEditor(
                table = table,
                onCellChange = drawingCanvasViewModel::updateTableCell,
                onToggleBold = drawingCanvasViewModel::toggleTableCellBold,
                onToggleItalic = drawingCanvasViewModel::toggleTableCellItalic,
                onToggleUnderline = drawingCanvasViewModel::toggleTableCellUnderline,
                onAppendRow = drawingCanvasViewModel::appendTableRow,
                onMove = drawingCanvasViewModel::moveTableBlockBy,
                onResize = drawingCanvasViewModel::resizeTableBlockBy,
                modifier = Modifier
                    .offset { IntOffset(table.x.toInt(), table.y.toInt()) }
            )
        }
    }
}

@Composable
private fun TableBlockEditor(
    table: TableBlock,
    onCellChange: (Int, Int, String) -> Unit,
    onToggleBold: (Int, Int) -> Unit,
    onToggleItalic: (Int, Int) -> Unit,
    onToggleUnderline: (Int, Int) -> Unit,
    onAppendRow: () -> Unit,
    onMove: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequesters = remember(table.rows, table.columns) {
        List(table.rows * table.columns) { FocusRequester() }
    }
    Surface(
        modifier = modifier
            .width(table.width.dp)
            .height(table.height.dp)
            .pointerInput(table.id) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onMove(dragAmount.x, dragAmount.y)
                }
            },
        color = Color(0xCC202020),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
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
                                .focusRequester(focusRequesters[index])
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.Tab) {
                                        val isLast = row == table.rows - 1 && col == table.columns - 1
                                        if (isLast) {
                                            onAppendRow()
                                        } else {
                                            val next = (index + 1).coerceAtMost(focusRequesters.lastIndex)
                                            focusRequesters[next].requestFocus()
                                        }
                                        true
                                    } else if (
                                        event.type == KeyEventType.KeyDown &&
                                        event.key == Key.B &&
                                        event.isCtrlPressed
                                    ) {
                                        onToggleBold(row, col)
                                        true
                                    } else if (
                                        event.type == KeyEventType.KeyDown &&
                                        event.key == Key.I &&
                                        event.isCtrlPressed
                                    ) {
                                        onToggleItalic(row, col)
                                        true
                                    } else if (
                                        event.type == KeyEventType.KeyDown &&
                                        event.key == Key.U &&
                                        event.isCtrlPressed
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
                            onResize(dragAmount.x, dragAmount.y)
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
