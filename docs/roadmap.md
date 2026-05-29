# Roadmap

Current status: **Unified Note MVP repair**.

This roadmap supersedes the old Phase 0 through Phase 10 completion narrative. Do not claim Phase 10 is complete, and do not advance to late polish/release work until the MVP repair milestones are implemented and verified on a Samsung Galaxy Tab with S Pen.

Each milestone must have:

1. an implementation change set,
2. automated checks where practical,
3. a Samsung tablet manual acceptance script, and
4. a result entry in `docs/audit/unified_note_mvp_repair_acceptance.md`.

## Milestone 1: Single entry, default TextContainer, title persistence, basic text input

Goal: opening or creating a note always lands on one normal unified note editor with a default editable `TextContainer`.

Required behavior:

- There is only one primary user-facing note entry path for normal notes.
- A newly created note contains or immediately creates a default `TextContainer`.
- The note title can be edited, saved, closed, and reloaded without loss.
- Basic text input works inside the default `TextContainer` with a hardware keyboard and the on-screen keyboard.

Samsung tablet manual acceptance script:

1. Install the debug build on a Samsung Galaxy Tab with S Pen.
2. Launch NeoNote and create a new note from the primary `New Note` entry.
3. Confirm the app opens one unified editor, not a separate table-only or debug-first editor.
4. Type a note title, then type several lines of body text in the default `TextContainer`.
5. Close the note, reopen it from the note list, and verify the title and body text are unchanged.
6. Record PASS/FAIL, device model, Android version, app build, tester, date, and notes in `docs/audit/unified_note_mvp_repair_acceptance.md`.

Exit criteria:

- Manual script passes on the target tablet.
- Any failure is either fixed or explicitly documented as a blocking defect.

## Milestone 2: Stable S Pen writing, stable finger pan/zoom, correct stroke coordinates after zoom

Goal: stylus and finger input are separated reliably, and ink coordinates remain correct through viewport transforms.

Required behavior:

- S Pen writes smoothly in normal inking mode.
- Finger gestures pan and pinch-zoom the canvas without accidentally creating ink.
- Strokes drawn after zoom appear under the pen tip and stay in the correct canvas position after further pan/zoom changes.
- Existing strokes do not drift after zooming in, zooming out, closing, and reopening.

Samsung tablet manual acceptance script:

1. Open the Milestone 1 test note on a Samsung Galaxy Tab with S Pen.
2. Draw several S Pen strokes at 100% zoom near existing text.
3. Use fingers to pan and pinch-zoom; verify no finger-created strokes appear.
4. At a non-100% zoom level, draw a box around a visible object and verify the stroke follows the S Pen tip.
5. Pan/zoom away and back; verify the box still aligns with the original object.
6. Save, close, reopen, and verify stroke positions remain correct.
7. Record the result in `docs/audit/unified_note_mvp_repair_acceptance.md`.

Exit criteria:

- S Pen writing, finger pan/zoom, and post-zoom stroke placement pass real-device testing.
- Any coordinate drift is a blocking defect for later milestones.

## Milestone 3: Text blocks and ink coexist on one surface

Goal: text editing and handwriting coexist naturally on the same canvas without finger mis-touch problems.

Required behavior:

- Text blocks and ink can exist in the same note at the same time.
- Tapping existing text focuses it for editing.
- Writing with S Pen on blank canvas creates ink.
- Finger taps or drags do not accidentally create ink while navigating or selecting.
- Blank-space handwriting does not steal focus from active text in a way that loses content.

Samsung tablet manual acceptance script:

1. Open a note with the default `TextContainer`.
2. Type multiple lines of text.
3. Tap the text with a finger and verify editing/focus works.
4. Use the S Pen to write in blank space next to and below the text.
5. Use fingers to pan, tap blank space, and tap text again; verify finger input does not create stray ink.
6. Save, close, reopen, and verify text and ink both remain present and correctly placed.
7. Record the result in `docs/audit/unified_note_mvp_repair_acceptance.md`.

Exit criteria:

- Text edit, blank-space handwriting, and finger navigation all pass together on tablet hardware.

## Milestone 4: Tables as TextContainer nodes

Goal: tables are inline nodes inside `TextContainer`, not a separate default note type.

Required behavior:

- A table can be inserted into a `TextContainer`.
- Table cells are editable.
- Tab moves between cells and creates/continues rows according to the current table-editing rule.
- Table content saves and reloads as part of the containing note.
- The normal note entry path does not default to standalone `TableBlock` editing.

Samsung tablet manual acceptance script:

1. Open a note that already contains text and ink from previous milestones.
2. Insert a table from the normal editor controls while the `TextContainer` is active.
3. Enter text in several cells using the on-screen keyboard and, if available, a hardware keyboard.
4. Press Tab repeatedly and verify focus advances through cells predictably.
5. Save, close, reopen, and verify table structure and cell content are preserved.
6. Confirm the table appears as content inside the `TextContainer`, not as the only default note surface.
7. Record the result in `docs/audit/unified_note_mvp_repair_acceptance.md`.

Exit criteria:

- Inline insertion, editing, Tab navigation, and persistence pass real-device testing.

## Milestone 5: Select/move text blocks with anchored ink persistence

Goal: users can select and move text blocks, and ink anchored to a block moves with it and persists.

Required behavior:

- A text block can be selected on the unified canvas.
- A selected text block can be moved with touch or pen interaction according to the active tool rules.
- Ink intentionally anchored to that text block moves with the block.
- Anchored ink relationships save and reload.
- Non-anchored ink stays in its own canvas position.

Samsung tablet manual acceptance script:

1. Create or open a note with one `TextContainer` and nearby S Pen annotations.
2. Anchor at least one annotation to the text block using the implemented anchoring workflow.
3. Add a separate non-anchored stroke elsewhere on the canvas.
4. Select the text block and move it to a new position.
5. Verify anchored ink moves with the block and non-anchored ink does not.
6. Save, close, reopen, and verify the moved block and anchored ink relationship are preserved.
7. Record the result in `docs/audit/unified_note_mvp_repair_acceptance.md`.

Exit criteria:

- Selection, movement, anchoring, and persistence pass real-device testing.
- Milestone 6 must not start until Milestones 1 through 5 have PASS entries from Samsung tablet testing.

## Milestone 6: Images, formulas, export, and performance optimization

Goal: add rich content and polish only after the core unified note behavior is proven.

Entry gate:

- Milestones 1, 2, 3, 4, and 5 must all have PASS results from Samsung tablet manual testing in `docs/audit/unified_note_mvp_repair_acceptance.md`.
- If any of the first five milestones are PARTIAL, FAIL, or untested, Milestone 6 is blocked.

Required behavior after entry gate passes:

- Image insertion, movement, persistence, and reload.
- Formula insertion, editing, persistence, and reload.
- Export paths that preserve text, ink, tables, images, formulas, and layout as far as each export format allows.
- Performance optimization for realistic notes with many strokes, text blocks, and inline tables.

Samsung tablet manual acceptance script:

1. Confirm the Milestone 6 entry gate is satisfied in the audit log.
2. Insert an image and verify it can be positioned, saved, closed, and reloaded.
3. Insert a formula and verify it can be edited, saved, closed, and reloaded.
4. Export the note using each supported export format and inspect output fidelity.
5. Load or create a stress note with many strokes, text blocks, and tables; verify pan/zoom and writing remain usable.
6. Record performance observations and export fidelity results in `docs/audit/unified_note_mvp_repair_acceptance.md`.

Exit criteria:

- Rich content, export, and performance are validated only after the first five milestones pass on tablet hardware.
