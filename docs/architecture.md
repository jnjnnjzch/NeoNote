# Table Ink Canvas Architecture

Table Ink Canvas starts as a fork of Google's `android/cahier` sample. The first engineering constraint is to preserve Cahier's low-latency Jetpack Ink pipeline while adding editable table blocks as a separate document layer.

## Product Scope

Table Ink Canvas is an Android-first note-taking app for Samsung Galaxy Tab devices with S Pen input. Editable tables provide the main knowledge structure. Pressure-sensitive handwriting provides the reasoning and annotation layer.

Before v1.0, the app must not add cloud sync, OCR, AI features, collaboration, or multi-platform support.

## Layer Model

The app uses separate layers for structured blocks and ink:

- `Document`: the versioned root object for a note.
- `CanvasPage`: a page or canvas surface inside a document.
- `Blocks`: structured editable content, starting with `TableBlock`.
- `InkLayer`: finalized stroke data and stroke metadata.

Ink and tables must remain independent in storage and rendering. Later anchoring features may associate strokes with a block, but they must not merge stroke data into table data.

## Inking Rules

The Jetpack Ink API remains the source of truth for stylus input and rendering behavior.

- Stylus latency is more important than table editing richness.
- Predicted stroke points may be rendered for responsiveness.
- Predicted stroke points must never be saved as finalized stroke data.
- Compose recomposition must not be triggered for every stylus point in performance-sensitive paths.
- Finger input defaults to canvas navigation once infinite canvas behavior exists.
- Stylus input defaults to writing unless the selected tool says otherwise.

## Table Rules

Tables are structured blocks, not a replacement for the ink layer.

- Phase 2 introduces a placeholder `TableBlock`.
- Phase 3 implements a simple 3x3 editable table with keyboard navigation and save/load.
- Later table formatting must remain exportable and versioned.
- Cell-level ink anchoring is explicitly out of scope until after block-level anchoring works.

## Persistence

Document data must be versioned from the first model milestone. Serialization changes require tests in the same PR.

Native export eventually targets a zip-based `.ticnote` archive. User data must remain exportable through v1.0 export formats.

## Milestone Gates

Every milestone must:

- Build in GitHub Actions.
- Produce an installable debug APK until release signing is introduced.
- Keep the feature scope to the current phase.
- Include a `docs/devlog` entry.
- Include tests when data model or serialization behavior changes.

Phase 0 is documentation and CI only. It must build the upstream fork without app logic changes.
