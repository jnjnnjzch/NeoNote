package com.neonote

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
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
    val inputMode = when (controller.state.currentTool) {
        EditorTool.Pen -> InputMode.Navigate
        EditorTool.Text -> InputMode.Write
        EditorTool.Selection -> InputMode.Selection
        EditorTool.Eraser -> InputMode.Erase
    }

    Box(
        modifier = modifier
            .background(Color(0xFFF9F8FC))
            .pointerInteropFilter { motionEvent ->
                if (AndroidStylusInputAdapter.isStylusOrEraser(motionEvent)) {
                    val inputEvent = AndroidStylusInputAdapter.toInputEvent(motionEvent)
                    controller.updateInputDiagnostics(
                        diagnostics = AndroidStylusInputAdapter.toDiagnostics(motionEvent),
                        eventTimeMillis = motionEvent.eventTime,
                        force = motionEvent.actionMasked != MotionEvent.ACTION_MOVE,
                    )
                    if (inputEvent != null) {
                        controller.routeInputEvent(
                            router = router,
                            event = inputEvent,
                            mode = inputMode,
                        )
                    }
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
            controller.currentCanvas.objects.forEach { canvasObject ->
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

        if (controller.currentCanvas.objects.isEmpty() && controller.currentCanvas.inkLayer.strokes.isEmpty()) {
            Text(
                text = when (controller.state.currentTool) {
                    EditorTool.Text -> "Tap anywhere to start typing"
                    EditorTool.Pen -> "Write with S Pen · Drag with one finger · Pinch to zoom"
                    EditorTool.Selection -> "Draw around ink or objects to select them"
                    EditorTool.Eraser -> "Erase with S Pen or the pen eraser"
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.92f))
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
        is RichContentBox -> RichContentBoxView(
            box = canvasObject,
            selected = selected,
            selectionMode = selectionMode,
            controller = controller,
        )
        else -> Unit
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
            router = router,
            inputAdapter = inputAdapter,
            controller = controller,
            type = PointerEventType.Down,
            changes = listOf(down),
            platformSnapshot = platformSnapshotProvider(),
            mode = mode,
        )

        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Final)
            val platformSnapshot = platformSnapshotProvider()
            if (event.changes.any { it.isConsumed }) return@awaitEachGesture

            val pressedChanges = event.changes.filter { it.pressed }
            if (pressedChanges.size >= 2) {
                val zoomChange = event.calculateZoom()
                val centroid = event.calculateCentroid(useCurrent = true)
                routePointerEvent(
                    router = router,
                    inputAdapter = inputAdapter,
                    controller = controller,
                    type = PointerEventType.Move,
                    changes = pressedChanges,
                    platformSnapshot = platformSnapshot,
                    mode = mode,
                )
                controller.zoomViewportBy(zoomChange, CanvasPoint(centroid.x, centroid.y))
                event.changes.forEach { it.consume() }
                continue
            }

            val primary = event.changes.firstOrNull() ?: return@awaitEachGesture
            if (primary.changedToUpIgnoreConsumed()) {
                routePointerEvent(
                    router = router,
                    inputAdapter = inputAdapter,
                    controller = controller,
                    type = PointerEventType.Up,
                    changes = listOf(primary),
                    platformSnapshot = platformSnapshot,
                    mode = mode,
                )
                return@awaitEachGesture
            }

            if (primary.pressed && primary.positionChange() != Offset.Zero) {
                routePointerEvent(
                    router = router,
                    inputAdapter = inputAdapter,
                    controller = controller,
                    type = PointerEventType.Move,
                    changes = listOf(primary),
                    platformSnapshot = platformSnapshot,
                    mode = mode,
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
    val inputEvent = inputAdapter.toInputEvent(
        type = type,
        changes = changes,
        platformSnapshot = platformSnapshot,
    ) ?: return
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
    controller.routeInputEvent(
        router = router,
        event = inputEvent,
        mode = mode,
    )
}
