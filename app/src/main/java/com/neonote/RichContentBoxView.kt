package com.neonote

import android.content.ClipDescription
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
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
import com.neonote.model.ListKind
import com.neonote.model.RichContentBox
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
            RichContentRenderer(
                content = box.content,
                selectionMode = selectionMode,
                selected = selected,
                onFocus = { controller.activateRichContentBox(box.id) },
                onToggleTodoChecked = { blockIndex ->
                    controller.toggleRichContentTodoCheckedState(boxId = box.id, blockIndex = blockIndex)
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            BasicTextField(
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
                textStyle = LocalTextStyle.current.copy(color = Color(0xFF0F172A)),
                cursorBrush = SolidColor(Color(0xFF7C3AED)),
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
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (platformTextFieldValue.text.isEmpty()) {
                            Text(
                                text = "Start typing…",
                                color = Color(0xFF94A3B8),
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

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
