package com.neonote

import android.content.ClipDescription
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.neonote.engine.InlineStyle
import com.neonote.engine.MathExpressionFormatter
import com.neonote.model.InlineFormula
import com.neonote.model.InlineImage
import com.neonote.model.InlineLineBreak
import com.neonote.model.InlineText
import com.neonote.model.ListKind
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TextAlignment

private val RichParagraphBodySize = 16.sp
private val RichParagraphLineHeight = 24.sp

@Composable
internal fun RichParagraphEditor(
    box: RichContentBox,
    blockIndex: Int,
    paragraph: ParagraphNode,
    active: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    controller: NeoNoteEditorController,
    markerNumber: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboardManager = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    val focusRequester = remember { FocusRequester() }
    val modelText = paragraph.plainTextForEditor()
    val restoredSelection = controller.activeRichContentParagraphSelection(box.id, blockIndex)
    var platformValue by remember(box.id, blockIndex) {
        val start = restoredSelection?.start?.coerceIn(0, modelText.length) ?: modelText.length
        val end = restoredSelection?.end?.coerceIn(0, modelText.length) ?: start
        mutableStateOf(TextFieldValue(modelText, TextRange(start, end)))
    }
    val editable = !selectionMode && !selected
    LaunchedEffect(active, editable) { if (active && editable) focusRequester.requestFocus() }
    LaunchedEffect(modelText) {
        if (platformValue.text != modelText) {
            val restored = controller.activeRichContentParagraphSelection(box.id, blockIndex)
            val start = (restored?.start ?: platformValue.selection.start).coerceIn(0, modelText.length)
            val end = (restored?.end ?: platformValue.selection.end).coerceIn(0, modelText.length)
            platformValue = TextFieldValue(modelText, TextRange(start, end))
        }
    }
    val paragraphTextStyle = LocalTextStyle.current.copy(
        color = Color(0xFF0F172A), lineHeight = RichParagraphLineHeight,
        fontSize = when (paragraph.style.headingLevel) { 1 -> 26.sp; 2 -> 22.sp; 3 -> 18.sp; else -> RichParagraphBodySize },
        fontWeight = if (paragraph.style.headingLevel > 0) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = when (paragraph.style.alignment) { TextAlignment.Start -> TextAlign.Start; TextAlignment.Center -> TextAlign.Center; TextAlignment.End -> TextAlign.End },
    )
    val indent = (paragraph.style.indentLevel * 20).dp
    Row(modifier = modifier.fillMaxWidth().padding(start = indent), verticalAlignment = Alignment.Top) {
        when (paragraph.listMetadata?.kind) {
            ListKind.Bullet -> Text("•", Modifier.padding(end = 8.dp).widthIn(min = 18.dp), color = Color(0xFF334155), style = paragraphTextStyle)
            ListKind.Numbered -> Text("${markerNumber.coerceAtLeast(1)}.", Modifier.padding(end = 8.dp).widthIn(min = 24.dp), color = Color(0xFF334155), style = paragraphTextStyle)
            ListKind.Todo -> CompactTodoCheckbox(
                checked = paragraph.listMetadata.checked,
                onCheckedChange = { controller.toggleRichContentTodoCheckedState(box.id, blockIndex) },
                enabled = editable,
            )
            null -> Unit
        }
        BasicTextField(
            value = platformValue,
            onValueChange = { next ->
                val previous = platformValue; platformValue = next
                controller.updateRichContentParagraphFromPlatformInput(box.id, blockIndex, previous.text, next.text,
                    next.selection.start, next.selection.end, next.composition != null)
            },
            enabled = editable, readOnly = !active, singleLine = false, minLines = 1,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
            textStyle = paragraphTextStyle, cursorBrush = SolidColor(Color(0xFF7C3AED)),
            visualTransformation = remember(paragraph) { CompleteParagraphVisualTransformation(paragraph) },
            modifier = Modifier.weight(1f).heightIn(min = 24.dp).focusRequester(focusRequester)
                .onFocusChanged { fs -> if (fs.isFocused && editable && !active) {
                    controller.focusRichContentParagraph(box.id, blockIndex, platformValue.selection.start, platformValue.selection.end)
                } }
                .onPreviewKeyEvent { event ->
                    val style = event.richContentShortcutStyle(); val list = event.richContentShortcutListKind()
                    val paste = event.richContentPlainTextPaste(clipboardManager, context)
                    when {
                        active && event.type == KeyEventType.KeyDown && event.key == Key.Enter && event.isShiftPressed -> {
                            controller.insertActiveRichContentLineBreak(box.id)
                            true
                        }
                        active && event.type == KeyEventType.KeyDown && event.key == Key.Tab -> {
                            controller.changeActiveParagraphIndent(box.id, if (event.isShiftPressed) -1 else 1)
                            true
                        }
                        paste != null && active -> {
                            val a=minOf(platformValue.selection.start, platformValue.selection.end); val b=maxOf(platformValue.selection.start, platformValue.selection.end)
                            val nextText=platformValue.text.replaceRange(a,b,paste); val cursor=a+paste.length; val prev=platformValue
                            platformValue=TextFieldValue(nextText, TextRange(cursor))
                            controller.updateRichContentParagraphFromPlatformInput(box.id,blockIndex,prev.text,nextText,cursor,cursor,false); true
                        }
                        style != null && active -> { controller.toggleRichContentParagraphStyle(box.id,blockIndex,style,platformValue.selection.start,platformValue.selection.end); true }
                        list != null && active -> { controller.toggleRichContentParagraphList(box.id,blockIndex,list,platformValue.selection.start,platformValue.selection.end); true }
                        else -> false
                    }
                },
            decorationBox = { inner -> Box(Modifier.fillMaxWidth()) {
                if (platformValue.text.isEmpty() && active) Text("Start typing…", color = Color(0xFF94A3B8), style = paragraphTextStyle)
                inner()
            } },
        )
    }
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
internal class CompleteParagraphVisualTransformation(
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
        fontSize = (16f * fontScale.coerceIn(0.5f, 4f)).sp,
    )
}

private val inlineAtomStyle = SpanStyle(
    color = Color(0xFF1E3A8A),
    background = Color(0xFFE0F2FE),
    fontWeight = FontWeight.SemiBold,
)

private fun androidx.compose.ui.input.key.KeyEvent.richContentPlainTextPaste(
    clipboardManager: ClipboardManager?,
    context: android.content.Context,
): String? {
    if (type != KeyEventType.KeyDown || !isCtrlPressed || isShiftPressed || key != Key.V) return null
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
