package com.neonote

import com.neonote.engine.DocumentSearchHit
import com.neonote.engine.DocumentSearchIndex
import com.neonote.engine.FileDocumentLibrary

/** Keeps search UI orchestration out of the editor reducer. */
internal class DocumentSearchCoordinator(
    private val searchIndex: DocumentSearchIndex,
    private val library: FileDocumentLibrary,
    private val controller: NeoNoteEditorController,
) {
    suspend fun search(query: String): List<DocumentSearchHit> = searchIndex.search(query)

    suspend fun open(hit: DocumentSearchHit): Boolean {
        val loaded = library.load(hit.documentId).document ?: return false
        controller.replaceDocument(loaded, recordHistory = false)
        if (loaded.pages.any { it.id == hit.pageId }) controller.switchPage(hit.pageId)
        return true
    }
}
