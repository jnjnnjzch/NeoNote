package com.neonote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonote.engine.RichContentLayoutDefaults
import com.neonote.model.BlockFormula
import com.neonote.model.BlockImage
import com.neonote.model.InlineFormula
import com.neonote.model.InlineImage
import com.neonote.model.InlineLineBreak
import com.neonote.model.InlineNode
import com.neonote.model.InlineText
import com.neonote.model.ListKind
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.TableNode

@Composable
internal fun RichContentRenderer(
    content: RichContent,
    selectionMode: Boolean,
    selected: Boolean,
    onFocus: () -> Unit,
    onToggleTodoChecked: (Int) -> Unit,
    modifier: Modifier = Modifier,
    applyContentPadding: Boolean = true,
    selectedObjectBlockIndex: Int? = null,
    onObjectBlockFocus: (Int) -> Unit = { onFocus() },
) {
    val focusModifier = if (!selectionMode && !selected) {
        Modifier.clickable(onClick = onFocus)
    } else {
        Modifier
    }
    val paddingModifier = if (applyContentPadding) {
        Modifier.padding(
            horizontal = RichContentLayoutDefaults.RendererHorizontalPadding.dp,
            vertical = RichContentLayoutDefaults.RendererVerticalPadding.dp,
        )
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .then(focusModifier)
            .then(paddingModifier),
        verticalArrangement = Arrangement.spacedBy(RichContentLayoutDefaults.BlockSpacing.dp),
    ) {
        if (content.blocks.isEmpty()) {
            Text("Start typing…", color = Color(0xFF94A3B8), style = richContentBodyTextStyle())
        }
        var numberedIndex = 0
        var previousNumbered = false
        content.blocks.forEachIndexed { blockIndex, block ->
            when (block) {
                is ParagraphNode -> {
                    val metadata = block.listMetadata
                    val markerNumber = if (metadata?.kind == ListKind.Numbered) {
                        numberedIndex = if (previousNumbered) numberedIndex + 1 else 1
                        previousNumbered = true
                        numberedIndex
                    } else {
                        previousNumbered = false
                        numberedIndex = 0
                        0
                    }
                    RichParagraphRenderer(
                        paragraph = block,
                        markerNumber = markerNumber,
                        blockIndex = blockIndex,
                        selectionMode = selectionMode,
                        selected = selected,
                        onToggleTodoChecked = onToggleTodoChecked,
                    )
                }
                is BlockFormula -> {
                    previousNumbered = false
                    numberedIndex = 0
                    RichBlockCard(label = "Formula", accent = "ƒ", text = block.expression.ifBlank { "empty expression" }, height = RichContentLayoutDefaults.FormulaCardHeight)
                }
                is BlockImage -> {
                    previousNumbered = false
                    numberedIndex = 0
                    ImageBlockView(
                        block = block,
                        selected = selectedObjectBlockIndex == blockIndex,
                        enabled = !selectionMode && !selected,
                        onClick = { onObjectBlockFocus(blockIndex) },
                    )
                }
                is TableNode -> {
                    previousNumbered = false
                    numberedIndex = 0
                    StaticTablePreview(table = block)
                }
            }
        }
    }
}

@Composable
private fun RichParagraphRenderer(
    paragraph: ParagraphNode,
    markerNumber: Int,
    blockIndex: Int,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleTodoChecked: (Int) -> Unit,
) {
    val metadata = paragraph.listMetadata
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RichContentLayoutDefaults.LineHeight.dp),
        verticalAlignment = Alignment.Top,
    ) {
        when (metadata?.kind) {
            ListKind.Bullet -> Text(
                "•",
                modifier = Modifier
                    .padding(end = 8.dp)
                    .widthIn(min = 18.dp),
                color = Color(0xFF334155),
            )
            ListKind.Numbered -> Text(
                "$markerNumber.",
                modifier = Modifier
                    .padding(end = 8.dp)
                    .widthIn(min = 24.dp),
                color = Color(0xFF334155),
            )
            ListKind.Todo -> Checkbox(
                checked = metadata.checked,
                onCheckedChange = { onToggleTodoChecked(blockIndex) },
                enabled = !selectionMode && !selected,
            )
            null -> Unit
        }
        Text(
            text = paragraph.toDisplayAnnotatedString(),
            color = Color(0xFF0F172A),
            style = richContentBodyTextStyle(),
        )
    }
}

