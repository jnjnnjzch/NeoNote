package com.neonote

import android.content.ClipDescription
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonote.engine.InlineStyle
import com.neonote.engine.MathExpressionFormatter
import com.neonote.model.InlineFormula
import com.neonote.model.InlineImage
import com.neonote.model.InlineLineBreak
import com.neonote.model.InlineText
import com.neonote.model.ListKind
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContentBox
import com.neonote.model.TextAlignment

private val MinimumUnifiedEditorHeight = 48.dp
private val UnifiedBodyLineHeight = 24.sp

/** One platform text field for a paragraph-only box, enabling native cross-paragraph selection. */
@Composable
internal fun UnifiedRichTextEditor(
    box: RichContentBox,
    selectionMode: Boolean,
    selected: Boolean,
    controller: NeoNoteEditorController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    val requester = remember { FocusRequester() }
    val modelText = box.content.toUnifiedPlatformText()
    var value by remember(box.id) {
        mutableStateOf(TextFieldValue(modelText, TextRange(modelText.length)))
    }
    val editable = !selectionMode && !selected

    LaunchedEffect(box.isFocused, editable) {
        if (box.isFocused && editable) requester.requestFocus()
    }
    LaunchedEffect(modelText) {
        if (value.text != modelText) {
            value = value.copy(
                text = modelText,
                selection = TextRange(
                    value.selection.start.coerceIn(0, modelText.length),
                    value.selection.end.coerceIn(0, modelText.length),
                ),
                composition = null,
            )
        }
    }

    if (!box.isFocused) {
        RichContentRenderer(
            content = box.content,
            selectionMode = selectionMode,
            selected = selected,
            onFocus = { controller.activateRichContentBox(box.id) },
            onToggleTodoChecked = { controller.toggleRichContentTodoCheckedState(box.id, it) },
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight(unbounded = true)
                .heightIn(min = MinimumUnifiedEditorHeight),
            applyContentPadding = false,
        )
        return
    }

    BasicTextField(
        value = value,
        onValueChange = { next ->
            val previous = value
            value = next
            controller.updateRichContentFromPlatformInput(
                boxId = box.id,
                previousText = previous.text,
                nextText = next.text,
                selectionStart = next.selection.start,
                selectionEnd = next.selection.end,
                hasActiveComposition = next.composition != null,
            )
        },
        enabled = editable,
        singleLine = false,
        minLines = 1,
        maxLines = Int.MAX_VALUE,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Default,
        ),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            lineHeight = UnifiedBodyLineHeight,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = remember(box.content) {
            UnifiedRichTextVisualTransformation(box.content.blocks.filterIsInstance<ParagraphNode>())
        },
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(unbounded = true)
            .heightIn(min = MinimumUnifiedEditorHeight)
            .focusRequester(requester)
            .onFocusChanged {
                if (it.isFocused && editable && !box.isFocused) controller.activateRichContentBox(box.id)
            }
            .onPreviewKeyEvent { event ->
                val style = event.unifiedStyleShortcut()
                val list = event.unifiedListShortcut()
                val paste = event.unifiedPlainTextPaste(clipboard, context)
                when {
                    paste != null -> {
                        val start = minOf(value.selection.start, value.selection.end)
                        val end = maxOf(value.selection.start, value.selection.end)
                        val nextText = value.text.replaceRange(start, end, paste)
                        val cursor = start + paste.length
                        val previous = value
                        value = TextFieldValue(nextText, TextRange(cursor))
                        controller.updateRichContentFromPlatformInput(
                            box.id,
                            previous.text,
                            nextText,
                            cursor,
                            cursor,
                            hasActiveComposition = false,
                        )
                        true
                    }
                    style != null -> {
                        controller.toggleRichContentStyle(box.id, style, value.selection.start, value.selection.end)
                        true
                    }
                    list != null -> {
                        controller.toggleRichContentList(box.id, list, value.selection.start, value.selection.end)
                        true
                    }
                    else -> false
                }
            },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(unbounded = true)
                    .heightIn(min = MinimumUnifiedEditorHeight),
            ) {
                if (value.text.isEmpty()) {
                    Text(
                        "Start typing…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = UnifiedBodyLineHeight,
                        ),
                    )
                }
                inner()
            }
        },
    )
}

