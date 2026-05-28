# NeoNote Current Function and OneNote Logic Audit

## 0. Executive Summary
- Current product status: `PRODUCT_PROTOTYPE`
- Biggest mismatch from intended OneNote-like logic: current table model is a standalone `TableBlock` overlay, not a table embedded inside a movable text container.
- Biggest missing feature: OneNote-like unified selection/move model (select ink + text container together and move as a group).
- Whether infinite canvas is real: `PARTIAL_CANVAS` (has pan/zoom transform and doc coordinates for table, but no full unified object model and no robust selection model).
- Whether current table model should be replaced: yes, should be wrapped/replaced by `TextContainer` model.

## 1. Evidence Table
| Area | Claimed behavior | Actual behavior | Evidence files/classes | Tests | Verdict | Severity |
|---|---|---|---|---|---|---|
| Product shell branding | NeoNote branding | Home title/subtitle/build badge exist; app label is NeoNote | `app/src/main/res/values/strings.xml`, `HomeScreenComponents.kt` | `CahierAppTest.homeScreen_showsNeoNoteBrand...` | PASS | P2 |
| Normal vs Debug split | Clean normal mode + separate debug mode | Debug overlays/controls (pressure panel, debug metrics, stress/export buttons, select toggle) are in main drawing UI | `DrawingCanvas.kt` (`InkDebugOverlay`, `PressureTestPanel`, top bar buttons) | No dedicated mode-split tests | FAIL | P0 |
| Table+ink entry path | Normal user can start quickly | Home has visible `New Ink Note` / `New Table Note`; both route to drawing note | `HomeScreenComponents.kt`, `CahierHomeScreen.kt` | `CahierAppTest.newTableNote...` | PASS | P2 |
| Infinite canvas | True shared canvas | `CanvasTransform` exists; finger pan+pinch implemented; table uses doc coords + transform | `CanvasTransform.kt`, `DrawingCanvas.kt`, `DrawingSurface.kt` | `CanvasTransformMapperTest` | PARTIAL | P0 |
| Table model | Table inside text container | Standalone `TableBlock` in `CanvasPage.blocks`, directly rendered overlay | `DocumentModel.kt`, `DrawingCanvas.kt`, `DrawingCanvasViewModel.kt` | Serializer + UI tests for block | FAIL | P0 |
| Table editing | cell edit/tab/format/multiline | Cell editing works; Tab next cell + last-cell append row; Ctrl+B/I/U implemented; multiline enabled (`singleLine=false`) | `TableBlockEditor` in `DrawingCanvas.kt`, `DrawingCanvasViewModel.kt` | `TableBlockEditorUiTest` (edit/tab/Ctrl+B only) | PARTIAL | P1 |
| Table visual style | Light note-like table | Current is dark debug-like surface with obvious resize handle and strong borders | `TableBlockEditor` styling in `DrawingCanvas.kt` | No visual acceptance tests | FAIL | P1 |
| Ink low-latency path | Preserve Jetpack Ink/Cahier path | Uses `InProgressStrokes` + Cahier stroke renderer pipeline | `DrawingSurface.kt` | Existing drawing tests, no perf benchmark proof | PARTIAL | P1 |
| Pressure behavior | Pressure affects stroke output | Pressure captured and mapped to brush size; `getCurrentBrushWithPressureCurve` used for in-progress brush | `DrawingCanvasViewModel.kt`, `PressureCurveMapper.kt`, `DrawingCanvas.kt` | `PressureCurveMapperTest` | PARTIAL | P1 |
| Predicted points persistence | Predicted points never saved | No explicit predicted-point persistence in app logic; saves finalized strokes from `onStrokesFinished` | `DrawingSurface.kt`, `DrawingCanvasViewModel.onStrokesFinished` | No dedicated predicted-point test | NOT VERIFIED | P1 |
| Ink/table independence | Ink independent from table | Base model independent; but automatic block-level anchoring exists and moves strokes with table | `DocumentModel.StrokeAnchor`, `DrawingCanvasViewModel.anchorNewStrokes/recomputeStrokeTranslations` | `StrokeIdMapperTest` (id remap only) | PARTIAL | P1 |
| Lasso/select/multi-select | OneNote-like mixed selection | Selection mode toggle exists, but lasso/multi-select move of ink+container not implemented | `DrawingSurface.kt` (`isSelectionMode` placeholder), `DrawingCanvas.kt` | No tests | FAIL | P0 |
| Images | Real clipboard image paste | Clipboard URI path imports file into app assets; can attach to selected cell or add canvas `ImageBlock` | `DrawingCanvas.kt` paste handler, `DrawingCanvasViewModel.importImageFromUriToAssets` | No end-to-end instrumentation for clipboard | PARTIAL | P1 |
| LaTeX | Visual render in-app | LaTeX is parsed/stored and exported as markup/span text; no real math renderer in canvas | `DrawingCanvasViewModel.extractLatex`, `ExportComposer.kt` | `ExportComposerTest` | FAIL | P1 |
| Export | Preserve layout table+ink | Markdown/HTML contain table text/images and stroke count summary; PDF is summary text, not visual composition; `.ticnote` includes doc/manifest/ink summary/assets | `ExportComposer.kt`, `TicNoteArchiveWriter.kt`, `DrawingCanvasViewModel.exportPdf` | `ExportComposerTest`, `TicNoteArchiveWriterTest` | PARTIAL | P0 |
| Release/install policy | Clear installable artifact policy | Debug installable naming + unsigned/AAB non-installable naming + policy checks present | `.github/workflows/ci.yml` | workflow policy steps | PARTIAL | P1 |

