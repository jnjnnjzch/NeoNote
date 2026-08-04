package com.neonote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.neonote.engine.AssetStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Shared, destination-aware image decoder preventing every composable from decoding full assets independently. */
internal object AssetImageLoader {
    private val cache = object : LruCache<String, Bitmap>(cacheBudgetKilobytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
    }

    suspend fun load(
        store: AssetStore,
        assetId: String,
        requestedWidthPx: Int,
        requestedHeightPx: Int,
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        val reference = store.get(assetId) ?: return@withContext null
        val path = reference.uri ?: return@withContext null
        val file = File(path)
        if (!file.isFile) return@withContext null
        val targetWidth = requestedWidthPx.coerceIn(1, MaximumDecodeDimension)
        val targetHeight = requestedHeightPx.coerceIn(1, MaximumDecodeDimension)
        val key = "$path:${file.lastModified()}:${bucket(targetWidth)}x${bucket(targetHeight)}"
        synchronized(cache) { cache.get(key) }?.let { return@withContext it.asImageBitmap() }
        val decoded = decodeSampled(file.absolutePath, targetWidth, targetHeight) ?: return@withContext null
        synchronized(cache) { cache.put(key, decoded) }
        decoded.asImageBitmap()
    }

    fun clear(assetId: String? = null) {
        synchronized(cache) {
            if (assetId == null) cache.evictAll()
            else cache.snapshot().keys.filter { it.contains("/$assetId:") || it.contains("\\$assetId:") }
                .forEach(cache::remove)
        }
    }

    internal fun calculateSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        requestedWidth: Int,
        requestedHeight: Int,
    ): Int {
        if (sourceWidth <= 0 || sourceHeight <= 0) return 1
        val targetWidth = requestedWidth.coerceAtLeast(1)
        val targetHeight = requestedHeight.coerceAtLeast(1)
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth && sourceHeight / (sample * 2) >= targetHeight) sample *= 2
        while (max(sourceWidth / sample, sourceHeight / sample) > MaximumDecodeDimension) sample *= 2
        return sample.coerceAtLeast(1)
    }

    private fun decodeSampled(path: String, requestedWidth: Int, requestedHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight, requestedWidth, requestedHeight)
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }

    private fun bucket(value: Int): Int {
        var bucket = 64
        while (bucket < value && bucket < MaximumDecodeDimension) bucket *= 2
        return bucket.coerceAtMost(MaximumDecodeDimension)
    }

    private fun cacheBudgetKilobytes(): Int =
        ((Runtime.getRuntime().maxMemory() / 1024L) / 12L).coerceIn(8L * 1024L, 64L * 1024L).toInt()

    private const val MaximumDecodeDimension = 2_048
}
