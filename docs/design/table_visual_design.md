# Inline Table Visual Design (Milestone 3)

## Goal
Table should read as a lightweight note structure inside a note box.

## Implemented Style
- Thin pale grid line (`NeoNoteVisualTokens.tableGridLine`).
- No dark table background.
- No heavy spreadsheet headers.
- Subtle active-cell outline (`NeoNoteVisualTokens.activeCellOutline`).
- Multiline-friendly cell editor (`singleLine=false`).
- Reduced visual noise: no permanent resize handle in inline table path.

## Interaction Styling
- Active cell uses slightly thicker, muted accent border.
- Non-active cells use thin pale neutral border.
- Keyboard behavior preserved:
  - Tab next cell
  - last-cell Tab append row
  - Ctrl+B / Ctrl+I / Ctrl+U

## Deferred
- Advanced row/column contextual menu polish.
- richer typography presets per table role (header/body).
