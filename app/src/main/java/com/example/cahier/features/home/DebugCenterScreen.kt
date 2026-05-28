package com.example.cahier.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DebugCenterScreen(
    onOpenBuildInfo: () -> Unit,
    onOpenSPenMetrics: () -> Unit,
    onOpenPressureTest: () -> Unit,
    onOpenStrokeLatencyLab: () -> Unit,
    onOpenCanvasTransformLab: () -> Unit,
    onOpenTextContainerTableLab: () -> Unit,
    onOpenImagePasteLab: () -> Unit,
    onOpenLatexLab: () -> Unit,
    onOpenExportLab: () -> Unit,
    onOpenStressGenerator: () -> Unit,
    onOpenRawDocumentInspector: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Debug Center") }) },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Debug-only tools and labs", style = MaterialTheme.typography.titleMedium)
            DebugItem("Build Info", onOpenBuildInfo)
            DebugItem("S Pen Metrics", onOpenSPenMetrics)
            DebugItem("Pressure Test", onOpenPressureTest)
            DebugItem("Stroke Latency Lab", onOpenStrokeLatencyLab)
            DebugItem("Canvas Transform Lab", onOpenCanvasTransformLab)
            DebugItem("TextContainer/Table Lab", onOpenTextContainerTableLab)
            DebugItem("Image Paste Lab", onOpenImagePasteLab)
            DebugItem("LaTeX Lab", onOpenLatexLab)
            DebugItem("Export Lab", onOpenExportLab)
            DebugItem("Stress Generator", onOpenStressGenerator)
            DebugItem("Raw Document Inspector", onOpenRawDocumentInspector)
        }
    }
}

@Composable
private fun DebugItem(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label)
    }
}
