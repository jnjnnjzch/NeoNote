package com.neonote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonote.engine.MathExpressionFormatter
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
import com.neonote.model.TextAlignment

private val DisplayBodyFontSize = 16.sp
private val DisplayLineHeight = 24.sp

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
    val editable = !selectionMode && !selected
    val focusModifier = if (editable) Modifier.clickable(onClick = onFocus) else Modifier
    val paddingModifier = if (applyContentPadding) Modifier.padding(
        horizontal = RichContentLayoutDefaults.RendererHorizontalPadding.dp,
        vertical = RichContentLayoutDefaults.RendererVerticalPadding.dp,
    ) else Modifier
    Column(
        modifier = modifier.then(focusModifier).then(paddingModifier),
        verticalArrangement = Arrangement.spacedBy(RichContentLayoutDefaults.BlockSpacing.dp),
    ) {
        if (content.blocks.isEmpty()) {
            Text(
                "Start typing…",
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = DisplayBodyFontSize,
                    lineHeight = DisplayLineHeight,
                ),
            )
        }
        var numberedIndex = 0
        var previousNumbered = false
        content.blocks.forEachIndexed { blockIndex, block ->
            when (block) {
                is ParagraphNode -> {
                    val marker = if (block.listMetadata?.kind == ListKind.Numbered) {
                        numberedIndex = if (previousNumbered) numberedIndex + 1 else 1
                        previousNumbered = true
                        numberedIndex
                    } else {
                        numberedIndex = 0
                        previousNumbered = false
                        0
                    }
                    RichParagraphRenderer(
                        paragraph = block,
                        markerNumber = marker,
                        blockIndex = blockIndex,
                        selectionMode = selectionMode,
                        selected = selected,
                        onToggleTodoChecked = onToggleTodoChecked,
                    )
                }
                is BlockFormula -> {
                    numberedIndex = 0
                    previousNumbered = false
                    val rendered = MathExpressionFormatter.render(block.expression)
                    StaticBlockCard(
                        label = if (rendered.isValid) "Formula" else "Formula error",
                        accent = "ƒx",
                        text = rendered.displayText.ifBlank { "Enter formula" } +
                            if (block.numbered) "  (${blockIndex + 1})" else "",
                        error = !rendered.isValid,
                    )
                }
                is BlockImage -> {
                    numberedIndex = 0
                    previousNumbered = false
                    StaticBlockCard(
                        label = "Image",
                        accent = "▧",
                        text = listOfNotNull(
                            block.caption?.takeIf(String::isNotBlank),
                            block.altText?.takeIf(String::isNotBlank),
                            "${block.rotationDegrees.toInt()}°",
                        ).joinToString(" · "),
                        selected = selectedObjectBlockIndex == blockIndex,
                        onClick = if (editable) ({ onObjectBlockFocus(blockIndex) }) else null,
                    )
                }
                is TableNode -> {
                    numberedIndex = 0
                    previousNumbered = false
                    StaticTableGrid(block)
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
    val indent = (paragraph.style.indentLevel * 20).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent)
            .heightIn(min = DisplayLineHeight.value.dp),
        verticalAlignment = Alignment.Top,
    ) {
        when (metadata?.kind) {
            ListKind.Bullet -> Text(
                "•",
                Modifier.padding(end = 8.dp).widthIn(min = 18.dp),
                color = Color(0xFF334155),
                fontSize = DisplayBodyFontSize,
            )
            ListKind.Numbered -> Text(
                "$markerNumber.",
                Modifier.padding(end = 8.dp).widthIn(min = 24.dp),
                color = Color(0xFF334155),
                fontSize = DisplayBodyFontSize,
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
            modifier = Modifier.weight(1f),
            color = Color(0xFF0F172A),
            style = MaterialTheme.typography.bodyLarge.copy(
                lineHeight = DisplayLineHeight,
                fontSize = when (paragraph.style.headingLevel) {
                    1 -> 26.sp
                    2 -> 22.sp
                    3 -> 18.sp
                    else -> DisplayBodyFontSize
                },
                fontWeight = if (paragraph.style.headingLevel > 0) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = when (paragraph.style.alignment) {
                    TextAlignment.Start -> TextAlign.Start
                    TextAlignment.Center -> TextAlign.Center
                    TextAlignment.End -> TextAlign.End
                },
            ),
        )
    }
}

@Composable
private fun StaticBlockCard(
    label: String,
    accent: String,
    text: String,
    selected: Boolean = false,
    error: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val interaction = onClick?.let { action -> Modifier.clickable(onClick = action) } ?: Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .then(interaction)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF8FAFC))
            .border(
                if (selected) 2.dp else 1.dp,
                when {
                    error -> Color(0xFFDC2626)
                    selected -> Color(0xFF6D4AFF)
                    else -> Color(0xFFCBD5E1)
                },
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            accent,
            modifier = Modifier
                .padding(end = 8.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFFE0E7FF))
                .padding(horizontal = 7.dp, vertical = 3.dp),
            color = Color(0xFF3730A3),
            fontWeight = FontWeight.SemiBold,
        )
        Column {
            Text(
                label,
                color = if (error) Color(0xFFB42318) else Color(0xFF475569),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text.ifBlank { " " },
                color = Color(0xFF0F172A),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = DisplayBodyFontSize),
            )
        }
    }
}

@Composable
private fun StaticTableGrid(table: TableNode) {
    val rows = table.rows.size.coerceAtLeast(1)
    val columns = (table.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    Column(modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFCBD5E1))) {
        repeat(rows) { rowIndex ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(columns) { columnIndex ->
                    val cell = table.rows.getOrNull(rowIndex)?.getOrNull(columnIndex)
                    Text(
                        cell?.content?.previewTextForTableCell().orEmpty().ifBlank { " " },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                            .background(if (rowIndex < table.headerRowCount) Color(0xFFF1EEF9) else Color.Transparent)
                            .border(1.dp, Color(0xFFE2E8F0))
                            .padding(7.dp),
                        color = Color(0xFF334155),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

internal fun ParagraphNode.toDisplayAnnotatedString(): AnnotatedString = buildAnnotatedString {
    withStyle(ParagraphStyle()) { inlines.forEach { appendInlineNode(it) } }
}

private fun AnnotatedString.Builder.appendInlineNode(inline: InlineNode) {
    when (inline) {
        is InlineText -> withStyle(inline.textSpanStyle()) { append(inline.text) }
        InlineLineBreak -> append("\n")
        is InlineFormula -> withStyle(inlineChipStyle) {
            append(" ƒ ${MathExpressionFormatter.render(inline.expression).displayText.ifBlank { "empty" }} ")
        }
        is InlineImage -> withStyle(inlineChipStyle) {
            append(" Image: ${inline.altText?.takeIf(String::isNotBlank) ?: "embedded"} ")
        }
    }
}

private fun InlineText.textSpanStyle(): SpanStyle {
    val decorations = buildList {
        if (underline) add(TextDecoration.Underline)
        if (strikethrough) add(TextDecoration.LineThrough)
    }
    return SpanStyle(
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        textDecoration = decorations.takeIf(List<TextDecoration>::isNotEmpty)?.let(TextDecoration::combine),
        color = textColorArgb?.let(::Color) ?: Color.Unspecified,
        background = highlightColorArgb?.let(::Color) ?: Color.Unspecified,
        fontSize = (16f * fontScale.coerceIn(0.5f, 4f)).sp,
    )
}

private val inlineChipStyle = SpanStyle(
    color = Color(0xFF1E3A8A),
    background = Color(0xFFE0F2FE),
    fontWeight = FontWeight.Medium,
)
