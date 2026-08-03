from pathlib import Path

source_path = Path(__file__).with_name("product_quality_patch.py")
source = source_path.read_text(encoding="utf-8")
redundant_patch = '''patch(
    "app/src/main/java/com/neonote/InfiniteCanvasViewport.kt",
    '''\'''        mode = if (selectionMode) InputMode.Selection else InputMode.Write,
    )'''\''',
    '''\'''        mode = mode,
    )'''\''',
)
'''
if redundant_patch not in source:
    raise RuntimeError("Expected redundant viewport patch block was not found")
source = source.replace(redundant_patch, "", 1)
exec(compile(source, str(source_path), "exec"), {"__name__": "__main__", "__file__": str(source_path)})
