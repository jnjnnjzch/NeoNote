# Phase 2: Versioned Document Model

## Scope

- Added versioned model types:
  - `TicDocument`
  - `CanvasPage`
  - `Block`
  - placeholder `TableBlock`
  - `InkLayerRef`
- Added `DocumentSerializer` for JSON encode/decode with block type discriminator.
- Connected drawing notes to the new model using `Note.text` as the document JSON container.

## Layer Separation

- Structured document blocks are persisted in note text JSON.
- Ink strokes remain persisted independently in `strokesData`.
- Predicted stroke handling remains runtime-only and outside persisted document data.

## Tests

- Added `DocumentSerializerTest`:
  - table block round-trip
  - invalid payload handling
