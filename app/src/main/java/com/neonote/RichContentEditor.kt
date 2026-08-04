package com.neonote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neonote.engine.ActiveRichContentTarget
import com.neonote.engine.RichContentLayoutDefaults
import com.neonote.model.BlockFormula
import com.neonote.model.BlockImage
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContentBox
import com.neonote.model.TableNode

@Composable
internal fun RichContentEditor(
    box: RichContentBox,
    selectionMode: Boolean,
    selected: Boolean,
    controller: NeoNoteEditorController,
    modifier: Modifier = Modifier,
) {
    val activeTarget = controller.activeRichContentTarget(box.id)
    val activeBlockIndex = activeTarget?.blockIndexForEditor()
        ?: box.content.blocks.indexOfFirst { it is ParagraphNode }.coerceAtLeast(0)

    Column(
        modifier = modifier.fillMaxSize().padding(
            horizontal = RichContentLayoutDefaults.RendererHorizontalPadding.dp,
            vertical = RichContentLayoutDefaults.RendererVerticalPadding.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(RichContentLayoutDefaults.BlockSpacing.dp),
    ) {
        if (box.content.blocks.isEmpty()) {
            RichParagraphEditor(
                box = box,
                blockIndex = 0,
                paragraph = ParagraphNode(),
                active = true,
                selectionMode = selectionMode,
                selected = selected,
                controller = controller,
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }

        box.content.blocks.forEachIndexed { blockIndex, block ->
            when (block) {
                is ParagraphNode -> RichParagraphEditor(
                    box = box,
                    blockIndex = blockIndex,
                    paragraph = block,
                    active = activeTarget == ActiveRichContentTarget.Paragraph(blockIndex) ||
                        (activeTarget == null && blockIndex == activeBlockIndex),
                    selectionMode = selectionMode,
                    selected = selected,
                    controller = controller,
                    modifier = Modifier.fillMaxWidth(),
                )
                is BlockFormula -> FormulaBlockEditor(
                    box = box,
                    blockIndex = blockIndex,
                    formula = block,
                    active = activeTarget == ActiveRichContentTarget.FormulaBlock(blockIndex),
                    selectionMode = selectionMode,
                    selected = selected,
                    controller = controller,
                    modifier = Modifier.fillMaxWidth(),
                )
                is TableNode -> TableBlockEditor(
                    box = box,
                    blockIndex = blockIndex,
                    table = block,
                    selectionMode = selectionMode,
                    selected = selected,
                    controller = controller,
                    modifier = Modifier.fillMaxWidth(),
                )
                is BlockImage -> ImageBlockView(
                    box = box,
                    blockIndex = blockIndex,
                    block = block,
                    selected = activeTarget == ActiveRichContentTarget.ImageBlock(blockIndex) ||
                        controller.selectedRichContentObjectBlockIndex(box.id) == blockIndex,
                    enabled = !selectionMode && !selected,
                    controller = controller,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun ActiveRichContentTarget.blockIndexForEditor(): Int = when (this) {
    is ActiveRichContentTarget.Paragraph -> blockIndex
    is ActiveRichContentTarget.TableCell -> address.blockIndex
    is ActiveRichContentTarget.FormulaBlock -> blockIndex
    is ActiveRichContentTarget.ImageBlock -> blockIndex
}
