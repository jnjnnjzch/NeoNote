package com.neonote.engine

import com.neonote.model.NeoNoteDocument
import java.io.File
import kotlinx.serialization.json.Json

public data class AssetGarbageCollectionResult(
    val referencedAssetCount: Int,
    val deletedFileCount: Int,
    val retainedTemporaryFileCount: Int,
)

/** Deletes only assets that are not referenced by active, trashed, or backup documents. */
public class FileAssetGarbageCollector(
    private val json: Json = DefaultDocumentJson,
    private val temporaryFileGraceMillis: Long = 24L * 60L * 60L * 1_000L,
) {
    public fun collect(
        documentRoots: List<File>,
        assetsDirectory: File,
        nowMillis: Long = System.currentTimeMillis(),
    ): AssetGarbageCollectionResult {
        val references = documentRoots.asSequence()
            .filter(File::exists)
            .flatMap { root -> root.walkTopDown().asSequence() }
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .mapNotNull { file -> runCatching { json.decodeFromString(NeoNoteDocument.serializer(), file.readText()) }.getOrNull() }
            .flatMap { it.referencedAssetIds().asSequence() }
            .toSet()

        var deleted = 0
        var retainedTemp = 0
        assetsDirectory.listFiles().orEmpty().forEach { file ->
            if (!file.isFile) return@forEach
            val assetId = when {
                file.name.endsWith(".properties") -> file.name.removeSuffix(".properties")
                file.name.endsWith(".tmp") -> file.name.removeSuffix(".tmp")
                else -> file.name
            }
            val referenced = assetId in references
            val recentTemporary = file.name.endsWith(".tmp") && nowMillis - file.lastModified() < temporaryFileGraceMillis
            when {
                referenced -> Unit
                recentTemporary -> retainedTemp += 1
                file.delete() -> deleted += 1
            }
        }
        return AssetGarbageCollectionResult(references.size, deleted, retainedTemp)
    }
}

