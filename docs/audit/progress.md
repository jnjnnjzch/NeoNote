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
- Status: IN PROGRESS
- Date: 2026-05-28
- Commit: pending
- Scope: preserve Jetpack Ink path/pressure behavior, expose S Pen diagnostics in Debug Center, and avoid per-point Compose recomposition where possible.
- Hardware note:
  - `DEVICE_VERIFICATION_REQUIRED`: physical Samsung S Pen feel/latency/pressure response verification.