## 2. OneNote-like Logic Compliance
### Canvas
- Target behavior: one freeform canvas, stable coordinates, pan/zoom, all object types share coordinate model.
- Current implementation: has `CanvasTransform(scale/panX/panY)`, finger pan/zoom, table positioned by doc coordinates transformed to screen; strokes rendered with transform matrix.
- Mismatch: no complete unified object interaction model (selection/multi-select/group move is missing); only table and strokes partially share transform behavior.
- Recommended correction: keep `CanvasTransform`, but move all editable objects under one selection/transform contract.

### Text container
- Target behavior: movable text container is the primary note unit.
- Current implementation: no `TextContainer` block in drawing model.
- Mismatch: missing core OneNote-like note-box abstraction.
- Recommended correction: add `TextContainer` as first-class canvas object.

### Table inside text container
- Target behavior: table is inline content in text flow.
- Current implementation: table is standalone `TableBlock`.
- Mismatch: cannot have ordinary text before/after table in same container.
- Recommended correction: embed/wrap table editor inside rich text container model.

### Ink layer
- Target behavior: freehand layer, independent from text structure.
- Current implementation: independent stroke list exists; optional anchoring metadata also exists.
- Mismatch: automatic anchor behavior adds complexity not required by target.
- Recommended correction: keep freehand independence; de-emphasize automatic anchoring in normal mode.

### Selection and movement
- Target behavior: select container, lasso ink, multi-select both, move together.
- Current implementation: selection mode toggle exists but no implemented lasso/multi-select group movement.
- Mismatch: major interaction gap.
- Recommended correction: implement simple multi-select/group transform before advanced anchoring.

### Debug separation
- Target behavior: debug tools isolated from normal mode.
- Current implementation: debug UI leaks into default drawing screen.
- Mismatch: normal user sees debug controls.
- Recommended correction: split Normal Mode and Debug Mode entry points.

## 3. Table Model Audit
- Is table inside a text container? **No**.
- If no, what is current model? `CanvasPage.blocks` contains standalone `TableBlock` with absolute x/y/width/height; rendered via `TableBlockEditor`.
- What would need to change to make it OneNote-like?
  1. Add `TextContainer` block with rich text document.
  2. Represent table as inline element inside container content.
  3. Move/resize at container level; inline table follows content flow.
