package com.neonote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neonote.engine.FileAssetStore
import com.neonote.model.BlockImage
import com.neonote.model.ImageCrop
import com.neonote.model.RichContentBox
import kotlinx.coroutines.Dispatchers
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
    val assetStore = FileAssetStore(FileAssetStore.defaultDirectory(context.filesDir))
    val imageBitmap by produceState<ImageBitmap?>(null, block.assetId, context.filesDir.absolutePath) {
        value = withContext(Dispatchers.IO) {
            assetStore.get(block.assetId)?.uri?.let(::decodeSampledBitmap)?.asImageBitmap()
        }
    }
    val shape = RoundedCornerShape(12.dp)
    val imageHeight = (block.height ?: 220f).coerceIn(96f, 640f).dp
    val cropEnabled = block.crop != ImageCrop()

    Column(
        modifier = modifier
            .widthIn(min = 120.dp)
            .clip(shape)
            .background(Color.White)
            .border(if (selected) 2.dp else 1.dp, if (selected) Color(0xFF5B3FD1) else Color(0xFFD8D3E1), shape)
            .then(if (enabled) Modifier.clickable {
                controller.selectRichContentObjectBlock(box.id, blockIndex)
            } else Modifier)
            .semantics {
                contentDescription = "Image block ${block.displayTitle()}"
                role = Role.Button
                this.selected = selected
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .background(Color(0xFFF2F0F6)),
            contentAlignment = Alignment.Center,
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = requireNotNull(imageBitmap),
                    contentDescription = block.altText,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(rotationZ = block.rotationDegrees),
                    contentScale = if (cropEnabled) ContentScale.Crop else ContentScale.Fit,
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("▧", color = Color(0xFF756E83), style = MaterialTheme.typography.headlineMedium)
                    Text("Image unavailable", color = Color(0xFF756E83), style = MaterialTheme.typography.bodySmall)
                    Text(block.assetId, color = Color(0xFF9A94A5), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (selected && enabled) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    controller.updateRichContentImage(box.id, blockIndex, height = (block.height ?: 220f) * 0.8f)
                }) { Text("Smaller") }
                TextButton(onClick = {
                    controller.updateRichContentImage(box.id, blockIndex, height = (block.height ?: 220f) * 1.25f)
                }) { Text("Larger") }
                TextButton(onClick = {
                    controller.updateRichContentImage(box.id, blockIndex, rotationDegrees = (block.rotationDegrees + 90f) % 360f)
                }) { Text("Rotate") }
                TextButton(onClick = {
                    controller.updateRichContentImage(
                        box.id,
                        blockIndex,
                        crop = if (cropEnabled) ImageCrop() else ImageCrop(0.1f, 0.1f, 0.9f, 0.9f),
                    )
                }) { Text(if (cropEnabled) "Fit" else "Crop") }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = block.caption.orEmpty(),
                    onValueChange = { controller.updateRichContentImage(box.id, blockIndex, caption = it.take(200)) },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF302B3A)),
                    decorationBox = { inner ->
                        if (block.caption.isNullOrBlank()) Text("Add caption…", color = Color(0xFF9A94A5), style = MaterialTheme.typography.bodySmall)
                        inner()
                    },
                )
                TextButton(onClick = { controller.deleteRichContentBlock(box.id, blockIndex) }) {
                    Text("Delete", color = Color(0xFFB42318), fontWeight = FontWeight.SemiBold)
                }
            }
        } else if (!block.caption.isNullOrBlank()) {
            Text(
                block.caption,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color(0xFF5F596A),
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
    while (bounds.outWidth / sample > MaximumDecodedImageDimension || bounds.outHeight / sample > MaximumDecodedImageDimension) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}

private fun BlockImage.displayTitle(): String = altText?.takeIf(String::isNotBlank) ?: "Image"
