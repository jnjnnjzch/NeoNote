package com.neonote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.neonote.engine.DocumentSummary
import com.neonote.model.EditorTool
import com.neonote.model.EraserMode
import com.neonote.model.InkBrush
import com.neonote.model.NotePage

internal val NeoPurple = Color(0xFF5B3FD1)
internal val NeoPurpleSoft = Color(0xFFEDE9FE)

@Composable
internal fun WorkspaceTopBar(
    title: String,
    favorite: Boolean,
    saveLabel: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onLibrary: () -> Unit,
    onTitleChange: (String) -> Unit,
    onFavorite: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onResetView: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onSettings: () -> Unit,
    onSaveNow: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderAction("☰", "Open document library", onClick = onLibrary)
            Surface(Modifier.size(38.dp), RoundedCornerShape(11.dp), color = NeoPurple) {
                Box(contentAlignment = Alignment.Center) {
                    Text("N", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = title,
                        onValueChange = onTitleChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        cursorBrush = SolidColor(NeoPurple),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box {
                                if (title.isBlank()) Text("Untitled note", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                inner()
                            }
                        },
                    )
                    HeaderAction(if (favorite) "★" else "☆", "Toggle favorite", onClick = onFavorite)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(7.dp).clip(CircleShape)
                            .background(if (saveLabel == "Saved") Color(0xFF16A34A) else Color(0xFFF59E0B)),
                    )
                    Text(
                        saveLabel,
                        Modifier.padding(start = 6.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            HeaderAction("↶", "Undo", enabled = canUndo, onClick = onUndo)
            HeaderAction("↷", "Redo", enabled = canRedo, onClick = onRedo)
            HeaderAction("100%", "Reset canvas view", onClick = onResetView)
            HeaderAction("Export", "Export NeoNote archive", onClick = onExport)
            HeaderAction("Share", "Share NeoNote archive", onClick = onShare)
            HeaderAction("⚙", "Settings", onClick = onSettings)
            HeaderAction("Save", "Save now", onClick = onSaveNow)
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
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        ),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) { Text(label, fontWeight = FontWeight.SemiBold) }
}

@Composable
internal fun PageRail(
    pages: List<NotePage>,
    currentPageId: String?,
    onPageSelected: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAddPage: () -> Unit,
) {
    Surface(
        modifier = Modifier.width(176.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "PAGES",
                Modifier.padding(start = 16.dp, top = 18.dp, bottom = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
                    PageRailItem(
                        page = page,
                        pageNumber = index + 1,
                        selected = page.id == currentPageId,
                        canDelete = pages.size > 1,
                        canMoveUp = index > 0,
                        canMoveDown = index < pages.lastIndex,
                        onClick = { onPageSelected(page.id) },
                        onRename = { onRename(page.id, it) },
                        onDuplicate = { onDuplicate(page.id) },
                        onDelete = { onDelete(page.id) },
                        onMoveUp = { onMove(index, index - 1) },
                        onMoveDown = { onMove(index, index + 1) },
                    )
                }
            }
            TextButton(onClick = onAddPage, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Text("＋ New page", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PageRailItem(
    page: NotePage,
    pageNumber: Int,
    selected: Boolean,
    canDelete: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            if (selected) {
                BasicTextField(
                    value = page.title,
                    onValueChange = { onRename(it.take(80)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (page.title.isBlank()) Text("Page $pageNumber", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inner()
                    },
                )
            } else {
                Text(
                    page.title.ifBlank { "Page $pageNumber" },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                if (page.canvas.objects.isEmpty() && page.canvas.inkLayer.strokes.isEmpty()) "Blank"
                else "${page.canvas.objects.size + page.canvas.inkLayer.strokes.size} items",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            if (selected) {
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    MiniAction("↑", "Move page up", canMoveUp, onMoveUp)
                    MiniAction("↓", "Move page down", canMoveDown, onMoveDown)
                    MiniAction("⧉", "Duplicate page", true, onDuplicate)
                    MiniAction("×", "Delete page", canDelete, onDelete)
                }
            }
        }
    }
}

@Composable
private fun MiniAction(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = description },
        contentPadding = PaddingValues(horizontal = 6.dp),
    ) { Text(label) }
}

@Composable
internal fun ToolDock(
    selectedTool: EditorTool,
    onToolSelected: (EditorTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
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
private fun ToolDockItem(symbol: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier.clip(RoundedCornerShape(15.dp)).background(background).clickable(onClick = onClick)
            .semantics { role = Role.Button; contentDescription = "$label tool" }
            .padding(horizontal = 16.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(symbol, color = foreground, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(label, color = foreground, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun ToolContextPanel(
    controller: NeoNoteEditorController,
    preferences: NeoNotePreferences,
    onPreferencesChange: (NeoNotePreferences) -> Unit,
    onInsertFloatingImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = controller.state
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    ) {
        when (state.currentTool) {
            EditorTool.Pen -> Row(
                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PenColor(Color(0xFF171326), "Black") { controller.setInkColor(0xFF171326.toInt()) }
                PenColor(Color(0xFF5B3FD1), "Purple") { controller.setInkColor(0xFF5B3FD1.toInt()) }
                PenColor(Color(0xFF1565C0), "Blue") { controller.setInkColor(0xFF1565C0.toInt()) }
                PenColor(Color(0xFFC62828), "Red") { controller.setInkColor(0xFFC62828.toInt()) }
                ContextAction("Pen", state.inkSettings.brush == InkBrush.Pen) {
                    controller.setInkBrush(InkBrush.Pen)
                    onPreferencesChange(preferences.copy(defaultBrush = InkBrush.Pen))
                }
                ContextAction("Highlighter", state.inkSettings.brush == InkBrush.Highlighter) {
                    controller.setInkBrush(InkBrush.Highlighter)
                    onPreferencesChange(preferences.copy(defaultBrush = InkBrush.Highlighter))
                }
                Text("Width", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = state.inkSettings.width,
                    onValueChange = {
                        controller.setInkWidth(it)
                        onPreferencesChange(preferences.copy(defaultInkWidth = it))
                    },
                    valueRange = 1f..24f,
                    modifier = Modifier.width(150.dp),
                )
                Text("Opacity", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = state.inkSettings.opacity,
                    onValueChange = {
                        controller.setInkOpacity(it)
                        onPreferencesChange(preferences.copy(defaultInkOpacity = it))
                    },
                    valueRange = 0.1f..1f,
                    modifier = Modifier.width(120.dp),
                )
                Text("Pressure", style = MaterialTheme.typography.labelMedium)
                Switch(
                    checked = state.inkSettings.pressureEnabled,
                    onCheckedChange = {
                        controller.setPressureEnabled(it)
                        onPreferencesChange(preferences.copy(pressureEnabled = it))
                    },
                )
                ContextAction("Place image", false, onInsertFloatingImage)
            }
            EditorTool.Eraser -> Row(
                Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ContextAction("Segment", state.eraserMode == EraserMode.Segment) {
                    controller.setEraserMode(EraserMode.Segment)
                    onPreferencesChange(preferences.copy(eraserMode = EraserMode.Segment))
                }
                ContextAction("Whole stroke", state.eraserMode == EraserMode.Stroke) {
                    controller.setEraserMode(EraserMode.Stroke)
                    onPreferencesChange(preferences.copy(eraserMode = EraserMode.Stroke))
                }
            }
            EditorTool.Selection -> Row(
                Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("${state.selection.selectedRefs.size} selected", Modifier.padding(10.dp))
                ContextAction("Duplicate", false, controller::duplicateSelection)
                ContextAction("80%", false) { controller.scaleSelection(0.8f) }
                ContextAction("125%", false) { controller.scaleSelection(1.25f) }
                ContextAction("Front", false, controller::bringSelectionToFront)
                ContextAction("Back", false, controller::sendSelectionToBack)
                ContextAction("Lock", false) { controller.setSelectionLocked(true) }
                ContextAction("Unlock", false) { controller.setSelectionLocked(false) }
                ContextAction("Delete", false, controller::deleteSelection)
                ContextAction("Clear", false, controller::clearSelection)
            }
            EditorTool.Text -> Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tap the canvas to create a text box. Formatting appears above the active box.")
                Spacer(Modifier.width(12.dp))
                ContextAction("Place image", false, onInsertFloatingImage)
            }
        }
    }
}

@Composable
private fun ContextAction(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier.clip(RoundedCornerShape(9.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun PenColor(color: Color, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(color).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$description ink"; role = Role.Button },
    )
}

@Composable
internal fun CanvasMetaChip(pageNumber: Int, pageCount: Int, zoomPercent: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Text(
            "Page $pageNumber of $pageCount · $zoomPercent%",
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
internal fun CompactPageSwitcher(
    pageNumber: Int,
    pageCount: Int,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniAction("‹", "Previous page", canGoPrevious, onPrevious)
            Text("$pageNumber/$pageCount", Modifier.padding(horizontal = 8.dp))
            MiniAction("›", "Next page", canGoNext, onNext)
            MiniAction("+", "Add page", true, onAdd)
        }
    }
}

@Composable
internal fun DocumentLibraryDialog(
    documents: List<DocumentSummary>,
    showTrash: Boolean,
    currentDocumentId: String,
    onToggleTrash: () -> Unit,
    onCreate: (String) -> Unit,
    onOpen: (String) -> Unit,
    onTrash: (String) -> Unit,
    onRestore: (String) -> Unit,
    onDeleteForever: (String) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var newTitle by remember { mutableStateOf("") }
    val visible = documents.filter { it.isTrashed == showTrash && it.title.contains(query, ignoreCase = true) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 18.dp,
        ) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (showTrash) "Trash" else "NeoNote library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onToggleTrash) { Text(if (showTrash) "Documents" else "Trash") }
                    TextButton(onClick = onImport) { Text("Import") }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { inner -> if (query.isBlank()) Text("Search notes…", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() },
                )
                if (!showTrash) {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it.take(120) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(10.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            decorationBox = { inner -> if (newTitle.isBlank()) Text("New note title", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() },
                        )
                        TextButton(onClick = { onCreate(newTitle.ifBlank { "Untitled Note" }); newTitle = "" }) { Text("Create") }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(visible, key = { _, item -> "${item.isTrashed}:${item.id}" }) { _, item ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (item.id == currentDocumentId && !showTrash) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text((if (item.isFavorite) "★ " else "") + item.title.ifBlank { "Untitled Note" }, fontWeight = FontWeight.SemiBold)
                                    Text("${item.pageCount} pages", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                }
                                if (showTrash) {
                                    TextButton(onClick = { onRestore(item.id) }) { Text("Restore") }
                                    TextButton(onClick = { onDeleteForever(item.id) }) { Text("Delete forever", color = MaterialTheme.colorScheme.error) }
                                } else {
                                    TextButton(onClick = { onOpen(item.id) }) { Text("Open") }
                                    TextButton(onClick = { onTrash(item.id) }) { Text("Trash", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NeoNoteSettingsDialog(
    preferences: NeoNotePreferences,
    onPreferencesChange: (NeoNotePreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Appearance", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppAppearance.entries.forEach { appearance ->
                        ContextAction(appearance.name, preferences.appearance == appearance) {
                            onPreferencesChange(preferences.copy(appearance = appearance))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pressure-sensitive ink", Modifier.weight(1f))
                    Switch(preferences.pressureEnabled, onCheckedChange = {
                        onPreferencesChange(preferences.copy(pressureEnabled = it))
                    })
                }
                Text("Default eraser", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ContextAction("Segment", preferences.eraserMode == EraserMode.Segment) {
                        onPreferencesChange(preferences.copy(eraserMode = EraserMode.Segment))
                    }
                    ContextAction("Whole stroke", preferences.eraserMode == EraserMode.Stroke) {
                        onPreferencesChange(preferences.copy(eraserMode = EraserMode.Stroke))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
