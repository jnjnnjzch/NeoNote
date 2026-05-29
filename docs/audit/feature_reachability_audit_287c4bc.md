# NeoNote Feature Reachability and Wiring Audit (287c4bc)

Branch: `codex/phase-0-1-foundation`  
Commit: `287c4bc`  
Audit type: **AUDIT ONLY** (no implementation changes)

Build/test execution attempt:
- `.\gradlew.bat :app:assembleDebug` -> NOT RUNNABLE in this environment (cannot download Gradle distribution; `java.net.SocketException: Permission denied: connect` to `services.gradle.org`).
- `.\gradlew.bat :app:testDebugUnitTest` -> same blocker.

Bottom line:
- This build looks like an **engineering demo / prototype** with many capabilities present in code, but user-facing reachability is inconsistent.
- If USABLE_ALPHA was claimed, from Normal Mode user flow evidence this is **not currently reachable as a product experience**.
- Formula display appears to be the one area that is relatively more complete, but still has export/wiring caveats.

## Functional Status Matrix
| Feature | Expected user path | Actual user path found | Implementation exists? | UI wired? | Runtime likely works? | Tests/evidence | Verdict | Root cause |
|---|---|---|---|---|---|---|---|---|
| A1 App opens as NeoNote not Cahier | Launch app shows NeoNote identity | `strings.xml` app name is NeoNote, but package/class/navigation still `cahier` | Yes (mixed) | Yes | Likely | `strings.xml`, `MainActivity`, `CahierNavGraph` | UI_UNDISCOVERABLE | hidden behind legacy path |
| A2 Home shows New/Recent/Settings clearly | Home first screen with explicit entry actions | Home has top brand + buttons New Ink/New Table/Open Last; notes list; nav includes Settings | Yes | Yes | Likely | `HomePane`, `NoteList` | PASS | unclear from code |
| A3 Create Table+Ink note from Home | Tap New Table from Home | `btn-new-table` -> `addTableInkNote()` -> Drawing route | Yes | Yes | Likely | `HomeScreenComponents.kt`, `HomeScreenViewModel.kt` | PASS | unclear from code |
| A4 Open normal note without Debug | Home note tap opens note editor | Text note -> text canvas; Drawing note -> drawing canvas NORMAL | Yes | Yes | Likely | `HomePane` routing | PASS | unclear from code |
| B1 Normal Mode exists | AppMode NORMAL route | `AppMode.NORMAL` and default route arg exist | Yes | Yes | Likely | `AppMode.kt`, `CahierNavGraph.kt` | PASS | unclear from code |
| B2 Debug Mode exists | Explicit debug route/entry | `AppMode.DEBUG` route + Debug Center screen | Yes | Yes | Likely | `AppMode.kt`, `DebugCenterScreen.kt` | PASS | unclear from code |
| B3 Normal hides debug metrics/raw/stress/build jargon | Normal editor should be clean | Ink debug overlay gated by `if (appMode==DEBUG)`; but topbar still shows `Select`, `Stylus/Finger` mode toggles, Export, Formula etc. | Partial | Partial | Mixed | `DrawingCanvas.kt` | UI_UNDISCOVERABLE | UI exists but wrong route |
| B4 Debug Center intentional route | Settings/About route to Debug Center | Settings has Debug switch + Open Debug Center button (enabled only DEBUG) | Yes | Yes | Likely | `SettingsScreen.kt` | PASS | unclear from code |
| B5 Debug tools not mixed into normal editing | Debug labs should not clutter normal | Some debug/advanced controls remain in shared topbar; Debug Center exists but separation incomplete | Partial | Partial | Mixed | `DrawingCanvasTopBar` | HIDDEN_IN_DEBUG | UI exists but wrong route |
| C1 New Table Note creates TextContainer | New Table Note should create TextContainer block | `addTableInkNote` creates `TextContainerBlock` | Yes | Yes | Likely | `HomeScreenViewModel.kt` | PASS | unclear from code |
| C2 Paragraph before table | Auto paragraph-before exists/editable | Creates `ParagraphNode("")` before table; editor bound to it | Yes | Yes | Likely | `HomeScreenViewModel.kt`, `TextContainerEditor` | PASS | unclear from code |
| C3 Inline table exists in TextContainer | Inline table in same container | `TableNode` inserted and edited in `InlineTableNodeEditor` | Yes | Yes | Likely | `DrawingCanvas.kt` | PASS | unclear from code |
| C4 Paragraph after table | Auto paragraph-after exists/editable | Creates and binds second paragraph | Yes | Yes | Likely | same as above | PASS | unclear from code |
| C5/C6 Edit paragraphs before/after | User edits both paragraphs | TextFields wired to `updateTextContainerParagraphAt(0/1)` | Yes | Yes | Likely | `DrawingCanvas.kt`, ViewModel methods | PASS | unclear from code |
| C7 Drag/move whole TextContainer | Gesture move container | drag gesture on container -> `moveTextContainerBy` | Yes | Yes | Likely | `TextContainerEditor` pointerInput | PASS | unclear from code |
| C8 Moving container moves inline table | Inline table should move with container | Table is nested in container content so moves together | Yes | Yes | Likely | model nesting | PASS | unclear from code |
| C9 Legacy standalone TableBlock not default | Normal flow should prefer TextContainer table | Default flow uses TextContainer; legacy `TableBlockEditor` still rendered if block exists | Yes | Partial | Likely | `DrawingSurfaceWithTarget` still renders `TableBlockEditor` | IMPLEMENTED_NOT_WIRED | hidden behind legacy path |
| D1 Tap/select cell | Select table cell in inline table | Focus on cell updates active cell | Yes | Yes | Likely | `InlineTableNodeEditor` | PASS | unclear from code |
| D2 Edit cell text | Type in cell updates model | onValueChange -> updateInlineTableCell | Yes | Yes | Likely | ViewModel cell updates | PASS | unclear from code |
| D3 Tab next cell | Keyboard tab navigation | Implemented in onPreviewKeyEvent | Yes | Yes | Likely | `InlineTableNodeEditor` | PASS | unclear from code |
| D4 Tab last appends row | Last cell tab adds row | Implemented with append row call | Yes | Yes | Likely | inline editor code | PASS | unclear from code |
| D5/D6/D7 Ctrl+B/I/U | Keyboard style toggles | Implemented in key handling | Yes | Yes | Likely on hardware keyboard | `InlineTableNodeEditor`, `TableBlockEditorUiTest`(legacy editor only) | NOT_VERIFIED | needs physical device |
| D8 Multiline cells | Multiline editing | `singleLine=false` | Yes | Yes | Likely | inline/legacy editors | PASS | unclear from code |
| D9 Add/delete row/column from UI | Explicit row/column controls | Append row exists via Tab; no explicit add/delete row/col UI controls found | Partial | No | No | no button handlers found | IMPLEMENTED_NOT_WIRED | model exists but no UI |
| D10 Note-like visual quality | Not spreadsheet/debug-like | Visual debt acknowledged; still form-like traces | Partial | Yes | Mixed | visual tokens + known debt | UI_UNDISCOVERABLE | broken renderer |
| E1 S Pen write in Normal Mode | Stylus writes by default | stylus-write toggle + DrawingSurface raw events | Yes | Yes | Likely | `stylusWritesByDefault`, DrawingSurface wiring | NOT_VERIFIED | needs physical device |
| E2 Ink shown while writing | Real-time stroke rendering | DrawingSurface handles strokes list + renderer | Yes | Yes | Likely | `DrawingSurfaceWithTarget` | NOT_VERIFIED | needs physical device |
| E3 Ink persisted save/reopen | Save and reopen note keeps strokes | `saveStrokes` and repository updates exist | Yes | Yes | Likely | ViewModel persistence | NOT_VERIFIED | unclear from code |
| E4 Pressure captured | Pressure value sampled | `onRawMotionEvent` records pressure | Yes | Yes | Likely | ink debug metrics code | PASS | unclear from code |
| E5 Pressure affects rendering | pressure influences brush size/render | `mapPressureToScale`, pressure curve brush path present | Yes | Partial | Likely | `getCurrentBrushWithPressureCurve` path + tests mapper | NOT_VERIFIED | needs physical device |
| E6 Finger/stylus separation | Finger pan, stylus write separated | finger-only pan/zoom in pointerInteropFilter + stylus mode toggles | Yes | Yes | Likely | MotionEvent tool-type checks | PASS | unclear from code |
| E7 Latency/feel | Real hardware feel | cannot be validated in code | N/A | N/A | N/A | none | DEVICE_VERIFICATION_REQUIRED | needs physical device |
| F1/F2 Pan + pinch zoom | Finger gestures in Normal Mode | pointerInteropFilter implements one-finger pan + two-finger zoom | Yes | Yes | Likely | `DrawingSurfaceWithTarget` | PASS | unclear from code |
| F3 TextContainer uses canvas coordinates | Shared transform coordinates | docToScreen/canvasToScreen used for placement/move | Yes | Yes | Likely | mapper usage | PASS | unclear from code |
| F4 Ink uses same coordinate system | Ink should align with transformed content | Stroke drawing plus manual translations; transform not globally applied to all rendering paths | Partial | Partial | Mixed | transform state local + translations | WIRED_BUT_BROKEN | broken state update |
| F5 Image/Formula share coordinate system | Blocks should obey same transform | Image/formula positioned with transform mapping | Yes | Yes | Likely | `docToScreenX/Y` for blocks | PASS | unclear from code |
| F6 No drift after pan/zoom | Content/ink stable after gestures | Mapper tests exist, but full runtime no-drift path uncertain | Partial | Partial | Unknown | `CanvasTransformMapperTest` only math-level | NOT_VERIFIED | unclear from code |
| F7 Save/reopen preserves coordinates | coords persisted in document | block coords persisted in document text | Yes | Yes | Likely | Document model + persistDocument | PASS | unclear from code |
| F8 code-only gestures | if not reachable mark implemented_not_wired | gestures are reachable via normal canvas touch | Yes | Yes | Likely | runtime route exists | PASS | unclear from code |
| G1 Selection mode in Normal | user can enter selection | topbar Select button always visible | Yes | Yes | Likely | `setSelectionMode` wiring | PASS | unclear from code |
| G2 Lasso ink selection | draw lasso to select strokes | onSelectionLasso -> selectStrokesInScreenRect(rect-based) | Yes | Yes | Likely | `DrawingSurface` callback | PASS | unclear from code |
| G3 Selection feedback | selected strokes visibly indicated | hasSelection state used; exact visual feedback uncertain | Partial | Partial | Unknown | no explicit dedicated UX proof | NOT_VERIFIED | unclear from code |
| G4 Select TextContainer | toggle text container selection | `Text:On/Text:Off` in selection mode | Yes | Yes | Likely | topbar + state flow | PASS | unclear from code |
| G5 Multi-select TextContainer+ink | select both kinds simultaneously | stroke set + text flag can coexist | Yes | Yes | Likely | `refreshSelectionState` | PASS | unclear from code |
| G6 Move selected group together | move both together | `moveSelectionBy` moves container + selected stroke translations | Yes | Yes | Likely | ViewModel moveSelectionBy | PASS | unclear from code |
| G7 Not reliant on auto anchoring | should be explicit selection model | explicit selection exists; legacy anchoring still present in compatibility path | Partial | Partial | Mixed | anchor functions still in VM | IMPLEMENTED_NOT_WIRED | hidden behind legacy path |
| G8 Auto anchoring hidden/legacy | Normal should not depend on it | comment says legacy; code still computes anchors/translations | Partial | Partial | Mixed | `recomputeStrokeTranslations` etc | IMPLEMENTED_NOT_WIRED | hidden behind legacy path |
| H1 Paste real clipboard image | user paste image from clipboard in normal | topbar `Paste Img` triggers clipboard URI import | Yes | Yes | Device/OS dependent | `pasteImageBlockFromClipboard()` | NOT_VERIFIED | needs physical device |
| H2 Paste into TextContainer/table | if intended should work | table paste flow exists only around legacy `TableBlockEditor` + selected cell route | Partial | Partial | Mixed | `onPasteImage` path tied to standalone table | WIRED_BUT_BROKEN | UI exists but wrong route |
| H3 Paste as ImageBlock on canvas | clipboard image becomes block | implemented via `addImageBlock` | Yes | Yes | Likely | VM methods | PASS | unclear from code |
| H4 Image copied to app assets | should not depend on external URI | imported to `filesDir/note_assets` | Yes | Yes | Likely | `importImageFromUriToAssets` | PASS | unclear from code |
| H5 Persist after reopen | image blocks should persist | document persisted, asset path saved | Yes | Yes | Likely | persistDocument | NOT_VERIFIED | unclear from code |
| H6 Image appears in export | HTML/MD/ticnote should include assets | export includes image blocks and assets | Yes | Yes | Likely | `ExportComposer`, archive writer | PASS | unclear from code |
| I1 Create formula in Normal | explicit formula action in normal | topbar Formula button exists | Yes | Yes | Likely | `addFormulaBlock()` call | PASS | unclear from code |
| I2 Formula source preserved | source retained in model | `FormulaBlock(source, rendered)` | Yes | Yes | Likely | DocumentModel | PASS | unclear from code |
| I3 Formula visually renders | visible rendered formula | rendered currently plain text (`f(x): ...`), not true math layout | Partial | Yes | Works but limited | block rendering text | WIRED_BUT_BROKEN | broken renderer |
| I4 Formula in TextContainer if intended | inline formula node usage | `FormulaNode` exists but no clear UI insertion/wiring | Partial | No | Unknown | no insertion UI path found | IMPLEMENTED_NOT_WIRED | model exists but no UI |
| I5 FormulaBlock on canvas | standalone formula block display | yes, displayed as text block | Yes | Yes | Likely | `formulaBlocks` rendering | PASS | unclear from code |
| I6 Persist after reopen | formula persists | document persisted via serializer | Yes | Yes | Likely | persistDocument | NOT_VERIFIED | unclear from code |
| I7 Formula appears in export | formula in md/html/pdf/ticnote | md/html include source/rendered; pdf prints rendered string | Yes | Yes | Likely | ExportComposer + exportPdf | PASS | unclear from code |
| I8 only feature truly working? | call out if formula is strongest | formula path appears comparatively strongest but still plain-text rendering | N/A | N/A | N/A | aggregate evidence | NOT_VERIFIED | unclear from code |
| J1 Export from Normal Mode | user can export in normal | topbar Export button always available | Yes | Yes | Likely | `exportAllFormats` | PASS | unclear from code |
| J2 Access PDF/HTML/MD/.ticnote | export outputs reachable | writes all 4 formats to internal `filesDir/exports/<ts>` | Yes | Partial | Likely | VM exportAllFormats | UI_UNDISCOVERABLE | model exists but no UI |
| J3 Export path discoverable | user can find output path | no clear user-visible chooser/open/share after export | Partial | No | Unknown | only `_lastExportDirectory` state | UI_UNDISCOVERABLE | model exists but no UI |
| J4 PDF visual fidelity | PDF should preserve table/ink/image/formula visually | current PDF draws block boxes/text summaries; not full visual composition | Partial | Yes | No for layout fidelity | `exportPdf` implementation | WIRED_BUT_BROKEN | broken renderer |
| J5 HTML positioned content | not just counts | HTML includes absolute positioned table/image/formula | Yes | Yes | Likely | `ExportComposer.toHtml` | PASS | unclear from code |
| J6 Markdown contains table/formula source | should include both | yes includes table cells + latex + formulas | Yes | Yes | Likely | `toMarkdown` | PASS | unclear from code |
| J7 .ticnote contains document/assets/ink | archive completeness | has `document.json`, assets, `ink.json` stroke count only (not full stroke data) | Partial | Yes | Partial | `TicNoteArchiveWriter` | WIRED_BUT_BROKEN | missing persistence |
| J8 find/share exported files | user discoverability | no share flow for export directory in drawing flow | Partial | No | Unknown | no post-export UX route | UI_UNDISCOVERABLE | model exists but no UI |
| J9 export hidden debug only? | if debug-only mark hidden | export button in normal, not debug-only | Yes | Yes | Likely | topbar export | PASS | unclear from code |
| K1 Debug APK installable | artifact install check | local artifact exists in build outputs but not freshly verified here | Partial | N/A | Unknown | prior build artifacts present | NOT_VERIFIED | unclear from code |
| K2 Signed release installable w/secrets | release signing path | no live signing verification in this audit | Unknown | N/A | Unknown | none | NOT_VERIFIED | unclear from code |
| K3 Unsigned APK marked not installable | policy clarity | `ReleaseArtifactPolicy` tests exist but UX route unclear | Partial | Partial | Unknown | unit tests for policy object | IMPLEMENTED_NOT_WIRED | model exists but no UI |
| K4 AAB not installable direct | policy clarity | same as above | Partial | Partial | Unknown | policy tests only | IMPLEMENTED_NOT_WIRED | model exists but no UI |
| K5 Version/build info visible appropriately | user can see build info | shown in Settings card; build badge only debug mode in Home | Yes | Yes | Likely | `SettingsScreen`, `NoteList` | PASS | unclear from code |

