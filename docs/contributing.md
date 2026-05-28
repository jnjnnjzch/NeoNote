# Contributing

Table Ink Canvas follows a phase-gated development process so the S Pen inking path stays fast while the table system grows around it.

## Pull Request Rules

- One feature per PR.
- Do not skip phases.
- Every PR must pass CI.
- Every PR must include a `docs/devlog` entry.
- Every PR that changes the data model or serialization must include tests.
- Do not refactor the whole app unless the phase requires it.
- Do not replace Jetpack Ink.
- Do not add Flutter or web-based ink rendering.

## Phase Boundaries

Phase 0 is limited to CI and documentation. It must not modify app source code.

For later phases, prefer small working implementations that preserve the Cahier inking pipeline. Table editing can improve over time; pen smoothness is the priority.

## CI Expectations

Every PR must produce an installable debug APK through GitHub Actions. Release signing and AAB output are reserved for Phase 10.

## Devlog

Each PR gets one short Markdown file under `docs/devlog/`. Use the date and a concise slug:

```text
docs/devlog/YYYY-MM-DD-short-description.md
```

The entry should include:

- Phase.
- Scope.
- Tests or build verification.
- Notes about any risk to inking latency, persistence, or exportability.
