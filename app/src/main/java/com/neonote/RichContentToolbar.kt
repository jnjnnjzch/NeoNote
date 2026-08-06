package com.neonote

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.neonote.engine.ActiveRichContentTarget
import com.neonote.engine.FileAssetStore
import com.neonote.engine.InlineStyle
import com.neonote.model.ListKind
import com.neonote.model.TextAlignment
import kotlinx.coroutines.launch

private val ToolbarPurple = Color(0xFF5B3FD1)
private val ToolbarSurface = Color(0xFAFFFFFF)
private val ToolbarActionBackground = Color(0xFFF5F3F9)
private val ToolbarSelectedBackground = Color(0xFFE8E2FF)
private val ToolbarDividerColor = Color(0xFFE5E1EC)

@Composable
internal fun RichContentToolbar(
    boxId: String,
    controller: NeoNoteEditorController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val assetStore = remember(context) { FileAssetStore(FileAssetStore.defaultDirectory(context.filesDir)) }
    val tableCell = controller.activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell
    val typingStyle = controller.activeRichContentTypingStyle(boxId)
    val listKind = controller.activeRichContentListKind(boxId)
    val tableDimensions = controller.activeRichContentTableDimensions(boxId)
    var insertMenuVisible by remember { mutableStateOf(false) }
    var moreMenuVisible by remember { mutableStateOf(false) }
    var tableMenuVisible by remember { mutableStateOf(false) }
    var linkDialogVisible by remember { mutableStateOf(false) }
    var linkText by remember { mutableStateOf("") }
    var deleteTableConfirmationVisible by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val draft = context.contentResolver.readEditorImageAssetDraft(uri) ?: return@launch
            val reference = assetStore.put(draft)
            controller.insertImageAtActiveContext(boxId, reference.id, reference.fileName ?: "Image")
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth().focusProperties { canFocus = false },
        shape = RoundedCornerShape(13.dp),
        color = ToolbarSurface,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, ToolbarDividerColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 5.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarAction("Done", "Finish editing", compact = false, emphasized = true) { controller.finishRichContentEditing(boxId) }
            ToolbarDivider()
            ToolbarAction("B", "Bold", selected = typingStyle?.bold == true, fontWeight = FontWeight.Bold) {
                controller.toggleActiveRichContentStyle(boxId, InlineStyle.Bold)
            }
            ToolbarAction("I", "Italic", selected = typingStyle?.italic == true, fontStyle = FontStyle.Italic) {
                controller.toggleActiveRichContentStyle(boxId, InlineStyle.Italic)
            }
            ToolbarAction("U", "Underline", selected = typingStyle?.underline == true, textDecoration = TextDecoration.Underline) {
                controller.toggleActiveRichContentStyle(boxId, InlineStyle.Underline)
            }
            ToolbarAction("•", "Bullet list", selected = listKind == ListKind.Bullet) { controller.toggleActiveRichContentList(boxId, ListKind.Bullet) }
            ToolbarAction("☐", "Checklist", selected = listKind == ListKind.Todo) { controller.toggleActiveRichContentList(boxId, ListKind.Todo) }

            Box {
                ToolbarAction("＋", "Insert content") { insertMenuVisible = true }
                DropdownMenu(expanded = insertMenuVisible, onDismissRequest = { insertMenuVisible = false }) {
                    DropdownMenuItem(text = { Text("Table") }, onClick = {
                        insertMenuVisible = false
                        controller.insertTableAtActiveContext(boxId)
                    })
                    DropdownMenuItem(text = { Text("Formula") }, onClick = {
                        insertMenuVisible = false
                        controller.insertFormulaAtActiveContext(boxId)
                    })
                    DropdownMenuItem(text = { Text("Image") }, onClick = {
                        insertMenuVisible = false
                        imagePicker.launch("image/*")
                    })
                }
            }

            if (tableCell != null) {
                Box {
                    ToolbarAction("Table", "Table actions", compact = false) { tableMenuVisible = true }
                    DropdownMenu(expanded = tableMenuVisible, onDismissRequest = { tableMenuVisible = false }) {
                        DropdownMenuItem(text = { Text("Insert row above") }, onClick = {
                            tableMenuVisible = false; controller.addActiveRichContentTableRow(boxId, after = false)
                        })
                        DropdownMenuItem(text = { Text("Insert row below") }, onClick = {
                            tableMenuVisible = false; controller.addActiveRichContentTableRow(boxId, after = true)
                        })
                        DropdownMenuItem(
                            text = { Text("Delete row") },
                            enabled = (tableDimensions?.first ?: 0) > 1,
                            onClick = { tableMenuVisible = false; controller.deleteActiveRichContentTableRow(boxId) },
                        )
                        DropdownMenuItem(text = { Text("Insert column left") }, onClick = {
                            tableMenuVisible = false; controller.addActiveRichContentTableColumn(boxId, after = false)
                        })
                        DropdownMenuItem(text = { Text("Insert column right") }, onClick = {
                            tableMenuVisible = false; controller.addActiveRichContentTableColumn(boxId, after = true)
                        })
                        DropdownMenuItem(
                            text = { Text("Delete column") },
                            enabled = (tableDimensions?.second ?: 0) > 1,
                            onClick = { tableMenuVisible = false; controller.deleteActiveRichContentTableColumn(boxId) },
                        )
                        DropdownMenuItem(text = { Text("Fit column to content") }, onClick = {
                            tableMenuVisible = false
                            controller.activeRichContentSuggestedColumnWidth(boxId)?.let { width ->
                                controller.setActiveRichContentTableColumnWidth(boxId, width)
                            }
                        })
                        DropdownMenuItem(text = { Text("Use automatic column width") }, onClick = {
                            tableMenuVisible = false
                            controller.setActiveRichContentTableColumnWidth(boxId, null)
                        })
                        DropdownMenuItem(text = { Text("Delete table", color = MaterialTheme.colorScheme.error) }, onClick = {
                            tableMenuVisible = false; deleteTableConfirmationVisible = true
                        })
                    }
                }
            }

            Box {
                ToolbarAction("•••", "More formatting") { moreMenuVisible = true }
                DropdownMenu(expanded = moreMenuVisible, onDismissRequest = { moreMenuVisible = false }) {
                    DropdownMenuItem(
                        text = { Text(if (listKind == ListKind.Numbered) "Turn off numbering" else "Numbered list") },
                        onClick = { moreMenuVisible = false; controller.toggleActiveRichContentList(boxId, ListKind.Numbered) },
                    )
                    DropdownMenuItem(text = { Text("Strikethrough") }, onClick = {
                        moreMenuVisible = false; controller.toggleActiveRichContentStyle(boxId, InlineStyle.Strikethrough)
                    })
                    DropdownMenuItem(text = { Text("Heading") }, onClick = {
                        moreMenuVisible = false; controller.setActiveHeadingLevel(boxId, 1)
                    })
                    DropdownMenuItem(text = { Text("Body text") }, onClick = {
                        moreMenuVisible = false; controller.setActiveHeadingLevel(boxId, 0)
                    })
                    DropdownMenuItem(text = { Text("Align left") }, onClick = {
                        moreMenuVisible = false; controller.setActiveParagraphAlignment(boxId, TextAlignment.Start)
                    })
                    DropdownMenuItem(text = { Text("Align center") }, onClick = {
                        moreMenuVisible = false; controller.setActiveParagraphAlignment(boxId, TextAlignment.Center)
                    })
                    DropdownMenuItem(text = { Text("Increase indent") }, onClick = {
                        moreMenuVisible = false; controller.changeActiveParagraphIndent(boxId, 1)
                    })
                    DropdownMenuItem(text = { Text("Decrease indent") }, onClick = {
                        moreMenuVisible = false; controller.changeActiveParagraphIndent(boxId, -1)
                    })
                    DropdownMenuItem(text = { Text("Link…") }, onClick = {
                        moreMenuVisible = false; linkDialogVisible = true
                    })
                    DropdownMenuItem(text = { Text("Remove link") }, onClick = {
                        moreMenuVisible = false; controller.setActiveRichContentLink(boxId, null)
                    })
                }
            }
            ColorAction(Color(0xFF171326), "Black text") { controller.setActiveRichContentTextColor(boxId, 0xFF171326.toInt()) }
            ColorAction(Color(0xFF5B3FD1), "Purple text") { controller.setActiveRichContentTextColor(boxId, 0xFF5B3FD1.toInt()) }
        }
    }

    if (deleteTableConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { deleteTableConfirmationVisible = false },
            title = { Text("Delete this table?") },
            text = { Text("The table and everything inside it will be removed. You can undo this action.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTableConfirmationVisible = false
                    controller.deleteActiveRichContentTable(boxId)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTableConfirmationVisible = false }) { Text("Cancel") } },
        )
    }

    if (linkDialogVisible) {
        AlertDialog(
            onDismissRequest = { linkDialogVisible = false },
            title = { Text("Link") },
            text = {
                BasicTextField(
                    value = linkText,
                    onValueChange = { linkText = it.take(2048) },
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFF6F4FA), RoundedCornerShape(8.dp)).padding(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF24202B)),
                    decorationBox = { inner -> Box { if (linkText.isBlank()) Text("https://…", color = Color(0xFF9A94A5)); inner() } },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    controller.setActiveRichContentLink(boxId, linkText.trim().ifBlank { null })
                    linkDialogVisible = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { linkDialogVisible = false }) { Text("Cancel") } },
        )
    }
}

