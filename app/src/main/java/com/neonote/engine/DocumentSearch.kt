package com.neonote.engine

import com.neonote.model.BlockFormula
import com.neonote.model.BlockImage
import com.neonote.model.BlockNode
import com.neonote.model.FloatingImage
import com.neonote.model.InlineFormula
import com.neonote.model.InlineImage
import com.neonote.model.InlineLineBreak
import com.neonote.model.InlineNode
import com.neonote.model.InlineText
import com.neonote.model.NeoNoteDocument
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TableNode
import com.neonote.model.effectiveSectionId
import com.neonote.model.effectiveSections
import java.io.File
import kotlinx.serialization.json.Json

public data class DocumentSearchHit(
    val documentId: String,
    val documentTitle: String,
    val sectionId: String,
    val sectionTitle: String,
    val pageId: String,
    val pageTitle: String,
    val snippet: String,
    val score: Int,
    val targetObjectId: String? = null,
    val targetX: Float? = null,
    val targetY: Float? = null,
)

public interface DocumentSearchIndex {
    public suspend fun search(query: String, includeTrash: Boolean = false): List<DocumentSearchHit>
}

/**
 * Deterministic app-private full-text index. Documents are small enough to be
 * decoded on demand; this avoids a second database that could drift from the
 * atomic JSON source of truth.
 */
public class FileDocumentSearchIndex(
    rootDirectory: File,
    private val json: Json = DefaultDocumentJson,
) : DocumentSearchIndex {
    private val documentsDirectory = rootDirectory.resolve("documents")
    private val trashDirectory = rootDirectory.resolve("trash")

    override suspend fun search(query: String, includeTrash: Boolean): List<DocumentSearchHit> {
        val terms = query.normalizedTerms()
        if (terms.isEmpty()) return emptyList()
        val files = buildList {
            addAll(jsonFiles(documentsDirectory))
            if (includeTrash) addAll(jsonFiles(trashDirectory))
        }
        return files.mapNotNull(::decodeDocument)
            .flatMap { document -> document.search(terms) }
            .sortedWith(
                compareByDescending<DocumentSearchHit>(DocumentSearchHit::score)
                    .thenBy(String.CASE_INSENSITIVE_ORDER, DocumentSearchHit::documentTitle)
                    .thenBy(String.CASE_INSENSITIVE_ORDER, DocumentSearchHit::sectionTitle)
                    .thenBy(String.CASE_INSENSITIVE_ORDER, DocumentSearchHit::pageTitle),
            )
            .take(MaximumSearchResults)
    }

    private fun jsonFiles(directory: File): List<File> =
        directory.listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
            .orEmpty()
            .toList()

    private fun decodeDocument(file: File): NeoNoteDocument? = runCatching {
        json.decodeFromString(NeoNoteDocument.serializer(), file.readText())
    }.getOrNull()

    private fun NeoNoteDocument.search(terms: List<String>): List<DocumentSearchHit> {
        val sectionsById = effectiveSections().associateBy { it.id }
        return pages.mapNotNull { page ->
            val sectionId = effectiveSectionId(page)
            val section = sectionsById.getValue(sectionId)
            val searchableObjects = page.searchableObjects()
            val pageBody = searchableObjects.joinToString("\n", transform = SearchableCanvasObject::text)
            val fields = SearchFields(
                documentTitle = title,
                sectionTitle = section.title,
                pageTitle = page.title,
                body = pageBody,
            )
            val score = fields.score(terms)
            if (score <= 0 || !terms.all(fields.normalizedCorpus::contains)) return@mapNotNull null
            val bestTarget = searchableObjects
                .map { candidate -> candidate to candidate.text.matchScore(terms) }
                .filter { (_, targetScore) -> targetScore > 0 }
                .maxByOrNull { (_, targetScore) -> targetScore }
                ?.first
            DocumentSearchHit(
                documentId = id,
                documentTitle = title,
                sectionId = sectionId,
                sectionTitle = section.title,
                pageId = page.id,
                pageTitle = page.title.ifBlank { "Untitled page" },
                snippet = (bestTarget?.text ?: pageBody).bestSnippet(terms),
                score = score,
                targetObjectId = bestTarget?.objectId,
                targetX = bestTarget?.centerX,
                targetY = bestTarget?.centerY,
            )
        }
    }
}

