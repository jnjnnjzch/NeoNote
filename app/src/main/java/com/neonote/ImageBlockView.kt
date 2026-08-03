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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neonote.engine.FileAssetStore
import com.neonote.engine.RichContentLayoutDefaults
import com.neonote.model.BlockImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MaximumDecodedImageDimension = 1_600

@Composable
internal fun ImageBlockView(
    block: BlockImage,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val assetStore = FileAssetStore(FileAssetStore.defaultDirectory(context.filesDir))
    val imageBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = block.assetId,
        key2 = context.filesDir.absolutePath,
    ) {
        value = withContext(Dispatchers.IO) {
            val reference = assetStore.get(block.assetId) ?: return@withContext null
            reference.uri?.let(::decodeSampledBitmap)?.asImageBitmap()
        }
    }

    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (selected) Color(0xFF5B3FD1) else Color(0xFFD8D3E1)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(RichContentLayoutDefaults.ImageCardHeight.dp)
            .clip(shape)
            .background(Color.White)
            .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics {
                contentDescription = "Image block ${block.displayTitle()}"
                role = Role.Button
                this.selected = selected
            },
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFFF2F0F6)),
            contentAlignment = Alignment.Center,
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = requireNotNull(imageBitmap),
                    contentDescription = block.altText,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "▧",
                        color = Color(0xFF756E83),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "Image unavailable",
                        modifier = Modifier.padding(top = 4.dp),
                        color = Color(0xFF756E83),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = block.displayTitle(),
                modifier = Modifier.weight(1f),
                color = Color(0xFF272330),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    selected -> "Selected"
                    imageBitmap != null -> "Local image"
                    else -> "Missing"
                },
                color = if (selected) Color(0xFF5B3FD1) else Color(0xFF7B7586),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun decodeSampledBitmap(path: String): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (
        bounds.outWidth / sampleSize > MaximumDecodedImageDimension ||
        bounds.outHeight / sampleSize > MaximumDecodedImageDimension
    ) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    )
}

private fun BlockImage.displayTitle(): String =
    altText?.takeIf { it.isNotBlank() } ?: "Image"
