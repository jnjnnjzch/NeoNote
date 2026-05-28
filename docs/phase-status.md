# NeoNote Phase Status

Last updated: 2026-05-28

## Overall

- Product name: `NeoNote`
- Roadmap progress: `Phase 0 -> Phase 10` implemented in branch `codex/phase-0-1-foundation`

## Per-Phase Status

1. Phase 0 (Done): CI workflows, architecture docs, debug APK pipeline.
2. Phase 1 (Done): inking pipeline audit and live debug overlay metrics.
3. Phase 2 (Done): versioned document model (`Document > CanvasPage > Blocks + InkLayer`) and serializer tests.
4. Phase 3 (Done): TableBlock v0.1 (`3x3`, text input, `Tab` navigation, last-cell `Tab` row append, `Ctrl+B`, persistence).
5. Phase 4 (Done): table move/resize interactions and persisted block frame.
6. Phase 5 (Done): cell formatting (`bold/italic/underline`), multiline editing, rich text fields for image and LaTeX parsing.
7. Phase 6 (Done): block-level ink anchoring and move-follow behavior.
8. Phase 7 (Done): exports (`PDF`, `HTML+assets`, `Markdown+assets`, `.ticnote` archive).
9. Phase 8 (Done): stress document generator and stylus debug update throttling.
10. Phase 9 (Done): beta toolbar controls, eraser/select toggles, stylus/finger behavior settings, pressure curve setting.
11. Phase 10 (Done): release workflow (`APK`, `AAB`, optional signing, GitHub Release), changelog, license/notice/privacy drafts.

## Current Feature Snapshot

- Low-latency Jetpack Ink drawing path retained from Cahier.
- Ink debug telemetry overlay (pressure/tool/event rate/finalized/cancel/palm).
- Structured table layer with editable TableBlock and keyboard formatting shortcuts.
- Ink and table layers persisted separately.
- Block-level anchored strokes that follow table movement.
- Export bundle support for user data portability.
- CI for build/test/artifacts and release pipeline scaffolding.
