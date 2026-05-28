# NeoNote Debug APK Functionality & Install Audit (77db893)

- Branch: `codex/phase-0-1-foundation`
- HEAD: `77db8933427aca4b3ac0b9dcadf93e16d09b022b`
- Scope: CI workflow + code inspection + reported runtime symptoms
- Constraint: no app code changes made in this audit

## 1) Build and Artifact Status

Data source:
- `.github/workflows/ci.yml`
- prior user-reported install behavior
- CI steps that run `aapt/apksigner/unzip`

Known produced artifacts by current workflow:

1. `NeoNote-debug-installable-v<versionName>-<versionCode>-<shortSha>.apk`  
   - job: `verify`  
   - type: `apk`  
   - directly installable: **true** (expected, if verify step passes)  
   - signed: **true** (debug key)  
   - unsigned: false  
   - debug signed: true  
   - release signed: false  
   - applicationId/version/buildType: extracted by `aapt` in verify  
   - gitSha: embedded in filename + BuildConfig (`GIT_SHA`)  
   - sha256: not emitted as standalone for debug in current workflow
   - classification: `INSTALLABLE_DEBUG_APK`

2. `NeoNote-release-unsigned-NOT-INSTALLABLE-v<versionName>-<versionCode>-<shortSha>.apk`  
   - job: `build_release`  
   - type: `apk`  
   - directly installable: **false** (unsigned release artifact)  
   - signed: false (or invalid signature for install)  
   - unsigned: true  
   - debug signed: false  
   - release signed: false  
   - applicationId/version/buildType: from `aapt dump badging` artifact logs
   - sha256: included in `release-sha256.txt`
   - classification: `UNSIGNED_RELEASE_NOT_INSTALLABLE`

3. `NeoNote-release-signed-installable-v<versionName>-<versionCode>-<shortSha>.apk` (conditional)  
   - job: `build_release` (only if signing secrets exist)  
   - type: `apk`  
   - directly installable: **true** only when `apksigner verify` passes  
   - signed: true  
   - unsigned: false  
   - debug signed: false  
   - release signed: true  
   - classification: `INSTALLABLE_SIGNED_RELEASE_APK`

4. `NeoNote-release-bundle-NOT-INSTALLABLE-DIRECTLY-v<versionName>-<versionCode>-<shortSha>.aab`  
   - job: `build_release`  
   - type: `aab`  
   - directly installable: false  
   - classification: `AAB_NOT_DIRECTLY_INSTALLABLE`

5. `release-sha256.txt`, `release-*.badging.txt`, `release-*-signing.txt`, `release-*-unzip.txt`  
   - job: `build_release`  
   - type: `txt`  
   - directly installable: false  
   - classification: `REPORT_ONLY`

6. `adb-install-output.txt`  
   - job: `install_smoke_debug`  
   - type: `txt`  
   - directly installable: false  
   - classification: `REPORT_ONLY`

7. `docs/audit/current_artifact_inventory.md`, `docs/audit/current_artifact_inventory.json` (uploaded artifact)  
   - job: `artifact_inventory`  
   - type: `md/json`  
   - directly installable: false  
   - classification: `REPORT_ONLY`

Notes:
- Exact file size/versionName/versionCode/applicationId/SHA256 per-run are generated in CI artifacts, not present in repo statically.
- The workflow now labels non-installables explicitly in filename.

## 2) Why Unsigned Release APK Failed

- Failed file is unsigned release APK: **CONFIRMED** (by filename + workflow path).
- Installation failure is expected: **CONFIRMED**.
- It should not be treated as installable: **CONFIRMED**.
- User should ignore unsigned release for direct install: **CONFIRMED**.
- Recommended install file:  
  `NeoNote-debug-installable-v<...>.apk`  
  or signed release APK only if produced and verified.

## 3) Debug APK Identity (what can be confirmed now)

From workflow + gradle config:
- applicationId: `com.example.cahier`
- buildType: `debug`
- signing: debug keystore (`ci/signing/debug.keystore`, alias `androiddebugkey`)
- main activity: `com.example.cahier.MainActivity` (Manifest)
- app label: `@string/app_name`
- versionName/versionCode source: dynamic in `app/build.gradle.kts`
  - `versionName = <tag-without-v>-<shortSha>`
  - `versionCode = 1000 + GITHUB_RUN_NUMBER` (CI)
- embedded build metadata: **yes**
  - `BuildConfig.GIT_SHA`
  - `BuildConfig.BUILD_TIME_UTC`
  - `BuildConfig.GITHUB_RUN_NUMBER`
  - `BuildConfig.APPLICATION_ID_VALUE`

Command-equivalent checks in CI:
- `unzip -t <debug.apk>`
- `apksigner verify --verbose --print-certs <debug.apk>`
- `aapt dump badging <debug.apk>`

Whether still stuck at `0.1.10`:
- For current pipeline config, should **not** remain 0.1.10 in CI-tag builds.
- If user still sees 0.1.10, likely old APK/package opened (see section 6).

## 4) Debug APK Feature Reachability Audit

Legend: implemented / reachable / coverage

1. S Pen / ink drawing canvas  
- implemented: yes  
- reachable: yes  
- path: Home -> Add note -> Drawing note -> DrawingCanvas  
- files: `CahierHomeScreen.kt`, `DrawingCanvas.kt`, `DrawingCanvasViewModel.kt`  
- tests: `CahierAppTest`, `DrawingCanvasViewModelTest`

