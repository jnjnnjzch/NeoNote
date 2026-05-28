# Milestone 3: Visual Quality Gate

## Goal
Move Normal Mode from engineering-demo feel to calm, paper-like note editing.

## Implemented
- Added centralized visual tokens:
  - `app/src/main/java/com/example/cahier/core/ui/theme/NeoNoteVisualTokens.kt`
- Updated drawing/editor visual surface:
  - warm paper-like canvas background
  - muted toolbar background
  - reduced visual noise in control set
- Removed debug overlays from Normal editor surface:
  - hidden Ink debug metrics strip
  - hidden pressure test panel
- Updated TextContainer style:
  - near-white surface
  - subtle default border
  - muted selected/focus border
  - comfortable padding
- Updated inline table style:
  - thin pale grid lines
  - subtle active-cell outline
  - no dark spreadsheet styling

## Visual Evidence
- Added/updated previews in `DrawingCanvas.kt`:
  - `NormalEditorEmptyPreview`
  - `TextContainerParagraphPreview`
  - `TextContainerInlineTablePreview`
  - `ActiveCellPreview`
  - `SelectedTextContainerPreview`

## Validation
- `:app:clean :app:assembleDebug` PASS
- `:app:testDebugUnitTest` PASS

## Notes
- Legacy standalone `TableBlock` remains compatibility-only.
- Milestone 3 intentionally does not implement lasso/export/latex/image-paste enhancements.
