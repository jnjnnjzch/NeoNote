# Phase 4: Infinite Canvas v0

## Scope

- Added interactive table block movement with drag gestures.
- Added table block resize handle and persisted dimensions.
- Persisted block frame (`x`, `y`, `width`, `height`) in document model.

## Input Defaults

- Stylus inking path is unchanged and remains the primary draw interaction.
- Table editing remains keyboard-driven inside cells.

## Notes

- This phase commit focuses on block move/resize and persistence.
- Pan/zoom controls are deferred to a follow-up refinement commit to keep inking stability risk low.
