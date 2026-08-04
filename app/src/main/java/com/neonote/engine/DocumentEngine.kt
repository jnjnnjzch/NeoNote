package com.neonote.engine

import com.neonote.model.EditorState
import com.neonote.model.LegacyDefaultSectionId
import com.neonote.model.NeoNoteDocument
import com.neonote.model.NotePage
import com.neonote.model.NoteSection
import com.neonote.model.effectiveSectionId
import com.neonote.model.effectiveSections

/** Pure notebook, section and page lifecycle operations. */
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
                firstSectionId = command.firstSectionId ?: idGenerator.nextId("section"),
            ),
        )
        is DocumentCommand.AddPage -> addPage(command.document, command.page, command.sectionId)
        is DocumentCommand.DeletePage -> deletePage(command.document, command.pageId)
        is DocumentCommand.DuplicatePage -> duplicatePage(command.document, command.pageId)
        is DocumentCommand.RenamePage -> renamePage(command.document, command.pageId, command.title)
        is DocumentCommand.MovePage -> movePage(command.document, command.fromIndex, command.toIndex)
        is DocumentCommand.MovePageToSection -> movePageToSection(command.document, command.pageId, command.sectionId, command.sectionPageIndex)
        is DocumentCommand.SwitchPage -> switchPage(command.state, command.pageId)
        is DocumentCommand.CreateSection -> createSection(command.document, command.title, command.colorArgb)
        is DocumentCommand.RenameSection -> renameSection(command.document, command.sectionId, command.title)
        is DocumentCommand.DeleteSection -> deleteSection(command.document, command.sectionId)
        is DocumentCommand.MoveSection -> moveSection(command.document, command.fromIndex, command.toIndex)
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
        firstSectionId: String = idGenerator.nextId("section"),
    ): NeoNoteDocument {
        val now = clockMillis()
        val section = NoteSection(
            id = firstSectionId,
            title = "Notes",
            colorArgb = DefaultSectionColors.first(),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        return NeoNoteDocument(
            id = documentId,
            title = title.take(MaxDocumentTitleLength),
            sections = listOf(section),
            pages = listOf(NotePage(
                id = firstPageId,
                title = "Page 1",
                sectionId = firstSectionId,
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
        sectionId: String? = null,
    ): DocumentCommandResult.PageAdded {
        val materialized = document.materializeSections()
        val targetSection = sectionId?.takeIf { id -> materialized.sections.any { it.id == id } }
            ?: page?.sectionId?.takeIf { id -> materialized.sections.any { it.id == id } }
            ?: materialized.sections.first().id
        val now = clockMillis()
        val countInSection = materialized.pages.count { it.sectionId == targetSection }
        val nextPage = (page ?: NotePage(
            id = idGenerator.nextId("page"),
            title = "Page ${countInSection + 1}",
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )).copy(sectionId = targetSection)
        return DocumentCommandResult.PageAdded(touch(materialized.copy(pages = materialized.pages + nextPage)), nextPage)
    }

    public fun deletePage(document: NeoNoteDocument, pageId: String): DocumentCommandResult.PageDeleted {
        val materialized = document.materializeSections()
        val index = materialized.pages.indexOfFirst { it.id == pageId }
        require(index >= 0) { "Unknown page id: $pageId" }
        if (materialized.pages.size == 1) {
            return DocumentCommandResult.PageDeleted(materialized, pageId, materialized.pages.single().id)
        }
        val pages = materialized.pages.filterNot { it.id == pageId }
        val nextId = pages[index.coerceAtMost(pages.lastIndex)].id
        return DocumentCommandResult.PageDeleted(touch(materialized.copy(pages = pages)), pageId, nextId)
    }

    public fun duplicatePage(document: NeoNoteDocument, pageId: String): DocumentCommandResult.PageAdded {
        val materialized = document.materializeSections()
        val index = materialized.pages.indexOfFirst { it.id == pageId }
        require(index >= 0) { "Unknown page id: $pageId" }
        val source = materialized.pages[index]
        val now = clockMillis()
        val duplicate = source.copy(
            id = idGenerator.nextId("page"),
            title = source.title.ifBlank { "Page" } + " copy",
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        val insertion = index + 1
        val pages = materialized.pages.take(insertion) + duplicate + materialized.pages.drop(insertion)
        return DocumentCommandResult.PageAdded(touch(materialized.copy(pages = pages)), duplicate)
    }

    public fun renamePage(document: NeoNoteDocument, pageId: String, title: String): DocumentCommandResult.PageUpdated {
        val materialized = document.materializeSections()
        val index = materialized.pages.indexOfFirst { it.id == pageId }
        require(index >= 0) { "Unknown page id: $pageId" }
        val updated = materialized.pages[index].copy(
            title = title.take(MaxPageTitleLength),
            updatedAtEpochMillis = clockMillis(),
        )
        return DocumentCommandResult.PageUpdated(
            touch(materialized.copy(pages = materialized.pages.replaceAt(index, updated))),
            updated,
        )
    }

    public fun movePage(document: NeoNoteDocument, fromIndex: Int, toIndex: Int): DocumentCommandResult.PagesReordered {
        val materialized = document.materializeSections()
        require(fromIndex in materialized.pages.indices) { "fromIndex must address a page" }
        val pages = materialized.pages.toMutableList()
        val page = pages.removeAt(fromIndex)
        pages.add(toIndex.coerceIn(0, pages.size), page)
        return DocumentCommandResult.PagesReordered(touch(materialized.copy(pages = pages)))
    }

    public fun movePageToSection(
        document: NeoNoteDocument,
        pageId: String,
        sectionId: String,
        sectionPageIndex: Int? = null,
    ): DocumentCommandResult.PageMoved {
        val materialized = document.materializeSections()
        require(materialized.sections.any { it.id == sectionId }) { "Unknown section id: $sectionId" }
        val page = materialized.pages.firstOrNull { it.id == pageId } ?: error("Unknown page id: $pageId")
        val remaining = materialized.pages.filterNot { it.id == pageId }
        val targetPages = remaining.filter { it.sectionId == sectionId }
        val targetInSection = (sectionPageIndex ?: targetPages.size).coerceIn(0, targetPages.size)
        val globalInsertion = if (targetPages.isEmpty()) {
            val nextSectionIndex = materialized.sections.indexOfFirst { it.id == sectionId } + 1
            val nextSectionIds = materialized.sections.drop(nextSectionIndex).mapTo(mutableSetOf(), NoteSection::id)
            remaining.indexOfFirst { it.sectionId in nextSectionIds }.takeIf { it >= 0 } ?: remaining.size
        } else {
            val anchor = if (targetInSection == targetPages.size) targetPages.last() else targetPages[targetInSection]
            val anchorIndex = remaining.indexOfFirst { it.id == anchor.id }
            if (targetInSection == targetPages.size) anchorIndex + 1 else anchorIndex
        }
        val moved = page.copy(sectionId = sectionId, updatedAtEpochMillis = clockMillis())
        val pages = remaining.take(globalInsertion) + moved + remaining.drop(globalInsertion)
        return DocumentCommandResult.PageMoved(touch(materialized.copy(pages = pages)), moved)
    }

    public fun switchPage(state: EditorState, pageId: String): DocumentCommandResult.PageSwitched {
        require(state.document.pages.any { it.id == pageId }) { "Unknown page id: $pageId" }
        return DocumentCommandResult.PageSwitched(state.copy(currentPageId = pageId))
    }

    public fun createSection(
        document: NeoNoteDocument,
        title: String,
        colorArgb: Int? = null,
    ): DocumentCommandResult.SectionAdded {
        val materialized = document.materializeSections()
        val now = clockMillis()
        val section = NoteSection(
            id = idGenerator.nextId("section"),
            title = title.take(MaxSectionTitleLength).ifBlank { "New section" },
            colorArgb = colorArgb ?: DefaultSectionColors[materialized.sections.size % DefaultSectionColors.size],
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        return DocumentCommandResult.SectionAdded(
            touch(materialized.copy(sections = materialized.sections + section)),
            section,
        )
    }

    public fun renameSection(
        document: NeoNoteDocument,
        sectionId: String,
        title: String,
    ): DocumentCommandResult.SectionUpdated {
        val materialized = document.materializeSections()
        val index = materialized.sections.indexOfFirst { it.id == sectionId }
        require(index >= 0) { "Unknown section id: $sectionId" }
        val section = materialized.sections[index].copy(
            title = title.take(MaxSectionTitleLength),
            updatedAtEpochMillis = clockMillis(),
        )
        return DocumentCommandResult.SectionUpdated(
            touch(materialized.copy(sections = materialized.sections.replaceAt(index, section))),
            section,
        )
    }

    public fun deleteSection(document: NeoNoteDocument, sectionId: String): DocumentCommandResult.SectionDeleted {
        val materialized = document.materializeSections()
        val index = materialized.sections.indexOfFirst { it.id == sectionId }
        require(index >= 0) { "Unknown section id: $sectionId" }
        if (materialized.sections.size == 1) {
            return DocumentCommandResult.SectionDeleted(materialized, sectionId, materialized.sections.single().id)
        }
        val sections = materialized.sections.filterNot { it.id == sectionId }
        val destination = sections[index.coerceAtMost(sections.lastIndex)].id
        val pages = materialized.pages.map { page ->
            if (page.sectionId == sectionId) page.copy(sectionId = destination, updatedAtEpochMillis = clockMillis()) else page
        }
        return DocumentCommandResult.SectionDeleted(
            touch(materialized.copy(sections = sections, pages = pages)),
            sectionId,
            destination,
        )
    }

    public fun moveSection(
        document: NeoNoteDocument,
        fromIndex: Int,
        toIndex: Int,
    ): DocumentCommandResult.SectionsReordered {
        val materialized = document.materializeSections()
        require(fromIndex in materialized.sections.indices) { "fromIndex must address a section" }
        val sections = materialized.sections.toMutableList()
        val section = sections.removeAt(fromIndex)
        sections.add(toIndex.coerceIn(0, sections.size), section)
        val order = sections.mapIndexed { index, item -> item.id to index }.toMap()
        val pages = materialized.pages.sortedWith(compareBy { order[it.sectionId] ?: Int.MAX_VALUE })
        return DocumentCommandResult.SectionsReordered(touch(materialized.copy(sections = sections, pages = pages)))
    }

    private fun NeoNoteDocument.materializeSections(): NeoNoteDocument {
        val normalizedSections = effectiveSections().map { section ->
            if (section.id == LegacyDefaultSectionId && section.createdAtEpochMillis == 0L) {
                section.copy(createdAtEpochMillis = createdAtEpochMillis, updatedAtEpochMillis = updatedAtEpochMillis)
            } else section
        }
        val valid = normalizedSections.mapTo(mutableSetOf(), NoteSection::id)
        val fallback = normalizedSections.first().id
        val normalizedPages = pages.map { page ->
            page.copy(sectionId = page.sectionId?.takeIf(valid::contains) ?: fallback)
        }
        return if (sections == normalizedSections && pages == normalizedPages) this
        else copy(sections = normalizedSections, pages = normalizedPages)
    }

    private fun touch(document: NeoNoteDocument): NeoNoteDocument = document.copy(
        revision = document.revision + 1,
        updatedAtEpochMillis = clockMillis(),
    )

    private fun <T> List<T>.replaceAt(index: Int, item: T): List<T> =
        mapIndexed { currentIndex, current -> if (currentIndex == index) item else current }

    public companion object {
        public const val MaxDocumentTitleLength: Int = 120
        public const val MaxSectionTitleLength: Int = 80
        public const val MaxPageTitleLength: Int = 80
        public val DefaultSectionColors: List<Int> = listOf(
            0xFF5B3FD1.toInt(),
            0xFF1565C0.toInt(),
            0xFF00897B.toInt(),
            0xFFE65100.toInt(),
            0xFFAD1457.toInt(),
        )
    }
}

public sealed interface DocumentCommand {
    public data class CreateDocument(
        val title: String,
        val assetStoreId: String,
        val documentId: String? = null,
        val firstPageId: String? = null,
        val firstSectionId: String? = null,
    ) : DocumentCommand
    public data class AddPage(
        val document: NeoNoteDocument,
        val page: NotePage? = null,
        val sectionId: String? = null,
    ) : DocumentCommand
    public data class DeletePage(val document: NeoNoteDocument, val pageId: String) : DocumentCommand
    public data class DuplicatePage(val document: NeoNoteDocument, val pageId: String) : DocumentCommand
    public data class RenamePage(val document: NeoNoteDocument, val pageId: String, val title: String) : DocumentCommand
    public data class MovePage(val document: NeoNoteDocument, val fromIndex: Int, val toIndex: Int) : DocumentCommand
    public data class MovePageToSection(
        val document: NeoNoteDocument,
        val pageId: String,
        val sectionId: String,
        val sectionPageIndex: Int? = null,
    ) : DocumentCommand
    public data class SwitchPage(val state: EditorState, val pageId: String) : DocumentCommand
    public data class CreateSection(val document: NeoNoteDocument, val title: String, val colorArgb: Int? = null) : DocumentCommand
    public data class RenameSection(val document: NeoNoteDocument, val sectionId: String, val title: String) : DocumentCommand
    public data class DeleteSection(val document: NeoNoteDocument, val sectionId: String) : DocumentCommand
    public data class MoveSection(val document: NeoNoteDocument, val fromIndex: Int, val toIndex: Int) : DocumentCommand
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
    public data class PageMoved(val document: NeoNoteDocument, val page: NotePage) : DocumentCommandResult
    public data class PageSwitched(val state: EditorState) : DocumentCommandResult
    public data class SectionAdded(val document: NeoNoteDocument, val section: NoteSection) : DocumentCommandResult
    public data class SectionUpdated(val document: NeoNoteDocument, val section: NoteSection) : DocumentCommandResult
    public data class SectionDeleted(val document: NeoNoteDocument, val deletedSectionId: String, val destinationSectionId: String) : DocumentCommandResult
    public data class SectionsReordered(val document: NeoNoteDocument) : DocumentCommandResult
}
