package com.neonote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.neonote.engine.DocumentSummary

@Composable
internal fun NaturalDocumentLibraryDialog(
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
    var menuExpanded by remember { mutableStateOf(false) }
    val visible = documents.filter {
        it.isTrashed == showTrash && it.title.contains(query.trim(), ignoreCase = true)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.9f),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 18.dp,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (showTrash) "Trash" else "Notes",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (showTrash) "Deleted notes stay here until removed forever"
                            else "${visible.size} ${if (visible.size == 1) "note" else "notes"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Done") }
                    Box {
                        TextButton(onClick = { menuExpanded = true }) { Text("⋮") }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (showTrash) "Show notes" else "Show trash") },
                                onClick = {
                                    menuExpanded = false
                                    onToggleTrash()
                                },
                            )
                            if (!showTrash) {
                                DropdownMenuItem(
                                    text = { Text("Import archive") },
                                    onClick = {
                                        menuExpanded = false
                                        onImport()
                                    },
                                )
                            }
                        }
                    }
                }

                BasicTextField(
                    value = query,
                    onValueChange = { query = it.take(200) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 13.dp, vertical = 12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box {
                            if (query.isBlank()) {
                                Text("Search notes…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            inner()
                        }
                    },
                )

                if (!showTrash) {
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it.take(120) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { inner ->
                                Box {
                                    if (newTitle.isBlank()) {
                                        Text("New note title", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    inner()
                                }
                            },
                        )
                        TextButton(
                            onClick = {
                                onCreate(newTitle.ifBlank { "Untitled Note" })
                                newTitle = ""
                            },
                        ) { Text("Create", fontWeight = FontWeight.SemiBold) }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                if (visible.isEmpty()) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            if (query.isNotBlank()) "No matching notes"
                            else if (showTrash) "Trash is empty" else "Create your first note above",
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (query.isNotBlank()) {
                            Text(
                                "Try a shorter title or clear the search.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(visible, key = { "${it.isTrashed}:${it.id}" }) { item ->
                            DocumentLibraryCard(
                                item = item,
                                current = item.id == currentDocumentId && !showTrash,
                                showTrash = showTrash,
                                onOpen = { onOpen(item.id) },
                                onTrash = { onTrash(item.id) },
                                onRestore = { onRestore(item.id) },
                                onDeleteForever = { onDeleteForever(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentLibraryCard(
    item: DocumentSummary,
    current: Boolean,
    showTrash: Boolean,
    onOpen: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!showTrash) Modifier.clickable(onClick = onOpen) else Modifier),
        shape = RoundedCornerShape(13.dp),
        color = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (item.isFavorite) "★ " else "") + item.title.ifBlank { "Untitled Note" },
                    color = if (current) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${item.pageCount} ${if (item.pageCount == 1) "page" else "pages"}" +
                        if (current) " · Open" else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.padding(horizontal = 2.dp))
            if (showTrash) {
                TextButton(onClick = onRestore) { Text("Restore") }
                TextButton(onClick = onDeleteForever) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onTrash) {
                    Text("Trash", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
