package com.neonote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

private const val MaximumDecodedImageDimension = 1_024

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
            reference.uri
                ?.let(::decodeSampledBitmap)
                ?.asImageBitmap()
        }
    }

    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (selected) Color(0xFF2563EB) else Color(0xFFCBD5E1)
    val backgroundColor = if (selected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(RichContentLayoutDefaults.ImageCardHeight.dp)
            .clip(shape)
            .background(backgroundColor)
            .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics {
                contentDescription = "Image block ${block.displayTitle()}"
                role = Role.Button
                this.selected = selected
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 88.dp, height = 68.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFE2E8F0))
                .border(
                    width = 1.dp,
                    color = Color(0xFFCBD5E1),
                    shape = RoundedCornerShape(10.dp),
                ),
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
                Text(
                    text = "▧",
                    color = Color(0xFF475569),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = block.displayTitle(),
                color = Color(0xFF0F172A),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (imageBitmap != null) "Stored locally" else "Missing asset: ${block.assetId}",
                color = Color(0xFF64748B),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                Text(
                    text = "Selected image block",
                    color = Color(0xFF2563EB),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
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
