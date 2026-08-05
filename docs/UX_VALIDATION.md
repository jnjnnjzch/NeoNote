# NeoNote natural editor UX acceptance

Validated user journey:

1. Open or create a note from a phone-width library.
2. Pan and pinch the canvas with a finger while S Pen remains the writing input.
3. Tap blank canvas space to create readable text; edge taps and keyboard resize keep the editor visible.
4. Tap existing text to edit it without creating a duplicate text box.
5. Use a compact formatting bar with persistent Done and More actions; display and edit typography stay visually stable.
6. Insert an image at a viewport-aware, aspect-preserving size, then move, resize, duplicate, or delete it directly.
7. Lasso mixed ink and objects, move them, and resize from the lower-right handle while the opposite corner remains anchored.
8. Edit tables, formulas, images, and nested table content without losing controls on narrow screens.
9. Search full note contents locally and open the matching page after saving current edits.

Automated validation completed on commit `9ed65de26f7bbfcdc3827c7aadf11bf021e12b3a`:

- JVM test aggregate passed.
- `lintReleaseCandidate` passed.
- `assembleReleaseCandidate` passed.
- APK artifact generated successfully.

Hardware-specific S Pen feel still requires final physical-device confirmation; automated validation does not substitute for device ergonomics.
