package com.neonote

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.neonote.engine.InputAction
import com.neonote.engine.InputMode
import com.neonote.engine.InputRouter
import com.neonote.engine.PointerEventType
import com.neonote.engine.SelectionEngine
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
import com.neonote.model.CanvasRect
import com.neonote.model.EditorTool
import com.neonote.model.FloatingImage
import com.neonote.model.RichContentBox

private const val FocusedObjectScreenMargin = 22f
private const val FocusedTextToolbarClearance = 72f
private const val FloatingToolDockClearance = 86f
private const val FingerInkHitToleranceScreenPx = 14f

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun InfiniteCanvasViewport(
    controller: NeoNoteEditorController,
    selectionMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val router = remember { InputRouter() }
    val inputAdapter = remember { ComposeInputAdapter() }
    val selectionEngine = remember { SelectionEngine() }
    val platformSnapshotStore = remember { PlatformSnapshotStore() }
    val density = LocalDensity.current
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val inputMode = when (controller.state.currentTool) {
        EditorTool.Pen -> InputMode.Write
        EditorTool.Text -> InputMode.Write
        EditorTool.Selection -> InputMode.Selection
        EditorTool.Eraser -> InputMode.Erase
    }

    Box(
        modifier = modifier
            .background(Color(0xFFFAF9FC))
            .onSizeChanged { nextViewportSize ->
                viewportSize = nextViewportSize
                controller.updateViewportMetrics(nextViewportSize.width, nextViewportSize.height, density.density)
                controller.state.focusedRichContentBoxId
                    ?.let { focusedId ->
                        controller.currentCanvas.objects.filterIsInstance<RichContentBox>()
                            .firstOrNull { it.id == focusedId }
                    }
                    ?.let { focusedBox -> keepCanvasObjectVisible(controller, focusedBox, nextViewportSize) }
            }
            .pointerInput(controller, controller.state.currentTool) {
                handleGlobalTouchNavigation(controller)
            }
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
                    selectionEngine = selectionEngine,
                    platformSnapshotProvider = { platformSnapshotStore.latest },
                    controller = controller,
                    mode = inputMode,
                )
            },
    ) {
        val visibleBounds = remember(controller.state.viewport, viewportSize) {
            if (viewportSize == IntSize.Zero) null else {
                val topLeft = controller.screenToDocument(CanvasPoint(0f, 0f))
                val bottomRight = controller.screenToDocument(CanvasPoint(viewportSize.width.toFloat(), viewportSize.height.toFloat()))
                val margin = 180f / controller.state.viewport.zoomScale.coerceAtLeast(.2f)
                CanvasRect(
                    minOf(topLeft.x, bottomRight.x) - margin,
                    minOf(topLeft.y, bottomRight.y) - margin,
                    maxOf(topLeft.x, bottomRight.x) + margin,
                    maxOf(topLeft.y, bottomRight.y) + margin,
                )
            }
        }
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
                visibleBounds = visibleBounds,
                modifier = Modifier.fillMaxSize(),
            )
            controller.currentCanvas.objects
                .asSequence()
                .filter { canvasObject ->
                    visibleBounds == null || canvasObject.bounds.intersects(visibleBounds) ||
                        controller.state.selection.isObjectSelected(canvasObject.id) ||
                        controller.state.focusedRichContentBoxId == canvasObject.id
                }
                .sortedBy(CanvasObject::zIndex)
                .forEach { canvasObject ->
                CanvasObjectView(
                    canvasObject = canvasObject,
                    selected = controller.state.selection.isObjectSelected(canvasObject.id),
                    selectionMode = selectionMode,
                    highlighted = controller.searchHighlightedObjectId == canvasObject.id,
                    controller = controller,
                )
            }
            SelectionOverlay(
                activeLassoPath = controller.activeLassoPath,
                selectedBounds = null,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (selectionMode && controller.selectedBounds != null) {
            SelectionTransformOverlay(
                controller = controller,
                modifier = Modifier.fillMaxSize().zIndex(45f),
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
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                color = Color(0xFF746D82),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun CanvasRect.intersects(other: CanvasRect): Boolean =
    right >= other.left && left <= other.right && bottom >= other.top && top <= other.bottom

private suspend fun PointerInputScope.handleGlobalTouchNavigation(controller: NeoNoteEditorController) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.type != PointerType.Touch) return@awaitEachGesture
        var draggingCanvas = false
        var lastPosition = down.position
        val startPosition = down.position
        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed && it.type == PointerType.Touch }
            if (pressed.size >= 2) {
                controller.clearSearchHighlight()
                val pan = event.calculatePan()
                val zoom = event.calculateZoom()
                val centroid = event.calculateCentroid(useCurrent = true)
                if (pan != Offset.Zero) controller.panViewportBy(pan.x, pan.y)
                if (zoom != 1f) controller.zoomViewportBy(zoom, CanvasPoint(centroid.x, centroid.y))
                event.changes.forEach { it.consume() }
                draggingCanvas = true
                continue
            }
            val primary = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (!primary.pressed) return@awaitEachGesture
            if (controller.state.currentTool == EditorTool.Pen) {
                val distance = (primary.position - startPosition).getDistance()
                if (draggingCanvas || distance > viewConfiguration.touchSlop) {
                    controller.clearSearchHighlight()
                    val delta = primary.position - lastPosition
                    if (delta != Offset.Zero) controller.panViewportBy(delta.x, delta.y)
                    primary.consume()
                    draggingCanvas = true
                }
            }
            lastPosition = primary.position
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
    highlighted: Boolean,
    controller: NeoNoteEditorController,
) {
    when (canvasObject) {
        is RichContentBox -> RichContentBoxView(canvasObject, selected, selectionMode, controller, highlighted)
        is FloatingImage -> FloatingImageView(canvasObject, selected, selectionMode, controller, highlighted)
    }
}

