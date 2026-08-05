package com.neonote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.neonote.engine.FileAssetStore
import com.neonote.model.FloatingImage
import kotlin.math.roundToInt

@Composable
internal fun FloatingImageView(
    image: FloatingImage,
    selected: Boolean,
    selectionMode: Boolean,
    controller: NeoNoteEditorController,
) {
    val context = LocalContext.current
    val store = FileAssetStore(FileAssetStore.defaultDirectory(context.filesDir))
    val requestedWidth = image.size.width.roundToInt().coerceAtLeast(1)
    val requestedHeight = image.size.height.roundToInt().coerceAtLeast(1)
    val bitmap by produceState<ImageBitmap?>(null, image.assetId, requestedWidth, requestedHeight, context.filesDir.absolutePath) {
        value = AssetImageLoader.load(store, image.assetId, requestedWidth, requestedHeight)
    }
    var destinationSize = IntSize(
        image.size.width.roundToInt().coerceAtLeast(1),
        image.size.height.roundToInt().coerceAtLeast(1),
    )
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .offset { IntOffset(image.position.x.roundToInt(), image.position.y.roundToInt()) }
            .size(image.size.width.dp, image.size.height.dp)
            .zIndex(image.zIndex.toFloat())
            .graphicsLayer(rotationZ = image.rotationDegrees)
            .clip(shape)
            .background(Color(0xFFF0EDF4))
            .border(
                if (selected) 2.dp else 1.dp,
                when {
                    image.isLocked -> Color(0xFFB7791F)
                    selected -> Color(0xFF2563EB)
                    else -> Color(0xFFD8D3E1)
                },
                shape,
            )
            .onSizeChanged { destinationSize = it }
            .then(
                if (selectionMode) Modifier.pointerInput(image.id) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        controller.selectCanvasObject(image.id)
                    }
                } else Modifier,
            )
            .semantics {
                contentDescription = buildString {
                    append(image.altText?.takeIf(String::isNotBlank) ?: "Floating image")
                    if (image.isLocked) append(", locked")
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val source = bitmap
        if (source == null) {
            Text("Image unavailable", color = Color(0xFF756E83))
        } else {
            val crop = image.crop.normalized()
            Canvas(Modifier.fillMaxSize()) {
                val sourceLeft = (crop.leftFraction * source.width).roundToInt().coerceIn(0, source.width - 1)
                val sourceTop = (crop.topFraction * source.height).roundToInt().coerceIn(0, source.height - 1)
                val sourceRight = (crop.rightFraction * source.width).roundToInt().coerceIn(sourceLeft + 1, source.width)
                val sourceBottom = (crop.bottomFraction * source.height).roundToInt().coerceIn(sourceTop + 1, source.height)
                drawImage(
                    image = source,
                    srcOffset = IntOffset(sourceLeft, sourceTop),
                    srcSize = IntSize(sourceRight - sourceLeft, sourceBottom - sourceTop),
                    dstOffset = IntOffset.Zero,
                    dstSize = destinationSize,
                    filterQuality = FilterQuality.Medium,
                )
            }
        }
        if (image.isLocked) {
            Text(
                "🔒",
                modifier = Modifier.align(Alignment.TopEnd).background(Color.White.copy(alpha = 0.82f), shape),
            )
        }
    }
}

