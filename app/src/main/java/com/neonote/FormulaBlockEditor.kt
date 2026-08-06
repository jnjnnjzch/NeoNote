package com.neonote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonote.engine.MathExpressionFormatter
import com.neonote.model.BlockFormula
import com.neonote.model.FormulaDisplayMode
import com.neonote.model.RichContentBox

@Composable
internal fun FormulaBlockEditor(
    box: RichContentBox,
    blockIndex: Int,
    formula: BlockFormula,
    formulaNumber: Int,
    active: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    controller: NeoNoteEditorController,
    modifier: Modifier = Modifier,
) {
    val enabled = !selectionMode && !selected
    val rendered = remember(formula.expression) { MathExpressionFormatter.render(formula.expression) }
    var menuVisible by remember(box.id, blockIndex) { mutableStateOf(false) }
    val openModifier = if (!active && enabled) Modifier.clickable {
        controller.focusRichContentFormulaBlock(box.id, blockIndex)
    } else Modifier

    Column(modifier = modifier.fillMaxWidth().then(openModifier).padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = rendered.displayText.ifBlank { if (active) "" else "Formula" },
                modifier = Modifier.weight(1f),
                color = when {
                    formula.expression.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .58f)
                    rendered.isValid -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.error
                },
                fontSize = if (formula.displayMode == FormulaDisplayMode.Display) 24.sp else 18.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
            )
            if (formula.numbered) {
                Text("($formulaNumber)", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            if (active) {
                Box {
                    Box(
                        modifier = Modifier.size(38.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .clickable { menuVisible = true }
                            .semantics { role = Role.Button; contentDescription = "Formula options" },
                        contentAlignment = Alignment.Center,
                    ) { Text("⋮", style = MaterialTheme.typography.titleMedium) }
                    DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }) {
                        DropdownMenuItem(text = { Text(if (formula.displayMode == FormulaDisplayMode.Display) "Use inline size" else "Use display size") }, onClick = {
                            menuVisible = false
                            controller.updateRichContentFormula(
                                box.id,
                                blockIndex,
                                displayMode = if (formula.displayMode == FormulaDisplayMode.Display) FormulaDisplayMode.Compact else FormulaDisplayMode.Display,
                            )
                        })
                        DropdownMenuItem(text = { Text(if (formula.numbered) "Remove number" else "Number formula") }, onClick = {
                            menuVisible = false
                            controller.updateRichContentFormula(box.id, blockIndex, numbered = !formula.numbered)
                        })
                        DropdownMenuItem(text = { Text("Delete formula", color = MaterialTheme.colorScheme.error) }, onClick = {
                            menuVisible = false
                            controller.deleteRichContentBlock(box.id, blockIndex)
                        })
                    }
                }
            }
        }

        if (active) {
            FormulaSourceField(box, blockIndex, formula, enabled, controller)
            rendered.errors.forEach { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
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
        if (value.text != formula.expression) value = value.copy(
            text = formula.expression,
            selection = TextRange(value.selection.start.coerceIn(0, formula.expression.length), value.selection.end.coerceIn(0, formula.expression.length)),
            composition = null,
        )
    }
    BasicTextField(
        value = value,
        onValueChange = { next ->
            value = next
            controller.updateRichContentFormulaExpression(box.id, blockIndex, next.text)
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            controller.continueAfterRichContentFormula(box.id, blockIndex)
        }),
        textStyle = LocalTextStyle.current.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .58f), RoundedCornerShape(7.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .45f), RoundedCornerShape(7.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused && !box.isFocused) controller.activateRichContentBox(box.id) }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                    controller.continueAfterRichContentFormula(box.id, blockIndex)
                    true
                } else false
            },
        decorationBox = { inner ->
            Box {
                if (value.text.isBlank()) Text("Type a formula…", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f), fontFamily = FontFamily.Monospace)
                inner()
            }
        },
    )
}
