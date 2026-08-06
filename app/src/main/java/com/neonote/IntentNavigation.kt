package com.neonote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.neonote.model.NotePage
import com.neonote.model.NoteSection

/** Stable notebook navigation. It lives outside the canvas so navigation never covers note content. */
@Composable
internal fun IntentNavigation(
    controller: NeoNoteEditorController,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    var pagesVisible by remember { mutableStateOf(false) }
    var createSectionVisible by remember { mutableStateOf(false) }
    var renameSection by remember { mutableStateOf<NoteSection?>(null) }
    var deleteSection by remember { mutableStateOf<NoteSection?>(null) }
    var sectionMenu by remember { mutableStateOf<NoteSection?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (compact) {
            CompactNotebookNavigationBar(
                controller = controller,
                onOpenPages = { pagesVisible = true },
                onCreateSection = { createSectionVisible = true },
                onRenameSection = { renameSection = it },
                onDeleteSection = { deleteSection = it },
            )
        } else {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    controller.sections.forEach { section ->
                        val selected = section.id == controller.currentSectionId
                        Box {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .45f)) else null,
                                modifier = Modifier.clickable(role = Role.Tab) { controller.switchSection(section.id) },
                            ) {
                                Row(
                                    Modifier
                                        .heightIn(min = 38.dp)
                                        .padding(start = 12.dp, end = if (selected) 3.dp else 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        section.title.ifBlank { "Section" },
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                    if (selected) {
                                        TextButton(
                                            onClick = { sectionMenu = section },
                                            modifier = Modifier.defaultMinSize(minWidth = 36.dp, minHeight = 36.dp),
                                            contentPadding = PaddingValues(4.dp),
                                        ) { Text("⋮") }
                                    }
                                }
                            }
                            DropdownMenu(
                                expanded = sectionMenu?.id == section.id,
                                onDismissRequest = { sectionMenu = null },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename section") },
                                    onClick = { sectionMenu = null; renameSection = section },
                                )
                                DropdownMenuItem(
                                    text = { Text("Move left") },
                                    enabled = controller.sections.indexOfFirst { it.id == section.id } > 0,
                                    onClick = {
                                        val index = controller.sections.indexOfFirst { it.id == section.id }
                                        sectionMenu = null
                                        controller.moveSection(index, index - 1)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Move right") },
                                    enabled = controller.sections.indexOfFirst { it.id == section.id } in 0 until controller.sections.lastIndex,
                                    onClick = {
                                        val index = controller.sections.indexOfFirst { it.id == section.id }
                                        sectionMenu = null
                                        controller.moveSection(index, index + 1)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete section", color = MaterialTheme.colorScheme.error) },
                                    enabled = controller.sections.size > 1,
                                    onClick = { sectionMenu = null; deleteSection = section },
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = { createSectionVisible = true },
                        modifier = Modifier.defaultMinSize(minWidth = 40.dp, minHeight = 38.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                    ) { Text("＋", fontWeight = FontWeight.Bold) }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .14f))
    }

    if (pagesVisible) {
        PageBrowserDialog(controller = controller, onDismiss = { pagesVisible = false })
    }
    if (createSectionVisible) {
        NameDialog(
            title = "New section",
            initialValue = "",
            confirmLabel = "Create",
            onDismiss = { createSectionVisible = false },
            onConfirm = { name ->
                createSectionVisible = false
                controller.createSection(name.ifBlank { "New section" })
            },
        )
    }
    renameSection?.let { section ->
        NameDialog(
            title = "Rename section",
            initialValue = section.title,
            confirmLabel = "Rename",
            onDismiss = { renameSection = null },
            onConfirm = { name ->
                renameSection = null
                controller.renameSection(section.id, name.ifBlank { section.title })
            },
        )
    }
    deleteSection?.let { section ->
        AlertDialog(
            onDismissRequest = { deleteSection = null },
            title = { Text("Delete section?") },
            text = { Text("Pages in “${section.title}” will be moved to another section. This can be undone.") },
            confirmButton = {
                TextButton(onClick = { deleteSection = null; controller.deleteSection(section.id) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteSection = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CompactNotebookNavigationBar(
    controller: NeoNoteEditorController,
    onOpenPages: () -> Unit,
    onCreateSection: () -> Unit,
    onRenameSection: (NoteSection) -> Unit,
    onDeleteSection: (NoteSection) -> Unit,
) {
    val page = controller.currentPageOrNull
    val currentSection = controller.sections.firstOrNull { it.id == controller.currentSectionId }
    var sectionMenuVisible by remember { mutableStateOf(false) }
    var renamePageVisible by remember(page?.id) { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            TextButton(
                onClick = { sectionMenuVisible = true },
                modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 40.dp).widthIn(max = 98.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    (currentSection?.title?.ifBlank { "Section" } ?: "Section") + "  ▾",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            DropdownMenu(expanded = sectionMenuVisible, onDismissRequest = { sectionMenuVisible = false }) {
                controller.sections.forEach { section ->
                    DropdownMenuItem(
                        text = { Text((if (section.id == controller.currentSectionId) "✓  " else "") + section.title.ifBlank { "Section" }) },
                        onClick = {
                            sectionMenuVisible = false
                            controller.switchSection(section.id)
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text("New section…") }, onClick = {
                    sectionMenuVisible = false
                    onCreateSection()
                })
                currentSection?.let { section ->
                    DropdownMenuItem(text = { Text("Rename current section…") }, onClick = {
                        sectionMenuVisible = false
                        onRenameSection(section)
                    })
                    val index = controller.sections.indexOfFirst { it.id == section.id }
                    DropdownMenuItem(text = { Text("Move section left") }, enabled = index > 0, onClick = {
                        sectionMenuVisible = false
                        controller.moveSection(index, index - 1)
                    })
                    DropdownMenuItem(text = { Text("Move section right") }, enabled = index in 0 until controller.sections.lastIndex, onClick = {
                        sectionMenuVisible = false
                        controller.moveSection(index, index + 1)
                    })
                    DropdownMenuItem(
                        text = { Text("Delete current section", color = MaterialTheme.colorScheme.error) },
                        enabled = controller.sections.size > 1,
                        onClick = {
                            sectionMenuVisible = false
                            onDeleteSection(section)
                        },
                    )
                }
            }
        }
        NavigationIcon("‹", "Previous page", controller.canSwitchToPreviousPageInSection) {
            controller.switchToPreviousPageInSection()
        }
        Text(
            page?.title?.ifBlank { "Untitled page" } ?: "Untitled page",
            Modifier
                .weight(1f)
                .clickable(role = Role.Button) { renamePageVisible = true }
                .padding(horizontal = 6.dp, vertical = 12.dp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${controller.currentSectionPageNumber}/${controller.pagesInCurrentSection.size.coerceAtLeast(1)} ▾",
            Modifier
                .clickable(role = Role.Button, onClick = onOpenPages)
                .padding(horizontal = 7.dp, vertical = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        NavigationIcon("›", "Next page", controller.canSwitchToNextPageInSection) {
            controller.switchToNextPageInSection()
        }
        NavigationIcon("＋", "Add page", true, controller::addPage)
    }
    if (renamePageVisible && page != null) {
        NameDialog(
            title = "Rename page",
            initialValue = page.title,
            confirmLabel = "Rename",
            onDismiss = { renamePageVisible = false },
            onConfirm = { value ->
                renamePageVisible = false
                controller.renamePage(page.id, value)
            },
        )
    }
}

@Composable
private fun NavigationIcon(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
            .semantics { contentDescription = description },
        contentPadding = PaddingValues(3.dp),
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

@Composable
private fun PageBrowserDialog(controller: NeoNoteEditorController, onDismiss: () -> Unit) {
    var renamePage by remember { mutableStateOf<NotePage?>(null) }
    var deletePage by remember { mutableStateOf<NotePage?>(null) }
    var pageMenu by remember { mutableStateOf<NotePage?>(null) }
    var movePage by remember { mutableStateOf<NotePage?>(null) }
    val pages = controller.pagesInCurrentSection

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(.82f),
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(controller.currentSectionTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Pages", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = controller::addPage) { Text("＋ New page") }
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
                    itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
                        val selected = page.id == controller.state.currentPageId
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        controller.switchPage(page.id)
                                        onDismiss()
                                    }
                                    .padding(start = 13.dp, top = 7.dp, bottom = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(page.title.ifBlank { "Untitled page" }, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                    val count = page.canvas.objects.size + page.canvas.inkLayer.strokes.size
                                    Text(if (count == 0) "Blank" else "$count items", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                }
                                Box {
                                    TextButton(
                                        onClick = { pageMenu = page },
                                        modifier = Modifier.defaultMinSize(minWidth = 42.dp, minHeight = 42.dp),
                                        contentPadding = PaddingValues(4.dp),
                                    ) { Text("⋮") }
                                    DropdownMenu(
                                        expanded = pageMenu?.id == page.id,
                                        onDismissRequest = { pageMenu = null },
                                    ) {
                                        DropdownMenuItem(text = { Text("Rename") }, onClick = { pageMenu = null; renamePage = page })
                                        DropdownMenuItem(text = { Text("Duplicate") }, onClick = { pageMenu = null; controller.duplicatePage(page.id) })
                                        DropdownMenuItem(
                                            text = { Text("Move up") },
                                            enabled = index > 0,
                                            onClick = { pageMenu = null; controller.movePageWithinSection(page.id, -1) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Move down") },
                                            enabled = index < pages.lastIndex,
                                            onClick = { pageMenu = null; controller.movePageWithinSection(page.id, 1) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Move to section…") },
                                            enabled = controller.sections.size > 1,
                                            onClick = { pageMenu = null; movePage = page },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                            enabled = pages.size > 1,
                                            onClick = { pageMenu = null; deletePage = page },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renamePage?.let { page ->
        NameDialog(
            title = "Rename page",
            initialValue = page.title,
            confirmLabel = "Rename",
            onDismiss = { renamePage = null },
            onConfirm = { name -> renamePage = null; controller.renamePage(page.id, name) },
        )
    }
    deletePage?.let { page ->
        AlertDialog(
            onDismissRequest = { deletePage = null },
            title = { Text("Delete page?") },
            text = { Text("“${page.title.ifBlank { "Untitled page" }}” will be removed. You can undo this action.") },
            confirmButton = {
                TextButton(onClick = { deletePage = null; controller.deletePage(page.id) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deletePage = null }) { Text("Cancel") } },
        )
    }
    movePage?.let { page ->
        AlertDialog(
            onDismissRequest = { movePage = null },
            title = { Text("Move page") },
            text = {
                Column {
                    controller.sections.filterNot { it.id == controller.currentSectionId }.forEach { section ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    movePage = null
                                    controller.movePageToSection(page.id, section.id)
                                }
                                .padding(vertical = 12.dp),
                        ) { Text(section.title.ifBlank { "Section" }) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { movePage = null }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun NameDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            BasicTextField(
                value = value,
                onValueChange = { value = it.take(100) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { role = Role.TextField }
                    .padding(vertical = 10.dp),
                decorationBox = { inner ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) { Box(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) { inner() } }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }, enabled = value.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun IntentPageRail(
    controller: NeoNoteEditorController,
    modifier: Modifier = Modifier,
) {
    var browserVisible by remember { mutableStateOf(false) }
    var renamePage by remember { mutableStateOf<NotePage?>(null) }
    val pages = controller.pagesInCurrentSection
    Surface(modifier = modifier.width(190.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    controller.currentSectionTitle.ifBlank { "Section" },
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(
                    onClick = { browserVisible = true },
                    modifier = Modifier.defaultMinSize(minWidth = 40.dp, minHeight = 40.dp),
                    contentPadding = PaddingValues(4.dp),
                ) { Text("⋮") }
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
                    val selected = page.id == controller.state.currentPageId
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (selected) renamePage = page else controller.switchPage(page.id)
                            },
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${index + 1}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                page.title.ifBlank { "Untitled page" },
                                Modifier.weight(1f),
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            TextButton(
                onClick = controller::addPage,
                modifier = Modifier.fillMaxWidth().padding(6.dp),
            ) { Text("＋ New page") }
        }
    }
    if (browserVisible) PageBrowserDialog(controller) { browserVisible = false }
    renamePage?.let { page ->
        NameDialog(
            title = "Rename page",
            initialValue = page.title,
            confirmLabel = "Rename",
            onDismiss = { renamePage = null },
            onConfirm = { value ->
                renamePage = null
                controller.renamePage(page.id, value)
            },
        )
    }
}
