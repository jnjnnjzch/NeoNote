package com.neonote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.neonote.model.CanvasPoint
import com.neonote.model.CanvasRect
import kotlin.math.roundToInt

private const val MinimumSelectionDimension = 48f
private val SelectionHandleSize = 22.dp

@Composable
internal fun SelectionOverlay(
    activeLassoPath: List<CanvasPoint>,
    selectedBounds: CanvasRect?,
    controller: NeoNoteEditorController,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            selectedBounds?.let { bounds ->
                drawRect(
                    color = Color(0xFF2563EB),
                    topLeft = Offset(bounds.left, bounds.top),
                    size = Size(bounds.width, bounds.height),
                    style = Stroke(width = 2f),
                )
                drawLine(
                    color = Color(0xFF2563EB),
                    start = Offset(bounds.center.x, bounds.top),
                    end = Offset(bounds.center.x, bounds.top - 34f),
                    strokeWidth = 2f,
                )
            }
            activeLassoPath.zipWithNext { start, end ->
                drawLine(
                    color = Color(0xFF2563EB),
                    start = Offset(start.x, start.y),
                    end = Offset(end.x, end.y),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )
            }
        }

        if (selectedBounds != null && controller.state.selection.selectedRefs.isNotEmpty()) {
            ResizeHandle(SelectionHandle.TopLeft, selectedBounds.left, selectedBounds.top, controller)
            ResizeHandle(SelectionHandle.TopRight, selectedBounds.right, selectedBounds.top, controller)
            ResizeHandle(SelectionHandle.BottomLeft, selectedBounds.left, selectedBounds.bottom, controller)
            ResizeHandle(SelectionHandle.BottomRight, selectedBounds.right, selectedBounds.bottom, controller)
            ResizeHandle(SelectionHandle.Left, selectedBounds.left, selectedBounds.center.y, controller)
            ResizeHandle(SelectionHandle.Right, selectedBounds.right, selectedBounds.center.y, controller)
            ResizeHandle(SelectionHandle.Bottom, selectedBounds.center.x, selectedBounds.bottom, controller)
        }
    }
}

@Composable
private fun ResizeHandle(
    handle: SelectionHandle,
    x: Float,
    y: Float,
    controller: NeoNoteEditorController,
) {
    Box(
        Modifier
            .offset { IntOffset((x - 11f).roundToInt(), (y - 11f).roundToInt()) }
            .size(SelectionHandleSize)
            .background(Color.White, CircleShape)
            .border(2.dp, Color(0xFF2563EB), CircleShape)
            .semantics { contentDescription = handle.description }
            .pointerInput(handle, controller) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val bounds = controller.selectedBounds ?: return@detectDragGestures
                    val width = bounds.width.coerceAtLeast(MinimumSelectionDimension)
                    val height = bounds.height.coerceAtLeast(MinimumSelectionDimension)
                    val targetWidth = when (handle) {
                        SelectionHandle.TopLeft, SelectionHandle.BottomLeft, SelectionHandle.Left -> width - dragAmount.x
                        SelectionHandle.TopRight, SelectionHandle.BottomRight, SelectionHandle.Right -> width + dragAmount.x
                        SelectionHandle.Bottom -> width
                    }.coerceAtLeast(MinimumSelectionDimension)
                    val targetHeight = when (handle) {
                        SelectionHandle.TopLeft, SelectionHandle.TopRight -> height - dragAmount.y
                        SelectionHandle.BottomLeft, SelectionHandle.BottomRight, SelectionHandle.Bottom -> height + dragAmount.y
                        SelectionHandle.Left, SelectionHandle.Right -> height
                    }.coerceAtLeast(MinimumSelectionDimension)
                    val anchor = when (handle) {
                        SelectionHandle.TopLeft -> CanvasPoint(bounds.right, bounds.bottom)
                        SelectionHandle.TopRight -> CanvasPoint(bounds.left, bounds.bottom)
                        SelectionHandle.BottomLeft -> CanvasPoint(bounds.right, bounds.top)
                        SelectionHandle.BottomRight -> CanvasPoint(bounds.left, bounds.top)
                        SelectionHandle.Left -> CanvasPoint(bounds.right, bounds.center.y)
                        SelectionHandle.Right -> CanvasPoint(bounds.left, bounds.center.y)
                        SelectionHandle.Bottom -> CanvasPoint(bounds.center.x, bounds.top)
                    }
                    controller.scaleSelection(targetWidth / width, targetHeight / height)
                }
            },
    )
}

private enum class SelectionHandle(val description: String) {
    TopLeft("Resize selection from top left"),
    TopRight("Resize selection from top right"),
    BottomLeft("Resize selection from bottom left"),
    BottomRight("Resize selection from bottom right"),
    Left("Resize selection width from left"),
    Right("Resize selection width from right"),
    Bottom("Resize selection height"),
}
