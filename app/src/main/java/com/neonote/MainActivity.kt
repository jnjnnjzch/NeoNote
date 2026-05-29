package com.neonote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.neonote.engine.InputAction
import com.neonote.engine.InputEvent
import com.neonote.engine.InputMode
import com.neonote.engine.InputPointer
import com.neonote.engine.InputRouter
import com.neonote.engine.PointerEventType
import com.neonote.engine.PointerTool
import com.neonote.model.CanvasObject
import com.neonote.model.CanvasPoint
import com.neonote.model.InlineText
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContentBox
import kotlin.math.roundToInt

public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NeoNoteApp() }
    }
}

@Composable
public fun NeoNoteApp(controller: NeoNoteEditorController = remember { NeoNoteEditorController() }) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF8FAFC)) {
            NeoNoteEditorScreen(controller = controller)
        }
    }
}

@Composable
private fun NeoNoteEditorScreen(controller: NeoNoteEditorController) {
    val state = controller.state
    val selectionMode = state.currentTool == com.neonote.model.EditorTool.Selection
    Column(modifier = Modifier.fillMaxSize()) {
        EditorToolbar(
            title = state.document.title,
            selectionMode = selectionMode,
            viewportLabel = "pan=(${state.viewport.panOffsetX.roundToInt()}, ${state.viewport.panOffsetY.roundToInt()}) zoom=${"%.2f".format(state.viewport.zoomScale)}x",
            onToggleSelectionMode = { controller.setSelectionMode(!selectionMode) },
        )
        InfiniteCanvasViewport(
            controller = controller,
            selectionMode = selectionMode,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun EditorToolbar(
    title: String,
    selectionMode: Boolean,
    viewportLabel: String,
    onToggleSelectionMode: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
            Text(text = viewportLabel, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
        }
        Button(onClick = onToggleSelectionMode) {
            Text(if (selectionMode) "Selection: ON" else "Selection: OFF")
        }
    }
}

@Composable
private fun InfiniteCanvasViewport(
    controller: NeoNoteEditorController,
    selectionMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val router = remember { InputRouter() }
    Box(
        modifier = modifier
            .background(Color(0xFFEFF6FF))
            .pointerInput(selectionMode) {
                handleCanvasPointerInput(
                    router = router,
                    controller = controller,
                    selectionMode = selectionMode,
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = controller.state.viewport.panOffsetX
                    translationY = controller.state.viewport.panOffsetY
                    scaleX = controller.state.viewport.zoomScale
                    scaleY = controller.state.viewport.zoomScale
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            controller.currentCanvas.objects.forEach { canvasObject ->
                CanvasObjectView(
                    canvasObject = canvasObject,
                    selected = controller.state.selection.isObjectSelected(canvasObject.id),
                    selectionMode = selectionMode,
                    controller = controller,
                )
            }
        }
        Text(
            text = "Tap blank canvas to create text · Drag blank canvas to pan · Pinch to zoom",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.88f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            color = Color(0xFF334155),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun CanvasObjectView(
    canvasObject: CanvasObject,
    selected: Boolean,
    selectionMode: Boolean,
    controller: NeoNoteEditorController,
) {
    when (canvasObject) {
        is RichContentBox -> RichContentBoxView(
            box = canvasObject,
            selected = selected,
            selectionMode = selectionMode,
            controller = controller,
        )
        else -> Unit
    }
}

@Composable
private fun RichContentBoxView(
    box: RichContentBox,
    selected: Boolean,
    selectionMode: Boolean,
    controller: NeoNoteEditorController,
) {
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(box.isFocused, selectionMode) {
        if (box.isFocused && !selectionMode) {
            focusRequester.requestFocus()
        }
    }
    val borderColor = when {
        selected -> Color(0xFF2563EB)
        box.isFocused -> Color(0xFF7C3AED)
        else -> Color(0xFFCBD5E1)
    }
    val modifier = Modifier
        .offset { IntOffset(box.position.x.roundToInt(), box.position.y.roundToInt()) }
        .size(
            width = with(density) { box.size.width.toDp() },
            height = with(density) { box.size.height.toDp() },
        )
        .clip(RoundedCornerShape(14.dp))
        .background(Color.White)
        .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
        .then(
            if (selectionMode) {
                Modifier
                    .pointerInput(box.id) {
                        detectTapGestures(onTap = { controller.selectCanvasObject(box.id) })
                    }
                    .pointerInput(box.id, selected) {
                        detectDragGestures(
                            onDragStart = { controller.selectCanvasObject(box.id) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (!controller.state.selection.isObjectSelected(box.id)) {
                                    controller.selectCanvasObject(box.id)
                                }
                                controller.moveSelectedObjectsByScreenDelta(dragAmount)
                            },
                        )
                    }
            } else {
                Modifier
            },
        )

    Box(modifier = modifier.padding(8.dp)) {
        TextField(
            value = box.plainText(),
            onValueChange = { controller.updateRichContentText(box.id, it) },
            enabled = !selectionMode,
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester),
            placeholder = { Text("Start typing…") },
        )
    }
}

private suspend fun PointerInputScope.handleCanvasPointerInput(
    router: InputRouter,
    controller: NeoNoteEditorController,
    selectionMode: Boolean,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        if (down.isConsumed) return@awaitEachGesture

        routePointer(
            router = router,
            controller = controller,
            type = PointerEventType.Down,
            position = down.position,
            selectionMode = selectionMode,
        )

        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Final)
            if (event.changes.any { it.isConsumed }) return@awaitEachGesture

            val pressedChanges = event.changes.filter { it.pressed }
            if (pressedChanges.size >= 2) {
                val zoomChange = event.calculateZoom()
                val centroid = event.calculateCentroid(useCurrent = true)
                routePointer(
                    router = router,
                    controller = controller,
                    type = PointerEventType.Move,
                    position = centroid,
                    pointerCount = pressedChanges.size,
                    selectionMode = selectionMode,
                )
                controller.zoomViewportBy(zoomChange, CanvasPoint(centroid.x, centroid.y))
                event.changes.forEach(PointerInputChange::consume)
                continue
            }

            val primary = event.changes.firstOrNull() ?: return@awaitEachGesture
            if (primary.changedToUpIgnoreConsumed()) {
                routePointer(
                    router = router,
                    controller = controller,
                    type = PointerEventType.Up,
                    position = primary.position,
                    selectionMode = selectionMode,
                )
                return@awaitEachGesture
            }

            if (primary.pressed && primary.positionChange() != Offset.Zero) {
                routePointer(
                    router = router,
                    controller = controller,
                    type = PointerEventType.Move,
                    position = primary.position,
                    selectionMode = selectionMode,
                )
            }
        }
    }
}

private fun routePointer(
    router: InputRouter,
    controller: NeoNoteEditorController,
    type: PointerEventType,
    position: Offset,
    pointerCount: Int = 1,
    selectionMode: Boolean,
) {
    val pointers = List(pointerCount) { index ->
        InputPointer(
            id = index + 1,
            position = CanvasPoint(position.x + index, position.y + index),
            tool = PointerTool.Finger,
        )
    }
    val result = router.route(
        canvas = controller.currentCanvas,
        event = InputEvent(type = type, pointers = pointers),
        mode = if (selectionMode) InputMode.Selection else InputMode.Write,
    )
    when (val action = result.action) {
        is InputAction.PanBy -> controller.panViewportBy(action.dx, action.dy)
        is InputAction.CreateOrFocusRichContentBox -> {
            controller.focusOrCreateRichContentBox(controller.screenToDocument(action.position))
        }
        else -> Unit
    }
}

private fun RichContentBox.plainText(): String = content.blocks.joinToString("\n") { block ->
    when (block) {
        is ParagraphNode -> block.inlines.joinToString("") { inline ->
            when (inline) {
                is InlineText -> inline.text
                else -> ""
            }
        }
        else -> ""
    }
}
