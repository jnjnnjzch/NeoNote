# Phase 1: Inking Pipeline Debug Overlay

## Scope

- Audited inking integration points:
  - `DrawingSurface` as the ink input/rendering host.
  - `DrawingCanvas` as composition and tool orchestration.
  - `DrawingCanvasViewModel` as stroke finalization and persistence boundary.
- Added a debug overlay for:
  - pressure
  - tool type
  - point count
  - event rate (Hz)
  - finalized stroke count
  - cancel events
  - palm events
- Kept Jetpack Ink inking flow in place and did not alter finalized stroke persistence behavior.

## Notes

- Predicted points remain transient in the Ink API path and are not saved.
- Raw motion event sampling is read-only and returns `false` to avoid intercepting the inking stream.

## Verification

- CI workflows added for build and unit test verification.
- Local full build is currently blocked on missing Android SDK in this environment.
