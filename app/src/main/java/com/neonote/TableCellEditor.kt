package com.neonote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonote.engine.ActiveRichContentTarget
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
import com.neonote.model.ListKind
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.TableCell
import com.neonote.model.TableCellAddress
import com.neonote.model.TableNode
import com.neonote.model.TextAlignment
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
    var numberedItem = 0
    var numberedFormula = 0
    Column(
        modifier = modifier.padding(horizontal = 7.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(RichContentLayoutDefaults.BlockSpacing.dp),
    ) {
        blocks.forEachIndexed { contentBlockIndex, block ->
            if (block is ParagraphNode && block.listMetadata?.kind == ListKind.Numbered) numberedItem++
            else if (block !is ParagraphNode || block.listMetadata?.kind != ListKind.Numbered) numberedItem = 0
            val blockAddress = address.withContentBlock(contentBlockIndex)
            val formulaNumber = if (block is BlockFormula && block.numbered) ++numberedFormula else null
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
                        markerNumber = numberedItem,
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
                    formulaNumber = formulaNumber,
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
    markerNumber: Int = 0,
    modifier: Modifier = Modifier,
) {
    val enabled = !selectionMode && !selected
    val requester = remember { FocusRequester() }
    val modelText = paragraph.plainTextForCellEditor()
    val paragraphTextStyle = LocalTextStyle.current.copy(
        color = Color(0xFF0F172A),
        fontSize = when (paragraph.style.headingLevel) {
            1 -> 20.sp
            2 -> 18.sp
            3 -> 16.sp
            else -> 15.sp
        },
        lineHeight = when (paragraph.style.headingLevel) {
            1 -> 27.sp
            2 -> 25.sp
            else -> 22.sp
        },
        fontWeight = if (paragraph.style.headingLevel > 0) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = when (paragraph.style.alignment) {
            TextAlignment.Start -> TextAlign.Start
            TextAlignment.Center -> TextAlign.Center
            TextAlignment.End -> TextAlign.End
        },
    )
    val indent = (paragraph.style.indentLevel * 16).dp
    val restoredSelection = controller.activeRichContentTableCellSelection(boxId, address)
    var value by remember(boxId, address) {
        val start = restoredSelection?.start?.coerceIn(0, modelText.length) ?: modelText.length
        val end = restoredSelection?.end?.coerceIn(0, modelText.length) ?: start
        mutableStateOf(TextFieldValue(modelText, TextRange(start, end)))
    }
    LaunchedEffect(active, enabled) {
        if (active && enabled) requester.requestFocus()
    }
    LaunchedEffect(modelText) {
        if (value.text != modelText) {
            val restored = controller.activeRichContentTableCellSelection(boxId, address)
            val start = (restored?.start ?: value.selection.start).coerceIn(0, modelText.length)
            val end = (restored?.end ?: value.selection.end).coerceIn(0, modelText.length)
            value = TextFieldValue(modelText, TextRange(start, end))
        }
    }
    Row(modifier = modifier.fillMaxWidth().padding(start = indent), verticalAlignment = Alignment.Top) {
        when (paragraph.listMetadata?.kind) {
            ListKind.Bullet -> Text("•", Modifier.padding(end = 5.dp), color = Color(0xFF475569), style = paragraphTextStyle)
            ListKind.Numbered -> Text("${markerNumber.coerceAtLeast(1)}.", Modifier.padding(end = 5.dp), color = Color(0xFF475569), style = paragraphTextStyle)
            ListKind.Todo -> CompactTodoCheckbox(
                checked = paragraph.listMetadata.checked,
                onCheckedChange = { controller.toggleRichContentTableCellTodoCheckedState(boxId, address) },
                enabled = enabled,
            )
            null -> Unit
        }
        BasicTextField(
            value = value,
            onValueChange = { next ->
                val previous = value
                value = next
                controller.updateRichContentTableCellFromPlatformInput(
                    boxId,
                    address,
                    previous.text,
                    next.text,
                    next.selection.start,
                    next.selection.end,
                )
            },
            enabled = enabled,
            readOnly = !active,
            singleLine = false,
            minLines = 1,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Default,
            ),
            textStyle = paragraphTextStyle,
            visualTransformation = remember(paragraph) { CompleteParagraphVisualTransformation(paragraph) },
            cursorBrush = SolidColor(Color(0xFF6D4AFF)),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 24.dp)
                .focusRequester(requester)
                .onFocusChanged {
                    if (it.isFocused && enabled && !active) controller.focusRichContentTableCell(
                        boxId,
                        address,
                        value.selection.start,
                        value.selection.end,
                    )
                }
                .onPreviewKeyEvent { event ->
                    val style = event.tableCellShortcutStyle()
                    val list = event.tableCellShortcutListKind()
                    when {
                        active && event.type == KeyEventType.KeyDown && event.key == Key.Enter && event.isShiftPressed -> {
                            controller.insertActiveRichContentLineBreak(boxId)
                            true
                        }
                        active && event.type == KeyEventType.KeyDown && event.key == Key.Tab -> {
                            controller.moveActiveRichContentTableCell(boxId, forward = !event.isShiftPressed)
                            true
                        }
                        active && style != null -> {
                            controller.toggleActiveRichContentStyle(boxId, style)
                            true
                        }
                        active && list != null -> {
                            controller.toggleActiveRichContentList(boxId, list)
                            true
                        }
                        else -> false
                    }
                },
            decorationBox = { inner -> Box(Modifier.fillMaxWidth()) { inner() } },
        )
    }

}

