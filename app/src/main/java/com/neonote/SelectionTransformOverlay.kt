package com.neonote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun SelectionTransformOverlay(
    controller: NeoNoteEditorController,
    modifier: Modifier = Modifier,
) {
    val bounds = controller.selectedBounds ?: return
    val density = LocalDensity.current
    val topLeft = controller.documentToScreen(com.neonote.model.CanvasPoint(bounds.left, bounds.top))
    val bottomRight = controller.documentToScreen(com.neonote.model.CanvasPoint(bounds.right, bounds.bottom))
    val widthPx = (bottomRight.x - topLeft.x).coerceAtLeast(1f)
    val heightPx = (bottomRight.y - topLeft.y).coerceAtLeast(1f)
    val widthDp = with(density) { widthPx.toDp() }
    val heightDp = with(density) { heightPx.toDp() }
    val handleSize = 30.dp

    Box(modifier) {
        Box(
            modifier = Modifier
                .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
                .size(widthDp, heightDp),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Color(0xFF2563EB),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            if (controller.selectionCanTransform) Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = handleSize / 2, y = handleSize / 2)
                    .size(handleSize)
                    .background(Color.White, CircleShape)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Resize selection"
                    }
                    .pointerInput(controller.state.selection.selectedRefs, widthPx, heightPx) {
                        var accumulatedX = 0f
                        var accumulatedY = 0f
                        var appliedX = 1f
                        var appliedY = 1f
                        detectDragGestures(
                            onDragStart = {
                                accumulatedX = 0f
                                accumulatedY = 0f
                                appliedX = 1f
                                appliedY = 1f
                                controller.beginSelectionTransform()
                            },
                            onDragCancel = controller::cancelSelectionTransform,
                            onDragEnd = controller::endSelectionTransform,
                        ) { change, dragAmount ->
                            change.consume()
                            accumulatedX += dragAmount.x
                            accumulatedY += dragAmount.y
                            val targetX = ((widthPx + accumulatedX) / widthPx).coerceIn(0.12f, 8f)
                            val targetY = if (controller.selectionResizesTextWidthOnly) 1f
                            else ((heightPx + accumulatedY) / heightPx).coerceIn(0.12f, 8f)
                            val stepX = targetX / appliedX
                            val stepY = targetY / appliedY
                            controller.resizeSelectionDuringTransform(stepX, stepY)
                            appliedX = targetX
                            appliedY = targetY
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(12.dp).background(Color(0xFF2563EB), CircleShape))
            }
        }

        Surface(
            modifier = Modifier.offset {
                IntOffset(
                    x = max(4f, bottomRight.x - with(density) { 164.dp.toPx() }).roundToInt(),
                    y = max(4f, topLeft.y - with(density) { 48.dp.toPx() }).roundToInt(),
                )
            },
            shape = RoundedCornerShape(13.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            shadowElevation = 6.dp,
        ) {
            Row {
                SelectionQuickAction("Copy", "Copy selection", controller::copySelection)
                if (controller.selectionHasLockedObjects) {
                    SelectionQuickAction("Unlock", "Unlock selection", { controller.setSelectionLocked(false) })
                } else {
                    SelectionQuickAction("Cut", "Cut selection", controller::cutSelection, enabled = controller.selectionCanDelete)
                }
                SelectionQuickAction(
                    "Delete",
                    "Delete selection",
                    controller::deleteSelection,
                    destructive = true,
                    enabled = controller.selectionCanDelete,
                )
            }
        }
    }
}

@Composable
private fun SelectionQuickAction(
    label: String,
    description: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = .32f)
                destructive -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            },
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
