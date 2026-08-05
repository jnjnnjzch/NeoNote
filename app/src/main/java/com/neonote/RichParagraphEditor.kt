package com.neonote

import android.content.ClipDescription
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonote.engine.FileAssetStore
import com.neonote.engine.InlineStyle
import com.neonote.engine.MathExpressionFormatter
import com.neonote.engine.RichContentLayoutDefaults
import com.neonote.model.InlineFormula
import com.neonote.model.InlineImage
import com.neonote.model.InlineLineBreak
import com.neonote.model.InlineText
import com.neonote.model.ListKind
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TextAlignment
import kotlinx.coroutines.launch

@Composable
internal fun RichParagraphEditor(
    box: RichContentBox,
    blockIndex: Int,
    paragraph: ParagraphNode,
    active: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    controller: NeoNoteEditorController,
    modifier: Modifier = Modifier,
) {
    if (!active) {
        RichContentRenderer(
            content = RichContent(blocks = listOf(paragraph)),
            selectionMode = selectionMode,
            selected = selected,
            onFocus = { controller.focusRichContentParagraph(box.id, blockIndex, paragraph.plainTextForEditor().length) },
            onToggleTodoChecked = { controller.toggleRichContentTodoCheckedState(box.id, blockIndex) },
            modifier = modifier,
            applyContentPadding = false,
        )
        return
    }

    val context = LocalContext.current
    val clipboardManager = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    val assetStore = remember(context) { FileAssetStore(FileAssetStore.defaultDirectory(context.filesDir)) }
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val modelText = paragraph.plainTextForEditor()
    var platformValue by remember(box.id, blockIndex) { mutableStateOf(TextFieldValue(modelText)) }

    LaunchedEffect(active, selectionMode, selected) {
        if (active && !selectionMode && !selected) focusRequester.requestFocus()
    }
    LaunchedEffect(modelText) {
        if (platformValue.text != modelText) {
            val start = platformValue.selection.start.coerceIn(0, modelText.length)
            val end = platformValue.selection.end.coerceIn(0, modelText.length)
            platformValue = TextFieldValue(modelText, TextRange(start, end))
        }
    }

    val paragraphTextStyle = LocalTextStyle.current.copy(
        color = Color(0xFF0F172A),
        lineHeight = RichContentLayoutDefaults.LineHeight.sp,
        fontSize = when (paragraph.style.headingLevel) {
            1 -> 24.sp
            2 -> 20.sp
            3 -> 17.sp
            else -> 14.sp
        },
        fontWeight = if (paragraph.style.headingLevel > 0) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = when (paragraph.style.alignment) {
            TextAlignment.Start -> TextAlign.Start
            TextAlignment.Center -> TextAlign.Center
            TextAlignment.End -> TextAlign.End
        },
    )
    val indent = (paragraph.style.indentLevel * 20).dp

    BasicTextField(
        value = platformValue,
        onValueChange = { nextValue ->
            val previousValue = platformValue
            platformValue = nextValue
            controller.updateRichContentParagraphFromPlatformInput(
                boxId = box.id,
                blockIndex = blockIndex,
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
        textStyle = paragraphTextStyle,
        cursorBrush = SolidColor(Color(0xFF7C3AED)),
        visualTransformation = remember(paragraph) { CompleteParagraphVisualTransformation(paragraph) },
        modifier = modifier
            .fillMaxWidth()
            .padding(start = indent)
            .heightIn(min = RichContentLayoutDefaults.LineHeight.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused && !selectionMode && !selected && !box.isFocused) {
                    controller.activateRichContentBox(box.id)
                }
            }
            .onPreviewKeyEvent { keyEvent ->
                val style = keyEvent.richContentShortcutStyle()
                val listKind = keyEvent.richContentShortcutListKind()
                val imageUri = if (keyEvent.isRichContentPasteShortcut()) clipboardManager?.primaryImageUri(context) else null
                val pastedText = keyEvent.richContentPlainTextPaste(clipboardManager, context)
                when {
                    imageUri != null && box.isFocused -> {
                        coroutineScope.launch {
                            val draft = context.contentResolver.readClipboardImageDraft(imageUri) ?: return@launch
                            val reference = assetStore.put(draft)
                            controller.insertRichContentImagePlaceholder(
                                boxId = box.id,
                                assetId = reference.id,
                                altText = reference.fileName ?: "Pasted image",
                            )
                        }
                        true
                    }
                    pastedText != null && box.isFocused -> {
                        val start = minOf(platformValue.selection.start, platformValue.selection.end)
                        val end = maxOf(platformValue.selection.start, platformValue.selection.end)
                        val nextText = platformValue.text.replaceRange(start, end, pastedText)
                        val nextCursor = start + pastedText.length
                        val previousValue = platformValue
                        platformValue = TextFieldValue(nextText, TextRange(nextCursor))
                        controller.updateRichContentParagraphFromPlatformInput(
                            boxId = box.id,
                            blockIndex = blockIndex,
                            previousText = previousValue.text,
                            nextText = nextText,
                            selectionStart = nextCursor,
                            selectionEnd = nextCursor,
                            hasActiveComposition = false,
                        )
                        true
                    }
                    style != null && box.isFocused -> {
                        controller.toggleRichContentParagraphStyle(
                            boxId = box.id,
                            blockIndex = blockIndex,
                            style = style,
                            selectionStart = platformValue.selection.start,
                            selectionEnd = platformValue.selection.end,
                        )
                        true
                    }
                    listKind != null && box.isFocused -> {
                        controller.toggleRichContentParagraphList(
                            boxId = box.id,
                            blockIndex = blockIndex,
                            kind = listKind,
                            selectionStart = platformValue.selection.start,
                            selectionEnd = platformValue.selection.end,
                        )
                        true
                    }
                    else -> false
                }
            },
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (platformValue.text.isEmpty()) {
                    Text("Start typing…", color = Color(0xFF94A3B8), style = paragraphTextStyle)
                }
                innerTextField()
            }
        },
    )
}

