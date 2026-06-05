package com.neonote

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neonote.model.BlockNode
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox

@Composable
internal fun RichContentEditor(
    box: RichContentBox,
    selectionMode: Boolean,
    selected: Boolean,
    controller: NeoNoteEditorController,
    modifier: Modifier = Modifier,
) {
    val activeBlockIndex = controller.activeRichContentBlockIndex(box.id)
        ?: box.content.blocks.indexOfFirst { it is ParagraphNode }.coerceAtLeast(0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 6.dp),
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
                is ParagraphNode -> {
                    RichParagraphEditor(
                        box = box,
                        blockIndex = blockIndex,
                        paragraph = block,
                        active = blockIndex == activeBlockIndex,
                        selectionMode = selectionMode,
                        selected = selected,
                        controller = controller,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    RichStaticEditorBlock(
                        block = block,
                        blockIndex = blockIndex,
                        selectionMode = selectionMode,
                        selected = selected,
                        controller = controller,
                        boxId = box.id,
                    )
                }
            }
        }
    }
}

@Composable
private fun RichStaticEditorBlock(
    block: BlockNode,
    blockIndex: Int,
    selectionMode: Boolean,
    selected: Boolean,
    controller: NeoNoteEditorController,
    boxId: String,
) {
    RichContentRenderer(
        content = RichContent(blocks = listOf(block)),
        selectionMode = selectionMode,
        selected = selected,
        onFocus = { controller.focusRichContentParagraph(boxId = boxId, blockIndex = blockIndex) },
        onToggleTodoChecked = { localBlockIndex ->
            controller.toggleRichContentTodoCheckedState(boxId = boxId, blockIndex = blockIndex + localBlockIndex)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