private suspend fun PointerInputScope.handleCanvasPointerInput(
    router: InputRouter,
    inputAdapter: ComposeInputAdapter,
    selectionEngine: SelectionEngine,
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
            selectionEngine = selectionEngine,
            controller = controller,
            type = PointerEventType.Down,
            changes = listOf(down),
            platformSnapshot = platformSnapshotProvider(),
            mode = mode,
            viewportSize = size,
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
                    router = router,
                    inputAdapter = inputAdapter,
                    selectionEngine = selectionEngine,
                    controller = controller,
                    type = PointerEventType.Move,
                    changes = pressed,
                    platformSnapshot = platformSnapshot,
                    mode = mode,
                    viewportSize = size,
                )
                controller.zoomViewportBy(zoom, CanvasPoint(centroid.x, centroid.y))
                event.changes.forEach { it.consume() }
                continue
            }
            val primary = event.changes.firstOrNull() ?: return@awaitEachGesture
            if (primary.changedToUpIgnoreConsumed()) {
                routePointerEvent(
                    router = router,
                    inputAdapter = inputAdapter,
                    selectionEngine = selectionEngine,
                    controller = controller,
                    type = PointerEventType.Up,
                    changes = listOf(primary),
                    platformSnapshot = platformSnapshot,
                    mode = mode,
                    viewportSize = size,
                )
                return@awaitEachGesture
            }
            if (primary.pressed && primary.positionChange() != Offset.Zero) {
                routePointerEvent(
                    router = router,
                    inputAdapter = inputAdapter,
                    selectionEngine = selectionEngine,
                    controller = controller,
                    type = PointerEventType.Move,
                    changes = listOf(primary),
                    platformSnapshot = platformSnapshot,
                    mode = mode,
                    viewportSize = size,
                )
            }
        }
    }
}

