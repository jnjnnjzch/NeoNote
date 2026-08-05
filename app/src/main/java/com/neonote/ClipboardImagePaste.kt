package com.neonote

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.neonote.engine.AssetDraft
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MaximumClipboardImageBytes = 32 * 1024 * 1024

/** Returns the first clipboard URI that can conservatively be identified as an image. */
internal fun ClipboardManager.primaryImageUri(context: Context): Uri? {
    val clip = primaryClip ?: return null
    val descriptionContainsImage = (0 until clip.description.mimeTypeCount).any { index ->
        ClipDescription.compareMimeTypes(clip.description.getMimeType(index), "image/*")
    }
    for (index in 0 until clip.itemCount) {
        val item = clip.getItemAt(index)
        val uri = item.uri ?: item.intent?.data ?: continue
        val resolvedType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        if (clipboardUriCanRepresentImage(resolvedType, descriptionContainsImage, clip.itemCount)) return uri
    }
    return null
}

/** The aggregate clip MIME is trusted only when there is one unresolved URI item. */
internal fun clipboardUriCanRepresentImage(
    resolvedMimeType: String?,
    descriptionContainsImage: Boolean,
    itemCount: Int,
): Boolean = when {
    resolvedMimeType != null -> resolvedMimeType.startsWith("image/", ignoreCase = true)
    itemCount == 1 -> descriptionContainsImage
    else -> false
}

internal suspend fun ContentResolver.readClipboardImageDraft(uri: Uri): AssetDraft? = withContext(Dispatchers.IO) {
    runCatching {
        val resolvedType = getType(uri)
        if (resolvedType != null && !resolvedType.startsWith("image/", ignoreCase = true)) return@runCatching null
        val bytes = openInputStream(uri)?.use { it.readBytesLimited(MaximumClipboardImageBytes) }
            ?: return@runCatching null
        if (bytes.isEmpty()) return@runCatching null
        AssetDraft(resolvedType ?: "image/*", bytes, clipboardDisplayName(uri))
    }.getOrNull()
}

private fun ContentResolver.clipboardDisplayName(uri: Uri): String? = runCatching {
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
    }
}.getOrNull()

private fun InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        require(total <= limit) { "Clipboard image exceeds ${limit / (1024 * 1024)} MB" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
