from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def patch(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"Expected patch anchor not found in {path}: {old[:120]!r}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


def patch_all(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    if old not in text:
        return
    target.write_text(text.replace(old, new), encoding="utf-8")


# Controller: product-level tool switching, selection deletion, viewport reset,
# and stroke erasing. These are kept in the reducer rather than implemented as
# UI-only state so undo/autosave and every input surface see the same document.
patch(
    "app/src/main/java/com/neonote/NeoNoteEditorController.kt",
    "import com.neonote.model.TableCellAddress\n",
    "import com.neonote.model.TableCellAddress\nimport com.neonote.model.ViewportState\n",
)

patch(
    "app/src/main/java/com/neonote/NeoNoteEditorController.kt",
    '''    public fun redo() {
        if (redoDocuments.isEmpty()) return
        val nextDocument = redoDocuments.removeLast()
        undoDocuments.addLast(state.document.historySnapshot())
        undoDocuments.trimToHistoryLimit()
        restoreDocumentFromHistory(nextDocument)
    }

    public suspend fun saveDocument''',
    '''    public fun redo() {
        if (redoDocuments.isEmpty()) return
        val nextDocument = redoDocuments.removeLast()
        undoDocuments.addLast(state.document.historySnapshot())
        undoDocuments.trimToHistoryLimit()
        restoreDocumentFromHistory(nextDocument)
    }

    public fun setTool(tool: EditorTool) {
        if (state.currentTool == tool) return
        if (tool != EditorTool.Text) {
            state.focusedRichContentBoxId?.let(::commitRichContentEditing)
        }
        cancelInkIfActive()
        val nextCanvas = if (tool == EditorTool.Text) {
            currentCanvas
        } else {
            currentCanvas.setFocusedRichContentBox(null)
        }
        state = state.copy(
            currentTool = tool,
            focusedRichContentBoxId = if (tool == EditorTool.Text) state.focusedRichContentBoxId else null,
            selection = if (tool == EditorTool.Selection) state.selection else SelectionState(),
            document = if (nextCanvas == currentCanvas) state.document else state.document.withCanvas(nextCanvas),
        )
    }

    public fun resetViewport() {
        state = state.copy(viewport = ViewportState())
    }

    public fun deleteSelection() {
        val selection = state.selection
        if (selection.selectedRefs.isEmpty()) return
        val updatedCanvas = currentCanvas.copy(
            objects = currentCanvas.objects.filterNot { selection.isObjectSelected(it.id) },
            inkLayer = currentCanvas.inkLayer.copy(
                strokes = currentCanvas.inkLayer.strokes.filterNot { selection.isStrokeSelected(it.id) },
            ),
        )
        state = state.copy(
            document = state.document.withCanvas(updatedCanvas.setFocusedRichContentBox(null)),
            focusedRichContentBoxId = null,
            selection = SelectionState(),
        )
        inkSession = InkSession.fromCanvas(updatedCanvas)
    }

    public suspend fun saveDocument''',
)

patch(
    "app/src/main/java/com/neonote/NeoNoteEditorController.kt",
    '''            is InputAction.ContinueInk -> continueInk(action.samples)
            is InputAction.PanBy -> panViewportBy(action.dx, action.dy)''',
    '''            is InputAction.ContinueInk -> continueInk(action.samples)
            is InputAction.EraseAt -> eraseInkAtScreenPositions(action.positions)
            is InputAction.PanBy -> panViewportBy(action.dx, action.dy)''',
)

patch(
    "app/src/main/java/com/neonote/NeoNoteEditorController.kt",
    '''    private fun commitInkLayer(inkLayer: com.neonote.model.InkLayer) {
        val updatedCanvas = currentCanvas.copy(inkLayer = inkLayer)
        state = state.copy(document = state.document.withCanvas(updatedCanvas))
    }

    public fun setSelectionMode''',
    '''    private fun commitInkLayer(inkLayer: com.neonote.model.InkLayer) {
        val updatedCanvas = currentCanvas.copy(inkLayer = inkLayer)
        state = state.copy(document = state.document.withCanvas(updatedCanvas))
    }

    private fun eraseInkAtScreenPositions(screenPositions: List<CanvasPoint>) {
        if (screenPositions.isEmpty() || currentCanvas.inkLayer.strokes.isEmpty()) return
        val documentPositions = screenPositions.map(::screenToDocument)
        val tolerance = 14f / state.viewport.zoomScale.coerceAtLeast(MinZoomScale)
        val remainingStrokes = currentCanvas.inkLayer.strokes.filterNot { stroke ->
            documentPositions.any { point ->
                selectionEngine.hitTestInkStroke(stroke = stroke, point = point, tolerance = tolerance)
            }
        }
        if (remainingStrokes.size == currentCanvas.inkLayer.strokes.size) return
        val updatedCanvas = currentCanvas.copy(
            inkLayer = currentCanvas.inkLayer.copy(strokes = remainingStrokes),
        )
        state = state.copy(document = state.document.withCanvas(updatedCanvas))
        inkSession = InkSession.fromCanvas(updatedCanvas)
    }

    public fun setSelectionMode''',
)

# Viewport: derive the actual input mode from the selected product tool.
patch(
    "app/src/main/java/com/neonote/InfiniteCanvasViewport.kt",
    "import com.neonote.model.CanvasPoint\n",
    "import com.neonote.model.CanvasPoint\nimport com.neonote.model.EditorTool\n",
)
patch(
    "app/src/main/java/com/neonote/InfiniteCanvasViewport.kt",
    '''    val platformSnapshotStore = remember { PlatformSnapshotStore() }
    Box(''',
    '''    val platformSnapshotStore = remember { PlatformSnapshotStore() }
    val inputMode = when (controller.state.currentTool) {
        EditorTool.Pen -> InputMode.Navigate
        EditorTool.Text -> InputMode.Write
        EditorTool.Selection -> InputMode.Selection
        EditorTool.Eraser -> InputMode.Erase
    }
    Box(''',
)
patch_all(
    "app/src/main/java/com/neonote/InfiniteCanvasViewport.kt",
    "mode = if (selectionMode) InputMode.Selection else InputMode.Write,",
    "mode = inputMode,",
)
patch(
    "app/src/main/java/com/neonote/InfiniteCanvasViewport.kt",
    ".pointerInput(selectionMode) {",
    ".pointerInput(inputMode) {",
)
patch(
    "app/src/main/java/com/neonote/InfiniteCanvasViewport.kt",
    "selectionMode = selectionMode,\n                )",
    "mode = inputMode,\n                )",
)
patch(
    "app/src/main/java/com/neonote/InfiniteCanvasViewport.kt",
    '''    controller: NeoNoteEditorController,
    selectionMode: Boolean,
) {
    awaitEachGesture''',
    '''    controller: NeoNoteEditorController,
    mode: InputMode,
) {
    awaitEachGesture''',
)
patch_all(
    "app/src/main/java/com/neonote/InfiniteCanvasViewport.kt",
    "selectionMode = selectionMode,",
    "mode = mode,",
)
patch(
    "app/src/main/java/com/neonote/InfiniteCanvasViewport.kt",
    '''    platformSnapshot: AndroidPointerSnapshot?,
    selectionMode: Boolean,
) {''',
    '''    platformSnapshot: AndroidPointerSnapshot?,
    mode: InputMode,
) {''',
)
patch(
    "app/src/main/java/com/neonote/InfiniteCanvasViewport.kt",
    '''        mode = if (selectionMode) InputMode.Selection else InputMode.Write,
    )''',
    '''        mode = mode,
    )''',
)
patch(
    "app/src/main/java/com/neonote/InfiniteCanvasViewport.kt",
    ".background(Color(0xFFEFF6FF))",
    ".background(Color(0xFFF9F8FC))",
)
patch(
    "app/src/main/java/com/neonote/InfiniteCanvasViewport.kt",
    '''        Text(
            text = "Tap blank canvas to create text · Drag blank canvas to pan · Pinch to zoom",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.88f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            color = Color(0xFF334155),
            style = MaterialTheme.typography.bodySmall,
        )''',
    '''        if (controller.currentCanvas.objects.isEmpty() && controller.currentCanvas.inkLayer.strokes.isEmpty()) {
            Text(
                text = when (controller.state.currentTool) {
                    EditorTool.Text -> "Tap anywhere to start typing"
                    EditorTool.Pen -> "Write with S Pen · Drag with one finger · Pinch to zoom"
                    EditorTool.Selection -> "Draw around ink or objects to select them"
                    EditorTool.Eraser -> "Erase with S Pen or the pen eraser"
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.92f))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                color = Color(0xFF746D82),
                style = MaterialTheme.typography.bodyMedium,
            )
        }''',
)

# Active rich text now renders inline marks while the user edits, instead of
# showing formatting only after focus leaves the field.
patch(
    "app/src/main/java/com/neonote/RichParagraphEditor.kt",
    "import androidx.compose.ui.text.TextRange\n",
    '''import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
''',
)
patch(
    "app/src/main/java/com/neonote/RichParagraphEditor.kt",
    "import androidx.compose.ui.text.input.ImeAction\n",
    '''import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
''',
)
patch(
    "app/src/main/java/com/neonote/RichParagraphEditor.kt",
    '''        cursorBrush = SolidColor(Color(0xFF7C3AED)),
        modifier = modifier''',
    '''        cursorBrush = SolidColor(Color(0xFF7C3AED)),
        visualTransformation = remember(paragraph) { ParagraphStyleVisualTransformation(paragraph) },
        modifier = modifier''',
)
patch(
    "app/src/main/java/com/neonote/RichParagraphEditor.kt",
    '''private const val InlineAtomPlaceholder: String = "\\uFFFC"

private fun androidx.compose.ui.input.key.KeyEvent.richContentPlainTextPaste''',
    '''private const val InlineAtomPlaceholder: String = "\\uFFFC"

private class ParagraphStyleVisualTransformation(
    private val paragraph: ParagraphNode,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text.text)
        var offset = 0
        paragraph.inlines.forEach { inline ->
            when (inline) {
                is InlineText -> {
                    val end = (offset + inline.text.length).coerceAtMost(text.length)
                    if (end > offset && (inline.bold || inline.italic || inline.underline)) {
                        builder.addStyle(
                            style = SpanStyle(
                                fontWeight = if (inline.bold) FontWeight.Bold else null,
                                fontStyle = if (inline.italic) FontStyle.Italic else null,
                                textDecoration = if (inline.underline) TextDecoration.Underline else null,
                            ),
                            start = offset,
                            end = end,
                        )
                    }
                    offset = end
                }
                InlineLineBreak -> offset = (offset + 1).coerceAtMost(text.length)
                is InlineFormula, is InlineImage -> offset = (offset + 1).coerceAtMost(text.length)
            }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

private fun androidx.compose.ui.input.key.KeyEvent.richContentPlainTextPaste''',
)

print("Product-quality source patches applied")