private fun ParagraphNode.plainTextForEditor(): String = inlines.joinToString("") { inline ->
    when (inline) {
        is InlineText -> inline.text
        InlineLineBreak -> "\n"
        is InlineFormula, is InlineImage -> InlineAtomPlaceholder
    }
}

private const val InlineAtomPlaceholder: String = "\uFFFC"

/** Preserves one model character per platform character, keeping IME offsets identity-mapped. */
private class CompleteParagraphVisualTransformation(
    private val paragraph: ParagraphNode,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val visible = text.text.toCharArray()
        val spans = mutableListOf<Triple<SpanStyle, Int, Int>>()
        var offset = 0
        paragraph.inlines.forEach { inline ->
            when (inline) {
                is InlineText -> {
                    val end = (offset + inline.text.length).coerceAtMost(text.length)
                    if (end > offset) spans += Triple(inline.completeSpanStyle(), offset, end)
                    offset = end
                }
                InlineLineBreak -> offset = (offset + 1).coerceAtMost(text.length)
                is InlineFormula -> {
                    if (offset < visible.size) visible[offset] = formulaGlyph(inline.expression)
                    if (offset < text.length) spans += Triple(inlineAtomStyle, offset, offset + 1)
                    offset = (offset + 1).coerceAtMost(text.length)
                }
                is InlineImage -> {
                    if (offset < visible.size) visible[offset] = '▧'
                    if (offset < text.length) spans += Triple(inlineAtomStyle, offset, offset + 1)
                    offset = (offset + 1).coerceAtMost(text.length)
                }
            }
        }
        val builder = AnnotatedString.Builder(visible.concatToString())
        spans.forEach { (style, start, end) -> builder.addStyle(style, start, end) }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    private fun formulaGlyph(expression: String): Char {
        val display = MathExpressionFormatter.render(expression).displayText
        return display.firstOrNull { !it.isWhitespace() } ?: 'ƒ'
    }
}

private fun InlineText.completeSpanStyle(): SpanStyle {
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
        fontSize = (14f * fontScale.coerceIn(0.5f, 4f)).sp,
    )
}

private val inlineAtomStyle = SpanStyle(
    color = Color(0xFF1E3A8A),
    background = Color(0xFFE0F2FE),
    fontWeight = FontWeight.SemiBold,
)

private fun androidx.compose.ui.input.key.KeyEvent.isRichContentPasteShortcut(): Boolean =
    type == KeyEventType.KeyDown && isCtrlPressed && !isShiftPressed && key == Key.V

private fun androidx.compose.ui.input.key.KeyEvent.richContentPlainTextPaste(
    clipboardManager: ClipboardManager?,
    context: android.content.Context,
): String? {
    if (!isRichContentPasteShortcut()) return null
    val clip = clipboardManager?.primaryClip ?: return null
    if (!clip.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) || clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.replace("\r\n", "\n")?.replace('\r', '\n')
}

private fun androidx.compose.ui.input.key.KeyEvent.richContentShortcutStyle(): InlineStyle? {
    if (type != KeyEventType.KeyDown || !isCtrlPressed) return null
    return when {
        !isShiftPressed && key == Key.B -> InlineStyle.Bold
        !isShiftPressed && key == Key.I -> InlineStyle.Italic
        !isShiftPressed && key == Key.U -> InlineStyle.Underline
        isShiftPressed && key == Key.X -> InlineStyle.Strikethrough
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
