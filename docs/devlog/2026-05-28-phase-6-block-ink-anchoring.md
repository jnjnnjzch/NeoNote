# Phase 6: Block-Level Ink Anchoring

## Scope

- Added block-level stroke anchor metadata in document model.
- New finalized strokes are anchored to the active table block (index range based).
- Added stroke translation mapping during rendering.
- Moving a table block now moves its anchored stroke set visually.

## Boundaries

- Anchoring granularity is block-level only.
- Cell-level anchoring is intentionally not implemented in this phase.
- Predicted points are still not persisted.
