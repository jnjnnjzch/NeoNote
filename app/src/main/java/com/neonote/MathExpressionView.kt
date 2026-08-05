package com.neonote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonote.engine.MathExpressionFormatter
import com.neonote.engine.MathExpressionParser
import com.neonote.engine.MathNode

@Composable
internal fun MathExpressionView(
    expression: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    color: Color = Color(0xFF171326),
) {
    val parsed = remember(expression) { MathExpressionParser.parse(expression) }
    if (!parsed.isValid) {
        Text(
            MathExpressionFormatter.render(expression).displayText.ifBlank { "Enter a formula" },
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
        )
    } else {
        MathNodeView(parsed.node, modifier, fontSize, color)
    }
}

@Composable
private fun MathNodeView(node: MathNode, modifier: Modifier, fontSize: TextUnit, color: Color) {
    when (node) {
        is MathNode.Text -> Text(
            node.value,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
        )
        is MathNode.Sequence -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            node.items.forEach { MathNodeView(it, Modifier, fontSize, color) }
        }
        is MathNode.Fraction -> Column(
            modifier = modifier.width(IntrinsicSize.Max).widthIn(min = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MathNodeView(node.numerator, Modifier.padding(horizontal = 4.dp), fontSize * 0.78f, color)
            HorizontalDivider(Modifier.fillMaxWidth(), thickness = 1.dp, color = color)
            MathNodeView(node.denominator, Modifier.padding(horizontal = 4.dp), fontSize * 0.78f, color)
        }
        is MathNode.Root -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (node.index != null) {
                MathNodeView(node.index, Modifier.align(Alignment.Top).padding(end = 1.dp), fontSize * 0.55f, color)
            }
            Text("√", color = color, fontSize = fontSize * 1.15f, fontFamily = FontFamily.Serif)
            MathNodeView(
                node.radicand,
                Modifier.drawBehind {
                    drawLine(color, start = androidx.compose.ui.geometry.Offset.Zero,
                        end = androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                }.padding(top = 2.dp),
                fontSize,
                color,
            )
        }
        is MathNode.Scripts -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MathNodeView(node.base, Modifier, fontSize, color)
            Column(verticalArrangement = Arrangement.Center) {
                node.superscript?.let { MathNodeView(it, Modifier, fontSize * 0.58f, color) }
                node.subscript?.let { MathNodeView(it, Modifier, fontSize * 0.58f, color) }
            }
        }
        is MathNode.Delimited -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(node.left, color = color, fontSize = fontSize * 1.1f, fontFamily = FontFamily.Serif)
            MathNodeView(node.content, Modifier, fontSize, color)
            Text(node.right, color = color, fontSize = fontSize * 1.1f, fontFamily = FontFamily.Serif)
        }
    }
}
