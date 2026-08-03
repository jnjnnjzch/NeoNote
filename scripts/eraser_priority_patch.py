from __future__ import annotations

from pathlib import Path

path = Path(__file__).resolve().parents[1] / "app/src/main/java/com/neonote/engine/InputRouter.kt"
text = path.read_text(encoding="utf-8")
old = '''    public fun route(canvas: InfiniteCanvas, event: InputEvent, mode: InputMode = InputMode.Write): InputRouteResult {
        if (mode == InputMode.Selection) {
            return routeSelectionMode(canvas, event)
        }

        val hasPhysicalEraser = event.pointers.any { it.tool == PointerTool.Eraser }
        val hasStylus = event.pointers.any { it.tool == PointerTool.SPen || it.tool == PointerTool.Eraser }
        if (hasPhysicalEraser || (mode == InputMode.Erase && hasStylus)) {
            activeFingerDown = null
            return InputRouteResult(canvas = canvas, action = event.toEraseAction())
        }

        if (hasStylus) {'''
new = '''    public fun route(canvas: InfiniteCanvas, event: InputEvent, mode: InputMode = InputMode.Write): InputRouteResult {
        val hasPhysicalEraser = event.pointers.any { it.tool == PointerTool.Eraser }
        if (hasPhysicalEraser) {
            activeFingerDown = null
            return InputRouteResult(canvas = canvas, action = event.toEraseAction())
        }

        if (mode == InputMode.Selection) {
            return routeSelectionMode(canvas, event)
        }

        val hasStylus = event.pointers.any { it.tool == PointerTool.SPen }
        if (mode == InputMode.Erase && hasStylus) {
            activeFingerDown = null
            return InputRouteResult(canvas = canvas, action = event.toEraseAction())
        }

        if (hasStylus) {'''
if new not in text:
    if old not in text:
        raise RuntimeError("Input router eraser-priority anchor not found")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Physical eraser priority applied")
