# NeoNote Repository Autopilot Protocol

## Mission
Drive NeoNote from current status to `USABLE_ALPHA` using repository-driven execution.

## Mandatory Loop
1. Read `AUTOPILOT.md`.
2. Read `docs/autopilot/status.json`.
3. Read `docs/autopilot/task_queue.md`.
4. Find the active milestone.
5. Implement only that milestone.
6. Run:
   - `.\gradlew.bat :app:assembleDebug`
   - `.\gradlew.bat :app:testDebugUnitTest`
7. If tests fail, fix failures before moving on.
8. Update `docs/audit/progress.md`.
9. Update `docs/autopilot/status.json`.
10. Update `docs/autopilot/task_queue.md`.
11. Commit the milestone.
12. Promote the next queued milestone automatically.
13. Continue without asking the user.

## Stop Policy
Stop only on real blockers:
- missing JDK
- missing Android SDK
- Gradle cannot run
- unresolvable merge conflict
- missing signing secrets for signed release
- physical Samsung S Pen validation is strictly required

If physical Samsung S Pen validation is required:
- mark only that sub-item `DEVICE_VERIFICATION_REQUIRED`
- continue with non-hardware implementation
- do not block the whole roadmap

## Execution Constraints
- Do not stop after a successful milestone.
- Do not ask for confirmation between milestones.
- Do not repeat completed milestones.
- Do not reopen Milestone 3 visual polish unless explicitly requested by user.
- Do not let Debug Mode pollute Normal Mode.
- Do not reintroduce standalone `TableBlock` as the default user-facing model.
- Do not use automatic ink anchoring as the main Normal Mode behavior.
- Use explicit selection + lasso + multi-select group move.

## Current Active Milestone
Milestone 4: Normal Mode / Debug Mode separation
