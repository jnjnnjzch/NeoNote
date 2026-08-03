from pathlib import Path

path = Path(__file__).resolve().parents[1] / "app/src/main/java/com/neonote/RichContentBoxView.kt"
text = path.read_text(encoding="utf-8")
text = text.replace(
    "import com.neonote.model.RichContentBox\n",
    "import com.neonote.model.EditorTool\nimport com.neonote.model.RichContentBox\n",
    1,
)
old_state = '''    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current'''
new_state = '''    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val contentInteractionDisabled = controller.state.currentTool != EditorTool.Text'''
if old_state in text:
    text = text.replace(old_state, new_state, 1)
elif new_state not in text:
    raise RuntimeError("Rich content interaction state anchor not found")
old_renderer = '''                    selectionMode = selectionMode,
                    selected = selected,
                    onFocus = { controller.activateRichContentBox(box.id) },'''
new_renderer = '''                    selectionMode = selectionMode || contentInteractionDisabled,
                    selected = selected,
                    onFocus = { controller.activateRichContentBox(box.id) },'''
if old_renderer in text:
    text = text.replace(old_renderer, new_renderer, 1)
elif new_renderer not in text:
    raise RuntimeError("Rich content renderer interaction anchor not found")
path.write_text(text, encoding="utf-8")
print("Non-text tools now pass gestures through rich content")
