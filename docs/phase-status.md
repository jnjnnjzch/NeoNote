# NeoNote Phase Status

Last updated: 2026-05-29

## Overall

- Product name: `NeoNote`
- Current status: **Unified Note MVP repair**
- Roadmap progress: old Phase 0 through Phase 10 sequencing is superseded by the MVP repair milestones in `docs/roadmap.md`.
- Truth source for current milestone verification: `docs/audit/unified_note_mvp_repair_acceptance.md`
- Important note: NeoNote does not currently claim Phase 10 completion. The product remains in MVP repair until the required Samsung tablet manual acceptance scripts pass.

## Current MVP Repair Milestones

1. Milestone 1 (PENDING TABLET VERIFICATION): single entry, default `TextContainer`, title persistence, and basic text input.
2. Milestone 2 (PENDING TABLET VERIFICATION): stable S Pen writing, stable finger pan/zoom, and correct stroke coordinates after zoom.
3. Milestone 3 (PENDING TABLET VERIFICATION): text blocks and ink coexist; clicking text edits it; blank space accepts handwriting; finger input avoids accidental ink.
4. Milestone 4 (PENDING TABLET VERIFICATION): tables are insertable/editable `TextContainer` nodes with Tab navigation and save/reload support.
5. Milestone 5 (PENDING TABLET VERIFICATION): text blocks can be selected/moved, anchored ink moves with them, and anchoring persists.
6. Milestone 6 (BLOCKED): images, formulas, export, and performance optimization. This milestone may start only after Milestones 1 through 5 have PASS records from Samsung tablet testing.

## Superseded Phase Roadmap Status

The previous phase roadmap is retained only as historical context. It must not be used to advertise release readiness or Phase 10 completion.

- Phase 0 through Phase 9 contained useful prototypes and implementation work, but they do not prove the unified tablet note-taking MVP.
- Phase 10 release work is **not complete for product claims** because installability, signing, end-to-end feature readiness, and real-device acceptance are not established for the repaired unified note workflow.
- Any old documentation or devlog entry that says a phase passed should be read as historical implementation/audit context, not as current product readiness.

## Current Feature Snapshot

- The active product direction is one unified normal note surface.
- The default content model centers on editable `TextContainer` content with ink on the same canvas.
- Samsung S Pen behavior, finger pan/zoom behavior, and transformed stroke coordinates are hard gates.
- Tables must live as nodes inside `TextContainer` before image/formula/export polish proceeds.
- Anchored ink movement and persistence are required before Milestone 6 can begin.
- All milestone results must be recorded under `docs/audit/`.