2. Ink debug overlay  
- implemented: yes  
- reachable: yes (on drawing screen overlay)  
- files: `DrawingCanvas.kt` (`InkDebugOverlay`)  
- flags: none explicit  
- tests: indirect only (no dedicated UI assertion)

3. Editable TableBlock  
- implemented: yes  
- reachable: yes (`ensureDefaultTableBlock()` on drawing screen)  
- files: `DrawingCanvas.kt`, `DrawingCanvasViewModel.kt`  
- tests: `TableBlockEditorUiTest`

4. Tab navigation  
- implemented: yes  
- reachable: yes (table cell `onPreviewKeyEvent`)  
- tests: `TableBlockEditorUiTest.tabOnLastCell_appendsRow`

5. Ctrl+B / Ctrl+I / Ctrl+U  
- implemented: yes  
- reachable: yes in table cell key handler  
- tests: `Ctrl+B` covered; I/U mostly uncovered

6. Table move / resize  
- implemented: yes  
- reachable: yes (drag areas in table editor)  
- files: `DrawingCanvas.kt`, `DrawingCanvasViewModel.kt`  
- tests: no direct instrumentation proving gesture flow

7. Image paste  
- implemented: partial  
- reachable: yes (`Paste Img` button in table editor)  
- files: `DrawingCanvas.kt`, `DrawingCanvasViewModel.kt`  
- tests: no dedicated end-to-end clipboard instrumentation

8. LaTeX parsing/rendering  
- parsing: yes (`extractLatex`)  
- visual rendering: partial/unclear (stored/exported, not strong in-canvas renderer evidence)  
- files: `DrawingCanvasViewModel.kt`, `ExportComposer.kt`  
- tests: `ExportComposerTest` covers output embedding

9. Anchored ink  
- implemented: partial-to-yes (stroke ID mapping + anchor translation present)  
- reachable: implicit via drawing near table + move table  
- files: `DrawingCanvasViewModel.kt`, `StrokeIdMapper.kt`  
- tests: `StrokeIdMapperTest` (logic-level), no full gesture E2E

10. Export  
- implemented: yes (md/html/pdf/.ticnote paths)  
- reachable: yes (`Export` button on drawing screen)  
- tests: `ExportComposerTest`, `TicNoteArchiveWriterTest`

11. Stress document generator  
- implemented: yes  
- reachable: yes (`Stress` button on drawing screen)  
- tests: `StressDocumentFactoryTest`

12. Settings / toolbar  
- implemented: yes  
- reachable: yes (bottom/nav to Settings, drawing toolbar buttons)  
- tests: `CahierAppTest` (settings existence)

13. Build info screen  
- implemented: yes (new card in Settings)  
- reachable: yes (Home -> Settings)  
- files: `SettingsScreen.kt`, `app/build.gradle.kts`
- tests: none yet

## 5) App Launch Flow

- Launch screen: Home/Cahier list-detail UI (`HomePane`).
- It is still visually Cahier-derived UI: **yes**.
- “NeoNote Home” branding: partial (build info card says NeoNote; global shell still Cahier style).
- Create table note path: drawing note entry -> default table appears in drawing canvas.
- Create ink note path: drawing note entry.
- Build info path: Settings screen card.
- Missing launcher shortcuts/buttons: no explicit dedicated “NeoNote Build Info” quick action on first screen.

## 6) Why Debug App Appears Not Updated (classification)

- versionName stuck at 0.1.10: **POSSIBLE** (old artifact/package opened)  
- versionCode not increasing: **UNLIKELY in CI now**, **POSSIBLE locally**  
- installed old APK artifact: **CONFIRMED/POSSIBLE high**  
- installed artifact zip without extraction: **POSSIBLE**  
- debug and release different applicationId: **UNLIKELY** (same appId currently)  
- opening old package: **POSSIBLE**  
- Build Info screen missing: **UNLIKELY** (now present in code)  
- app UI unchanged despite new build: **POSSIBLE** (same base UI + stale install confusion)  
- CI artifact naming ambiguous: **reduced but still POSSIBLE** if user picks wrong file type

## 7) Tests

Existing relevant:
- debug build/test gate: workflow `verify` (`assembleDebug`, `testDebugUnitTest`)
- app launch/nav: `CahierAppTest`, `CahierListDetailTest`
- table UI: `TableBlockEditorUiTest`
- ink viewmodel basics: `DrawingCanvasViewModelTest`
- export: `ExportComposerTest`, `TicNoteArchiveWriterTest`
- settings/build metadata: code exists, no dedicated UI test
- artifact validation: workflow checks (`unzip/apksigner/aapt`)

Missing:
- Test asserting Settings Build Info displays expected `versionCode/gitSha`.
- Test asserting selected artifact filename pattern includes versionCode+sha.
- CI assertion that unsigned release is never labeled installable.
- End-to-end instrumentation for image paste and anchored ink behavior.

## 8) Required Next Fixes (ordered)

P0:
1. Ensure artifact inventory includes full per-file metadata fields requested (currently partial in generated docs).
2. Add CI guard: fail if any artifact naming breaks installability conventions.
3. Publish a single “recommended install file” note in release summary.

P1:
1. Add UI test for Build Info card values present and non-default.
2. Add explicit in-app “Build Info” entry from home for easy user verification.

P2:
1. If release signed APK required, ensure signing secrets configured and signed artifact verification report surfaced prominently.
2. Add test/report proving release signed APK installability path.

P3:
1. Cleanup legacy Cahier naming in user-facing text if branding consistency desired.
2. Expand README_INSTALL troubleshooting matrix with common `INSTALL_*` codes.

## 9) Final Verdict

ARTIFACT_CONFUSION_LIKELY
