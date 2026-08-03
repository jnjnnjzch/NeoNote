package com.neonote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun EditorToolbar(
    title: String,
    selectionMode: Boolean,
    viewportLabel: String,
    diagnosticsLabel: String,
    persistenceStatus: String,
    pageLabel: String,
    canGoToPreviousPage: Boolean,
    canGoToNextPage: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onAddPage: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
            Text(text = viewportLabel, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
            if (diagnosticsLabel.isNotBlank()) {
                Text(text = diagnosticsLabel, style = MaterialTheme.typography.bodySmall, color = Color(0xFF475569))
            }
            Text(text = persistenceStatus, style = MaterialTheme.typography.bodySmall, color = Color(0xFF0369A1))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onPreviousPage, enabled = canGoToPreviousPage) {
                Text("Prev")
            }
            Text(
                text = pageLabel,
                modifier = Modifier.padding(horizontal = 8.dp),
                color = Color(0xFF334155),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onNextPage, enabled = canGoToNextPage) {
                Text("Next")
            }
            Button(onClick = onAddPage, modifier = Modifier.padding(start = 8.dp)) {
                Text("Add Page")
            }
        }
        Button(
            onClick = onUndo,
            enabled = canUndo,
            modifier = Modifier.padding(start = 8.dp),
        ) {
            Text("Undo")
        }
        Button(
            onClick = onRedo,
            enabled = canRedo,
            modifier = Modifier.padding(start = 8.dp),
        ) {
            Text("Redo")
        }
        Button(onClick = onSave, modifier = Modifier.padding(start = 8.dp)) {
            Text("Save")
        }
        Button(onClick = onLoad, modifier = Modifier.padding(start = 8.dp)) {
            Text("Load")
        }
        Button(onClick = onToggleSelectionMode, modifier = Modifier.padding(start = 8.dp)) {
            Text(if (selectionMode) "Selection: ON" else "Selection: OFF")
        }
    }
}
