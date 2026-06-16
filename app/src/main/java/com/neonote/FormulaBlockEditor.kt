package com.neonote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.neonote.engine.RichContentLayoutDefaults
import com.neonote.model.BlockFormula
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
    if (!active) {
        FormulaCard(
            expression = formula.expression,
            editing = false,
            enabled = enabled,
            modifier = modifier,
            onClick = { controller.focusRichContentFormulaBlock(box.id, blockIndex) },
        )
        return
    }

    val focusRequester = remember { FocusRequester() }
    var platformValue by remember(box.id, blockIndex) {
        mutableStateOf(TextFieldValue(formula.expression, TextRange(formula.expression.length)))
    }

    LaunchedEffect(active, enabled) {
        if (active && enabled) focusRequester.requestFocus()
    }
    LaunchedEffect(formula.expression) {
        if (platformValue.text != formula.expression) {
            platformValue = TextFieldValue(formula.expression, TextRange(formula.expression.length))
        }
    }

    FormulaCard(
        expression = formula.expression,
        editing = true,
        enabled = enabled,
        modifier = modifier,
        input = {
            BasicTextField(
                value = platformValue,
                onValueChange = { nextValue ->
                    platformValue = nextValue
                    controller.updateRichContentFormulaExpression(
                        boxId = box.id,
                        blockIndex = blockIndex,
                        expression = nextValue.text,
                    )
                },
                enabled = enabled,
                singleLine = false,
                minLines = 1,
                textStyle = LocalTextStyle.current.copy(color = Color(0xFF0F172A)),
                cursorBrush = SolidColor(Color(0xFF7C3AED)),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && enabled && !box.isFocused) {
                            controller.activateRichContentBox(box.id)
                        }
                        // Keep the formula session active across transient platform
                        // focus moves, such as tapping the rich-content toolbar.
                        // Explicit editor transitions (focusing another block or
                        // selection/page changes) own formula blur semantics.
                    },
                decorationBox = { innerTextField ->
                    if (platformValue.text.isEmpty()) {
                        Text("Enter formula expression…", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
                    }
                    innerTextField()
                },
            )
        },
    )
}

@Composable
private fun FormulaCard(
    expression: String,
    editing: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    input: (@Composable () -> Unit)? = null,
) {
    val clickModifier = if (enabled && !editing) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(RichContentLayoutDefaults.FormulaCardHeight.dp)
            .then(clickModifier)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF8FAFC))
            .border(width = 1.dp, color = if (editing) Color(0xFF7C3AED) else Color(0xFFCBD5E1), shape = RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "ƒ",
            modifier = Modifier
                .padding(end = 8.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFFE0E7FF))
                .padding(horizontal = 7.dp, vertical = 3.dp),
            color = Color(0xFF3730A3),
            style = MaterialTheme.typography.labelMedium,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Formula", color = Color(0xFF475569), style = MaterialTheme.typography.labelSmall)
            if (editing && input != null) {
                input()
            } else {
                Text(
                    text = expression.ifBlank { "empty expression" },
                    color = Color(0xFF0F172A),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
