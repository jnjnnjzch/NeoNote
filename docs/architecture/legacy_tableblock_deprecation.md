# Legacy TableBlock Deprecation Boundary (Milestone 0)

## Purpose
Define the freeze boundary for the old standalone `TableBlock` model during the OneNote-logic reset.

## Decision
- `TableBlock` remains **compatibility-only**.
- Existing notes with `TableBlock` must still load and render.
- New Normal Mode notes must **not** auto-insert standalone tables.

## What We Keep (for reuse/migration)
- `TableCell` data structure and basic formatting fields.
- Keyboard behaviors already implemented in legacy editor:
  - Tab next-cell navigation
  - Last-cell Tab appends row
  - Ctrl+B / Ctrl+I / Ctrl+U toggles
- Serializer/versioning patterns in `TicDocument`/`DocumentSerializer`.

## What Is Deprecated in Normal Mode
- Standalone table drag/resize handles as the primary table workflow.
- Dark/debug-like standalone table surface styling.
- Automatic table-centric anchoring as the default interaction model.

## Compatibility Guarantees
- Legacy documents containing standalone `TableBlock` continue to open.
- Legacy `TableBlock` rendering code remains available during transition.

## Boundary Applied in Milestone 0
- Removed auto-insertion of default `TableBlock` in `DrawingCanvas` startup path.
- Added explicit code comments marking legacy entry points:
  - `DrawingCanvasViewModel.ensureDefaultTableBlock()`
  - `TableBlockEditor(...)`

## Next Direction (Milestone 1+)
- Introduce `TextContainer` as first-class canvas object.
- Move table logic to inline `TableNode` inside `TextContainer` content.
- Keep legacy standalone path only for compatibility/migration.
