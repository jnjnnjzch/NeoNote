# NeoNote Phase Status

Last updated: 2026-05-28

## Overall

- Product name: `NeoNote`
- Roadmap progress: prototype implemented in branch `codex/phase-0-1-foundation`
- Truth source: `docs/audit/phase_completion_audit.md`
- Important note: this file reports audited completion status, not aspirational roadmap claims.

## Per-Phase Status

1. Phase 0 (PASS): CI workflows, architecture docs, debug APK pipeline.
2. Phase 1 (PARTIAL): inking debug overlay exists, but metric aggregation lacks automated coverage and hardware behavior is not verified.
3. Phase 2 (PASS): versioned document model and serializer tests exist.
4. Phase 3 (PASS): TableBlock v0.1 is editable and persistent; manual keyboard protocol is defined, serializer coverage exists.
5. Phase 4 (PARTIAL): table move/resize exists, but true infinite canvas pan/zoom is missing.
6. Phase 5 (PARTIAL): style fields and parsing exist; image paste is FAIL and LaTeX rendering is FAIL.
7. Phase 6 (PARTIAL): anchored ink has an index-range implementation, not stable stroke IDs.
8. Phase 7 (PARTIAL): exports exist but are not full-fidelity and lack integrity tests.
9. Phase 8 (PARTIAL): stress generation and throttling exist, but no performance benchmark evidence.
10. Phase 9 (PARTIAL): beta controls exist, but settings/tool UX is incomplete and lightly tested.
11. Phase 10 (NOT VERIFIED): release workflow exists, but artifact installability and signing evidence are incomplete.

## Current Feature Snapshot

- Low-latency Jetpack Ink drawing path is retained from Cahier, but latency is not benchmarked.
- Ink debug telemetry overlay exists for pressure/tool/event rate/finalized/cancel/palm; tilt and tests are still missing.
- Structured table layer is editable and persisted.
- Ink and table layers are intended to be separate; stronger integration tests are still needed.
- Block-level anchored strokes exist but need stable IDs and regression tests.
- Export support exists but needs real content fidelity and integrity tests.
- CI workflows build on version tags; release artifact verification is still being hardened.
