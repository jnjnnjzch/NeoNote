package com.neonote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.neonote.engine.RichContentLayoutDefaults
import com.neonote.model.EditorTool
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
    val focusManager = LocalFocusManager.current
    val contentInteractionDisabled = controller.state.currentTool != EditorTool.Text
    LaunchedEffect(selectionMode, selected) {
        if (selectionMode || selected) focusManager.clearFocus()
    }
    val borderColor = when {
        selected -> Color(0xFF2563EB)
        box.isFocused -> Color(0xFF7C3AED)
        else -> Color(0xFFE2E8F0)
    }
    val shape = RoundedCornerShape(14.dp)
    val boxWidth = with(density) { box.size.width.toDp() }
    val persistedHeight = with(density) { box.size.height.toDp() }
    val interactionModifier = if (selectionMode) {
        Modifier.pointerInput(box.id, selectionMode) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                controller.activateRichContentBox(box.id)
            }
        }
    } else Modifier
    val sizingModifier = if (box.autoSizeHeight) {
        Modifier.defaultMinSize(minHeight = 64.dp).wrapContentHeight().onSizeChanged {
            controller.updateRichContentBoxMeasuredHeight(box.id, it.height.toFloat())
        }
    } else Modifier.height(persistedHeight)

    Box(
        modifier = Modifier
            .offset { IntOffset(box.position.x.roundToInt(), box.position.y.roundToInt()) }
            .width(boxWidth)
            .then(sizingModifier)
            .clip(shape)
            .background(Color.White)
            .border(if (selected || box.isFocused) 2.dp else 1.dp, borderColor, shape)
            .padding(RichContentLayoutDefaults.BoxChromePadding.dp)
            .then(interactionModifier),
    ) {
        if (!box.isFocused) {
            RichContentRenderer(
                content = box.content,
                selectionMode = selectionMode || contentInteractionDisabled,
                selected = selected,
                onFocus = { controller.activateRichContentBox(box.id) },
                onToggleTodoChecked = { controller.toggleRichContentTodoCheckedState(box.id, it) },
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                selectedObjectBlockIndex = controller.selectedRichContentObjectBlockIndex(box.id),
                onObjectBlockFocus = { controller.selectRichContentObjectBlock(box.id, it) },
            )
        } else {
            RichContentEditor(
                box = box,
                selectionMode = selectionMode,
                selected = selected,
                controller = controller,
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            )
        }
    }
}
