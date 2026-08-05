package com.neonote

import android.content.ClipboardManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
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
import com.neonote.engine.AssetDraft
import com.neonote.engine.FileAssetStore
import com.neonote.engine.InlineStyle
import com.neonote.model.ListKind
import com.neonote.model.TextAlignment
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MaximumImportedImageBytes = 32 * 1024 * 1024
private val ToolbarPurple = Color(0xFF5B3FD1)
private val ToolbarSurface = Color(0xFCFFFFFF)
private val ToolbarActionBackground = Color(0xFFF3F0FB)
private val ToolbarDividerColor = Color(0xFFE5E1EC)

@Composable
internal fun RichContentToolbar(
    boxId: String,
    controller: NeoNoteEditorController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboard = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    val activeTableCell = controller.activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell
    val assetStore = remember(context) { FileAssetStore(FileAssetStore.defaultDirectory(context.filesDir)) }
    var linkDialogVisible by remember { mutableStateOf(false) }
    var linkText by remember { mutableStateOf("") }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) coroutineScope.launch {
            val draft = context.contentResolver.readImageAssetDraft(uri) ?: return@launch
            val reference = assetStore.put(draft)
            controller.insertRichContentImagePlaceholder(
                boxId = boxId,
                assetId = reference.id,
                altText = reference.fileName ?: "Imported image",
            )
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false },
        shape = RoundedCornerShape(16.dp),
        color = ToolbarSurface,
        shadowElevation = 10.dp,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, ToolbarDividerColor),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolbarAction("B", "Bold", fontWeight = FontWeight.Bold) {
                    controller.toggleActiveRichContentStyle(boxId, InlineStyle.Bold)
                }
                ToolbarAction("I", "Italic", fontStyle = FontStyle.Italic) {
                    controller.toggleActiveRichContentStyle(boxId, InlineStyle.Italic)
                }
                ToolbarAction("U", "Underline", textDecoration = TextDecoration.Underline) {
                    controller.toggleActiveRichContentStyle(boxId, InlineStyle.Underline)
                }
                ToolbarAction("S", "Strikethrough", textDecoration = TextDecoration.LineThrough) {
                    controller.toggleActiveRichContentStyle(boxId, InlineStyle.Strikethrough)
                }
                ToolbarDivider()
                ToolbarAction("A−", "Smaller text") { controller.setActiveRichContentFontScale(boxId, 0.85f) }
                ToolbarAction("A", "Normal text") { controller.setActiveRichContentFontScale(boxId, 1f) }
                ToolbarAction("A+", "Larger text") { controller.setActiveRichContentFontScale(boxId, 1.25f) }
                ToolbarDivider()
                ToolbarAction("•", "Bullet list") { controller.toggleActiveRichContentList(boxId, ListKind.Bullet) }
                ToolbarAction("1.", "Numbered list") { controller.toggleActiveRichContentList(boxId, ListKind.Numbered) }
                ToolbarAction("☐", "Checklist") { controller.toggleActiveRichContentList(boxId, ListKind.Todo) }
                ToolbarDivider()
                ToolbarAction("Link", "Add or edit link", compact = false) { linkDialogVisible = true }
                ToolbarAction("Unlink", "Remove link", compact = false) { controller.setActiveRichContentLink(boxId, null) }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ColorAction(Color(0xFF171326), "Black text") {
                    controller.setActiveRichContentTextColor(boxId, 0xFF171326.toInt())
                }
                ColorAction(Color(0xFF5B3FD1), "Purple text") {
                    controller.setActiveRichContentTextColor(boxId, 0xFF5B3FD1.toInt())
                }
                ColorAction(Color(0xFFC62828), "Red text") {
                    controller.setActiveRichContentTextColor(boxId, 0xFFC62828.toInt())
                }
                ColorAction(Color(0xFF1565C0), "Blue text") {
                    controller.setActiveRichContentTextColor(boxId, 0xFF1565C0.toInt())
                }
                HighlightAction(Color(0xFFFFF59D), "Yellow highlight") {
                    controller.setActiveRichContentHighlight(boxId, 0xFFFFF59D.toInt())
                }
                HighlightAction(Color(0xFFC8E6C9), "Green highlight") {
                    controller.setActiveRichContentHighlight(boxId, 0xFFC8E6C9.toInt())
                }
                ToolbarAction("×HL", "Clear highlight", compact = false) {
                    controller.setActiveRichContentHighlight(boxId, null)
                }
                ToolbarDivider()
                ToolbarAction("Body", "Body paragraph", compact = false) { controller.setActiveHeadingLevel(boxId, 0) }
                ToolbarAction("H1", "Heading level one") { controller.setActiveHeadingLevel(boxId, 1) }
                ToolbarAction("H2", "Heading level two") { controller.setActiveHeadingLevel(boxId, 2) }
                ToolbarAction("H3", "Heading level three") { controller.setActiveHeadingLevel(boxId, 3) }
                ToolbarAction("≡←", "Align left") { controller.setActiveParagraphAlignment(boxId, TextAlignment.Start) }
                ToolbarAction("≡↔", "Align center") { controller.setActiveParagraphAlignment(boxId, TextAlignment.Center) }
                ToolbarAction("≡→", "Align right") { controller.setActiveParagraphAlignment(boxId, TextAlignment.End) }
                ToolbarAction("←", "Decrease indent") { controller.changeActiveParagraphIndent(boxId, -1) }
                ToolbarAction("→", "Increase indent") { controller.changeActiveParagraphIndent(boxId, 1) }
                ToolbarDivider()
                ToolbarAction("Table", "Insert table", compact = false) {
                    controller.insertRichContentTablePlaceholder(boxId)
                }
                ToolbarAction("fx", "Insert formula") { controller.insertRichContentFormulaPlaceholder(boxId) }
                ToolbarAction("Image", "Import image", compact = false) { imagePicker.launch("image/*") }
                ToolbarAction("Paste", "Paste image from clipboard", compact = false) {
                    val imageUri = clipboard?.primaryImageUri(context)
                    if (imageUri != null) {
                        coroutineScope.launch {
                            val draft = context.contentResolver.readClipboardImageDraft(imageUri) ?: return@launch
                            val reference = assetStore.put(draft)
                            controller.insertRichContentImagePlaceholder(
                                boxId = boxId,
                                assetId = reference.id,
                                altText = reference.fileName ?: "Pasted image",
                            )
                        }
                    }
                }


            }

            if (activeTableCell != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToolbarAction("+ Row", "Add table row", compact = false) {
                        controller.addActiveRichContentTableRow(boxId)
                    }
                    ToolbarAction("− Row", "Delete table row", compact = false) {
                        controller.deleteActiveRichContentTableRow(boxId)
                    }
                    ToolbarAction("+ Col", "Add table column", compact = false) {
                        controller.addActiveRichContentTableColumn(boxId)
                    }
                    ToolbarAction("− Col", "Delete table column", compact = false) {
                        controller.deleteActiveRichContentTableColumn(boxId)
                    }
                    ToolbarAction("160", "Set column width") {
                        controller.setActiveRichContentTableColumnWidth(boxId, 160f)
                    }
                    ToolbarAction("Auto", "Automatic column width", compact = false) {
                        controller.setActiveRichContentTableColumnWidth(boxId, null)
                    }
                    ToolbarAction("Merge →", "Merge cell with the cell to the right", compact = false) {
                        controller.mergeActiveTableCellRight(boxId)
                    }
                    ToolbarAction("Merge ↓", "Merge cell with the cell below", compact = false) {
                        controller.mergeActiveTableCellDown(boxId)
                    }
                    ToolbarAction("Split", "Split merged cell", compact = false) {
                        controller.splitActiveTableCell(boxId)
                    }
                    ToolbarAction("Header", "Toggle header row", compact = false) {
                        controller.toggleActiveTableHeader(boxId)
                    }
                    ToolbarAction("Borders", "Toggle table borders", compact = false) {
                        controller.toggleActiveTableBorders(boxId)
                    }
                    ToolbarAction("Shade", "Toggle cell shading", compact = false) {
                        controller.toggleActiveTableCellShade(boxId)
                    }
                    ToolbarAction("V Align", "Cycle vertical cell alignment", compact = false) {
                        controller.cycleActiveTableCellAlignment(boxId)
                    }
                    ToolbarAction("Nested", "Insert nested table", compact = false) {
                        controller.insertNestedTable(boxId, activeTableCell.address, 2, 2)
                    }
                }
            }
        }
    }

    if (linkDialogVisible) {
        AlertDialog(
            onDismissRequest = { linkDialogVisible = false },
            title = { Text("Link") },
            text = {
                BasicTextField(
                    value = linkText,
                    onValueChange = { linkText = it.take(2048) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF6F4FA), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF24202B)),
                    decorationBox = { inner ->
                        Box {
                            if (linkText.isBlank()) Text("https://…", color = Color(0xFF9A94A5))
                            inner()
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    controller.setActiveRichContentLink(boxId, linkText)
                    linkDialogVisible = false
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { linkDialogVisible = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ToolbarDivider() {
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .width(1.dp)
            .height(30.dp)
            .background(ToolbarDividerColor),
    )
}

@Composable
private fun ToolbarAction(
    label: String,
    contentDescription: String,
    compact: Boolean = true,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    textDecoration: TextDecoration? = null,
    onAction: () -> Unit,
) {
    Box(
        modifier = Modifier
            .focusProperties { canFocus = false }
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(ToolbarActionBackground)
            .clickable(role = Role.Button, onClick = onAction)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .padding(horizontal = if (compact) 11.dp else 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = ToolbarPurple,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = fontWeight ?: FontWeight.SemiBold,
                fontStyle = fontStyle,
                textDecoration = textDecoration,
            ),
        )
    }
}

@Composable
private fun ColorAction(color: Color, description: String, onAction: () -> Unit) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(11.dp))
            .clickable(role = Role.Button, onClick = onAction)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(color))
    }
}

@Composable
private fun HighlightAction(color: Color, description: String, onAction: () -> Unit) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(11.dp))
            .clickable(role = Role.Button, onClick = onAction)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text("H", color = Color(0xFF4D4658), fontWeight = FontWeight.Bold)
        }
    }
}

private suspend fun android.content.ContentResolver.readImageAssetDraft(uri: Uri): AssetDraft? =
    withContext(Dispatchers.IO) {
        runCatching {
            val bytes = openInputStream(uri)?.use { it.readBytesLimited(MaximumImportedImageBytes) }
                ?: return@runCatching null
            if (bytes.isEmpty()) return@runCatching null
            AssetDraft(getType(uri) ?: "application/octet-stream", bytes, displayName(uri))
        }.getOrNull()
    }

private fun android.content.ContentResolver.displayName(uri: Uri): String? =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0) cursor.getString(index) else null
    }

private fun InputStream.readBytesLimited(maximumBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        require(total <= maximumBytes) { "Imported image exceeds the size limit" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
