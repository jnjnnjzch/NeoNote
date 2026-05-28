# Milestone 2: Inline Table inside TextContainer

## Summary
- Switched New Table Note workflow to create a drawing note preloaded with:
  - `TextContainerBlock`
  - paragraph before table
  - inline `TableNode`
  - paragraph after table
- Implemented inline table editing inside `TextContainerEditor`.
- Kept standalone `TableBlock` renderer as legacy compatibility path only.

## Key Changes
- `HomeScreenViewModel.addTableInkNote(...)`
  - creates initial `TicDocument` with `TextContainer + inline table`.
- Home UI wiring:
  - `btn-new-table` now calls `addTableInkNote`, not plain drawing note creation.
- `DrawingCanvas`:
  - renders `TextContainerEditor` with before/after paragraph fields.
  - renders inline `TableNode` editor with:
    - Tab navigation
    - last-cell Tab append row
    - Ctrl+B / Ctrl+I / Ctrl+U
    - multiline cells
- `DrawingCanvasViewModel`:
  - inline-table operations for `TextContainer` content:
    - update cell
    - append row
    - style toggles
    - insert table if missing

## Compatibility
- Legacy standalone `TableBlock` is still rendered when no `TextContainer` exists.
- New Normal Mode table entry path is inline table in `TextContainer`.