private fun routePointerEvent(
    router: InputRouter,
    inputAdapter: ComposeInputAdapter,
    selectionEngine: SelectionEngine,
    controller: NeoNoteEditorController,
    type: PointerEventType,
    changes: List<PointerInputChange>,
    platformSnapshot: AndroidPointerSnapshot?,
    mode: InputMode,
    viewportSize: IntSize,
) {
    val screenPosition = changes.firstOrNull()?.position ?: return
    val documentPosition = controller.screenToDocument(CanvasPoint(screenPosition.x, screenPosition.y))
    val targetObjectId = selectionEngine.hitTestCanvasObjects(controller.currentCanvas, documentPosition)
        .firstOrNull()
        ?.id
    val targetStrokeId = if (targetObjectId == null) {
        selectionEngine.hitTestInkStrokes(
            canvas = controller.currentCanvas,
            point = documentPosition,
            tolerance = FingerInkHitToleranceScreenPx / controller.state.viewport.zoomScale.coerceAtLeast(0.2f),
        ).firstOrNull()?.id
    } else {
        null
    }
    val inputEvent = inputAdapter.toInputEvent(
        type = type,
        changes = changes,
        platformSnapshot = platformSnapshot,
        targetObjectId = targetObjectId,
        targetStrokeId = targetStrokeId,
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
    val result = controller.routeInputEvent(router, inputEvent, mode)

    if (result.action is InputAction.CreateOrFocusRichContentBox) {
        controller.state.focusedRichContentBoxId
            ?.let { id -> controller.currentCanvas.objects.filterIsInstance<RichContentBox>().firstOrNull { it.id == id } }
            ?.let { keepCanvasObjectVisible(controller, it, viewportSize) }
        return
    }

    val focusedObjectId = (result.action as? InputAction.FocusExisting)?.objectId ?: return
    val focusedObject = controller.currentCanvas.objects.firstOrNull { it.id == focusedObjectId }
    when (focusedObject) {
        is RichContentBox -> {
            if (controller.state.currentTool != EditorTool.Eraser) {
                controller.setTool(EditorTool.Text)
                controller.activateRichContentBox(focusedObjectId)
                keepCanvasObjectVisible(controller, focusedObject, viewportSize)
            }
        }
        is FloatingImage -> {
            if (controller.state.currentTool != EditorTool.Eraser) {
                controller.setTool(EditorTool.Selection)
                controller.selectCanvasObject(focusedObjectId)
                keepCanvasObjectVisible(controller, focusedObject, viewportSize)
            }
        }
        null -> Unit
    }
}

private fun keepCanvasObjectVisible(
    controller: NeoNoteEditorController,
    canvasObject: CanvasObject,
    viewportSize: IntSize,
) {
    if (viewportSize.width <= 0 || viewportSize.height <= 0) return
    val bounds = canvasObject.bounds
    val topLeft = controller.documentToScreen(CanvasPoint(bounds.left, bounds.top))
    val bottomRight = controller.documentToScreen(CanvasPoint(bounds.right, bounds.bottom))
    val topClearance = if (controller.state.currentTool == EditorTool.Text) {
        FocusedTextToolbarClearance
    } else {
        FocusedObjectScreenMargin
    }
    var dx = 0f
    var dy = 0f
    if (topLeft.x < FocusedObjectScreenMargin) dx = FocusedObjectScreenMargin - topLeft.x
    if (bottomRight.x > viewportSize.width - FocusedObjectScreenMargin) {
        dx = viewportSize.width - FocusedObjectScreenMargin - bottomRight.x
    }
    if (topLeft.y < topClearance) dy = topClearance - topLeft.y
    val bottomClearance = if (controller.state.currentTool == EditorTool.Text) {
        FocusedObjectScreenMargin
    } else {
        FloatingToolDockClearance
    }
    if (bottomRight.y > viewportSize.height - bottomClearance) {
        dy = viewportSize.height - bottomClearance - bottomRight.y
    }
    if (dx != 0f || dy != 0f) controller.panViewportBy(dx, dy)
}
