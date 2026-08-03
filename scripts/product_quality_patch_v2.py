from __future__ import annotations

from pathlib import Path

source_path = Path(__file__).with_name("product_quality_patch.py")
lines = source_path.read_text(encoding="utf-8").splitlines(keepends=True)
anchor = "mode = if (selectionMode) InputMode.Selection else InputMode.Write,"
anchor_index = next((index for index, line in enumerate(lines) if anchor in line), None)
if anchor_index is None:
    raise RuntimeError("Expected redundant viewport patch anchor was not found")

start = anchor_index
while start >= 0 and lines[start].strip() != "patch(":
    start -= 1
if start < 0:
    raise RuntimeError("Could not find the beginning of the redundant patch block")

end = anchor_index
while end < len(lines) and lines[end].strip() != ")":
    end += 1
if end >= len(lines):
    raise RuntimeError("Could not find the end of the redundant patch block")

source = "".join(lines[:start] + lines[end + 1 :])
exec(
    compile(source, str(source_path), "exec"),
    {"__name__": "__main__", "__file__": str(source_path)},
)
