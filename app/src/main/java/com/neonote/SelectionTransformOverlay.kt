package com.neonote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
    val handleRadiusPx = with(density) { handleSize.toPx() / 2f }

    Box(modifier) {
        Box(
            modifier = Modifier
                .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
                .size(widthDp, heightDp),
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawRoundRect(
                    color = Color(0xFF2563EB),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            Box(
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
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val horizontal = (widthPx + dragAmount.x) / widthPx
                            val vertical = (heightPx + dragAmount.y) / heightPx
                            val scale = max(horizontal, vertical).coerceIn(0.82f, 1.18f)
                            if (scale != 1f) controller.scaleSelection(scale)
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
                    x = (bottomRight.x - with(density) { 88.dp.toPx() }).roundToInt(),
                    y = (topLeft.y - with(density) { 48.dp.toPx() }).roundToInt(),
                )
            },
            shape = RoundedCornerShape(13.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            shadowElevation = 6.dp,
        ) {
            Row {
                SelectionQuickAction("⧉", "Duplicate selection", controller::duplicateSelection)
                SelectionQuickAction("×", "Delete selection", controller::deleteSelection, destructive = true)
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
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .pointerInput(description) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}
