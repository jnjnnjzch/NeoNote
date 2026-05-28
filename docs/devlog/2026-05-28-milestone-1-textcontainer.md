# Milestone 1: TextContainer Foundation

## Summary
- Introduced `TextContainerBlock` as a first-class canvas object.
- Added structured container content nodes:
  - `ParagraphNode`
  - `TableNode` (placeholder)
  - `FormulaNode`
  - `ImageNode`
- Added default TextContainer creation for drawing notes.
- Added basic TextContainer editor in drawing canvas:
  - editable paragraph text
  - drag-to-move container
  - canvas-coordinate-based positioning with shared `CanvasTransform`.

## Key Code Changes
- `app/src/main/java/com/example/cahier/core/document/DocumentModel.kt`
  - Added new content-node model and `TextContainerBlock`.
- `app/src/main/java/com/example/cahier/features/drawing/viewmodel/DrawingCanvasViewModel.kt`
  - Added `ensureDefaultTextContainer()`
  - Added `updateTextContainerParagraph()`
  - Added `moveTextContainerBy()`
- `app/src/main/java/com/example/cahier/features/drawing/DrawingCanvas.kt`
  - Added `TextContainerEditor` rendering/editing and drag behavior.

## Tests
- `DocumentSerializerTest.encodeDecode_roundTripsTextContainerBlock`
  - verifies persistence of coordinates and content node structure.

## Notes
- Standalone `TableBlock` remains compatibility path.
- Inline table editing inside `TextContainer` content is deferred to Milestone 2.
