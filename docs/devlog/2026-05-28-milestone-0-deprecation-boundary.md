# Milestone 0: Deprecation Boundary

## Summary
- Froze standalone `TableBlock` as legacy/compatibility path.
- Stopped auto-inserting default `TableBlock` when opening drawing notes.
- Marked legacy entry points in code with explicit comments.

## Code Changes
- `app/src/main/java/com/example/cahier/features/drawing/DrawingCanvas.kt`
  - Removed startup auto-call to `ensureDefaultTableBlock()`.
  - Added legacy comment on `TableBlockEditor`.
- `app/src/main/java/com/example/cahier/features/drawing/viewmodel/DrawingCanvasViewModel.kt`
  - Added legacy compatibility documentation on `ensureDefaultTableBlock()`.

## Architecture Docs
- Added `docs/architecture/legacy_tableblock_deprecation.md`.

## Validation
- Legacy standalone table code path remains in project for compatibility.
- New drawing notes no longer default to standalone table insertion.
