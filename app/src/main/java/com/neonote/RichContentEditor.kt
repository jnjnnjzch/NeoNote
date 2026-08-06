package com.neonote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.wrapContentHeight
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
import com.neonote.model.RichContent
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
    Column(
        modifier = modifier.fillMaxWidth().wrapContentHeight().padding(
            horizontal = RichContentLayoutDefaults.RendererHorizontalPadding.dp,
            vertical = RichContentLayoutDefaults.RendererVerticalPadding.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(RichContentLayoutDefaults.BlockSpacing.dp),
    ) {
        if (box.content.usesUnifiedParagraphEditor()) {
            UnifiedRichTextEditor(
                box = box,
                selectionMode = selectionMode,
                selected = selected,
                controller = controller,
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            )
            return@Column
        }

        val activeTarget = controller.activeRichContentTarget(box.id)
        val activeBlockIndex = activeTarget?.blockIndexForEditor()
            ?: box.content.blocks.indexOfFirst { it is ParagraphNode }.coerceAtLeast(0)

        var numberedIndex = 0
        var previousNumbered = false
        var formulaNumber = 0
        box.content.blocks.forEachIndexed { blockIndex, block ->
            when (block) {
                is ParagraphNode -> {
                    val markerNumber = if (block.listMetadata?.kind == com.neonote.model.ListKind.Numbered) {
                        numberedIndex = if (previousNumbered) numberedIndex + 1 else 1; previousNumbered = true; numberedIndex
                    } else { numberedIndex = 0; previousNumbered = false; 0 }
                    RichParagraphEditor(
                    box = box,
                    blockIndex = blockIndex,
                    paragraph = block,
                    active = activeTarget == ActiveRichContentTarget.Paragraph(blockIndex) ||
                        (activeTarget == null && blockIndex == activeBlockIndex),
                    selectionMode = selectionMode,
                    selected = selected,
                    controller = controller,
                    markerNumber = markerNumber,
                    modifier = Modifier.fillMaxWidth(),
                )
                }
                is BlockFormula -> {
                    numberedIndex = 0; previousNumbered = false
                    if (block.numbered) formulaNumber += 1
                    FormulaBlockEditor(
                    box = box,
                    blockIndex = blockIndex,
                    formula = block,
                    formulaNumber = formulaNumber,
                    active = activeTarget == ActiveRichContentTarget.FormulaBlock(blockIndex),
                    selectionMode = selectionMode,
                    selected = selected,
                    controller = controller,
                    modifier = Modifier.fillMaxWidth(),
                )
                }
                is TableNode -> {
                    numberedIndex = 0; previousNumbered = false
                    TableBlockEditor(
                    box = box,
                    blockIndex = blockIndex,
                    table = block,
                    selectionMode = selectionMode,
                    selected = selected,
                    controller = controller,
                    modifier = Modifier.fillMaxWidth(),
                )
                }
                is BlockImage -> {
                    numberedIndex = 0; previousNumbered = false
                    ImageBlockView(
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
}

private fun ActiveRichContentTarget.blockIndexForEditor(): Int = when (this) {
    is ActiveRichContentTarget.Paragraph -> blockIndex
    is ActiveRichContentTarget.TableCell -> address.blockIndex
    is ActiveRichContentTarget.FormulaBlock -> blockIndex
    is ActiveRichContentTarget.ImageBlock -> blockIndex
}


/** Native cross-paragraph selection is used only when it can faithfully represent the content. */
internal fun RichContent.usesUnifiedParagraphEditor(): Boolean =
    blocks.isEmpty() || blocks.all { block -> block is ParagraphNode && block.listMetadata == null }
