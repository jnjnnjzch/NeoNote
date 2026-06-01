package com.neonote

import android.content.ClipDescription
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.neonote.engine.InlineStyle
import com.neonote.engine.toPlainText
import com.neonote.model.BlockFormula
import com.neonote.model.BlockImage
import com.neonote.model.InlineFormula
import com.neonote.model.InlineImage
import com.neonote.model.InlineLineBreak
import com.neonote.model.InlineText
import com.neonote.model.ListKind
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TableNode
import kotlin.math.roundToInt

@Composable
internal fun RichContentBoxView(
    box: RichContentBox,
    selected: Boolean,
    selectionMode: Boolean,
    controller: NeoNoteEditorController,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val clipboardManager = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(box.isFocused, selectionMode, selected) {
        if (selectionMode || selected) {
            focusManager.clearFocus()
        } else if (box.isFocused) {
            focusRequester.requestFocus()
        }
    }
    val borderColor = when {
        selected -> Color(0xFF2563EB)
        box.isFocused -> Color(0xFF7C3AED)
        else -> Color(0xFFE2E8F0)
    }
    val borderWidth = when {
        selected || box.isFocused -> 2.dp
        else -> 1.dp
    }
    val modifier = Modifier
        .offset { IntOffset(box.position.x.roundToInt(), box.position.y.roundToInt()) }
        .size(
            width = with(density) { box.size.width.toDp() },
            height = with(density) { box.size.height.toDp() },
        )
        .clip(RoundedCornerShape(14.dp))
        .background(Color.White)
        .border(width = borderWidth, color = borderColor, shape = RoundedCornerShape(14.dp))

    val contentModifier = modifier
        .padding(8.dp)
        .then(
            if (selectionMode) {
                Modifier.pointerInput(box.id, selectionMode) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        controller.activateRichContentBox(box.id)
                    }
                }
            } else {
                Modifier
            },
        )

    var platformTextFieldValue by remember(box.id) { mutableStateOf(TextFieldValue(box.toPlainText())) }
    val modelText = box.toPlainText()
    LaunchedEffect(modelText, box.isFocused) {
        if (!box.isFocused && platformTextFieldValue.text != modelText) {
            platformTextFieldValue = TextFieldValue(modelText)
        }
    }

    Box(modifier = contentModifier) {
        if (!box.isFocused) {
            RichContentDisplay(
                box = box,
                selectionMode = selectionMode,
                selected = selected,
                onFocus = { controller.activateRichContentBox(box.id) },
                onToggleTodoChecked = { blockIndex ->
                    controller.toggleRichContentTodoCheckedState(boxId = box.id, blockIndex = blockIndex)
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            TextField(
                value = platformTextFieldValue,
                onValueChange = { nextValue ->
                    val previousValue = platformTextFieldValue
                    platformTextFieldValue = nextValue
                    controller.updateRichContentFromPlatformInput(
                        boxId = box.id,
                        previousText = previousValue.text,
                        nextText = nextValue.text,
                        selectionStart = nextValue.selection.start,
                        selectionEnd = nextValue.selection.end,
                        hasActiveComposition = nextValue.composition != null,
                    )
                },
                enabled = !selectionMode && !selected,
                singleLine = false,
                minLines = 1,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { keyEvent ->
                        val style = keyEvent.richContentShortcutStyle()
                        val listKind = keyEvent.richContentShortcutListKind()
                        val pastedText = keyEvent.richContentPlainTextPaste(clipboardManager, context)
                        when {
                            pastedText != null && box.isFocused -> {
                                val selectionStart = minOf(platformTextFieldValue.selection.start, platformTextFieldValue.selection.end)
                                val selectionEnd = maxOf(platformTextFieldValue.selection.start, platformTextFieldValue.selection.end)
                                val nextText = platformTextFieldValue.text.replaceRange(selectionStart, selectionEnd, pastedText)
                                val nextCursor = selectionStart + pastedText.length
                                val previousValue = platformTextFieldValue
                                platformTextFieldValue = TextFieldValue(
                                    text = nextText,
                                    selection = TextRange(nextCursor),
                                )
                                controller.updateRichContentFromPlatformInput(
                                    boxId = box.id,
                                    previousText = previousValue.text,
                                    nextText = nextText,
                                    selectionStart = nextCursor,
                                    selectionEnd = nextCursor,
                                    hasActiveComposition = false,
                                )
                                true
                            }
                            style != null && box.isFocused -> {
                                controller.toggleRichContentStyle(
                                    boxId = box.id,
                                    style = style,
                                    selectionStart = platformTextFieldValue.selection.start,
                                    selectionEnd = platformTextFieldValue.selection.end,
                                )
                                true
                            }
                            listKind != null && box.isFocused -> {
                                controller.toggleRichContentList(
                                    boxId = box.id,
                                    kind = listKind,
                                    selectionStart = platformTextFieldValue.selection.start,
                                    selectionEnd = platformTextFieldValue.selection.end,
                                )
                                true
                            }
                            else -> false
                        }
                    }
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && !selectionMode && !selected && !box.isFocused) {
                            controller.activateRichContentBox(box.id)
                        } else if (!focusState.isFocused && box.isFocused) {
                            controller.commitRichContentEditing(box.id)
                        }
                    },
                placeholder = { Text("Start typing…") },
            )
        }
    }
}

