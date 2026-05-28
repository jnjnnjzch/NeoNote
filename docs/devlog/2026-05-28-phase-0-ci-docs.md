# Phase 0: CI and Architecture Docs

## Scope

- Added the Android debug APK GitHub Actions workflow.
- Added architecture, roadmap, and contribution documentation for Table Ink Canvas.
- Kept Phase 0 limited to repository infrastructure and docs.

## Verification

The workflow is configured to run `./gradlew assembleDebug` and upload debug APK artifacts from `**/build/outputs/apk/debug/*.apk`.

Local build verification is blocked until the upstream `android/cahier` source is present in this repository.

## Notes

No app source code or app logic was modified.