@Composable private fun ToolbarDivider() { Box(Modifier.size(width = 1.dp, height = 28.dp).background(ToolbarDividerColor)) }

@Composable
private fun ToolbarAction(
    label: String,
    contentDescription: String,
    compact: Boolean = true,
    emphasized: Boolean = false,
    selected: Boolean = false,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    textDecoration: TextDecoration? = null,
    onAction: () -> Unit,
) {
    Box(
        modifier = Modifier.focusProperties { canFocus = false }.defaultMinSize(minWidth = 42.dp, minHeight = 42.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected || emphasized) ToolbarSelectedBackground else ToolbarActionBackground)
            .clickable(role = Role.Button, onClick = onAction)
            .semantics { this.contentDescription = contentDescription; role = Role.Button }
            .padding(horizontal = if (compact) 9.dp else 11.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = ToolbarPurple, style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = fontWeight ?: FontWeight.SemiBold, fontStyle = fontStyle, textDecoration = textDecoration), maxLines = 1)
    }
}

@Composable
private fun ColorAction(color: Color, description: String, onAction: () -> Unit) {
    Box(
        modifier = Modifier.defaultMinSize(minWidth = 42.dp, minHeight = 42.dp).clip(RoundedCornerShape(9.dp))
            .clickable(role = Role.Button, onClick = onAction)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.size(24.dp).clip(CircleShape).background(color)) }
}
