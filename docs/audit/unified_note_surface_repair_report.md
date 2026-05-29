# NeoNote Unified Note Surface Repair Report

Date: 2026-05-29

Current status: `PRODUCT_PROTOTYPE_NOT_ONE_NOTE_LIKE`
Canvas status: `BROKEN_CANVAS` (`infinite_canvas=FAIL` until viewport zoom behavior is fully validated)

This repair pack focused on unified interaction corrections only.

## Implemented in this pack

1. Unified note entry direction
- Home now routes primary creation through one unified note path (`addUnifiedNote`) to the drawing/unified canvas editor.
- Existing `Table Starter` remains as a template path of the same unified editor.
- Note open/edit now routes to unified drawing editor path.

2. Tap-to-type default behavior (baseline)
- Finger tap on canvas now places/creates the primary `TextContainer` at tap location and requests text focus.
- This provides a natural tap-to-type path without requiring tool preselection.

3. Finger vs stylus behavior
- Finger input is consumed away from inking path by default in normal inking state.
- One-finger drag/pan + pinch zoom remains available via canvas transform path.

4. Selection/Lasso discoverability
- Toolbar label updated to `Select/Lasso`.
- Selection controls remain visible in normal mode.

5. Formula reachable in normal flow
- Formula now uses a normal-mode insert dialog with editable source input.

6. Clipboard paste behavior
- Added Ctrl+V handling in normal editor root.
- Added `More > Paste` fallback path.
- Paste routes:
  - inline table cell focused -> inline cell image attach path
  - otherwise -> canvas `ImageBlock`

7. Image object interactions
- Image blocks now support finger tap selection.
- Selected image supports drag move.
- Selected image provides a resize handle with drag resize.

8. Export reachability
- Export now opens a user-facing export options dialog (PDF/HTML/Markdown/.ticnote).
- Export result panel still shows generated file paths.

## .ticnote ink payload
- Archive includes `ink/strokes.json` payload and manifest points to it.
- This is stronger than count-only summary, but full restore verification remains pending.

## Still not complete / honest gaps

- Pinch/pan correctness is a hard gate. Do not proceed to image/formula/export polish until viewport zoom is fixed.
- Pressure sensitivity is code-path enabled but still requires physical Samsung S Pen verification for user-visible confidence.
- Formula rendering quality is still not full math-grade renderer in all contexts.
- Export retrieval/share UX is improved but still needs full Android share/open integration hardening.
- Unified object model behavior is improved but not yet fully OneNote-like across all edge cases.

## Validation run

Attempted:
- `.\gradlew.bat :app:assembleDebug`
- `.\gradlew.bat :app:testDebugUnitTest`

Result:
- Both blocked in this environment due Gradle wrapper download network restriction:
  - `Permission denied: connect` to `services.gradle.org`.

No false PASS claimed.
