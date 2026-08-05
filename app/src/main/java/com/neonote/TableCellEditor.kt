package com.neonote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonote.engine.ActiveRichContentTarget
import com.neonote.engine.AssetDraft
import com.neonote.engine.FileAssetStore
import com.neonote.engine.MathExpressionFormatter
import com.neonote.engine.RichContentLayoutDefaults
import com.neonote.model.BlockFormula
import com.neonote.model.BlockImage
import com.neonote.model.FormulaDisplayMode
import com.neonote.model.ImageCrop
import com.neonote.model.InlineFormula
import com.neonote.model.InlineImage
import com.neonote.model.InlineLineBreak
import com.neonote.model.InlineText
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.TableCell
import com.neonote.model.TableCellAddress
import com.neonote.model.TableNode
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MaximumNestedDecodedImageDimension = 1_024

@Composable
internal fun TableCellEditor(
    boxId: String,
    rootBlockIndex: Int,
    address: TableCellAddress,
    cell: TableCell,
    activeContentBlockIndex: Int?,
    selectionMode: Boolean,
    selected: Boolean,
    controller: NeoNoteEditorController,
    modifier: Modifier = Modifier,
) {
    val blocks = cell.content.blocks.ifEmpty { listOf(ParagraphNode()) }
    val activeAddress = (controller.activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell)?.address
    val activeCell = activeAddress?.path == address.path
    Column(
        modifier = modifier.padding(horizontal = 7.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(RichContentLayoutDefaults.BlockSpacing.dp),
    ) {
        blocks.forEachIndexed { contentBlockIndex, block ->
            val blockAddress = address.withContentBlock(contentBlockIndex)
            when (block) {
                is ParagraphNode -> if (block.isPlatformEditable()) {
                    TableCellParagraphEditor(
                        boxId = boxId,
                        address = blockAddress,
                        paragraph = block,
                        active = activeContentBlockIndex == contentBlockIndex,
                        selectionMode = selectionMode,
                        selected = selected,
                        controller = controller,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    TableCellStaticBlock(
                        boxId,
                        blockAddress,
                        RichContent(listOf(block)),
                        selectionMode,
                        selected,
                        controller,
                    )
                }
                is BlockFormula -> NestedFormulaEditor(
                    boxId = boxId,
                    address = blockAddress,
                    formula = block,
                    active = activeContentBlockIndex == contentBlockIndex,
                    enabled = !selectionMode && !selected,
                    controller = controller,
                )
                is BlockImage -> NestedImageEditor(
                    boxId = boxId,
                    address = blockAddress,
                    image = block,
                    active = activeContentBlockIndex == contentBlockIndex,
                    enabled = !selectionMode && !selected,
                    controller = controller,
                )
                is TableNode -> EditableTableGrid(
                    box = controller.currentCanvas.objects.filterIsInstance<com.neonote.model.RichContentBox>()
                        .firstOrNull { it.id == boxId } ?: return@forEachIndexed,
                    rootBlockIndex = rootBlockIndex,
                    table = block,
                    parentCellAddress = blockAddress,
                    tableBlockIndexInParent = contentBlockIndex,
                    activeAddress = activeAddress,
                    selectionMode = selectionMode,
                    selected = selected,
                    controller = controller,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (activeCell && !selectionMode && !selected) {
            CellInsertBar(boxId, address.withContentBlock(cell.content.blocks.size), controller)
        }
    }
}

@Composable
private fun TableCellParagraphEditor(
    boxId: String,
    address: TableCellAddress,
    paragraph: ParagraphNode,
    active: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    controller: NeoNoteEditorController,
    modifier: Modifier = Modifier,
) {
    val enabled = !selectionMode && !selected
    val requester = remember { FocusRequester() }
    val modelText = paragraph.plainTextForCellEditor()
    var value by remember(boxId, address) {
        mutableStateOf(TextFieldValue(modelText, TextRange(modelText.length)))
    }
    LaunchedEffect(active, enabled) {
        if (active && enabled) requester.requestFocus()
    }
    LaunchedEffect(modelText) {
        if (value.text != modelText) {
            val start = value.selection.start.coerceIn(0, modelText.length)
            val end = value.selection.end.coerceIn(0, modelText.length)
            value = TextFieldValue(modelText, TextRange(start, end))
        }
    }
    BasicTextField(
        value = value,
        onValueChange = { next ->
            value = next
            controller.updateRichContentTableCellFromPlatformInput(boxId, address, next.text)
        },
        enabled = enabled,
        singleLine = false,
        minLines = 1,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Default,
        ),
        textStyle = LocalTextStyle.current.copy(
            color = Color(0xFF0F172A),
            fontSize = 15.sp,
            lineHeight = 22.sp,
        ),
        cursorBrush = SolidColor(Color(0xFF6D4AFF)),
        modifier = modifier
            .heightIn(min = 28.dp)
            .focusRequester(requester)
            .onFocusChanged {
                if (it.isFocused && enabled) controller.focusRichContentTableCell(boxId, address)
            },
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth()) {
                if (value.text.isEmpty()) {
                    Text(
                        "Type…",
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun NestedFormulaEditor(
    boxId: String,
    address: TableCellAddress,
    formula: BlockFormula,
    active: Boolean,
    enabled: Boolean,
    controller: NeoNoteEditorController,
) {
    val rendered = remember(formula.expression) { MathExpressionFormatter.render(formula.expression) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF8F7FC))
            .border(1.dp, if (active) Color(0xFF6D4AFF) else Color(0xFFD7D2E0), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { controller.focusRichContentTableCell(boxId, address) }
            .padding(8.dp),
    ) {
        Text(
            rendered.displayText.ifBlank { "ƒ formula" },
            fontFamily = FontFamily.Serif,
            color = Color(0xFF262130),
        )
        if (active && enabled) {
            var source by remember(address) { mutableStateOf(formula.expression) }
            LaunchedEffect(formula.expression) {
                if (source != formula.expression) source = formula.expression
            }
            BasicTextField(
                value = source,
                onValueChange = {
                    source = it
                    controller.updateNestedFormula(boxId, address, expression = it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF262130),
                    fontFamily = FontFamily.Monospace,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                TextButton(
                    onClick = {
                        controller.updateNestedFormula(
                            boxId,
                            address,
                            displayMode = if (formula.displayMode == FormulaDisplayMode.Display) {
                                FormulaDisplayMode.Compact
                            } else {
                                FormulaDisplayMode.Display
                            },
                        )
                    },
                ) { Text("Mode") }
                TextButton(onClick = { controller.updateNestedFormula(boxId, address, numbered = !formula.numbered) }) {
                    Text("Number")
                }
                TextButton(onClick = { controller.deleteNestedBlock(boxId, address) }) {
                    Text("Delete", color = Color(0xFFB42318))
                }
            }
        }
    }
}

@Composable
private fun NestedImageEditor(
    boxId: String,
    address: TableCellAddress,
    image: BlockImage,
    active: Boolean,
    enabled: Boolean,
    controller: NeoNoteEditorController,
) {
    val context = LocalContext.current
    val store = remember(context) { FileAssetStore(FileAssetStore.defaultDirectory(context.filesDir)) }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, image.assetId) {
        value = withContext(Dispatchers.IO) {
            store.get(image.assetId)?.uri?.let(::decodeNestedBitmap)?.asImageBitmap()
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF3F1F6))
            .border(1.dp, if (active) Color(0xFF6D4AFF) else Color(0xFFD7D2E0), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { controller.focusRichContentTableCell(boxId, address) },
    ) {
        if (bitmap != null) {
            Image(
                requireNotNull(bitmap),
                contentDescription = image.altText,
                modifier = Modifier
                    .fillMaxWidth()
                    .height((image.height ?: 100f).coerceIn(60f, 260f).dp)
                    .graphicsLayer(rotationZ = image.rotationDegrees),
                contentScale = if (image.crop == ImageCrop()) ContentScale.Fit else ContentScale.Crop,
            )
        } else {
            Text("Image unavailable", Modifier.padding(10.dp), color = Color(0xFF756E83))
        }
        if (active && enabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                TextButton(
                    onClick = {
                        controller.updateNestedImage(boxId, address, height = (image.height ?: 100f) * 0.85f)
                    },
                ) { Text("Smaller") }
                TextButton(
                    onClick = {
                        controller.updateNestedImage(boxId, address, height = (image.height ?: 100f) * 1.18f)
                    },
                ) { Text("Larger") }
                TextButton(
                    onClick = {
                        controller.updateNestedImage(
                            boxId,
                            address,
                            rotationDegrees = (image.rotationDegrees + 90f) % 360f,
                        )
                    },
                ) { Text("Rotate") }
                TextButton(
                    onClick = {
                        controller.updateNestedImage(
                            boxId,
                            address,
                            crop = if (image.crop == ImageCrop()) {
                                ImageCrop(0.1f, 0.1f, 0.9f, 0.9f)
                            } else {
                                ImageCrop()
                            },
                        )
                    },
                ) { Text(if (image.crop == ImageCrop()) "Crop" else "Fit") }
                TextButton(onClick = { controller.deleteNestedBlock(boxId, address) }) {
                    Text("Delete", color = Color(0xFFB42318))
                }
            }
        }
    }
}

@Composable
private fun CellInsertBar(
    boxId: String,
    insertionAddress: TableCellAddress,
    controller: NeoNoteEditorController,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { FileAssetStore(FileAssetStore.defaultDirectory(context.filesDir)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val draft = context.contentResolver.readNestedImageDraft(uri) ?: return@launch
            val reference = store.put(draft)
            controller.insertNestedImage(boxId, insertionAddress, reference.id, reference.fileName)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TextButton(onClick = { controller.insertNestedFormula(boxId, insertionAddress) }) { Text("＋ Formula") }
        TextButton(onClick = { picker.launch("image/*") }) { Text("＋ Image") }
        TextButton(onClick = { controller.insertNestedTable(boxId, insertionAddress, 2, 2) }) { Text("＋ Table") }
    }
}

@Composable
private fun TableCellStaticBlock(
    boxId: String,
    address: TableCellAddress,
    content: RichContent,
    selectionMode: Boolean,
    selected: Boolean,
    controller: NeoNoteEditorController,
) {
    RichContentRenderer(
        content = content,
        selectionMode = selectionMode,
        selected = selected,
        onFocus = { controller.focusRichContentTableCell(boxId, address) },
        onToggleTodoChecked = {},
        modifier = Modifier.fillMaxWidth(),
        applyContentPadding = false,
        onObjectBlockFocus = { controller.focusRichContentTableCell(boxId, address) },
    )
}

internal fun TableCell.firstEditableParagraphIndex(): Int =
    content.blocks.indexOfFirst { it is ParagraphNode && it.isPlatformEditable() }.takeIf { it >= 0 } ?: 0

internal fun ParagraphNode.plainTextForCellEditor(): String = inlines.joinToString("") { inline ->
    when (inline) {
        is InlineText -> inline.text
        InlineLineBreak -> "\n"
        is InlineFormula, is InlineImage -> InlineAtomPlaceholder
    }
}

private fun ParagraphNode.isPlatformEditable(): Boolean = inlines.none { it is InlineFormula || it is InlineImage }

internal fun RichContent.previewTextForTableCell(): String = blocks.firstOrNull()?.let { block ->
    when (block) {
        is ParagraphNode -> block.plainTextForCellEditor()
        is BlockFormula -> MathExpressionFormatter.render(block.expression).displayText.ifBlank { "formula" }
        is BlockImage -> block.caption ?: block.altText?.takeIf(String::isNotBlank) ?: "image"
        is TableNode -> "nested table"
    }
}.orEmpty()

private fun decodeNestedBitmap(path: String): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (
        bounds.outWidth / sample > MaximumNestedDecodedImageDimension ||
        bounds.outHeight / sample > MaximumNestedDecodedImageDimension
    ) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}

private suspend fun android.content.ContentResolver.readNestedImageDraft(uri: Uri): AssetDraft? =
    withContext(Dispatchers.IO) {
        runCatching {
            val bytes = openInputStream(uri)?.use { it.readNestedBytesLimited(32 * 1024 * 1024) }
                ?: return@runCatching null
            AssetDraft(getType(uri) ?: "application/octet-stream", bytes, nestedDisplayName(uri))
        }.getOrNull()
    }

private fun android.content.ContentResolver.nestedDisplayName(uri: Uri): String? =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0) cursor.getString(index) else null
    }

private fun InputStream.readNestedBytesLimited(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        require(total <= limit) { "Image exceeds size limit" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private const val InlineAtomPlaceholder: String = "\uFFFC"
