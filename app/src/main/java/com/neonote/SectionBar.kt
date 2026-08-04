package com.neonote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neonote.model.NoteSection

@Composable
internal fun SectionBar(
    sections: List<NoteSection>,
    currentSectionId: String,
    canDelete: Boolean,
    onSelect: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAdd: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            sections.forEachIndexed { index, section ->
                val selected = section.id == currentSectionId
                val color = section.colorArgb?.let(::Color) ?: MaterialTheme.colorScheme.primary
                Surface(
                    shape = RoundedCornerShape(topStart = 11.dp, topEnd = 11.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                    color = if (selected) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { onSelect(section.id) },
                ) {
                    Row(
                        Modifier.padding(start = 10.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selected) {
                            BasicTextField(
                                value = section.title,
                                onValueChange = { onRename(section.id, it.take(80)) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.labelLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                ),
                                cursorBrush = SolidColor(color),
                                modifier = Modifier.background(Color.Transparent),
                            )
                        } else {
                            Text(section.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (selected) {
                            TextButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) { Text("‹") }
                            TextButton(onClick = { onMove(index, index + 1) }, enabled = index < sections.lastIndex) { Text("›") }
                            TextButton(onClick = { onDelete(section.id) }, enabled = canDelete) {
                                Text("×", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            TextButton(onClick = onAdd) { Text("＋ Section", color = MaterialTheme.colorScheme.primary) }
        }
    }
}
