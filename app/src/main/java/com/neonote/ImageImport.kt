package com.neonote

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.neonote.engine.AssetDraft
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val EditorImageImportLimitBytes = 64 * 1024 * 1024

/** Shared, validated image import path for inline, nested, and floating images. */
internal suspend fun ContentResolver.readEditorImageAssetDraft(uri: Uri): AssetDraft? =
    withContext(Dispatchers.IO) {
        runCatching {
            val bytes = openInputStream(uri)?.use { it.readEditorBytesLimited(EditorImageImportLimitBytes) }
                ?: return@runCatching null
            if (bytes.isEmpty()) return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            AssetDraft(
                mediaType = getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/*",
                bytes = bytes,
                fileName = editorDisplayName(uri),
            )
        }.getOrNull()
    }

private fun ContentResolver.editorDisplayName(uri: Uri): String? =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0) cursor.getString(index) else null
    }

private fun InputStream.readEditorBytesLimited(maximumBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        require(total <= maximumBytes) { "Image exceeds the size limit" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
