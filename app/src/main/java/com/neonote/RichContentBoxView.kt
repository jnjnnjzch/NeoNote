package com.neonote

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonote.model.EditorTool
import com.neonote.model.RichContentBox
import kotlin.math.roundToInt

private val MinimumRichContentBoxHeight = 44.dp

@Composable
internal fun RichContentBoxView(
    box: RichContentBox,
    selected: Boolean,
    selectionMode: Boolean,
    controller: NeoNoteEditorController,
    highlighted: Boolean = false,
) {
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val contentInteractionDisabled = controller.state.currentTool != EditorTool.Text
    LaunchedEffect(selectionMode, selected) { if (selectionMode || selected) focusManager.clearFocus() }
    val outline = when {
        selected -> Color(0xFF2563EB)
        highlighted -> Color(0xFFF59E0B)
        box.isFocused -> Color(0xFF7C3AED).copy(alpha = 0.72f)
        else -> Color.Transparent
    }
    val shape = RoundedCornerShape(7.dp)
    val boxWidth = with(density) { box.size.width.toDp() }
    val persistedHeight = with(density) { box.size.height.toDp() }
    val interactionModifier = if (selectionMode) Modifier.pointerInput(box.id, selectionMode) {
        awaitEachGesture { awaitFirstDown(requireUnconsumed = false); controller.activateRichContentBox(box.id) }
    } else Modifier
    val sizingModifier = if (box.autoSizeHeight) Modifier.wrapContentHeight(unbounded = true)
        .heightIn(min = MinimumRichContentBoxHeight)
        .onSizeChanged { if (it.height > 0) controller.updateRichContentBoxMeasuredHeight(box.id, it.height.toFloat()) }
    else Modifier.height(maxOf(persistedHeight, MinimumRichContentBoxHeight))

    Box(
        modifier = Modifier.offset { IntOffset(box.position.x.roundToInt(), box.position.y.roundToInt()) }
            .width(boxWidth).then(sizingModifier).animateContentSize().background(Color.Transparent, shape)
            .border(if (selected || box.isFocused || highlighted) if (highlighted) 2.dp else 1.dp else 0.dp, outline, shape).then(interactionModifier),
    ) {
        val contentModifier = Modifier.fillMaxWidth().wrapContentHeight(unbounded = true).padding(
            start = if (box.isFocused) 10.dp else 0.dp,
            end = if (box.isFocused) 18.dp else 0.dp,
            top = if (box.isFocused) 10.dp else 0.dp,
            bottom = if (box.isFocused) 5.dp else 0.dp,
        )
        RichContentEditor(box, selectionMode, selected, controller, contentModifier)
        if (box.isFocused) {
            Box(
                modifier = Modifier.align(Alignment.TopStart).offset(x = 3.dp, y = (-8).dp).size(40.dp, 24.dp)
                    .pointerInput(box.id) {
                        detectDragGestures(
                            onDragStart = { controller.beginRichContentGesture(box.id) },
                            onDragCancel = { controller.cancelRichContentGesture(box.id) },
                            onDragEnd = { controller.endRichContentGesture(box.id) },
                            onDrag = { change, drag ->
                                change.consume()
                                controller.moveFocusedRichContentBoxByScreenDelta(box.id, drag)
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) { Text("•••", color = MaterialTheme.colorScheme.primary.copy(alpha = 0.58f), fontSize = 11.sp, letterSpacing = 1.sp) }
            Box(
                modifier = Modifier.align(Alignment.CenterEnd).size(24.dp, 54.dp)
                    .pointerInput(box.id) {
                        detectDragGestures(
                            onDragStart = { controller.beginRichContentGesture(box.id) },
                            onDragCancel = { controller.cancelRichContentGesture(box.id) },
                            onDragEnd = { controller.endRichContentGesture(box.id) },
                            onDrag = { change, drag ->
                                change.consume()
                                controller.resizeFocusedRichContentBoxWidthByScreenDelta(box.id, drag.x)
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.width(3.dp).height(28.dp).background(outline, RoundedCornerShape(2.dp))) }
        }
    }
}
