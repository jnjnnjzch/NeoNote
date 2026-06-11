package com.neonote

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.neonote.engine.InlineStyle
import com.neonote.model.ListKind

@Composable
internal fun RichContentToolbar(
    boxId: String,
    controller: NeoNoteEditorController,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.focusProperties { canFocus = false },
        shape = RoundedCornerShape(12.dp),
        color = Color(0xF8FFFFFF),
        shadowElevation = 6.dp,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ToolbarAction(label = "B", contentDescription = "Bold") {
                controller.toggleActiveRichContentStyle(boxId = boxId, style = InlineStyle.Bold)
            }
            ToolbarAction(label = "I", contentDescription = "Italic") {
                controller.toggleActiveRichContentStyle(boxId = boxId, style = InlineStyle.Italic)
            }
            ToolbarAction(label = "U", contentDescription = "Underline") {
                controller.toggleActiveRichContentStyle(boxId = boxId, style = InlineStyle.Underline)
            }
            ToolbarAction(label = "•", contentDescription = "Bullet list") {
                controller.toggleActiveRichContentList(boxId = boxId, kind = ListKind.Bullet)
            }
            ToolbarAction(label = "1.", contentDescription = "Numbered list") {
                controller.toggleActiveRichContentList(boxId = boxId, kind = ListKind.Numbered)
            }
            ToolbarAction(label = "☐", contentDescription = "Todo list") {
                controller.toggleActiveRichContentList(boxId = boxId, kind = ListKind.Todo)
            }
            ToolbarAction(label = "▦", contentDescription = "Insert table placeholder") {
                controller.insertRichContentTablePlaceholder(boxId = boxId)
            }
            ToolbarAction(label = "ƒ", contentDescription = "Insert formula placeholder") {
                controller.insertRichContentFormulaPlaceholder(boxId = boxId)
            }
            ToolbarAction(label = "▧", contentDescription = "Insert image placeholder") {
                controller.insertRichContentImagePlaceholder(boxId = boxId)
            }
        }
    }
}

@Composable
private fun ToolbarAction(
    label: String,
    contentDescription: String,
    onAction: () -> Unit,
) {
    Text(
        text = label,
        modifier = Modifier
            .focusProperties { canFocus = false }
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
                onClick {
                    onAction()
                    true
                }
            }
            .pointerInput(onAction) {
                detectTapGestures(onTap = { onAction() })
            }
            .background(Color(0xFFEDE9FE), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color = Color(0xFF4C1D95),
        style = MaterialTheme.typography.labelLarge,
    )
}
