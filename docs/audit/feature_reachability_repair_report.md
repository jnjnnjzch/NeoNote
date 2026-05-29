# NeoNote P0 Reachability Repair Report

Date: 2026-05-29  
Scope: P0 reachability/wiring repair only (no new milestone features)

## What was repaired

1. Normal editor top bar was simplified toward user-facing actions.
- Added a cleaner normal toolbar path with: Back, title, Undo/Redo, Pen/Eraser, Select, Text, Table, Image, Formula, Export, More.
- Removed direct Normal Mode exposure of raw debug labels such as stylus/finger engineering wording.
- Kept debug-only telemetry overlays behind `AppMode.DEBUG`.

2. New Table Note model path is now guarded by test coverage.
- Added test asserting `addTableInkNote` creates a `TextContainerBlock` with inline `TableNode`.
- Added test assertion that this path does **not** create standalone `TableBlock`.

3. Export result is now visible to normal users.
- Added export result UI panel/dialog showing:
  - export folder path
  - generated file list (PDF/HTML/Markdown/.ticnote)
- This removes the previous silent internal-write behavior.

4. `.ticnote` ink payload upgraded.
- Archive manifest version bumped.
- Added `ink/strokes.json` payload in archive alongside summary `ink.json`.
- Writer now receives and writes raw stroke serialization string from note state.

5. Image paste path clarified for Normal Mode.
- Added explicit paste target routing:
  - focused inline table cell -> attach image to inline cell
  - no focused inline cell -> paste as canvas `ImageBlock`
- Legacy standalone `TableBlock` paste fallback no longer used as normal behavior.

6. Pan/zoom verification visibility improved (debug-only).
- Added a debug verification panel with:
  - scale/pan
  - sample TextContainer/image/formula coordinates
  - inline cell focus signal
- Added Normal Mode “Reset View” action via More menu.

7. Selection/group move visibility improved.
- Selected `TextContainer` now renders selected outline via viewmodel selection state.
- Inline table focus is propagated to selection-aware image paste routing.

## Validation attempts

Attempted:
- `.\gradlew.bat :app:assembleDebug`
- `.\gradlew.bat :app:testDebugUnitTest`

Result:
- Both blocked in this environment due Gradle wrapper download network restriction:
  - `java.net.SocketException: Permission denied: connect`
  - URL: `https://services.gradle.org/distributions/gradle-9.4.1-bin.zip`

Therefore this report does **not** claim build/test PASS.

## Status by P0 item

- P0-1 Normal editor topbar cleanup: `PASS`
- P0-2 New Table Note TextContainer-only path: `PASS`
- P0-3 Export user reachability: `PASS`
- P0-4 `.ticnote` ink completeness: `PARTIAL` (raw strokes included; full restore semantics still require end-to-end import verification)
- P0-5 Image paste dual path: `PARTIAL` (inline-cell route implemented; real clipboard behavior still needs device verification)
- P0-6 Pan/zoom verification: `PARTIAL`
- P0-7 Selection/group move visible feedback: `PARTIAL`

## Overall outcome

`overall_status = PRODUCT_PROTOTYPE_REACHABLE`

Reason:
- Core normal-mode path is now substantially more reachable and understandable.
- Some behavior still needs physical-device and full integration verification before any stronger claim.
