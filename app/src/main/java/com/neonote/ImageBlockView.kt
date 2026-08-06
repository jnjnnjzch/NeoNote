package com.neonote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.neonote.engine.FileAssetStore
import com.neonote.model.BlockImage
import com.neonote.model.ImageCrop
import com.neonote.model.RichContentBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MaximumDecodedImageDimension = 2_048

@Composable
internal fun ImageBlockView(
    box: RichContentBox,
    blockIndex: Int,
    block: BlockImage,
    selected: Boolean,
    enabled: Boolean,
    controller: NeoNoteEditorController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val assetStore = remember(context) { FileAssetStore(FileAssetStore.defaultDirectory(context.filesDir)) }
    val scope = rememberCoroutineScope()
    val replacementPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val draft = context.contentResolver.readEditorImageAssetDraft(uri) ?: return@launch
            val replacement = assetStore.put(draft)
            controller.updateRichContentImage(box.id, blockIndex, replacementAssetId = replacement.id)
        }
    }
    val imageBitmap by produceState<ImageBitmap?>(null, block.assetId, context.filesDir.absolutePath) {
        value = withContext(Dispatchers.IO) {
            assetStore.get(block.assetId)?.uri?.let(::decodeSampledBitmap)?.asImageBitmap()
        }
    }
    val shape = RoundedCornerShape(8.dp)
    val imageHeight = (block.height ?: 220f).coerceIn(72f, 920f).dp
    val cropEnabled = block.crop != ImageCrop()
    var menuVisible by remember(blockIndex, box.id) { mutableStateOf(false) }
    var resizeHeight by remember(blockIndex, box.id, block.height) { mutableStateOf(block.height ?: 220f) }

    Column(
        modifier = modifier
            .widthIn(min = 120.dp)
            .border(if (selected) 1.dp else 0.dp, MaterialTheme.colorScheme.primary.copy(alpha = .75f), shape)
            .then(if (enabled) Modifier.clickable { controller.selectRichContentObjectBlock(box.id, blockIndex) } else Modifier)
            .semantics {
                contentDescription = block.altText?.takeIf(String::isNotBlank) ?: "Image"
                role = Role.Button
                this.selected = selected
            },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(imageHeight).clip(shape).background(Color(0xFFF2F0F6)),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = imageBitmap
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = block.altText,
                    modifier = Modifier.fillMaxSize().graphicsLayer(rotationZ = block.rotationDegrees),
                    contentScale = if (cropEnabled) ContentScale.Crop else ContentScale.Fit,
                )
            } else {
                Text("Image unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (selected && enabled) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                ) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = .94f))
                            .clickable { menuVisible = true }
                            .semantics { role = Role.Button; contentDescription = "Image options" },
                        contentAlignment = Alignment.Center,
                    ) { Text("⋮", style = MaterialTheme.typography.titleMedium) }
                    DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }) {
                        DropdownMenuItem(text = { Text("Replace image…") }, onClick = {
                            menuVisible = false
                            replacementPicker.launch("image/*")
                        })
                        DropdownMenuItem(text = { Text(if (cropEnabled) "Fit image" else "Fill frame") }, onClick = {
                            menuVisible = false
                            controller.updateRichContentImage(
                                box.id,
                                blockIndex,
                                crop = if (cropEnabled) ImageCrop() else ImageCrop(.08f, .08f, .92f, .92f),
                            )
                        })
                        DropdownMenuItem(text = { Text("Rotate clockwise") }, onClick = {
                            menuVisible = false
                            controller.updateRichContentImage(
                                box.id,
                                blockIndex,
                                rotationDegrees = (block.rotationDegrees + 90f) % 360f,
                            )
                        })
                        DropdownMenuItem(text = { Text("Delete image", color = MaterialTheme.colorScheme.error) }, onClick = {
                            menuVisible = false
                            controller.deleteRichContentBlock(box.id, blockIndex)
                        })
                    }
                }
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = 7.dp).size(width = 64.dp, height = 28.dp)
                        .pointerInput(box.id, blockIndex) {
                            detectDragGestures(
                                onDragStart = {
                                    resizeHeight = block.height ?: 220f
                                    controller.beginRichContentGesture(box.id)
                                },
                                onDragCancel = { controller.cancelRichContentGesture(box.id) },
                                onDragEnd = { controller.endRichContentGesture(box.id) },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    resizeHeight = (resizeHeight + dragAmount.y / density).coerceIn(72f, 920f)
                                    controller.updateRichContentImage(box.id, blockIndex, height = resizeHeight)
                                },
                            )
                        }
                        .semantics { contentDescription = "Resize image vertically" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(width = 34.dp, height = 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
        }

        if (selected && enabled) {
            BasicTextField(
                value = block.caption.orEmpty(),
                onValueChange = { controller.updateRichContentImage(box.id, blockIndex, caption = it.take(300)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    Box {
                        if (block.caption.isNullOrBlank()) {
                            Text("Add caption…", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .68f), style = MaterialTheme.typography.bodySmall)
                        }
                        inner()
                    }
                },
            )
        } else if (!block.caption.isNullOrBlank()) {
            Text(
                block.caption,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun decodeSampledBitmap(path: String): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (
        bounds.outWidth / sample > MaximumDecodedImageDimension ||
        bounds.outHeight / sample > MaximumDecodedImageDimension
    ) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}
