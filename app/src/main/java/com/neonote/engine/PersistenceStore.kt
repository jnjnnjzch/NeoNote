package com.neonote.engine

import com.neonote.model.NeoNoteDocument

/**
 * Persistence boundary for saving and loading documents.
 *
 * Sync engines, cloud backends, export formats, and conflict resolution are intentionally out of scope.
 */
public interface PersistenceStore {
    public suspend fun save(document: NeoNoteDocument): PersistenceResult.Saved
    public suspend fun load(documentId: String): PersistenceResult.Loaded
}

public sealed interface PersistenceResult {
    public data class Saved(val documentId: String, val revision: Long) : PersistenceResult
    public data class Loaded(val document: NeoNoteDocument?) : PersistenceResult
}
