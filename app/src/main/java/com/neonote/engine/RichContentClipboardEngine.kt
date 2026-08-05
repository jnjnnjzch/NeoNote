package com.neonote.engine

import com.neonote.model.InlineFormula
import com.neonote.model.InlineImage
import com.neonote.model.InlineLineBreak
import com.neonote.model.InlineNode
import com.neonote.model.InlineText
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import kotlinx.serialization.Serializable

@Serializable
public data class RichClipboardFragment(
    val paragraphs: List<ParagraphNode> = emptyList(),
) {
    public fun plainText(): String = paragraphs.joinToString("\n") { it.clipboardPlainText() }

    public fun platformTextLength(): Int = paragraphs.platformTextLength()

    public companion object {
        public fun fromPlainText(text: String): RichClipboardFragment = RichClipboardFragment(
            text.replace("\r\n", "\n").replace('\r', '\n').split('\n').map { line ->
                ParagraphNode(inlines = if (line.isEmpty()) emptyList() else listOf(InlineText(line)))
            },
        )
    }
}

public data class RichContentReplacement(
    val content: RichContent,
    val cursorOffset: Int,
)

/** Structured copy/cut/paste for the unified paragraph editor. Inline atoms count as one platform character. */
public object RichContentClipboardEngine {
    public fun copy(content: RichContent, start: Int, end: Int): RichClipboardFragment? {
        val paragraphs = content.paragraphsOrNull() ?: return null
        val total = paragraphs.platformTextLength()
        val orderedStart = minOf(start, end).coerceIn(0, total)
        val orderedEnd = maxOf(start, end).coerceIn(0, total)
        if (orderedStart == orderedEnd) return null
        val from = paragraphs.locate(orderedStart)
        val to = paragraphs.locate(orderedEnd)
        return RichClipboardFragment(
            (from.paragraphIndex..to.paragraphIndex).map { index ->
                val paragraph = paragraphs[index]
                val localStart = if (index == from.paragraphIndex) from.localOffset else 0
                val localEnd = if (index == to.paragraphIndex) to.localOffset else paragraph.platformLength()
                paragraph.copy(
                    id = "",
                    inlines = paragraph.inlines.slicePlatformRange(localStart, localEnd),
                )
            },
        )
    }

    public fun replace(
        content: RichContent,
        start: Int,
        end: Int,
        fragment: RichClipboardFragment,
    ): RichContentReplacement {
        val source = content.paragraphsOrNull()
            ?: return RichContentReplacement(content, minOf(start, end).coerceAtLeast(0))
        val paragraphs = source.ifEmpty { listOf(ParagraphNode()) }
        val total = paragraphs.platformTextLength()
        val orderedStart = minOf(start, end).coerceIn(0, total)
        val orderedEnd = maxOf(start, end).coerceIn(0, total)
        val from = paragraphs.locate(orderedStart)
        val to = paragraphs.locate(orderedEnd)
        val firstSource = paragraphs[from.paragraphIndex]
        val lastSource = paragraphs[to.paragraphIndex]
        val prefix = firstSource.inlines.slicePlatformRange(0, from.localOffset)
        val suffix = lastSource.inlines.slicePlatformRange(to.localOffset, lastSource.platformLength())
        val inserted = fragment.paragraphs.map { it.copy(id = "", inlines = it.inlines.normalizeRuns()) }

        val replacementParagraphs = when (inserted.size) {
            0 -> listOf(firstSource.copy(inlines = (prefix + suffix).normalizeRuns()))
            1 -> {
                val entireParagraphRange = from.localOffset == 0 &&
                    to.localOffset == lastSource.platformLength() &&
                    from.paragraphIndex == to.paragraphIndex
                val styleSource = if (entireParagraphRange) inserted.single() else firstSource
                listOf(styleSource.copy(
                    id = firstSource.id,
                    inlines = (prefix + inserted.single().inlines + suffix).normalizeRuns(),
                ))
            }
            else -> buildList {
                val firstInserted = inserted.first()
                add(firstSource.copy(inlines = (prefix + firstInserted.inlines).normalizeRuns()))
                addAll(inserted.subList(1, inserted.lastIndex))
                val lastInserted = inserted.last()
                add(lastInserted.copy(inlines = (lastInserted.inlines + suffix).normalizeRuns()))
            }
        }
        val nextParagraphs = buildList {
            addAll(paragraphs.take(from.paragraphIndex))
            addAll(replacementParagraphs)
            addAll(paragraphs.drop(to.paragraphIndex + 1))
        }.ifEmpty { listOf(ParagraphNode()) }
        val nextContent = RichContent(nextParagraphs)
        val insertedLength = fragment.platformTextLength()
        return RichContentReplacement(
            content = nextContent,
            cursorOffset = (orderedStart + insertedLength).coerceIn(0, nextParagraphs.platformTextLength()),
        )
    }
}

