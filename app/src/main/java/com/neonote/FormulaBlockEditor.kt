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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonote.engine.MathExpressionParser
import com.neonote.model.BlockFormula
import com.neonote.model.FormulaDisplayMode
import com.neonote.model.RichContentBox

@Composable
internal fun FormulaBlockEditor(
    box: RichContentBox,
    blockIndex: Int,
    formula: BlockFormula,
    active: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    controller: NeoNoteEditorController,
    modifier: Modifier = Modifier,
) {
    val enabled = !selectionMode && !selected
    val parsed = remember(formula.expression) { MathExpressionParser.parse(formula.expression) }
    val openModifier = if (!active && enabled) Modifier.clickable {
        controller.focusRichContentFormulaBlock(box.id, blockIndex)
    } else Modifier

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(openModifier)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFAFAFF))
            .border(
                1.dp,
                if (active) Color(0xFF6D4AFF) else if (parsed.isValid) Color(0xFFD9D4E7) else Color(0xFFDC2626),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "ƒx",
                color = Color(0xFF4C35B4),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFFEDE9FE))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
            Text(
                text = if (formula.displayMode == FormulaDisplayMode.Display) "Display formula" else "Inline-size formula",
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                color = Color(0xFF6B6477),
                style = MaterialTheme.typography.labelMedium,
            )
            if (formula.numbered) Text("#", color = Color(0xFF6D4AFF), fontWeight = FontWeight.Bold)
        }

        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MathExpressionView(
                expression = formula.expression,
                modifier = Modifier.weight(1f),
                color = if (formula.expression.isBlank()) Color(0xFF9B95A8) else Color(0xFF171326),
                fontSize = if (formula.displayMode == FormulaDisplayMode.Display) 24.sp else 18.sp,
            )
            if (formula.numbered) {
                Text("(${blockIndex + 1})", color = Color(0xFF817A8E), style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (active) {
            FormulaSourceField(box, blockIndex, formula, enabled, controller)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    controller.updateRichContentFormula(box.id, blockIndex, displayMode =
                        if (formula.displayMode == FormulaDisplayMode.Display) FormulaDisplayMode.Compact else FormulaDisplayMode.Display)
                }) { Text(if (formula.displayMode == FormulaDisplayMode.Display) "Compact" else "Display") }
                TextButton(onClick = {
                    controller.updateRichContentFormula(box.id, blockIndex, numbered = !formula.numbered)
                }) { Text(if (formula.numbered) "Remove number" else "Number") }
                TextButton(onClick = { controller.deleteRichContentBlock(box.id, blockIndex) }) {
                    Text("Delete", color = Color(0xFFB42318))
                }
            }
        }

        parsed.errors.forEach { error ->
            Text(error, color = Color(0xFFB42318), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun FormulaSourceField(
    box: RichContentBox,
    blockIndex: Int,
    formula: BlockFormula,
    enabled: Boolean,
    controller: NeoNoteEditorController,
) {
    val requester = remember { FocusRequester() }
    var value by remember(box.id, blockIndex) {
        mutableStateOf(TextFieldValue(formula.expression, TextRange(formula.expression.length)))
    }
    LaunchedEffect(enabled) { if (enabled) requester.requestFocus() }
    LaunchedEffect(formula.expression) {
        if (value.text != formula.expression) value = TextFieldValue(formula.expression, TextRange(formula.expression.length))
    }
    BasicTextField(
        value = value,
        onValueChange = { next ->
            value = next
            controller.updateRichContentFormulaExpression(box.id, blockIndex, next.text)
        },
        enabled = enabled,
        textStyle = LocalTextStyle.current.copy(
            color = Color(0xFF272235),
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
        ),
        cursorBrush = SolidColor(Color(0xFF6D4AFF)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE0DCE8), RoundedCornerShape(8.dp))
            .padding(10.dp)
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused && !box.isFocused) controller.activateRichContentBox(box.id) },
        decorationBox = { inner ->
            if (value.text.isBlank()) Text("LaTeX: x^2 + \\frac{a}{b}", color = Color(0xFFA09AAA), fontFamily = FontFamily.Monospace)
            inner()
        },
    )
}
