package com.neonote.engine

import com.neonote.model.EditorState
import com.neonote.model.NeoNoteDocument
import com.neonote.model.NotePage

/** Pure document/page lifecycle operations used by the workspace and tests. */
public class DocumentEngine(
    private val idGenerator: IdGenerator,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) {
    public fun execute(command: DocumentCommand): DocumentCommandResult = when (command) {
        is DocumentCommand.CreateDocument -> DocumentCommandResult.DocumentCreated(
            createDocument(
                title = command.title,
                assetStoreId = command.assetStoreId,
                documentId = command.documentId ?: idGenerator.nextId("document"),
                firstPageId = command.firstPageId ?: idGenerator.nextId("page"),
            ),
        )
        is DocumentCommand.AddPage -> addPage(command.document, command.page)
        is DocumentCommand.DeletePage -> deletePage(command.document, command.pageId)
        is DocumentCommand.DuplicatePage -> duplicatePage(command.document, command.pageId)
        is DocumentCommand.RenamePage -> renamePage(command.document, command.pageId, command.title)
        is DocumentCommand.MovePage -> movePage(command.document, command.fromIndex, command.toIndex)
        is DocumentCommand.SwitchPage -> switchPage(command.state, command.pageId)
        is DocumentCommand.RenameDocument -> DocumentCommandResult.DocumentUpdated(
            touch(command.document.copy(title = command.title.take(MaxDocumentTitleLength))),
        )
        is DocumentCommand.ToggleFavorite -> DocumentCommandResult.DocumentUpdated(
            touch(command.document.copy(isFavorite = !command.document.isFavorite)),
        )
    }

    public fun createDocument(
        title: String,
        assetStoreId: String,
        documentId: String = idGenerator.nextId("document"),
        firstPageId: String = idGenerator.nextId("page"),
    ): NeoNoteDocument {
        val now = clockMillis()
        return NeoNoteDocument(
            id = documentId,
            title = title.take(MaxDocumentTitleLength),
            pages = listOf(NotePage(
                id = firstPageId,
                title = "Page 1",
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )),
            assetStoreId = assetStoreId,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
    }

    public fun addPage(
        document: NeoNoteDocument,
        page: NotePage? = null,
    ): DocumentCommandResult.PageAdded {
        val now = clockMillis()
        val nextPage = page ?: NotePage(
            id = idGenerator.nextId("page"),
            title = "Page ${document.pages.size + 1}",
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        return DocumentCommandResult.PageAdded(touch(document.copy(pages = document.pages + nextPage)), nextPage)
    }

    public fun deletePage(document: NeoNoteDocument, pageId: String): DocumentCommandResult.PageDeleted {
        val index = document.pages.indexOfFirst { it.id == pageId }
        require(index >= 0) { "Unknown page id: $pageId" }
        if (document.pages.size == 1) {
            return DocumentCommandResult.PageDeleted(document, pageId, document.pages.single().id)
        }
        val pages = document.pages.filterNot { it.id == pageId }
        val nextId = pages[index.coerceAtMost(pages.lastIndex)].id
        return DocumentCommandResult.PageDeleted(touch(document.copy(pages = pages)), pageId, nextId)
    }

    public fun duplicatePage(document: NeoNoteDocument, pageId: String): DocumentCommandResult.PageAdded {
        val index = document.pages.indexOfFirst { it.id == pageId }
        require(index >= 0) { "Unknown page id: $pageId" }
        val source = document.pages[index]
        val now = clockMillis()
        val duplicate = source.copy(
            id = idGenerator.nextId("page"),
            title = source.title.ifBlank { "Page ${index + 1}" } + " copy",
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        val insertion = index + 1
        val pages = document.pages.take(insertion) + duplicate + document.pages.drop(insertion)
        return DocumentCommandResult.PageAdded(touch(document.copy(pages = pages)), duplicate)
    }

    public fun renamePage(document: NeoNoteDocument, pageId: String, title: String): DocumentCommandResult.PageUpdated {
        val index = document.pages.indexOfFirst { it.id == pageId }
        require(index >= 0) { "Unknown page id: $pageId" }
        val updated = document.pages[index].copy(
            title = title.take(MaxPageTitleLength),
            updatedAtEpochMillis = clockMillis(),
        )
        return DocumentCommandResult.PageUpdated(
            touch(document.copy(pages = document.pages.replaceAt(index, updated))),
            updated,
        )
    }

    public fun movePage(document: NeoNoteDocument, fromIndex: Int, toIndex: Int): DocumentCommandResult.PagesReordered {
        require(fromIndex in document.pages.indices) { "fromIndex must address a page" }
        val pages = document.pages.toMutableList()
        val page = pages.removeAt(fromIndex)
        val target = toIndex.coerceIn(0, pages.size)
        pages.add(target, page)
        return DocumentCommandResult.PagesReordered(touch(document.copy(pages = pages)))
    }

    public fun switchPage(state: EditorState, pageId: String): DocumentCommandResult.PageSwitched {
        require(state.document.pages.any { it.id == pageId }) { "Unknown page id: $pageId" }
        return DocumentCommandResult.PageSwitched(state.copy(currentPageId = pageId))
    }

    private fun touch(document: NeoNoteDocument): NeoNoteDocument = document.copy(
        revision = document.revision + 1,
        updatedAtEpochMillis = clockMillis(),
    )

    private fun <T> List<T>.replaceAt(index: Int, item: T): List<T> =
        mapIndexed { currentIndex, current -> if (currentIndex == index) item else current }

    public companion object {
        public const val MaxDocumentTitleLength: Int = 120
        public const val MaxPageTitleLength: Int = 80
    }
}

public sealed interface DocumentCommand {
    public data class CreateDocument(
        val title: String,
        val assetStoreId: String,
        val documentId: String? = null,
        val firstPageId: String? = null,
    ) : DocumentCommand
    public data class AddPage(val document: NeoNoteDocument, val page: NotePage? = null) : DocumentCommand
    public data class DeletePage(val document: NeoNoteDocument, val pageId: String) : DocumentCommand
    public data class DuplicatePage(val document: NeoNoteDocument, val pageId: String) : DocumentCommand
    public data class RenamePage(val document: NeoNoteDocument, val pageId: String, val title: String) : DocumentCommand
    public data class MovePage(val document: NeoNoteDocument, val fromIndex: Int, val toIndex: Int) : DocumentCommand
    public data class SwitchPage(val state: EditorState, val pageId: String) : DocumentCommand
    public data class RenameDocument(val document: NeoNoteDocument, val title: String) : DocumentCommand
    public data class ToggleFavorite(val document: NeoNoteDocument) : DocumentCommand
}

public sealed interface DocumentCommandResult {
    public data class DocumentCreated(val document: NeoNoteDocument) : DocumentCommandResult
    public data class DocumentUpdated(val document: NeoNoteDocument) : DocumentCommandResult
    public data class PageAdded(val document: NeoNoteDocument, val page: NotePage) : DocumentCommandResult
    public data class PageDeleted(val document: NeoNoteDocument, val deletedPageId: String, val nextPageId: String) : DocumentCommandResult
    public data class PageUpdated(val document: NeoNoteDocument, val page: NotePage) : DocumentCommandResult
    public data class PagesReordered(val document: NeoNoteDocument) : DocumentCommandResult
    public data class PageSwitched(val state: EditorState) : DocumentCommandResult
}
