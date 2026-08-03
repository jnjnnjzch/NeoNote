package com.neonote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonote.engine.JsonFilePersistenceStore
import com.neonote.model.EditorTool
import com.neonote.model.NotePage
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val AutoSaveDebounceMillis = 1_200L
private val NeoPurple = Color(0xFF5B3FD1)
private val NeoPurpleSoft = Color(0xFFEDE9FE)
private val WorkspaceBackground = Color(0xFFF4F3F8)
private val RailBackground = Color(0xFFF8F7FC)
private val InkColor = Color(0xFF171529)

@Composable
internal fun NeoNoteEditorScreen(controller: NeoNoteEditorController) {
    val state = controller.state
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(WorkspaceBackground),
    ) {
        val expandedNavigation = maxWidth >= 720.dp

        Column(modifier = Modifier.fillMaxSize()) {
            WorkspaceTopBar(
                title = state.document.title,
                saveLabel = controller.persistenceStatus.toFriendlySaveLabel(),
                canUndo = controller.canUndo,
                canRedo = controller.canRedo,
                onUndo = controller::undo,
                onRedo = controller::redo,
                onResetView = controller::resetViewport,
                onSaveNow = { coroutineScope.launch { controller.saveDocument(persistenceStore) } },
            )
            HorizontalDivider(color = Color(0xFFE7E3F0))

            Row(modifier = Modifier.fillMaxSize()) {
                if (expandedNavigation) {
                    PageRail(
                        pages = state.document.pages,
                        currentPageId = state.currentPageId,
                        onPageSelected = controller::switchPage,
                        onAddPage = controller::addPage,
                    )
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp),
                        color = Color(0xFFE7E3F0),
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    InfiniteCanvasViewport(
                        controller = controller,
                        selectionMode = state.currentTool == EditorTool.Selection,
                        modifier = Modifier.fillMaxSize(),
                    )

                    CanvasMetaChip(
                        pageNumber = controller.currentPageNumber,
                        pageCount = controller.pageCount,
                        zoomPercent = (state.viewport.zoomScale * 100f).roundToInt(),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                    )

                    if (state.selection.selectedRefs.isNotEmpty()) {
                        SelectionActionBar(
                            selectedCount = state.selection.selectedRefs.size,
                            onDelete = controller::deleteSelection,
                            onClear = controller::clearSelection,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp),
                        )
                    }

                    ToolDock(
                        selectedTool = state.currentTool,
                        onToolSelected = controller::setTool,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp),
                    )

                    if (!expandedNavigation) {
                        CompactPageSwitcher(
                            pageNumber = controller.currentPageNumber,
                            pageCount = controller.pageCount,
                            canGoPrevious = controller.canSwitchToPreviousPage,
                            canGoNext = controller.canSwitchToNextPage,
                            onPrevious = controller::switchToPreviousPage,
                            onNext = controller::switchToNextPage,
                            onAdd = controller::addPage,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 12.dp, bottom = 96.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceTopBar(
    title: String,
    saveLabel: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onResetView: () -> Unit,
    onSaveNow: () -> Unit,
) {
    Surface(color = Color.White, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(11.dp),
                color = NeoPurple,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "N",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title.ifBlank { "Untitled note" },
                    color = InkColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (saveLabel == "Saved") Color(0xFF16A34A) else Color(0xFFF59E0B)),
                    )
                    Text(
                        text = saveLabel,
                        modifier = Modifier.padding(start = 6.dp),
                        color = Color(0xFF706B80),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            HeaderAction(
                label = "↶",
                description = "Undo",
                enabled = canUndo,
                onClick = onUndo,
            )
            HeaderAction(
                label = "↷",
                description = "Redo",
                enabled = canRedo,
                onClick = onRedo,
            )
            HeaderAction(
                label = "100%",
                description = "Reset canvas view",
                onClick = onResetView,
            )
            HeaderAction(
                label = "Save",
                description = "Save note now",
                onClick = onSaveNow,
            )
        }
    }
}

@Composable
private fun HeaderAction(
    label: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = description },
        colors = ButtonDefaults.textButtonColors(
            contentColor = NeoPurple,
            disabledContentColor = Color(0xFFB7B2C4),
        ),
    ) {
        Text(text = label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PageRail(
    pages: List<NotePage>,
    currentPageId: String?,
    onPageSelected: (String) -> Unit,
    onAddPage: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(124.dp)
            .fillMaxHeight(),
        color = RailBackground,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "PAGES",
                modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 10.dp),
                color = Color(0xFF8A8498),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(
                    items = pages,
                    key = { _, page -> page.id },
                ) { index, page ->
                    PageRailItem(
                        pageNumber = index + 1,
                        selected = page.id == currentPageId,
                        objectCount = page.canvas.objects.size + page.canvas.inkLayer.strokes.size,
                        onClick = { onPageSelected(page.id) },
                    )
                }
            }
            TextButton(
                onClick = onAddPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                Text("＋ New page", color = NeoPurple, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PageRailItem(
    pageNumber: Int,
    selected: Boolean,
    objectCount: Int,
    onClick: () -> Unit,
) {
    val background = if (selected) NeoPurpleSoft else Color.White
    val border = if (selected) NeoPurple else Color(0xFFE5E1EC)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "Open page $pageNumber"
            },
        shape = RoundedCornerShape(12.dp),
        color = background,
        border = androidx.compose.foundation.BorderStroke(1.dp, border),
        shadowElevation = if (selected) 2.dp else 0.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Text(
                text = "Page $pageNumber",
                color = if (selected) Color(0xFF3F2A9A) else InkColor,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (objectCount == 0) "Blank" else "$objectCount items",
                color = Color(0xFF817A8E),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun ToolDock(
    selectedTool: EditorTool,
    onToolSelected: (EditorTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFAFFFFFF),
        shadowElevation = 12.dp,
        tonalElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E1EC)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolDockItem("✎", "Pen", selectedTool == EditorTool.Pen) { onToolSelected(EditorTool.Pen) }
            ToolDockItem("T", "Text", selectedTool == EditorTool.Text) { onToolSelected(EditorTool.Text) }
            ToolDockItem("⌁", "Select", selectedTool == EditorTool.Selection) { onToolSelected(EditorTool.Selection) }
            ToolDockItem("⌫", "Erase", selectedTool == EditorTool.Eraser) { onToolSelected(EditorTool.Eraser) }
        }
    }
}

@Composable
private fun ToolDockItem(
    symbol: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) NeoPurple else Color.Transparent
    val foreground = if (selected) Color.White else Color(0xFF544E61)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(background)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "$label tool"
            }
            .padding(horizontal = 16.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(text = symbol, color = foreground, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = foreground, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CanvasMetaChip(
    pageNumber: Int,
    pageCount: Int,
    zoomPercent: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xEFFFFFFF),
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE6E2ED)),
    ) {
        Text(
            text = "Page $pageNumber of $pageCount  ·  $zoomPercent%",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = Color(0xFF716B7E),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD8D2E4)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$selectedCount selected",
                modifier = Modifier.padding(horizontal = 10.dp),
                color = InkColor,
                style = MaterialTheme.typography.labelLarge,
            )
            TextButton(onClick = onDelete) {
                Text("Delete", color = Color(0xFFB42318), fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onClear) {
                Text("Done", color = NeoPurple, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CompactPageSwitcher(
    pageNumber: Int,
    pageCount: Int,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xF7FFFFFF),
        shadowElevation = 7.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E1EC)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onPrevious, enabled = canGoPrevious) { Text("‹") }
            Text(
                text = "$pageNumber / $pageCount",
                color = InkColor,
                style = MaterialTheme.typography.labelMedium,
            )
            TextButton(onClick = onNext, enabled = canGoNext) { Text("›") }
            TextButton(onClick = onAdd) { Text("＋") }
        }
    }
}

private fun String.toFriendlySaveLabel(): String = when {
    startsWith("Saved") -> "Saved"
    startsWith("Loaded") -> "Loaded"
    startsWith("No local document") -> "New local note"
    equals("Not saved", ignoreCase = true) -> "Saving locally"
    else -> "Saving locally"
}
