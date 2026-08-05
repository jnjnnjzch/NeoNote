package com.neonote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import com.neonote.model.EditorTool
import com.neonote.model.EraserMode
import com.neonote.model.InkBrush

private val NaturalChromePurple = Color(0xFF5B3FD1)
private val NaturalChromeSoft = Color(0xFFF2EFFF)

@Composable
internal fun NaturalWorkspaceTopBar(
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
    BoxWithConstraints {
        val compact = maxWidth < 680.dp
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (compact) 60.dp else 66.dp)
                    .padding(horizontal = if (compact) 8.dp else 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NaturalHeaderButton("☰", "Open document library", onClick = onLibrary)
                if (!compact) {
                    Surface(Modifier.size(36.dp), RoundedCornerShape(10.dp), color = NaturalChromePurple) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("N", color = Color.White, fontWeight = FontWeight.Black, fontSize = 19.sp)
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = if (compact) 6.dp else 12.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = title,
                            onValueChange = onTitleChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            cursorBrush = SolidColor(NaturalChromePurple),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                Box {
                                    if (title.isBlank()) {
                                        Text("Untitled note", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    inner()
                                }
                            },
                        )
                        NaturalHeaderButton(
                            if (favorite) "★" else "☆",
                            "Toggle favorite",
                            compact = true,
                            onClick = onFavorite,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (saveLabel == "Saved") Color(0xFF16A34A) else Color(0xFFF59E0B)),
                        )
                        Text(
                            saveLabel,
                            Modifier.padding(start = 5.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                NaturalHeaderButton("↶", "Undo", enabled = canUndo, compact = true, onClick = onUndo)
                NaturalHeaderButton("↷", "Redo", enabled = canRedo, compact = true, onClick = onRedo)
                if (compact) {
                    NaturalOverflowMenu(
                        onResetView = onResetView,
                        onExport = onExport,
                        onShare = onShare,
                        onSettings = onSettings,
                        onSaveNow = onSaveNow,
                    )
                } else {
                    NaturalHeaderButton("100%", "Reset canvas view", onClick = onResetView)
                    NaturalHeaderButton("Export", "Export NeoNote archive", onClick = onExport)
                    NaturalHeaderButton("Share", "Share NeoNote archive", onClick = onShare)
                    NaturalHeaderButton("⚙", "Settings", compact = true, onClick = onSettings)
                    NaturalHeaderButton("Save", "Save now", onClick = onSaveNow)
                }
            }
        }
    }
}

@Composable
private fun NaturalOverflowMenu(
    onResetView: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onSettings: () -> Unit,
    onSaveNow: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        NaturalHeaderButton("⋮", "More workspace actions", compact = true) { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            NaturalMenuItem("Reset view", onResetView) { expanded = false }
            NaturalMenuItem("Save now", onSaveNow) { expanded = false }
            NaturalMenuItem("Export archive", onExport) { expanded = false }
            NaturalMenuItem("Share archive", onShare) { expanded = false }
            NaturalMenuItem("Settings", onSettings) { expanded = false }
        }
    }
}

@Composable
private fun NaturalMenuItem(label: String, action: () -> Unit, dismiss: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = {
            dismiss()
            action()
        },
    )
}

@Composable
private fun NaturalHeaderButton(
    label: String,
    description: String,
    enabled: Boolean = true,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .defaultMinSize(minWidth = if (compact) 42.dp else 48.dp, minHeight = 42.dp)
            .semantics { contentDescription = description },
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
        ),
        contentPadding = PaddingValues(horizontal = if (compact) 5.dp else 8.dp),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
internal fun NaturalToolDock(
    selectedTool: EditorTool,
    onToolSelected: (EditorTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NaturalToolDockItem("✎", "Write", selectedTool == EditorTool.Pen) { onToolSelected(EditorTool.Pen) }
            NaturalToolDockItem("T", "Type", selectedTool == EditorTool.Text) { onToolSelected(EditorTool.Text) }
            NaturalToolDockItem("⌁", "Select", selectedTool == EditorTool.Selection) { onToolSelected(EditorTool.Selection) }
            NaturalToolDockItem("⌫", "Erase", selectedTool == EditorTool.Eraser) { onToolSelected(EditorTool.Eraser) }
        }
    }
}

