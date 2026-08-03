from pathlib import Path

path = Path(__file__).resolve().parents[1] / "app/src/main/java/com/neonote/NeoNoteEditorScreen.kt"
text = path.read_text(encoding="utf-8")
text = text.replace("import androidx.compose.foundation.layout.Spacer\n", "")
old = '''                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp),
                        color = Color(0xFFE7E3F0),
                    )'''
new = '''                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(Color(0xFFE7E3F0)),
                    )'''
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise RuntimeError("Page rail divider block not found")
path.write_text(text, encoding="utf-8")
print("Final page rail layout fix applied")
