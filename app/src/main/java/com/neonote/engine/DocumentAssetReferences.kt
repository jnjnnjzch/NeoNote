package com.neonote.engine

import com.neonote.model.BlockImage
import com.neonote.model.FloatingImage
import com.neonote.model.InlineImage
import com.neonote.model.NeoNoteDocument
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TableNode

/** Returns every binary asset referenced by the full recursive document tree. */
public fun NeoNoteDocument.referencedAssetIds(): Set<String> = buildSet {
    pages.forEach { page ->
        page.canvas.objects.forEach { objectValue ->
            when (objectValue) {
                is FloatingImage -> add(objectValue.assetId)
                is RichContentBox -> addAll(objectValue.content.referencedAssetIds())
            }
        }
    }
}

private fun RichContent.referencedAssetIds(): Set<String> = buildSet {
    blocks.forEach { block ->
        when (block) {
            is BlockImage -> add(block.assetId)
            is ParagraphNode -> block.inlines
                .filterIsInstance<InlineImage>()
                .forEach { add(it.assetId) }
            is TableNode -> block.rows
                .asSequence()
                .flatten()
                .forEach { cell -> addAll(cell.content.referencedAssetIds()) }
            else -> Unit
        }
    }
}