## Per-feature audit notes (A–K)
For each line item above, trigger/screen/UI handler/state/renderer/tests were traced in:
- `app/src/main/java/com/example/cahier/features/home/*`
- `app/src/main/java/com/example/cahier/core/navigation/CahierNavGraph.kt`
- `app/src/main/java/com/example/cahier/features/drawing/DrawingCanvas.kt`
- `app/src/main/java/com/example/cahier/features/drawing/viewmodel/DrawingCanvasViewModel.kt`
- `app/src/main/java/com/example/cahier/core/document/DocumentModel.kt`
- `app/src/main/java/com/example/cahier/features/drawing/export/*`
- tests under `app/src/test/...` and `app/src/androidTest/...`

## Recommended Fix Order (do not implement in this audit)
### P0
1. Make Normal Mode route and surface truly product-clean (move advanced toggles out of normal topbar).  
Likely files: `DrawingCanvas.kt`, `CahierHomeScreen.kt`, `SettingsScreen.kt`  
Type: wiring/UI  
Risk: MEDIUM

2. Guarantee New Table Note opens real TextContainer inline-table path only; avoid legacy table entry ambiguity.  
Likely files: `DrawingCanvas.kt`, `DrawingCanvasViewModel.kt`, `HomeScreenViewModel.kt`  
Type: wiring  
Risk: MEDIUM

