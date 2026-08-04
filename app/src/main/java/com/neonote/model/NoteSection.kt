package com.neonote.model

import kotlinx.serialization.Serializable

@Serializable
data class NoteSection(
    val id: String,
    val title: String,
    val colorArgb: Int = 0xFF5B3FD1.toInt(),
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
)

public const val LegacyDefaultSectionId: String = "legacy-notes-section"

/** Materializes a stable section view for schema-3 documents without mutating their bytes. */
public fun NeoNoteDocument.effectiveSections(): List<NoteSection> =
    sections.ifEmpty {
        listOf(
            NoteSection(
                id = LegacyDefaultSectionId,
                title = "Notes",
                createdAtEpochMillis = createdAtEpochMillis,
                updatedAtEpochMillis = updatedAtEpochMillis,
            ),
        )
    }

public fun NeoNoteDocument.effectiveSectionId(page: NotePage): String {
    val validIds = effectiveSections().mapTo(mutableSetOf(), NoteSection::id)
    return page.sectionId?.takeIf(validIds::contains) ?: effectiveSections().first().id
}
