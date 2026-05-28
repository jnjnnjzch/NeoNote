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
- Status: IN PROGRESS
- Date: 2026-05-28
- Commit: pending
- Scope: single canvas transform and shared coordinates for TextContainer, ink, image, and formula with persistence/no-drift tests.
