# NeoNote OneNote Logic Reset Progress

## Milestone 0 - Deprecation Boundary
- Status: PASS
- Date: 2026-05-28
- Notes: standalone `TableBlock` default path deprecated; legacy compatibility retained.

## Milestone 1 - TextContainer Foundation
- Status: PASS
- Date: 2026-05-28
- Notes: TextContainer model and basic canvas editor implemented.

## Milestone 2 - Inline Table in TextContainer
- Status: PASS
- Date: 2026-05-28
- Notes: table workflow moved into TextContainer inline editor with required keyboard/multiline behaviors.

## Milestone 3 - Visual Baseline
- Status: PARTIAL_ACCEPTED / VISUAL_BASELINE_LOCKED
- Date: 2026-05-28
- Notes: accepted with recorded visual debt; do not loop on Milestone 3.5 polish.

## Milestone 4 - Normal/Debug Split
- Status: PASS
- Date: 2026-05-28
- Commit: pending
- Scope: `AppMode` split, `NormalEditorScreen`, `DebugCenterScreen`, mode-aware navigation, debug leakage removal from Normal Mode.
- Validation:
  - `.\gradlew.bat :app:assembleDebug` -> PASS
  - `.\gradlew.bat :app:testDebugUnitTest` -> PASS

## Milestone 5 - True Infinite Canvas
- Status: PASS
- Date: 2026-05-28
- Commit: pending
- Scope: single canvas transform and shared coordinates for TextContainer, ink, image, and formula with persistence/no-drift tests.
- Progress:
  - Added explicit `canvasToScreenX/Y` and `screenToCanvasX/Y` APIs in `CanvasTransformMapper`.
  - Added no-drift roundtrip tests for repeated screen/canvas coordinate conversion.
  - Added serializer test to preserve shared canvas coordinates across `TextContainerBlock`, `ImageBlock`, and `TableBlock`.
- Validation:
  - `.\gradlew.bat :app:assembleDebug` -> PASS
  - `.\gradlew.bat :app:testDebugUnitTest` -> PASS

## Milestone 6 - Lasso + Multi-Select Group Move
- Status: PASS
- Date: 2026-05-28
- Commit: pending
- Scope: lasso-select ink strokes, select TextContainer + ink together, and move selected group in Normal Mode without automatic anchoring behavior.
- Completed:
  - Added lasso rectangle gesture for ink selection in selection mode.
  - Added multi-select state (`TextContainer` + selected ink strokes) and group move drag behavior.
  - Stopped default auto-anchoring of newly written ink (legacy anchor read compatibility retained).
- Validation:
  - `.\gradlew.bat :app:assembleDebug` -> PASS
  - `.\gradlew.bat :app:testDebugUnitTest` -> PASS

## Milestone 7 - S Pen Inking Preservation
- Status: PASS (with device verification sub-item)
- Date: 2026-05-28
- Commit: pending
- Scope: preserve Jetpack Ink path/pressure behavior, expose S Pen diagnostics in Debug Center, and avoid per-point Compose recomposition where possible.
- Hardware note:
  - `DEVICE_VERIFICATION_REQUIRED`: physical Samsung S Pen feel/latency/pressure response verification.
- Validation:
  - `.\gradlew.bat :app:assembleDebug` -> PASS
  - `.\gradlew.bat :app:testDebugUnitTest` -> PASS

## Milestone 8 - Rich Content: Image + LaTeX
- Status: PASS
- Date: 2026-05-28
- Commit: pending
- Scope: real clipboard image paste flow, image block handling, and LaTeX source/render path hardening.
- Completed:
  - Added real clipboard image paste into document as `ImageBlock`.
  - Added `ImageBlock` on-canvas rendering with shared canvas coordinates/transform.
  - Added `FormulaBlock` model (`source` + `rendered`) with on-canvas rendering and persistence.
  - Added serializer test coverage for `FormulaBlock` source/render round-trip.
- Validation:
  - `.\gradlew.bat clean :app:assembleDebug` -> PASS
  - `.\gradlew.bat :app:testDebugUnitTest` -> PASS

## Milestone 9 - Layout-Preserving Export
- Status: PASS
- Date: 2026-05-28
- Commit: pending
- Scope: enrich `.ticnote` + HTML/Markdown/PDF export for table/image/formula/ink layout fidelity.
- Completed:
  - Markdown export now emits block layout coordinates and formula source/render pairs.
  - HTML export now emits positioned canvas blocks (table/image/formula) with layout CSS.
  - PDF export now draws block outlines/content at document coordinates rather than only counts.
  - `.ticnote` manifest updated with layout-preserving export capability metadata.
- Validation:
  - `.\gradlew.bat :app:assembleDebug` -> PASS
  - `.\gradlew.bat :app:testDebugUnitTest` -> PASS

## Milestone 10 - Release Artifact Policy
- Status: IN PROGRESS
- Date: 2026-05-28
- Commit: pending
- Scope: classify installability rules for debug/release/APK/AAB artifacts and add verification metadata/checksums.