private data class ParagraphLocation(val paragraphIndex: Int, val localOffset: Int)

private fun RichContent.paragraphsOrNull(): List<ParagraphNode>? =
    if (blocks.all { it is ParagraphNode }) blocks.filterIsInstance<ParagraphNode>() else null

private fun List<ParagraphNode>.locate(offset: Int): ParagraphLocation {
    if (isEmpty()) return ParagraphLocation(0, 0)
    var remaining = offset.coerceAtLeast(0)
    forEachIndexed { index, paragraph ->
        val length = paragraph.platformLength()
        if (remaining <= length || index == lastIndex) {
            return ParagraphLocation(index, remaining.coerceIn(0, length))
        }
        remaining -= length + 1
    }
    return ParagraphLocation(lastIndex, last().platformLength())
}

private fun List<ParagraphNode>.platformTextLength(): Int =
    sumOf(ParagraphNode::platformLength) + (size - 1).coerceAtLeast(0)

private fun ParagraphNode.platformLength(): Int = inlines.sumOf(InlineNode::platformLength)

private fun InlineNode.platformLength(): Int = when (this) {
    is InlineText -> text.length
    InlineLineBreak, is InlineFormula, is InlineImage -> 1
}

private fun ParagraphNode.clipboardPlainText(): String = inlines.joinToString("") { inline ->
    when (inline) {
        is InlineText -> inline.text
        InlineLineBreak -> "\n"
        is InlineFormula -> "${'$'}{${inline.expression}}"
        is InlineImage -> inline.altText?.takeIf(String::isNotBlank) ?: "[Image]"
    }
}

private fun List<InlineNode>.slicePlatformRange(start: Int, end: Int): List<InlineNode> {
    val orderedStart = minOf(start, end).coerceAtLeast(0)
    val orderedEnd = maxOf(start, end).coerceAtLeast(orderedStart)
    var offset = 0
    return buildList {
        this@slicePlatformRange.forEach { inline ->
            val next = offset + inline.platformLength()
            val overlapStart = maxOf(orderedStart, offset)
            val overlapEnd = minOf(orderedEnd, next)
            if (overlapStart < overlapEnd) {
                when (inline) {
                    is InlineText -> add(inline.copy(text = inline.text.substring(overlapStart - offset, overlapEnd - offset)))
                    InlineLineBreak, is InlineFormula, is InlineImage -> add(inline)
                }
            }
            offset = next
        }
    }.normalizeRuns()
}

private fun List<InlineNode>.normalizeRuns(): List<InlineNode> = buildList {
    this@normalizeRuns.forEach { inline ->
        if (inline is InlineText && inline.text.isEmpty()) return@forEach
        val previous = lastOrNull()
        if (previous is InlineText && inline is InlineText && previous.sameFormatting(inline)) {
            removeAt(lastIndex)
            add(previous.copy(text = previous.text + inline.text))
        } else {
            add(inline)
        }
    }
}

private fun InlineText.sameFormatting(other: InlineText): Boolean =
    copy(text = "") == other.copy(text = "")