3. Ensure paragraph-before/after editing remains obvious and stable in normal flow.  
Likely files: `DrawingCanvas.kt`  
Type: UI discoverability  
Risk: LOW

4. Ensure same-note ink + textcontainer workflow + save/reopen are validated end-to-end.  
Likely files: `DrawingCanvasViewModel.kt`, repository layer tests  
Type: implementation + verification  
Risk: HIGH

5. Ensure debug labs/metrics never leak into Normal Mode UX.  
Likely files: `DrawingCanvas.kt`, `HomeScreenComponents.kt`  
Type: wiring/UI  
Risk: LOW

### P1
1. Wire pan/zoom gestures with explicit user affordance and stable no-drift behavior for all block types and ink.  
Likely files: `DrawingCanvas.kt`, `CanvasTransform.kt`, `DrawingCanvasViewModel.kt`  
Type: implementation/wiring  
Risk: HIGH

2. Harden lasso/select/multi-select/group move feedback and persistence semantics.  
Likely files: `DrawingCanvas.kt`, `DrawingCanvasViewModel.kt`  
Type: implementation/UI  
Risk: MEDIUM

3. Make image paste clearly reachable in Normal Mode (including table/textcontainer intent) and robust to clipboard variants.  
Likely files: `DrawingCanvas.kt`, `DrawingCanvasViewModel.kt`  
Type: wiring/implementation  
Risk: MEDIUM

4. Make export path discoverable from Normal Mode (destination, open/share affordance).  
Likely files: `DrawingCanvas.kt`, `DrawingCanvasViewModel.kt`  
Type: wiring/UI  
Risk: MEDIUM

### P2
1. Table visual polish to reduce spreadsheet/debug feel.  
Likely files: `DrawingCanvas.kt`, `NeoNoteVisualTokens.kt`  
Type: UI  
Risk: LOW

2. Toolbar discoverability and command grouping polish.  
Likely files: `DrawingCanvas.kt`, `DrawingToolbox.kt`  
Type: UI  
Risk: LOW

3. Release artifact messaging and user-facing policy clarity.  
Likely files: `ReleaseArtifactPolicy.kt`, settings/debug screens  
Type: wiring/UI  
Risk: LOW

