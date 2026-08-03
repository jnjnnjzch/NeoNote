from pathlib import Path

path = Path(__file__).resolve().parents[1] / "app/src/main/java/com/neonote/NeoNoteEditorController.kt"
text = path.read_text(encoding="utf-8")

old_activate = '''    public fun activateRichContentBox(boxId: String) {
        if (state.currentTool == EditorTool.Selection) {
            selectCanvasObject(boxId)
        } else {
            focusRichContentBox(boxId)
        }
    }

    public fun focusRichContentBox(boxId: String) {
        if (state.currentTool == EditorTool.Selection) return'''
new_activate = '''    public fun activateRichContentBox(boxId: String) {
        when (state.currentTool) {
            EditorTool.Text -> focusRichContentBox(boxId)
            EditorTool.Selection -> selectCanvasObject(boxId)
            EditorTool.Pen, EditorTool.Eraser -> Unit
        }
    }

    public fun focusRichContentBox(boxId: String) {
        if (state.currentTool != EditorTool.Text) return'''
if old_activate in text:
    text = text.replace(old_activate, new_activate, 1)
elif new_activate not in text:
    raise RuntimeError("Rich content activation boundary not found")

text = text.replace(
    "if (state.currentTool == EditorTool.Selection || state.selection.selectedRefs.isNotEmpty()) return",
    "if (state.currentTool != EditorTool.Text || state.selection.selectedRefs.isNotEmpty()) return",
)

old_todo = '''    public fun toggleRichContentTodoCheckedState(boxId: String, blockIndex: Int) {
        val updatedCanvas = currentCanvas.updateRichContentBox(boxId) { box ->'''
new_todo = '''    public fun toggleRichContentTodoCheckedState(boxId: String, blockIndex: Int) {
        if (state.currentTool != EditorTool.Text || state.selection.selectedRefs.isNotEmpty()) return
        val updatedCanvas = currentCanvas.updateRichContentBox(boxId) { box ->'''
if old_todo in text:
    text = text.replace(old_todo, new_todo, 1)
elif new_todo not in text:
    raise RuntimeError("Todo tool boundary not found")

old_row = '''    public fun addActiveRichContentTableRow(boxId: String) {
        val target = activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell ?: return'''
new_row = '''    public fun addActiveRichContentTableRow(boxId: String) {
        if (state.currentTool != EditorTool.Text || state.selection.selectedRefs.isNotEmpty()) return
        val target = activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell ?: return'''
if old_row in text:
    text = text.replace(old_row, new_row, 1)
elif new_row not in text:
    raise RuntimeError("Table row tool boundary not found")

old_col = '''    public fun addActiveRichContentTableColumn(boxId: String) {
        val target = activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell ?: return'''
new_col = '''    public fun addActiveRichContentTableColumn(boxId: String) {
        if (state.currentTool != EditorTool.Text || state.selection.selectedRefs.isNotEmpty()) return
        val target = activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell ?: return'''
if old_col in text:
    text = text.replace(old_col, new_col, 1)
elif new_col not in text:
    raise RuntimeError("Table column tool boundary not found")

path.write_text(text, encoding="utf-8")
print("Rich content editing is now restricted to the Text tool")
