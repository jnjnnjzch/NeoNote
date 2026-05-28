# Phase 3: TableBlock v0.1

## Scope

- Implemented `TableBlock` with default `3x3` grid cells.
- Added cell-level text editing UI in drawing canvas.
- Added keyboard behavior:
  - `Tab` moves focus across cells.
  - `Tab` in the last cell appends a new row.
  - `Ctrl+B` toggles bold for the focused cell.
- Save/load is backed by the versioned document JSON in note data.

## Storage

- Table data persists in the `TicDocument` JSON.
- Ink strokes remain in `strokesData` and are unchanged.

## Tests

- Extended `DocumentSerializerTest` to verify row growth and bold/text persistence.
