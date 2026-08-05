# NeoNote mobile UX improvements

## Workspace

- Adaptive phone/tablet header with overflow actions instead of clipped controls.
- Compact four-mode dock for writing, typing, selection, and erasing.
- Context controls scroll horizontally on narrow screens and disappear while focused text entry needs the space.

## Text

- Blank-canvas taps create text at a readable width and keep it visible near screen edges.
- Keyboard-induced viewport changes re-center the active text region.
- Existing text is hit-tested before blank-canvas creation, preventing accidental duplicate boxes.
- Text boxes grow with content and blend into the page when idle.
- Editing and display typography use consistent sizing.
- Formatting keeps Done and More visible while preserving the full advanced toolset.

## Objects and ink

- Existing text, images, and ink receive correct input targets.
- Imported images use density-correct, viewport-aware, aspect-preserving initial sizes.
- Selected content exposes direct duplicate, delete, and lower-right resize controls.
- Interactive resize keeps the opposite corner anchored.

## Structured content

- Table, formula, image, and nested-content actions remain reachable on narrow screens.
- Nested images use bounded sampled decoding.
- Internal asset identifiers are no longer exposed as user-visible fallback text.

## Library and search

- Phone-width note library with clear create, title search, trash, restore, import, and overflow actions.
- Full local content search is connected to the user flow and saves current edits before indexing.
