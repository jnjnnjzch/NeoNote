# Unified Note MVP Repair Acceptance Log

Current status: **Unified Note MVP repair**

This file is the required audit location for Samsung tablet manual test results for the current MVP repair plan. Do not mark Phase 10 complete from this work. Record only observed results from real device testing.

## Result format

For each test run, record:

- Milestone:
- Result: `PASS`, `PARTIAL`, `FAIL`, or `BLOCKED`
- Date:
- Tester:
- Device model:
- Android / One UI version:
- App build or commit:
- Input hardware: S Pen, finger touch, hardware keyboard if used
- Script steps executed:
- Observed result:
- Defects / follow-up:

## Milestone 1: Single entry + default TextContainer + title save + basic text input

Status: `PENDING TABLET VERIFICATION`

Samsung tablet acceptance script:

1. Install the debug build on a Samsung Galaxy Tab with S Pen.
2. Launch NeoNote and create a new note from the primary `New Note` entry.
3. Confirm the app opens one unified editor, not a separate table-only or debug-first editor.
4. Type a note title, then type several lines of body text in the default `TextContainer`.
5. Close the note, reopen it from the note list, and verify the title and body text are unchanged.
6. Record PASS/FAIL, device model, Android version, app build, tester, date, and notes in this file.

Recorded results:

- No Samsung tablet result recorded yet.

## Milestone 2: Stable S Pen writing + stable finger pan/zoom + correct zoomed stroke coordinates

Status: `PENDING TABLET VERIFICATION`

Samsung tablet acceptance script:

1. Open the Milestone 1 test note on a Samsung Galaxy Tab with S Pen.
2. Draw several S Pen strokes at 100% zoom near existing text.
3. Use fingers to pan and pinch-zoom; verify no finger-created strokes appear.
4. At a non-100% zoom level, draw a box around a visible object and verify the stroke follows the S Pen tip.
5. Pan/zoom away and back; verify the box still aligns with the original object.
6. Save, close, reopen, and verify stroke positions remain correct.
7. Record the result in this file.

Recorded results:

- No Samsung tablet result recorded yet.

## Milestone 3: Text blocks and ink coexist without finger mis-touch

Status: `PENDING TABLET VERIFICATION`

Samsung tablet acceptance script:

1. Open a note with the default `TextContainer`.
2. Type multiple lines of text.
3. Tap the text with a finger and verify editing/focus works.
4. Use the S Pen to write in blank space next to and below the text.
5. Use fingers to pan, tap blank space, and tap text again; verify finger input does not create stray ink.
6. Save, close, reopen, and verify text and ink both remain present and correctly placed.
7. Record the result in this file.

Recorded results:

- No Samsung tablet result recorded yet.

## Milestone 4: Tables as TextContainer nodes

Status: `PENDING TABLET VERIFICATION`

Samsung tablet acceptance script:

1. Open a note that already contains text and ink from previous milestones.
2. Insert a table from the normal editor controls while the `TextContainer` is active.
3. Enter text in several cells using the on-screen keyboard and, if available, a hardware keyboard.
4. Press Tab repeatedly and verify focus advances through cells predictably.
5. Save, close, reopen, and verify table structure and cell content are preserved.
6. Confirm the table appears as content inside the `TextContainer`, not as the only default note surface.
7. Record the result in this file.

Recorded results:

- No Samsung tablet result recorded yet.

## Milestone 5: Select/move text blocks with anchored ink persistence

Status: `PENDING TABLET VERIFICATION`

Samsung tablet acceptance script:

1. Create or open a note with one `TextContainer` and nearby S Pen annotations.
2. Anchor at least one annotation to the text block using the implemented anchoring workflow.
3. Add a separate non-anchored stroke elsewhere on the canvas.
4. Select the text block and move it to a new position.
5. Verify anchored ink moves with the block and non-anchored ink does not.
6. Save, close, reopen, and verify the moved block and anchored ink relationship are preserved.
7. Record the result in this file.

Recorded results:

- No Samsung tablet result recorded yet.

## Milestone 6: Images, formulas, export, and performance optimization

Status: `BLOCKED`

Entry gate:

- Milestones 1 through 5 must all have Samsung tablet `PASS` results in this file.
- If any of Milestones 1 through 5 are missing, `PARTIAL`, `FAIL`, or `BLOCKED`, this milestone remains blocked.

Samsung tablet acceptance script after the entry gate passes:

1. Confirm the Milestone 6 entry gate is satisfied in this audit log.
2. Insert an image and verify it can be positioned, saved, closed, and reloaded.
3. Insert a formula and verify it can be edited, saved, closed, and reloaded.
4. Export the note using each supported export format and inspect output fidelity.
5. Load or create a stress note with many strokes, text blocks, and tables; verify pan/zoom and writing remain usable.
6. Record performance observations and export fidelity results in this file.

Recorded results:

- Blocked until Milestones 1 through 5 pass Samsung tablet testing.
