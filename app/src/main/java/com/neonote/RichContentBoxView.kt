package com.neonote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.neonote.engine.RichContentLayoutDefaults
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
    LaunchedEffect(box.isFocused, selectionMode, selected) {
        if (selectionMode || selected) {
            focusManager.clearFocus()
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
    val chromeShape = RoundedCornerShape(14.dp)
    val toolbarHeight = 44.dp
    val showsToolbar = box.isFocused && !selectionMode && !selected
    val toolbarHeightPx = if (showsToolbar) with(density) { toolbarHeight.toPx() }.roundToInt() else 0
    val boxWidth = with(density) { box.size.width.toDp() }
    val boxHeight = with(density) { box.size.height.toDp() }
    val boxSizeModifier = Modifier
        // Keep the persisted content rectangle at box.position, but include the
        // toolbar in this composable's hit-test bounds when it is visually above
        // the RichContentBox. Drawing above parent bounds can be visible yet
        // untappable on device; reserving space here keeps actions clickable.
        .offset { IntOffset(box.position.x.roundToInt(), box.position.y.roundToInt() - toolbarHeightPx) }
        .size(width = boxWidth, height = boxHeight + if (showsToolbar) toolbarHeight else 0.dp)

    val contentModifier = Modifier
        .clip(chromeShape)
        .background(Color.White)
        .border(width = borderWidth, color = borderColor, shape = chromeShape)
        .padding(RichContentLayoutDefaults.BoxChromePadding.dp)
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

    Box(modifier = boxSizeModifier) {
        if (showsToolbar) {
            RichContentToolbar(
                boxId = box.id,
                controller = controller,
                modifier = Modifier
                    .height(toolbarHeight)
                    .zIndex(1f),
            )
        }

        Box(
            modifier = contentModifier
                .width(boxWidth)
                .height(boxHeight)
                .then(if (showsToolbar) Modifier.offset(y = toolbarHeight) else Modifier),
        ) {
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
                    selectedObjectBlockIndex = controller.selectedRichContentObjectBlockIndex(box.id),
                    onObjectBlockFocus = { blockIndex ->
                        controller.selectRichContentObjectBlock(boxId = box.id, blockIndex = blockIndex)
                    },
                )
            } else {
                RichContentEditor(
                    box = box,
                    selectionMode = selectionMode,
                    selected = selected,
                    controller = controller,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

    }
}
