package com.neonote

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.neonote.engine.InputAction
import com.neonote.engine.InputMode
import com.neonote.engine.InputRouter
import com.neonote.engine.PointerEventType
import com.neonote.input.AndroidPointerSnapshot
import com.neonote.input.AndroidStylusInputAdapter
import com.neonote.input.AndroidToolTypes
import com.neonote.input.ComposeInputAdapter
import com.neonote.input.InputDiagnostics
import com.neonote.input.describeAndroidSource
import com.neonote.input.toAndroidPointerSnapshot
import com.neonote.input.withPressureSamples
import com.neonote.model.CanvasObject
import com.neonote.model.CanvasPoint
import com.neonote.model.EditorTool
import com.neonote.model.FloatingImage
import com.neonote.model.RichContentBox

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun InfiniteCanvasViewport(
    controller: NeoNoteEditorController,
    selectionMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val router = remember { InputRouter() }
    val inputAdapter = remember { ComposeInputAdapter() }
    val platformSnapshotStore = remember { PlatformSnapshotStore() }
    val density = LocalDensity.current
    val inputMode = when (controller.state.currentTool) {
        EditorTool.Pen -> InputMode.Write
        EditorTool.Text -> InputMode.Write
        EditorTool.Selection -> InputMode.Selection
        EditorTool.Eraser -> InputMode.Erase
    }

    Box(
        modifier = modifier
            .background(Color(0xFFFAF9FC))
            .onSizeChanged { controller.updateViewportMetrics(it.width, density.density) }
            .pointerInteropFilter { motionEvent ->
                if (AndroidStylusInputAdapter.isStylusOrEraser(motionEvent)) {
                    val inputEvent = AndroidStylusInputAdapter.toInputEvent(motionEvent)
                    controller.updateInputDiagnostics(
                        AndroidStylusInputAdapter.toDiagnostics(motionEvent),
                        eventTimeMillis = motionEvent.eventTime,
                        force = motionEvent.actionMasked != MotionEvent.ACTION_MOVE,
                    )
                    inputEvent?.let { controller.routeInputEvent(router, it, inputMode) }
                    true
                } else {
                    platformSnapshotStore.latest = motionEvent.toAndroidPointerSnapshot()
                    false
                }
            }
            .pointerInput(inputMode) {
                handleCanvasPointerInput(
                    router = router,
                    inputAdapter = inputAdapter,
                    platformSnapshotProvider = { platformSnapshotStore.latest },
                    controller = controller,
                    mode = inputMode,
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
            InkLayerView(
                pageId = controller.state.currentPageId,
                inkLayer = controller.currentCanvas.inkLayer,
                activeStroke = controller.activeInkStroke,
                modifier = Modifier.fillMaxSize(),
            )
            controller.currentCanvas.objects.sortedBy(CanvasObject::zIndex).forEach { canvasObject ->
                CanvasObjectView(
                    canvasObject = canvasObject,
                    selected = controller.state.selection.isObjectSelected(canvasObject.id),
                    selectionMode = selectionMode,
                    controller = controller,
                )
            }
            SelectionOverlay(
                activeLassoPath = controller.activeLassoPath,
                selectedBounds = controller.selectedBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }

        val focusedBoxId = controller.state.focusedRichContentBoxId
        if (controller.state.currentTool == EditorTool.Text && focusedBoxId != null) {
            RichContentToolbar(
                boxId = focusedBoxId,
                controller = controller,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .widthIn(max = 980.dp)
                    .wrapContentHeight()
                    .zIndex(50f),
            )
        }

        if (controller.currentCanvas.objects.isEmpty() && controller.currentCanvas.inkLayer.strokes.isEmpty()) {
            Text(
                text = when (controller.state.currentTool) {
                    EditorTool.Text -> "Tap anywhere to start typing"
                    EditorTool.Pen -> "Write with S Pen · tap to type · drag to move the page"
                    EditorTool.Selection -> "Drag around ink or objects to select"
                    EditorTool.Eraser -> "Erase with S Pen or the pen eraser"
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.9f))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                color = Color(0xFF746D82),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private class PlatformSnapshotStore {
    var latest: AndroidPointerSnapshot? = null
}

@Composable
private fun CanvasObjectView(
    canvasObject: CanvasObject,
    selected: Boolean,
    selectionMode: Boolean,
    controller: NeoNoteEditorController,
) {
    when (canvasObject) {
        is RichContentBox -> RichContentBoxView(canvasObject, selected, selectionMode, controller)
        is FloatingImage -> FloatingImageView(canvasObject, selected, selectionMode, controller)
    }
}

private suspend fun PointerInputScope.handleCanvasPointerInput(
    router: InputRouter,
    inputAdapter: ComposeInputAdapter,
    platformSnapshotProvider: () -> AndroidPointerSnapshot?,
    controller: NeoNoteEditorController,
    mode: InputMode,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        if (down.isConsumed) return@awaitEachGesture
        routePointerEvent(
            router,
            inputAdapter,
            controller,
            PointerEventType.Down,
            listOf(down),
            platformSnapshotProvider(),
            mode,
        )

        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Final)
            val platformSnapshot = platformSnapshotProvider()
            if (event.changes.any { it.isConsumed }) return@awaitEachGesture
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                val zoom = event.calculateZoom()
                val centroid = event.calculateCentroid(useCurrent = true)
                routePointerEvent(
                    router,
                    inputAdapter,
                    controller,
                    PointerEventType.Move,
                    pressed,
                    platformSnapshot,
                    mode,
                )
                controller.zoomViewportBy(zoom, CanvasPoint(centroid.x, centroid.y))
                event.changes.forEach { it.consume() }
                continue
            }
            val primary = event.changes.firstOrNull() ?: return@awaitEachGesture
            if (primary.changedToUpIgnoreConsumed()) {
                routePointerEvent(
                    router,
                    inputAdapter,
                    controller,
                    PointerEventType.Up,
                    listOf(primary),
                    platformSnapshot,
                    mode,
                )
                return@awaitEachGesture
            }
            if (primary.pressed && primary.positionChange() != Offset.Zero) {
                routePointerEvent(
                    router,
                    inputAdapter,
                    controller,
                    PointerEventType.Move,
                    listOf(primary),
                    platformSnapshot,
                    mode,
                )
            }
        }
    }
}

private fun routePointerEvent(
    router: InputRouter,
    inputAdapter: ComposeInputAdapter,
    controller: NeoNoteEditorController,
    type: PointerEventType,
    changes: List<PointerInputChange>,
    platformSnapshot: AndroidPointerSnapshot?,
    mode: InputMode,
) {
    val inputEvent = inputAdapter.toInputEvent(type, changes, platformSnapshot) ?: return
    controller.updateInputDiagnostics(
        InputDiagnostics(
            tool = inputEvent.pointers.first().tool,
            pressure = inputEvent.primaryPressure,
            rawPressure = inputEvent.primaryRawPressure,
            pointerCount = inputEvent.pointers.size,
            androidToolType = platformSnapshot?.pointerAt(0)?.toolType,
            isEraser = platformSnapshot?.pointerAt(0)?.toolType == AndroidToolTypes.Eraser,
            buttonState = platformSnapshot?.buttonState,
            deviceId = platformSnapshot?.deviceId,
            source = platformSnapshot?.source,
            sourceDescription = describeAndroidSource(platformSnapshot?.source),
        ).withPressureSamples(inputEvent.primaryInkSamples),
        force = type != PointerEventType.Move,
    )
    val result = controller.routeInputEvent(router, inputEvent, mode)
    val focusedObjectId = (result.action as? InputAction.FocusExisting)?.objectId ?: return
    val focusedObject = controller.currentCanvas.objects.firstOrNull { it.id == focusedObjectId }
    when (focusedObject) {
        is RichContentBox -> {
            if (controller.state.currentTool != EditorTool.Eraser) {
                controller.setTool(EditorTool.Text)
                controller.activateRichContentBox(focusedObjectId)
            }
        }
        is FloatingImage -> {
            if (controller.state.currentTool != EditorTool.Eraser) {
                controller.setTool(EditorTool.Selection)
                controller.selectCanvasObject(focusedObjectId)
            }
        }
        null -> Unit
    }
}
