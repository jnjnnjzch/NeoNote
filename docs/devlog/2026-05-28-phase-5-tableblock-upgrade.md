# Phase 5: TableBlock Upgrade

## Scope

- Added formatting fields per cell:
  - bold
  - italic
  - underline
- Added multiline cell editing.
- Added keyboard formatting shortcuts:
  - `Ctrl+B`
  - `Ctrl+I`
  - `Ctrl+U`
- Added lightweight rich paste parsing from cell text:
  - image markdown `![alt](uri)` -> `imageUri`
  - LaTeX wrapped as `$$...$$` -> `latex`

## Persistence

- Formatting and parsed rich fields persist in document JSON.
- Ink stroke storage remains separate.

## Tests

- Expanded serializer tests to cover new cell formatting and rich fields.
