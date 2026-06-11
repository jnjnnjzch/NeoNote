package com.neonote

import android.content.ClipDescription
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonote.engine.InlineStyle
import com.neonote.engine.RichContentLayoutDefaults
import com.neonote.model.InlineFormula
import com.neonote.model.InlineImage
import com.neonote.model.InlineLineBreak
import com.neonote.model.InlineText
import com.neonote.model.ListKind
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox

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
    val focusRequester = remember { FocusRequester() }
    val modelText = paragraph.plainTextForEditor()
    var platformTextFieldValue by remember(box.id, blockIndex) { mutableStateOf(TextFieldValue(modelText)) }

    LaunchedEffect(active, selectionMode, selected) {
        if (active && !selectionMode && !selected) focusRequester.requestFocus()
    }
    LaunchedEffect(modelText) {
        if (platformTextFieldValue.text != modelText) {
            platformTextFieldValue = TextFieldValue(modelText, TextRange(modelText.length))
        }
    }

    BasicTextField(
        value = platformTextFieldValue,
        onValueChange = { nextValue ->
            val previousValue = platformTextFieldValue
            platformTextFieldValue = nextValue
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
        textStyle = LocalTextStyle.current.copy(color = Color(0xFF0F172A), lineHeight = RichContentLayoutDefaults.LineHeight.sp),
        cursorBrush = SolidColor(Color(0xFF7C3AED)),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RichContentLayoutDefaults.LineHeight.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused && !selectionMode && !selected && !box.isFocused) {
                    controller.activateRichContentBox(box.id)
                }
                // Do not commit or clear the rich-content session on plain platform
                // focus loss. Toolbar taps can transiently move Android focus away
                // from BasicTextField; the editor session remains the source of
                // truth for the active paragraph selection until an explicit editor
                // transition (selection mode, page switch, or focusing another box)
                // commits it.
            }
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
                        platformTextFieldValue = TextFieldValue(text = nextText, selection = TextRange(nextCursor))
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
                            selectionStart = platformTextFieldValue.selection.start,
                            selectionEnd = platformTextFieldValue.selection.end,
                        )
                        true
                    }
                    listKind != null && box.isFocused -> {
                        controller.toggleRichContentParagraphList(
                            boxId = box.id,
                            blockIndex = blockIndex,
                            kind = listKind,
                            selectionStart = platformTextFieldValue.selection.start,
                            selectionEnd = platformTextFieldValue.selection.end,
                        )
                        true
                    }
                    else -> false
                }
            },
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (platformTextFieldValue.text.isEmpty()) {
                    Text(text = "Start typing…", color = Color(0xFF94A3B8), style = LocalTextStyle.current.copy(lineHeight = RichContentLayoutDefaults.LineHeight.sp))
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
