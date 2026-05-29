/*
 *
 *  * Copyright 2025 Google LLC. All rights reserved.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.example.cahier.core.ui

import android.annotation.SuppressLint
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.withSave
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.brush.compose.createWithComposeColor
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import coil3.compose.AsyncImage
import com.example.cahier.core.utils.pointerInputWithSiblingFallthrough
import com.example.cahier.features.drawing.CanvasTransform
import kotlin.math.hypot

enum class DrawingInputRoute {
    StylusInk,
    FingerPanZoom,
    FingerTapToFocus,
    Selection,
    Eraser
}

data class DrawingInputRoutingConfig(
    val stylusWritesByDefault: Boolean = true,
    val fingerPansByDefault: Boolean = true,
    val isSelectionMode: Boolean = false,
    val isEraserMode: Boolean = false
)

object DrawingInputRouter {
    fun routeFor(event: MotionEvent, config: DrawingInputRoutingConfig): DrawingInputRoute {
        if (config.isSelectionMode) return DrawingInputRoute.Selection
        if (config.isEraserMode) return DrawingInputRoute.Eraser
        val toolType = event.getToolType(event.actionIndex.coerceAtMost(event.pointerCount - 1))
        return when (toolType) {
            MotionEvent.TOOL_TYPE_FINGER -> {
                if (config.fingerPansByDefault) {
                    DrawingInputRoute.FingerPanZoom
                } else {
                    DrawingInputRoute.FingerTapToFocus
                }
            }

            MotionEvent.TOOL_TYPE_STYLUS -> {
                if (config.stylusWritesByDefault) {
                    DrawingInputRoute.StylusInk
                } else {
                    DrawingInputRoute.FingerPanZoom
                }
            }

            MotionEvent.TOOL_TYPE_ERASER -> DrawingInputRoute.Eraser

            else -> DrawingInputRoute.StylusInk
        }
    }
}

@SuppressLint("RestrictedApi", "VisibleForTests")
@Composable
fun DrawingSurface(
    strokes: List<Stroke>,
    strokeTranslations: Map<Int, Pair<Float, Float>> = emptyMap(),
    canvasTransform: CanvasTransform = CanvasTransform(),
    canvasStrokeRenderer: CanvasStrokeRenderer,
    onStrokesFinished: (List<Stroke>) -> Unit,
    onErase: (offsetX: Float, offsetY: Float) -> Unit,
    onEraseStart: () -> Unit,
    onEraseEnd: () -> Unit,
    currentBrush: Brush,
    onGetNextBrush: () -> Brush,
    isEraserMode: Boolean,
    isSelectionMode: Boolean = false,
    hasSelection: Boolean = false,
    backgroundImageUri: String?,
    onStartDrag: () -> Unit,
    onSelectionLasso: (Offset, Offset) -> Unit = { _, _ -> },
    onMoveSelection: (Float, Float) -> Unit = { _, _ -> },
    onRawMotionEvent: (MotionEvent) -> Unit = {},
    onFingerTap: (Float, Float) -> Unit = { _, _ -> },
    inputRoutingConfig: DrawingInputRoutingConfig = DrawingInputRoutingConfig(
        isSelectionMode = isSelectionMode,
        isEraserMode = isEraserMode
    ),
    modifier: Modifier = Modifier,
) {
    val textureStore = LocalTextureStore.current
    val viewConfiguration = ViewConfiguration.get(LocalContext.current)
    val fingerTapSlop = viewConfiguration.scaledTouchSlop.toFloat()
    val fingerTapTimeoutMillis = ViewConfiguration.getTapTimeout().toLong()
    var fingerTapDownX by remember { mutableStateOf(0f) }
    var fingerTapDownY by remember { mutableStateOf(0f) }
    var fingerTapDownTimeMillis by remember { mutableStateOf(0L) }
    var fingerTapCandidate by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        backgroundImageUri?.let {
            AsyncImage(
                model = it,
                contentDescription = "Background Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    onRawMotionEvent(event)
                    val route = DrawingInputRouter.routeFor(event, inputRoutingConfig)
                    if (route == DrawingInputRoute.FingerTapToFocus) {
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                fingerTapDownX = event.x
                                fingerTapDownY = event.y
                                fingerTapDownTimeMillis = event.eventTime
                                fingerTapCandidate = true
                            }

                            MotionEvent.ACTION_POINTER_DOWN -> fingerTapCandidate = false
                            MotionEvent.ACTION_MOVE -> {
                                if (hypot(event.x - fingerTapDownX, event.y - fingerTapDownY) > fingerTapSlop) {
                                    fingerTapCandidate = false
                                }
                            }

                            MotionEvent.ACTION_UP -> {
                                val moved = hypot(event.x - fingerTapDownX, event.y - fingerTapDownY)
                                val duration = event.eventTime - fingerTapDownTimeMillis
                                if (fingerTapCandidate && moved <= fingerTapSlop && duration <= fingerTapTimeoutMillis) {
                                    onFingerTap(event.x, event.y)
                                }
                                fingerTapCandidate = false
                            }

                            MotionEvent.ACTION_CANCEL -> fingerTapCandidate = false
                        }
                    }
                    route == DrawingInputRoute.FingerTapToFocus
                }
        ) {
            val canvas = drawContext.canvas.nativeCanvas
            strokes.forEachIndexed { index, stroke ->
                val blendMode = if (stroke.brush.family == StockBrushes.highlighter()) {
                    BlendMode.Multiply
                } else {
                    BlendMode.SrcOver
                }
                val matrix = Matrix()
                val translation = strokeTranslations[index] ?: (0f to 0f)
                matrix.postTranslate(translation.first, translation.second)
                matrix.postScale(canvasTransform.scale, canvasTransform.scale)
                matrix.postTranslate(canvasTransform.panX, canvasTransform.panY)
                drawContext.canvas.withSaveLayer(
                    drawContext.size.toRect(),
                    androidx.compose.ui.graphics.Paint()
                        .apply { this.blendMode = blendMode }) {
                    canvas.withSave {
                        canvasStrokeRenderer.draw(
                            stroke = stroke,
                            canvas = this,
                            strokeToScreenTransform = matrix
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInputWithSiblingFallthrough {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            onStartDrag()
                        },
                        onDrag = { change, _ ->
                            change.consume()
                        },
                        onDragEnd = {
                            // Do nothing.
                        }
                    )
                }
        )

        if (isSelectionMode) {
            var dragStart by remember { mutableStateOf<Offset?>(null) }
            var dragEnd by remember { mutableStateOf<Offset?>(null) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(hasSelection) {
                        detectDragGestures(
                            onDragStart = { start ->
                                dragStart = start
                                dragEnd = start
                            },
                            onDragEnd = {
                                val start = dragStart
                                val end = dragEnd
                                if (start != null && end != null) {
                                    if (hasSelection) {
                                        // Drag already applied incrementally as group move.
                                    } else {
                                        onSelectionLasso(start, end)
                                    }
                                }
                                dragStart = null
                                dragEnd = null
                            }
                        ) { change, dragAmount ->
                            if (hasSelection) {
                                onMoveSelection(dragAmount.x, dragAmount.y)
                            } else {
                                dragEnd = change.position
                            }
                            change.consume()
                        }
                    }
            ) {
                val start = dragStart
                val end = dragEnd
                if (start != null && end != null && !hasSelection) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val rect = Rect(start, end)
                        drawRect(
                            color = Color(0x3347A9FF),
                            topLeft = rect.topLeft,
                            size = rect.size
                        )
                        drawRect(
                            color = Color(0xFF47A9FF),
                            topLeft = rect.topLeft,
                            size = rect.size,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                        )
                    }
                }
            }
        } else if (isEraserMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onEraseStart() },
                            onDragEnd = { onEraseEnd() }
                        ) { change, _ ->
                            onErase(change.position.x, change.position.y)
                            change.consume()
                        }
                    }
            )
        } else {
            val cacheGen by textureStore.generation.collectAsState()
            key(cacheGen) {
                InProgressStrokes(
                    defaultBrush = currentBrush,
                    nextBrush = onGetNextBrush,
                    onStrokesFinished = onStrokesFinished,
                    textureBitmapStore = textureStore
                )
            }
        }

    }
}

@Preview
@Composable
fun DrawingSurfacePreview() {
    val textureStore = LocalTextureStore.current
    val cacheGen by textureStore.generation.collectAsState()
    val canvasStrokeRenderer = remember(cacheGen) { CanvasStrokeRenderer.create(textureStore) }
    var currentBrush by remember {
        mutableStateOf(
            Brush.createWithComposeColor(
                family = StockBrushes.highlighter(),
                color = androidx.compose.ui.graphics.Color.Blue,
                size = 10F,
                epsilon = 0.01F
            )
        )
    }

    DrawingSurface(
        strokes = emptyList(),
        canvasTransform = CanvasTransform(),
        canvasStrokeRenderer = canvasStrokeRenderer,
        onStrokesFinished = {},
        onErase = { _, _ -> },
        onEraseStart = {},
        onEraseEnd = {},
        currentBrush = currentBrush,
        onGetNextBrush = { currentBrush },
        isEraserMode = false,
        isSelectionMode = false,
        hasSelection = false,
        backgroundImageUri = null,
        onStartDrag = {}
    )
}