private data class SearchFields(
    val documentTitle: String,
    val sectionTitle: String,
    val pageTitle: String,
    val body: String,
) {
    val normalizedCorpus: String = listOf(documentTitle, sectionTitle, pageTitle, body)
        .joinToString("\n")
        .normalizedSearchText()

    fun score(terms: List<String>): Int = terms.sumOf { term ->
        documentTitle.matchWeight(term, 80) +
            sectionTitle.matchWeight(term, 45) +
            pageTitle.matchWeight(term, 60) +
            body.matchWeight(term, 8)
    }
}

private fun String.matchWeight(term: String, weight: Int): Int {
    val normalized = normalizedSearchText()
    if (term !in normalized) return 0
    val exactBonus = if (normalized.trim() == term) weight else 0
    var count = 0
    var start = 0
    while (true) {
        val index = normalized.indexOf(term, start)
        if (index < 0) break
        count++
        start = index + term.length.coerceAtLeast(1)
    }
    return exactBonus + count.coerceAtMost(12) * weight
}

private data class SearchableCanvasObject(
    val objectId: String,
    val text: String,
    val centerX: Float,
    val centerY: Float,
)

private fun com.neonote.model.NotePage.searchableObjects(): List<SearchableCanvasObject> =
    canvas.objects.sortedBy { it.zIndex }.mapNotNull { objectValue ->
        val text = when (objectValue) {
            is RichContentBox -> objectValue.content.searchableText()
            is FloatingImage -> objectValue.altText.orEmpty()
        }.trim()
        if (text.isBlank()) null else SearchableCanvasObject(
            objectId = objectValue.id,
            text = text,
            centerX = objectValue.bounds.center.x,
            centerY = objectValue.bounds.center.y,
        )
    }

private fun String.matchScore(terms: List<String>): Int {
    val normalized = normalizedSearchText()
    return terms.sumOf { term -> if (term in normalized) 1 + normalized.windowed(term.length).count { it == term } else 0 }
}

private fun RichContent.searchableText(): String = blocks.joinToString("\n", transform = BlockNode::searchableText)

private fun BlockNode.searchableText(): String = when (this) {
    is ParagraphNode -> inlines.joinToString("", transform = InlineNode::searchableText)
    is BlockFormula -> expression
    is BlockImage -> listOfNotNull(altText, caption).joinToString(" ")
    is TableNode -> rows.flatten().joinToString("\n") { it.content.searchableText() }
}

private fun InlineNode.searchableText(): String = when (this) {
    is InlineText -> text
    InlineLineBreak -> "\n"
    is InlineFormula -> expression
    is InlineImage -> altText.orEmpty()
}

private fun String.bestSnippet(terms: List<String>): String {
    val compact = replace(Regex("\\s+"), " ").trim()
    if (compact.isEmpty()) return "No text preview"
    val lower = compact.lowercase()
    val index = terms.map(lower::indexOf).filter { it >= 0 }.minOrNull() ?: 0
    val start = (index - SnippetContextCharacters).coerceAtLeast(0)
    val end = (index + SnippetLength).coerceAtMost(compact.length)
    return buildString {
        if (start > 0) append("…")
        append(compact.substring(start, end))
        if (end < compact.length) append("…")
    }
}

private fun String.normalizedTerms(): List<String> = normalizedSearchText()
    .split(Regex("\\s+"))
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()
    .take(MaximumSearchTerms)

private fun String.normalizedSearchText(): String = lowercase()
    .replace(Regex("[\\{}_]+"), " ")
    .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

private const val MaximumSearchResults = 200
private const val MaximumSearchTerms = 12
private const val SnippetContextCharacters = 48
private const val SnippetLength = 180