@Composable
private fun NaturalToolDockItem(
    symbol: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .defaultMinSize(minWidth = 58.dp, minHeight = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "$label tool"
            }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(symbol, color = foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = foreground, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun NaturalToolContextPanel(
    controller: NeoNoteEditorController,
    preferences: NeoNotePreferences,
    onPreferencesChange: (NeoNotePreferences) -> Unit,
    onInsertFloatingImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = controller.state
    if (state.currentTool == EditorTool.Text) return

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shadowElevation = 7.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (state.currentTool) {
                EditorTool.Pen -> {
                    NaturalPenColor(Color(0xFF171326), "Black") {
                        controller.setInkColor(0xFF171326.toInt())
                        onPreferencesChange(preferences.copy(defaultInkColorArgb = 0xFF171326.toInt()))
                    }
                    NaturalPenColor(Color(0xFF5B3FD1), "Purple") {
                        controller.setInkColor(0xFF5B3FD1.toInt())
                        onPreferencesChange(preferences.copy(defaultInkColorArgb = 0xFF5B3FD1.toInt()))
                    }
                    NaturalPenColor(Color(0xFF1565C0), "Blue") {
                        controller.setInkColor(0xFF1565C0.toInt())
                        onPreferencesChange(preferences.copy(defaultInkColorArgb = 0xFF1565C0.toInt()))
                    }
                    NaturalPenColor(Color(0xFFC62828), "Red") {
                        controller.setInkColor(0xFFC62828.toInt())
                        onPreferencesChange(preferences.copy(defaultInkColorArgb = 0xFFC62828.toInt()))
                    }
                    NaturalContextAction("Pen", state.inkSettings.brush == InkBrush.Pen) {
                        controller.setInkBrush(InkBrush.Pen)
                        onPreferencesChange(preferences.copy(defaultBrush = InkBrush.Pen))
                    }
                    NaturalContextAction("Highlighter", state.inkSettings.brush == InkBrush.Highlighter) {
                        controller.setInkBrush(InkBrush.Highlighter)
                        onPreferencesChange(preferences.copy(defaultBrush = InkBrush.Highlighter))
                    }
                    NaturalContextAction("Fine", state.inkSettings.width <= 3.5f) {
                        controller.setInkWidth(2.5f)
                        onPreferencesChange(preferences.copy(defaultInkWidth = 2.5f))
                    }
                    NaturalContextAction("Medium", state.inkSettings.width in 3.5f..8f) {
                        controller.setInkWidth(5.5f)
                        onPreferencesChange(preferences.copy(defaultInkWidth = 5.5f))
                    }
                    NaturalContextAction("Bold", state.inkSettings.width > 8f) {
                        controller.setInkWidth(12f)
                        onPreferencesChange(preferences.copy(defaultInkWidth = 12f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Pressure", style = MaterialTheme.typography.labelMedium)
                        Switch(
                            checked = state.inkSettings.pressureEnabled,
                            onCheckedChange = {
                                controller.setPressureEnabled(it)
                                onPreferencesChange(preferences.copy(pressureEnabled = it))
                            },
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                    NaturalContextAction("＋ Image", false, onClick = onInsertFloatingImage)
                }
                EditorTool.Eraser -> {
                    NaturalContextAction("Segment", state.eraserMode == EraserMode.Segment) {
                        controller.setEraserMode(EraserMode.Segment)
                        onPreferencesChange(preferences.copy(eraserMode = EraserMode.Segment))
                    }
                    NaturalContextAction("Whole stroke", state.eraserMode == EraserMode.Stroke) {
                        controller.setEraserMode(EraserMode.Stroke)
                        onPreferencesChange(preferences.copy(eraserMode = EraserMode.Stroke))
                    }
                    Text("S Pen eraser works in every mode", style = MaterialTheme.typography.labelMedium)
                }
                EditorTool.Selection -> {
                    val hasSelection = state.selection.selectedRefs.isNotEmpty()
                    Text(
                        if (hasSelection) "${state.selection.selectedRefs.size} selected" else "Drag around content to select",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    NaturalContextAction("Duplicate", false, enabled = hasSelection, onClick = controller::duplicateSelection)
                    NaturalContextAction("Smaller", false, enabled = hasSelection) { controller.scaleSelection(0.9f) }
                    NaturalContextAction("Larger", false, enabled = hasSelection) { controller.scaleSelection(1.1f) }
                    NaturalContextAction("Front", false, enabled = hasSelection, onClick = controller::bringSelectionToFront)
                    NaturalContextAction("Back", false, enabled = hasSelection, onClick = controller::sendSelectionToBack)
                    NaturalContextAction("Lock", false, enabled = hasSelection) { controller.setSelectionLocked(true) }
                    NaturalContextAction("Unlock", false, enabled = hasSelection) { controller.setSelectionLocked(false) }
                    NaturalContextAction("Delete", false, enabled = hasSelection, destructive = true, onClick = controller::deleteSelection)
                    NaturalContextAction("Clear", false, enabled = hasSelection, onClick = controller::clearSelection)
                }
                EditorTool.Text -> Unit
            }
        }
    }
}

@Composable
private fun NaturalContextAction(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val background = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        destructive -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
        modifier = Modifier
            .defaultMinSize(minHeight = 42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        color = foreground,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

@Composable
private fun NaturalPenColor(color: Color, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 42.dp, minHeight = 42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(NaturalChromeSoft)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "$description ink"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(25.dp).clip(CircleShape).background(color))
    }
}