@Composable
private fun RichContentDisplay(
    box: RichContentBox,
    selectionMode: Boolean,
    selected: Boolean,
    onFocus: () -> Unit,
    onToggleTodoChecked: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusModifier = if (!selectionMode && !selected) {
        Modifier.clickable(onClick = onFocus)
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .then(focusModifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (box.content.blocks.isEmpty()) {
            Text("Start typing…", color = Color(0xFF94A3B8))
        }
        var numberedIndex = 0
        var previousNumbered = false
        box.content.blocks.forEachIndexed { blockIndex, block ->
            when (block) {
                is ParagraphNode -> {
                    val metadata = block.listMetadata
                    val markerNumber = if (metadata?.kind == ListKind.Numbered) {
                        numberedIndex = if (previousNumbered) numberedIndex + 1 else 1
                        previousNumbered = true
                        numberedIndex
                    } else {
                        previousNumbered = false
                        numberedIndex = 0
                        0
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (metadata?.kind) {
                            ListKind.Bullet -> Text("•", modifier = Modifier.padding(end = 8.dp), color = Color(0xFF334155))
                            ListKind.Numbered -> Text("$markerNumber.", modifier = Modifier.padding(end = 8.dp), color = Color(0xFF334155))
                            ListKind.Todo -> Checkbox(
                                checked = metadata.checked,
                                onCheckedChange = { onToggleTodoChecked(blockIndex) },
                                enabled = !selectionMode && !selected,
                            )
                            null -> Unit
                        }
                        Text(text = block.displayText(), color = Color(0xFF0F172A))
                    }
                }
                is BlockFormula -> {
                    previousNumbered = false
                    numberedIndex = 0
                    RichBlockPlaceholder(label = "Formula", text = block.expression.ifBlank { "empty expression" })
                }
                is BlockImage -> {
                    previousNumbered = false
                    numberedIndex = 0
                    RichBlockPlaceholder(label = "Image", text = block.imagePlaceholderText())
                }
                is TableNode -> {
                    previousNumbered = false
                    numberedIndex = 0
                    StaticTablePlaceholder(table = block)
                }
            }
        }
    }
}

@Composable
private fun RichBlockPlaceholder(label: String, text: String) {
    Text(
        text = "$label: $text",
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF1F5F9))
            .border(width = 1.dp, color = Color(0xFFCBD5E1), shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        color = Color(0xFF334155),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun StaticTablePlaceholder(table: TableNode) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(width = 1.dp, color = Color(0xFFCBD5E1)),
    ) {
        if (table.rows.isEmpty()) {
            Text(
                text = "Table: 0 × 0",
                modifier = Modifier.padding(8.dp),
                color = Color(0xFF64748B),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            table.rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { cell ->
                        Text(
                            text = cell.content.cellPreviewText(),
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .border(width = 1.dp, color = Color(0xFFCBD5E1))
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            color = Color(0xFF334155),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

private fun ParagraphNode.displayText(): String = inlines.joinToString("") { inline ->
    when (inline) {
        is InlineText -> inline.text
        InlineLineBreak -> "\n"
        is InlineFormula -> inline.expression
        is InlineImage -> inline.imagePlaceholderText()
    }
}

private fun InlineImage.imagePlaceholderText(): String = altText?.takeIf { it.isNotBlank() } ?: "asset:$assetId"

private fun BlockImage.imagePlaceholderText(): String = altText?.takeIf { it.isNotBlank() } ?: "asset:$assetId"

private fun RichContent.cellPreviewText(): String = blocks.firstOrNull()?.let { block ->
    when (block) {
        is ParagraphNode -> block.displayText().ifBlank { " " }
        is BlockFormula -> block.expression.ifBlank { "formula" }
        is BlockImage -> block.imagePlaceholderText()
        is TableNode -> "nested table"
    }
} ?: " "

private fun androidx.compose.ui.input.key.KeyEvent.richContentPlainTextPaste(
    clipboardManager: ClipboardManager?,
    context: android.content.Context,
): String? {
    if (type != KeyEventType.KeyDown || !isCtrlPressed || isShiftPressed || key != Key.V) return null
    val clip = clipboardManager?.primaryClip ?: return null
    val description = clip.description
    if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) || clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.replace("\r\n", "\n")?.replace('\r', '\n')
}

private fun androidx.compose.ui.input.key.KeyEvent.richContentShortcutStyle(): InlineStyle? {
    if (type != KeyEventType.KeyDown || !isCtrlPressed || isShiftPressed) return null
    return when (key) {
        Key.B -> InlineStyle.Bold
        Key.I -> InlineStyle.Italic
        Key.U -> InlineStyle.Underline
        else -> null
    }
}

private fun androidx.compose.ui.input.key.KeyEvent.richContentShortcutListKind(): ListKind? {
    if (type != KeyEventType.KeyDown || !isCtrlPressed || !isShiftPressed) return null
    return when (key) {
        Key.B -> ListKind.Bullet
        Key.N -> ListKind.Numbered
        Key.T -> ListKind.Todo
        else -> null
    }
}