private class UnifiedRichTextVisualTransformation(
    private val paragraphs: List<ParagraphNode>,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val visible = text.text.toCharArray()
        val builder = AnnotatedString.Builder(text.text)
        var offset = 0
        paragraphs.forEachIndexed { paragraphIndex, paragraph ->
            val paragraphStart = offset
            paragraph.inlines.forEach { inline ->
                when (inline) {
                    is InlineText -> {
                        val end = (offset + inline.text.length).coerceAtMost(text.length)
                        if (end > offset) builder.addStyle(inline.toUnifiedSpanStyle(), offset, end)
                        offset = end
                    }
                    InlineLineBreak -> offset = (offset + 1).coerceAtMost(text.length)
                    is InlineFormula -> {
                        if (offset < visible.size) {
                            visible[offset] = MathExpressionFormatter.render(inline.expression).displayText
                                .firstOrNull { !it.isWhitespace() } ?: 'ƒ'
                            builder.addStyle(UnifiedAtomStyle, offset, offset + 1)
                        }
                        offset = (offset + 1).coerceAtMost(text.length)
                    }
                    is InlineImage -> {
                        if (offset < visible.size) {
                            visible[offset] = '▧'
                            builder.addStyle(UnifiedAtomStyle, offset, offset + 1)
                        }
                        offset = (offset + 1).coerceAtMost(text.length)
                    }
                }
            }
            val paragraphEnd = offset.coerceAtLeast(paragraphStart)
            if (paragraphEnd > paragraphStart) {
                builder.addStyle(paragraph.toUnifiedParagraphStyle(), paragraphStart, paragraphEnd)
                if (paragraph.style.headingLevel > 0) {
                    builder.addStyle(
                        SpanStyle(
                            fontSize = when (paragraph.style.headingLevel) {
                                1 -> 26.sp
                                2 -> 22.sp
                                else -> 18.sp
                            },
                            fontWeight = FontWeight.SemiBold,
                        ),
                        paragraphStart,
                        paragraphEnd,
                    )
                }
            }
            if (paragraphIndex < paragraphs.lastIndex) offset = (offset + 1).coerceAtMost(text.length)
        }
        val source = builder.toAnnotatedString()
        val styled = AnnotatedString.Builder(visible.concatToString()).also { target ->
            source.spanStyles.forEach { target.addStyle(it.item, it.start, it.end) }
            source.paragraphStyles.forEach { target.addStyle(it.item, it.start, it.end) }
        }.toAnnotatedString()
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

private fun ParagraphNode.toUnifiedParagraphStyle(): ParagraphStyle = ParagraphStyle(
    textAlign = when (style.alignment) {
        TextAlignment.Start -> TextAlign.Start
        TextAlignment.Center -> TextAlign.Center
        TextAlignment.End -> TextAlign.End
    },
    textIndent = TextIndent(
        firstLine = (style.indentLevel * 20).sp,
        restLine = (style.indentLevel * 20).sp,
    ),
    lineHeight = UnifiedBodyLineHeight,
)

private fun InlineText.toUnifiedSpanStyle(): SpanStyle {
    val decorations = buildList {
        if (underline) add(TextDecoration.Underline)
        if (strikethrough) add(TextDecoration.LineThrough)
    }
    return SpanStyle(
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
        color = textColorArgb?.let(::Color) ?: Color.Unspecified,
        background = highlightColorArgb?.let(::Color) ?: Color.Unspecified,
        fontSize = (16f * fontScale.coerceIn(0.5f, 4f)).sp,
    )
}

private val UnifiedAtomStyle = SpanStyle(
    color = Color(0xFF1E3A8A),
    background = Color(0xFFE0F2FE),
    fontWeight = FontWeight.SemiBold,
)

private fun com.neonote.model.RichContent.toUnifiedPlatformText(): String =
    blocks.filterIsInstance<ParagraphNode>().joinToString("\n") { paragraph ->
        paragraph.inlines.joinToString("") { inline ->
            when (inline) {
                is InlineText -> inline.text
                InlineLineBreak -> "\n"
                is InlineFormula, is InlineImage -> "\uFFFC"
            }
        }
    }

private fun androidx.compose.ui.input.key.KeyEvent.unifiedPlainTextPaste(
    clipboard: ClipboardManager?,
    context: android.content.Context,
): String? {
    if (type != KeyEventType.KeyDown || !isCtrlPressed || isShiftPressed || key != Key.V) return null
    val clip = clipboard?.primaryClip ?: return null
    if (!clip.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) || clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.replace("\r\n", "\n")?.replace('\r', '\n')
}

private fun androidx.compose.ui.input.key.KeyEvent.unifiedStyleShortcut(): InlineStyle? {
    if (type != KeyEventType.KeyDown || !isCtrlPressed) return null
    return when {
        !isShiftPressed && key == Key.B -> InlineStyle.Bold
        !isShiftPressed && key == Key.I -> InlineStyle.Italic
        !isShiftPressed && key == Key.U -> InlineStyle.Underline
        isShiftPressed && key == Key.X -> InlineStyle.Strikethrough
        else -> null
    }
}

private fun androidx.compose.ui.input.key.KeyEvent.unifiedListShortcut(): ListKind? {
    if (type != KeyEventType.KeyDown || !isCtrlPressed || !isShiftPressed) return null
    return when (key) {
        Key.B -> ListKind.Bullet
        Key.N -> ListKind.Numbered
        Key.T -> ListKind.Todo
        else -> null
    }
}