@Composable
private fun RichBlockCard(label: String, accent: String, text: String, height: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF8FAFC))
            .border(width = 1.dp, color = Color(0xFFCBD5E1), shape = RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = accent,
            modifier = Modifier
                .padding(end = 8.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFFE0E7FF))
                .padding(horizontal = 7.dp, vertical = 3.dp),
            color = Color(0xFF3730A3),
            style = MaterialTheme.typography.labelMedium,
        )
        Column {
            Text(text = label, color = Color(0xFF475569), style = MaterialTheme.typography.labelSmall)
            Text(text = text, color = Color(0xFF0F172A), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StaticTablePreview(table: TableNode) {
    val rowCount = table.rows.size.coerceAtLeast(RichContentLayoutDefaults.TableMinimumPreviewRows)
    val tableHeight = RichContentLayoutDefaults.TableHeaderPreviewHeight +
        rowCount * maxOf(RichContentLayoutDefaults.TableRowPreviewHeight, RichContentLayoutDefaults.TableCellPreviewHeight)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(tableHeight.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(width = 1.dp, color = Color(0xFFCBD5E1), shape = RoundedCornerShape(10.dp)),
    ) {
        Text(
            text = "Table preview",
            modifier = Modifier
                .fillMaxWidth()
                .height(RichContentLayoutDefaults.TableHeaderPreviewHeight.dp)
                .background(Color(0xFFF1F5F9))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            color = Color(0xFF475569),
            style = MaterialTheme.typography.labelSmall,
        )
        if (table.rows.isEmpty()) {
            Text(
                text = "0 × 0",
                modifier = Modifier.height(RichContentLayoutDefaults.TableRowPreviewHeight.dp).padding(8.dp),
                color = Color(0xFF64748B),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            table.rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    val cells = row.ifEmpty { listOf(null) }
                    cells.forEach { cell ->
                        Text(
                            text = cell?.content?.previewText().orEmpty().ifBlank { " " },
                            modifier = Modifier
                                .weight(1f)
                                .height(RichContentLayoutDefaults.TableCellPreviewHeight.dp)
                                .border(width = 1.dp, color = Color(0xFFE2E8F0))
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            color = Color(0xFF334155),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun richContentBodyTextStyle() = MaterialTheme.typography.bodyMedium.copy(
    lineHeight = RichContentLayoutDefaults.LineHeight.sp,
)

internal fun ParagraphNode.toDisplayAnnotatedString(): AnnotatedString = buildAnnotatedString {
    inlines.forEach { inline -> appendInlineNode(inline) }
}

private fun AnnotatedString.Builder.appendInlineNode(inline: InlineNode) {
    when (inline) {
        is InlineText -> withStyle(inline.textSpanStyle()) { append(inline.text) }
        InlineLineBreak -> append("\n")
        is InlineFormula -> withStyle(inlineChipStyle) { append(inline.formulaChipText()) }
        is InlineImage -> withStyle(inlineChipStyle) { append(inline.imageChipText()) }
    }
}

private fun InlineText.textSpanStyle(): SpanStyle = SpanStyle(
    fontWeight = if (bold) FontWeight.Bold else null,
    fontStyle = if (italic) FontStyle.Italic else null,
    textDecoration = if (underline) TextDecoration.Underline else null,
)

private val inlineChipStyle = SpanStyle(
    color = Color(0xFF1E3A8A),
    background = Color(0xFFE0F2FE),
    fontWeight = FontWeight.Medium,
)

internal fun InlineFormula.formulaChipText(): String = " ƒ ${expression.ifBlank { "empty" }} "

internal fun InlineImage.imageChipText(): String = " Image: ${imagePlaceholderText()} "

private fun InlineImage.imagePlaceholderText(): String = altText?.takeIf { it.isNotBlank() } ?: "asset:$assetId"

private fun BlockImage.imagePlaceholderText(): String = altText?.takeIf { it.isNotBlank() } ?: "asset:$assetId"

private fun ParagraphNode.previewText(): String = inlines.joinToString("") { inline ->
    when (inline) {
        is InlineText -> inline.text
        InlineLineBreak -> "\n"
        is InlineFormula -> inline.formulaChipText().trim()
        is InlineImage -> inline.imageChipText().trim()
    }
}

private fun RichContent.previewText(): String = blocks.firstOrNull()?.let { block ->
    when (block) {
        is ParagraphNode -> block.previewText().ifBlank { " " }
        is BlockFormula -> block.expression.ifBlank { "formula" }
        is BlockImage -> block.imagePlaceholderText()
        is TableNode -> "nested table"
    }
} ?: " "
