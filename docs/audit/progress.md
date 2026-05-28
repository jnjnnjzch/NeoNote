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
