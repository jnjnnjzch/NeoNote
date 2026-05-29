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
- Status: PASS
- Date: 2026-05-28
- Commit: pending
- Scope: classify installability rules for debug/release/APK/AAB artifacts and add verification metadata/checksums.
- Completed:
  - Added release artifact policy classifier for debug APK / signed release APK / unsigned APK / AAB installability.
  - Added SHA-256 checksum metadata generation for artifact verification.
  - Added unit tests for policy classification and checksum metadata shape.
- Validation:
  - `.\gradlew.bat :app:assembleDebug` -> PASS
  - `.\gradlew.bat :app:testDebugUnitTest` -> PASS

## Milestone 11 - Final Audit
- Status: PASS
- Date: 2026-05-28
- Commit: pending
- Scope: generate final audit artifacts and avoid overstating completion claims.
- Generated:
  - `docs/audit/onenote_logic_reset_report.md`
  - `docs/audit/neonote_post_reset_status.json`
- Validation:
  - `.\gradlew.bat :app:assembleDebug` -> PASS
  - `.\gradlew.bat :app:testDebugUnitTest` -> PASS

## P0 Reachability Repair Pack
- Status: PARTIAL
- Date: 2026-05-29
- Scope: repair normal-mode feature reachability/wiring without adding new major features.
- Completed:
  - Normal editor topbar cleaned for user-facing controls.
  - Export now produces visible result panel with explicit generated file paths.
  - New Table Note guardrail test added: TextContainer inline table path only; no default standalone TableBlock.
  - `.ticnote` now includes `ink/strokes.json` payload in archive.
  - Image paste routing split for inline table focus vs canvas image block fallback.
  - Debug-only canvas verification panel added; normal mode gets reset-view action.
  - Selected TextContainer outline now follows selection state for clearer move feedback.
- Validation:
  - `.\gradlew.bat :app:assembleDebug` -> FAIL (environment network blocker downloading Gradle wrapper)
  - `.\gradlew.bat :app:testDebugUnitTest` -> FAIL (same blocker)