@Composable
private fun NestedFormulaEditor(
    boxId: String,
    address: TableCellAddress,
    formula: BlockFormula,
    active: Boolean,
    enabled: Boolean,
    formulaNumber: Int?,
    controller: NeoNoteEditorController,
) {
    val rendered = remember(formula.expression) { MathExpressionFormatter.render(formula.expression) }
    var menuVisible by remember(address) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { controller.focusRichContentTableCell(boxId, address) }
            .then(
                if (active) Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFF6D4AFF).copy(alpha = .55f), RoundedCornerShape(6.dp))
                    .padding(6.dp)
                else Modifier.padding(vertical = 3.dp)
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                rendered.displayText.ifBlank { if (active) "Enter formula" else "" },
                modifier = Modifier.weight(1f),
                fontFamily = FontFamily.Serif,
                fontSize = if (formula.displayMode == FormulaDisplayMode.Display) 18.sp else 15.sp,
                color = Color(0xFF262130),
            )
            formulaNumber?.let {
                Text("($it)", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
            if (active && enabled) {
                Box {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { menuVisible = true },
                        contentAlignment = Alignment.Center,
                    ) { Text("⋮") }
                    DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }) {
                        DropdownMenuItem(
                            text = { Text(if (formula.displayMode == FormulaDisplayMode.Display) "Compact size" else "Display size") },
                            onClick = {
                                menuVisible = false
                                controller.updateNestedFormula(
                                    boxId,
                                    address,
                                    displayMode = if (formula.displayMode == FormulaDisplayMode.Display) FormulaDisplayMode.Compact else FormulaDisplayMode.Display,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (formula.numbered) "Remove number" else "Number formula") },
                            onClick = {
                                menuVisible = false
                                controller.updateNestedFormula(boxId, address, numbered = !formula.numbered)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete formula", color = MaterialTheme.colorScheme.error) },
                            onClick = { menuVisible = false; controller.deleteNestedBlock(boxId, address) },
                        )
                    }
                }
            }
        }
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
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    controller.continueAfterNestedContentBlock(boxId, address)
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp)
                    .onPreviewKeyEvent { event ->
                        when {
                            event.type == KeyEventType.KeyDown && event.key == Key.Enter -> {
                                controller.continueAfterNestedContentBlock(boxId, address)
                                true
                            }
                            event.type == KeyEventType.KeyDown && event.key == Key.Tab -> {
                                controller.moveActiveRichContentTableCell(boxId, forward = !event.isShiftPressed)
                                true
                            }
                            else -> false
                        }
                    },
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF262130),
                    fontFamily = FontFamily.Monospace,
                ),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                    ) { inner() }
                },
            )
            rendered.errors.forEach {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
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
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val store = remember(context) { FileAssetStore(FileAssetStore.defaultDirectory(context.filesDir)) }
    val scope = rememberCoroutineScope()
    val replacementPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val draft = context.contentResolver.readEditorImageAssetDraft(uri) ?: return@launch
            val replacement = store.put(draft)
            controller.updateNestedImage(boxId, address, replacementAssetId = replacement.id)
        }
    }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, image.assetId) {
        value = withContext(Dispatchers.IO) {
            store.get(image.assetId)?.uri?.let(::decodeNestedBitmap)?.asImageBitmap()
        }
    }
    val shape = RoundedCornerShape(7.dp)
    var menuVisible by remember(address) { mutableStateOf(false) }
    var resizeHeight by remember(address, image.height) { mutableStateOf(image.height ?: 100f) }
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (active) Modifier.border(1.dp, Color(0xFF6D4AFF).copy(alpha = .6f), shape) else Modifier)
            .clickable(enabled = enabled) { controller.focusRichContentTableCell(boxId, address) },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height((image.height ?: 100f).coerceIn(60f, 420f).dp)
                .clip(shape)
                .background(Color(0xFFF3F1F6)),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    requireNotNull(bitmap),
                    contentDescription = image.altText,
                    modifier = Modifier.fillMaxWidth().height((image.height ?: 100f).coerceIn(60f, 420f).dp)
                        .graphicsLayer(rotationZ = image.rotationDegrees),
                    contentScale = if (image.crop == ImageCrop()) ContentScale.Fit else ContentScale.Crop,
                )
            } else {
                Text("Image unavailable", Modifier.padding(10.dp), color = Color(0xFF756E83))
            }
            if (active && enabled) {
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = .92f))
                            .clickable { menuVisible = true },
                        contentAlignment = Alignment.Center,
                    ) { Text("⋮") }
                    DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }) {
                        DropdownMenuItem(
                            text = { Text("Replace image…") },
                            onClick = { menuVisible = false; replacementPicker.launch("image/*") },
                        )
                        DropdownMenuItem(
                            text = { Text(if (image.crop == ImageCrop()) "Fill frame" else "Fit image") },
                            onClick = {
                                menuVisible = false
                                controller.updateNestedImage(
                                    boxId,
                                    address,
                                    crop = if (image.crop == ImageCrop()) ImageCrop(.08f, .08f, .92f, .92f) else ImageCrop(),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Rotate clockwise") },
                            onClick = {
                                menuVisible = false
                                controller.updateNestedImage(boxId, address, rotationDegrees = (image.rotationDegrees + 90f) % 360f)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete image", color = MaterialTheme.colorScheme.error) },
                            onClick = { menuVisible = false; controller.deleteNestedBlock(boxId, address) },
                        )
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 6.dp)
                        .size(width = 58.dp, height = 26.dp)
                        .pointerInput(address) {
                            detectDragGestures(
                                onDragStart = {
                                    resizeHeight = image.height ?: 100f
                                    controller.beginRichContentGesture(boxId)
                                },
                                onDragCancel = { controller.cancelRichContentGesture(boxId) },
                                onDragEnd = { controller.endRichContentGesture(boxId) },
                                onDrag = { change, drag ->
                                    change.consume()
                                    resizeHeight = (resizeHeight + drag.y / density).coerceIn(60f, 420f)
                                    controller.updateNestedImage(boxId, address, height = resizeHeight)
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(width = 30.dp, height = 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        if (active && enabled) {
            BasicTextField(
                value = image.caption.orEmpty(),
                onValueChange = { controller.updateNestedImage(boxId, address, caption = it.take(300)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 7.dp, vertical = 6.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Tab) {
                            controller.moveActiveRichContentTableCell(boxId, forward = !event.isShiftPressed)
                            true
                        } else false
                    },
                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    Box {
                        if (image.caption.isNullOrBlank()) Text(
                            "Add caption…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .68f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        inner()
                    }
                },
            )
        } else if (!image.caption.isNullOrBlank()) {
            Text(
                image.caption,
                Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 5.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
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

private fun androidx.compose.ui.input.key.KeyEvent.tableCellShortcutStyle(): com.neonote.engine.InlineStyle? {
    if (type != KeyEventType.KeyDown || !isCtrlPressed) return null
    return when {
        !isShiftPressed && key == Key.B -> com.neonote.engine.InlineStyle.Bold
        !isShiftPressed && key == Key.I -> com.neonote.engine.InlineStyle.Italic
        !isShiftPressed && key == Key.U -> com.neonote.engine.InlineStyle.Underline
        isShiftPressed && key == Key.X -> com.neonote.engine.InlineStyle.Strikethrough
        else -> null
    }
}

private fun androidx.compose.ui.input.key.KeyEvent.tableCellShortcutListKind(): ListKind? {
    if (type != KeyEventType.KeyDown || !isCtrlPressed || !isShiftPressed) return null
    return when (key) {
        Key.B -> ListKind.Bullet
        Key.N -> ListKind.Numbered
        Key.T -> ListKind.Todo
        else -> null
    }
}

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
