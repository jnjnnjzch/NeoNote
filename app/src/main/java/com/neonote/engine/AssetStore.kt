package com.neonote.engine

/**
 * Asset reference API for local images and other binary resources.
 *
 * RichContent inline/block images and canvas-level FloatingImage objects both
 * reference asset ids from this boundary. This interface deliberately does not
 * define image paste, cloud upload, sync, sharing behavior, or a larger binary
 * asset pipeline.
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
    val uri: String? = null,
    val fileName: String? = null,
)

public sealed interface AssetStoreResult {
    public data object Deleted : AssetStoreResult
    public data object Missing : AssetStoreResult
}
