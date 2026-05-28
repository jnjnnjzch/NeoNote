# Roadmap

This roadmap is intentionally sequential. Do not skip phases, and keep each pull request scoped to one feature.

## Phase 0: Repository and CI

- Fork Google's `android/cahier` sample.
- Add GitHub Actions.
- Build a debug APK from the upstream fork.
- Add architecture and contribution documentation.
- Do not modify app logic.

## Phase 1: Inking Pipeline Audit

- Audit Cahier's Jetpack Ink pipeline.
- Add a debug overlay for pressure, tool type, point count, event rate, finalized stroke count, and cancel or palm events.
- Do not add tables.

## Phase 2: Versioned Document Model

- Introduce `Document > CanvasPage > Blocks + InkLayer`.
- Add a placeholder `TableBlock` type.
- Keep strokes independent from blocks.
- Add tests for model and serialization behavior.

## Phase 3: TableBlock v0.1

- Implement a 3x3 table.
- Support text input.
- Support Tab navigation.
- Make Tab in the last cell create a row.
- Support `Ctrl+B` bold.
- Save and load table content.

## Phase 4: Infinite Canvas

- Add pan and zoom.
- Add selection.
- Move and resize `TableBlock`.
- Stylus writes by default.
- Finger pans by default.
- Keyboard edits tables.

## Phase 5: TableBlock Upgrade

- Add bold, italic, and underline.
- Add multiline cells.
- Add rich text paste.
- Add image paste.
- Add inline LaTeX formula support.

## Phase 6: Block-Level Ink Anchoring

- Allow strokes to anchor to a `TableBlock`.
- Moving a table moves anchored strokes.
- Do not implement cell-level anchoring.

## Phase 7: Exports

- Export PDF.
- Export HTML plus assets.
- Export Markdown plus assets.
- Export native zip-based `.ticnote` archives.

## Phase 8: Performance

- Add a stress document generator.
- Optimize for many strokes, many tables, and images.
- Avoid Compose recomposition on every stylus point.

## Phase 9: Beta UX

- Add toolbars.
- Add eraser and select tools.
- Add table toolbar.
- Add recent notes.
- Add autosave.
- Add crash recovery.
- Add settings for stylus/finger behavior and pressure curve.

## Phase 10: Release

- Add signed APK build.
- Add AAB build.
- Add GitHub Release workflow.
- Add changelog.
- Add license audit.
- Add third-party notices.
- Draft privacy policy.