- Which existing code can be reused?
  - Cell model and editor behaviors: `TableCell`, Tab logic, Ctrl+B/I/U handling in `TableBlockEditor`.
  - Serializer framework: `DocumentSerializer` + versioned `TicDocument`.
- Which existing code should be discarded or deprecated?
  - Standalone table drag/resize handles in normal mode.
  - Automatic per-table anchor coupling as primary movement strategy.

## 4. Infinite Canvas Audit
- Classification: `PARTIAL_CANVAS`.
- Reason:
  - Yes: transform model + pan/zoom + table doc coords + stroke transform rendering.
  - No: missing full object model parity and OneNote-like selection semantics across object types.

## 5. Ink and Selection Audit
- Keep/simplify/remove anchoring decision: **simplify/deprecate automatic anchoring in Normal Mode**.
- Rationale: target behavior only needs freehand ink + container selection + multi-select group move; current auto anchoring (including legacy index-range fallback) is extra complexity and drift risk.

## 6. Normal Mode vs Debug Mode Audit
Debug features currently leaking into Normal Mode:
- Ink debug metrics overlay (`InkDebugOverlay`).
- Pressure test panel (`PressureTestPanel`).
- Pressure curve slider in top bar.
- Stress generation button.
- Export test-oriented top action placement.
- Selection mode debug toggle style (`Select:On/Off`).

## 7. Feature Completion Matrix
| Feature | Status | Evidence | Missing | Recommended next action |
|---|---|---|---|---|
| Home / product shell | PASS | `HomeScreenComponents.kt`, `strings.xml` | polish consistency | keep |
| Normal Mode | FAIL | debug controls in `DrawingCanvasTopBar` | no clean user mode | split mode |
| Debug Mode | PARTIAL | debug UI exists but mixed | explicit gating | separate route/flag |
| Text container | FAIL | absent in `DocumentModel` | core abstraction | add `TextContainer` |
| Table inside text container | FAIL | standalone `TableBlock` | inline table model | redesign table ownership |
| Table editing | PARTIAL | `TableBlockEditor`, tests | I/U tests, rich context | expand test coverage |
| Table visual design | FAIL | dark debug-like editor | note-like style missing | redesign visuals |
| S Pen inking | PARTIAL | `InProgressStrokes` path | latency benchmarks absent | add perf evidence |
| Pressure rendering | PARTIAL | pressure->size mapping | no visual acceptance tests | add deterministic tests/protocol |
| Ink lasso select | FAIL | selection placeholder only | no lasso selection | implement lasso |
| Multi-select move | FAIL | absent | no group transform | implement group selection |
| Infinite canvas | PARTIAL | transform + pan/zoom | incomplete interaction model | unify object model |
| Image paste | PARTIAL | clipboard URI import path | e2e test + UX | add robust paste tests |
| LaTeX rendering | FAIL | parse/store/export only | no true renderer | integrate renderer |
| Export | PARTIAL | md/html/ticnote/pdf summary | PDF/layout fidelity | visual export compositor |
| Release artifact policy | PARTIAL | CI naming/policy checks | emulator smoke flaky/manual | keep policy + stable smoke strategy |

## 8. Recommended Redesign
Do not implement in this audit. Recommended direction:
- Replace standalone `TableBlock` with `TextContainer` containing rich text + inline tables (or wrap current `TableBlock` under `TextContainer` as transition).
- Use simple multi-select movement for `TextContainer + InkStroke`.
- Avoid automatic ink-to-cell anchoring; keep anchoring out of Normal Mode.
- Keep Debug tools behind explicit Debug Mode.

## 9. Next Implementation Plan
1. Product shell cleanup (clear Normal Mode vs Debug Mode entry).
2. Introduce `TextContainer` abstraction in document model.
3. Place table inside `TextContainer` flow.
4. Redesign table UI to note-like light style.
5. Complete true canvas interaction model over shared coordinates.
6. Implement lasso/select + multi-select group move.
7. Isolate debug metrics/stress/build diagnostics to Debug Mode only.
8. Polish images/formulas/export fidelity for user-grade output.
