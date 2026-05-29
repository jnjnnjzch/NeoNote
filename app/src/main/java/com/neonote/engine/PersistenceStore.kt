package com.neonote.engine

import com.neonote.model.NeoNoteDocument
import java.io.File
import kotlinx.serialization.json.Json

/**
 * Persistence boundary for saving and loading documents.
 *
 * Sync engines, cloud backends, export formats, and conflict resolution are intentionally out of scope.
 */
public interface PersistenceStore {
    public suspend fun save(document: NeoNoteDocument): PersistenceResult.Saved
    public suspend fun load(documentId: String): PersistenceResult.Loaded
}

/**
 * Minimal local JSON implementation of [PersistenceStore].
 *
 * The JSON payload serializes document-space coordinates from the document model directly. It does not use,
 * derive, or persist viewport pan/zoom as a replacement for canvas object or ink positions.
 */
public class JsonFilePersistenceStore(
    private val documentsDirectory: File,
    private val json: Json = DefaultDocumentJson,
) : PersistenceStore {
    override suspend fun save(document: NeoNoteDocument): PersistenceResult.Saved {
        documentsDirectory.mkdirs()
        documentFile(document.id).writeText(json.encodeToString(NeoNoteDocument.serializer(), document))
        return PersistenceResult.Saved(documentId = document.id, revision = document.revision)
    }

    override suspend fun load(documentId: String): PersistenceResult.Loaded {
        val file = documentFile(documentId)
        if (!file.exists()) return PersistenceResult.Loaded(document = null)
        return PersistenceResult.Loaded(
            document = json.decodeFromString(NeoNoteDocument.serializer(), file.readText()),
        )
    }

    private fun documentFile(documentId: String): File = File(documentsDirectory, "${documentId.toSafeFileName()}.json")

    private fun String.toSafeFileName(): String = map { char ->
        when {
            char.isLetterOrDigit() || char == '-' || char == '_' || char == '.' -> char
            else -> '_'
        }
    }.joinToString(separator = "").ifBlank { "document" }
}

public val DefaultDocumentJson: Json = Json {
    classDiscriminator = "type"
    encodeDefaults = true
    prettyPrint = true
}

public sealed interface PersistenceResult {
    public data class Saved(val documentId: String, val revision: Long) : PersistenceResult
    public data class Loaded(val document: NeoNoteDocument?) : PersistenceResult
}
