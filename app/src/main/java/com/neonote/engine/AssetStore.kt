package com.neonote.engine

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID

/**
 * Asset reference API for local images and other binary resources.
 *
 * RichContent inline/block images and canvas-level FloatingImage objects both
 * reference asset ids from this boundary. Cloud upload, sync, and sharing remain
 * outside this local storage contract.
 */
public interface AssetStore {
    public suspend fun put(asset: AssetDraft): AssetReference
    public suspend fun get(assetId: String): AssetReference?
    public suspend fun delete(assetId: String): AssetStoreResult
}

public data class AssetDraft(
    val mediaType: String,
    val bytes: ByteArray,
    val fileName: String? = null,
) {
    override fun equals(other: Any?): Boolean = other is AssetDraft &&
        mediaType == other.mediaType &&
        bytes.contentEquals(other.bytes) &&
        fileName == other.fileName

    override fun hashCode(): Int {
        var result = mediaType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + (fileName?.hashCode() ?: 0)
        return result
    }
}

public data class AssetReference(
    val id: String,
    val mediaType: String,
    /** Absolute local file path for the current on-device implementation. */
    val uri: String? = null,
    val fileName: String? = null,
)

public sealed interface AssetStoreResult {
    public data object Deleted : AssetStoreResult
    public data object Missing : AssetStoreResult
}

/**
 * App-private asset storage used by the Android editor.
 *
 * Asset bytes and metadata are written through same-directory temporary files,
 * so an interrupted import cannot replace an existing asset with a partial file.
 * Generated ids are path-safe and document data cannot escape [assetsDirectory].
 */
public class FileAssetStore(
    private val assetsDirectory: File,
) : AssetStore {
    override suspend fun put(asset: AssetDraft): AssetReference {
        require(asset.bytes.isNotEmpty()) { "asset bytes must not be empty" }
        assetsDirectory.mkdirs()
        val id = "asset-${UUID.randomUUID()}${asset.mediaType.fileExtension()}"
        val file = requireNotNull(assetFile(id))
        file.writeBytesAtomically(asset.bytes)
        metadataFile(id).writePropertiesAtomically(
            Properties().apply {
                setProperty(MediaTypeKey, asset.mediaType)
                asset.fileName?.let { setProperty(FileNameKey, it) }
            },
        )
        return AssetReference(
            id = id,
            mediaType = asset.mediaType,
            uri = file.absolutePath,
            fileName = asset.fileName,
        )
    }

    override suspend fun get(assetId: String): AssetReference? {
        val file = assetFile(assetId) ?: return null
        if (!file.isFile) return null
        val metadata = metadataFile(assetId).readPropertiesOrNull()
        return AssetReference(
            id = assetId,
            mediaType = metadata?.getProperty(MediaTypeKey)
                ?: assetId.inferredMediaType(),
            uri = file.absolutePath,
            fileName = metadata?.getProperty(FileNameKey),
        )
    }

    override suspend fun delete(assetId: String): AssetStoreResult {
        val file = assetFile(assetId) ?: return AssetStoreResult.Missing
        if (!file.exists()) return AssetStoreResult.Missing
        val deleted = file.delete()
        metadataFile(assetId).delete()
        return if (deleted) AssetStoreResult.Deleted else AssetStoreResult.Missing
    }

    private fun assetFile(assetId: String): File? {
        if (!SafeAssetId.matches(assetId)) return null
        assetsDirectory.mkdirs()
        val root = assetsDirectory.canonicalFile
        val candidate = File(root, assetId).canonicalFile
        return candidate.takeIf { it.parentFile == root }
    }

    private fun metadataFile(assetId: String): File =
        File(assetsDirectory, "$assetId.properties")

    public companion object {
        public fun defaultDirectory(appFilesDirectory: File): File =
            appFilesDirectory.resolve("assets")
    }
}

private fun File.writeBytesAtomically(bytes: ByteArray) {
    val temporary = File(parentFile, "$name.tmp")
    temporary.writeBytes(bytes)
    temporary.replaceAtomically(this)
}

private fun File.writePropertiesAtomically(properties: Properties) {
    val temporary = File(parentFile, "$name.tmp")
    temporary.outputStream().use { properties.store(it, null) }
    temporary.replaceAtomically(this)
}

private fun File.replaceAtomically(destination: File) {
    try {
        try {
            Files.move(
                toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    } finally {
        if (exists()) delete()
    }
}

private fun File.readPropertiesOrNull(): Properties? {
    if (!isFile) return null
    return runCatching {
        Properties().also { properties ->
            inputStream().use(properties::load)
        }
    }.getOrNull()
}

private fun String.fileExtension(): String = when (lowercase()) {
    "image/jpeg", "image/jpg" -> ".jpg"
    "image/png" -> ".png"
    "image/webp" -> ".webp"
    "image/gif" -> ".gif"
    else -> ".bin"
}

private fun String.inferredMediaType(): String = when (substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    else -> "application/octet-stream"
}

private val SafeAssetId = Regex("[A-Za-z0-9._-]+")
private const val MediaTypeKey = "mediaType"
private const val FileNameKey = "fileName"
