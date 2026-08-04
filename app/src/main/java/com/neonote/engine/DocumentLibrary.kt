package com.neonote.engine

import com.neonote.model.NeoNoteDocument
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
public data class DocumentSummary(
    val id: String,
    val title: String,
    val pageCount: Int,
    val updatedAtEpochMillis: Long,
    val isFavorite: Boolean,
    val isTrashed: Boolean = false,
)

public interface DocumentLibrary : PersistenceStore {
    public suspend fun list(includeTrash: Boolean = false): List<DocumentSummary>
    override public suspend fun save(document: NeoNoteDocument): PersistenceResult.Saved
    override public suspend fun load(documentId: String): PersistenceResult.Loaded
    public suspend fun moveToTrash(documentId: String): Boolean
    public suspend fun restore(documentId: String): Boolean
    public suspend fun permanentlyDelete(documentId: String): Boolean
    public suspend fun exportJson(documentId: String): String?
    public suspend fun importJson(payload: String, replaceId: String? = null): NeoNoteDocument
}

/** Atomic app-private document library with a recoverable trash directory. */
public class FileDocumentLibrary(
    rootDirectory: File,
    private val json: Json = DefaultDocumentJson,
) : DocumentLibrary {
    private val documentsDirectory = rootDirectory.resolve("documents")
    private val trashDirectory = rootDirectory.resolve("trash")
    private val persistence = VersionedPersistenceStore(documentsDirectory, json)

    override suspend fun list(includeTrash: Boolean): List<DocumentSummary> {
        val active = scan(documentsDirectory, isTrashed = false)
        val trash = if (includeTrash) scan(trashDirectory, isTrashed = true) else emptyList()
        return (active + trash).sortedWith(
            compareByDescending<DocumentSummary> { it.isFavorite }
                .thenByDescending { it.updatedAtEpochMillis }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
    }

    override suspend fun save(document: NeoNoteDocument): PersistenceResult.Saved = persistence.save(document)
    override suspend fun load(documentId: String): PersistenceResult.Loaded = persistence.load(documentId)

    override suspend fun moveToTrash(documentId: String): Boolean = move(
        documentFile(documentsDirectory, documentId),
        documentFile(trashDirectory, documentId),
    )

    override suspend fun restore(documentId: String): Boolean = move(
        documentFile(trashDirectory, documentId),
        documentFile(documentsDirectory, documentId),
    )

    override suspend fun permanentlyDelete(documentId: String): Boolean {
        val active = documentFile(documentsDirectory, documentId)
        val trash = documentFile(trashDirectory, documentId)
        var deleted = false
        if (active.exists()) deleted = active.delete() || deleted
        if (trash.exists()) deleted = trash.delete() || deleted
        persistence.deleteBackups(documentId)
        return deleted
    }

    override suspend fun exportJson(documentId: String): String? {
        val active = documentFile(documentsDirectory, documentId)
        val trash = documentFile(trashDirectory, documentId)
        return when {
            active.exists() -> active.readText()
            trash.exists() -> trash.readText()
            else -> null
        }
    }

    override suspend fun importJson(payload: String, replaceId: String?): NeoNoteDocument {
        val decoded = json.decodeFromString(NeoNoteDocument.serializer(), payload)
        val imported = if (replaceId == null) decoded else decoded.copy(id = replaceId)
        save(imported)
        return imported
    }

    private fun scan(directory: File, isTrashed: Boolean): List<DocumentSummary> {
        if (!directory.exists()) return emptyList()
        return directory.listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
            .orEmpty()
            .mapNotNull { file ->
                runCatching {
                    val document = json.decodeFromString(NeoNoteDocument.serializer(), file.readText())
                    DocumentSummary(
                        id = document.id,
                        title = document.title,
                        pageCount = document.pages.size,
                        updatedAtEpochMillis = document.updatedAtEpochMillis.takeIf { it > 0L } ?: file.lastModified(),
                        isFavorite = document.isFavorite,
                        isTrashed = isTrashed,
                    )
                }.getOrNull()
            }
    }

    private fun move(source: File, destination: File): Boolean {
        if (!source.exists()) return false
        destination.parentFile?.mkdirs()
        return runCatching {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            true
        }.getOrElse {
            source.copyTo(destination, overwrite = true)
            source.delete()
        }
    }

    private fun documentFile(directory: File, documentId: String): File =
        directory.resolve("${documentId.safeName()}.json")

    public fun pruneUnreferencedAssets(assetsDirectory: File): AssetGarbageCollectionResult =
        FileAssetGarbageCollector(json).collect(
            documentRoots = listOf(documentsDirectory, trashDirectory, documentsDirectory.resolve(".backups")),
            assetsDirectory = assetsDirectory,
        )

}
