# NeoNote OneNote Logic Reset Progress

## Milestone 0 — Deprecation Boundary
- Status: DONE
- Date: 2026-05-28
- Commit: pending

### Completed
- Added deprecation architecture doc:
  - `docs/architecture/legacy_tableblock_deprecation.md`
- Marked legacy compatibility entry points in code comments:
  - `DrawingCanvasViewModel.ensureDefaultTableBlock()`
  - `TableBlockEditor(...)`
- Removed auto-insertion of standalone table on drawing-screen startup.

### Notes
- Legacy `TableBlock` model remains loadable/renderable for existing documents.
- New normal drawing flow no longer defaults to standalone `TableBlock`.

### Validation
- Local run blocked in this environment:
  - Java 17+ configured successfully
  - Android SDK missing (`ANDROID_HOME`/`sdk.dir` not found), so Gradle Android tasks cannot execute locally
- CI remains the source of truth for Android build/test verification.

## Milestone 1 — TextContainer Foundation
- Status: IN PROGRESS (core model + basic editor implemented)
- Date: 2026-05-28
- Commit: pending

### Completed
- Added `TextContainerBlock` and `TextContainerContent` node model:
  - `ParagraphNode`
  - `TableNode` (placeholder)
  - `FormulaNode`
  - `ImageNode`
- Added default TextContainer creation for drawing notes.
- Added basic TextContainer editor and drag movement on shared canvas transform.
- Added serializer round-trip test for TextContainer coordinates/content.

### Remaining for Milestone 1 acceptance
- Full UI/interaction verification in CI/device run.
- Additional explicit persistence/movement tests beyond serializer baseline.

## Milestone 2 — Inline Table in TextContainer
- Status: IN PROGRESS (core workflow switched)
- Date: 2026-05-28
- Commit: pending

### Completed
- `New Table Note` now initializes `TextContainer` with inline `TableNode`.
- Added before/after paragraph fields around inline table in the same container.
- Added inline table editing behavior in `TextContainerEditor`:
  - Tab navigation
  - last-cell Tab append row
  - Ctrl+B / Ctrl+I / Ctrl+U
  - multiline cells
- Legacy standalone `TableBlock` now compatibility-rendered only when no `TextContainer` exists.

### Remaining for Milestone 2 acceptance
- Dedicated persistence tests for before/after paragraph + inline table edits.
- CI/instrumentation run confirmation.

## Milestone 3 — Visual Quality Gate
- milestone: 3
- status: PARTIAL
- local_build: PASS
- unit_tests: PASS
- visual_evidence: available
- remaining_visual_issues:
  - inline table still inherits some TextField/form affordances
  - toolbar can be further reduced to icon-led calm controls
  - full screenshot golden pipeline not yet established

### Completed
- Added centralized visual token set (`NeoNoteVisualTokens`).
- Applied paper-like canvas and container/table styling.
- Removed debug overlays from normal drawing surface.
- Added preview evidence for required normal-mode visual states.
