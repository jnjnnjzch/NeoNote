# NeoNote Android release process

## Build identities

| Variant | Application ID | Version | Purpose |
| --- | --- | --- | --- |
| `debug` | `com.neonote.debug` | `1.0.0-debug` | Developer diagnostics and routine CI |
| `releaseCandidate` | `com.neonote.rc` | `1.0.0-rc.1` | Non-debug, installable acceptance build signed with the Android debug key |
| `release` | `com.neonote` | `1.0.0` | Production build signed with the persistent NeoNote release key |

The release-candidate APK is deliberately isolated from production data and must not be published as the final production APK. Its purpose is device acceptance testing before the production key is used.

## Required GitHub Actions secrets

A production release requires all four secrets:

- `NEONOTE_KEYSTORE_BASE64`: base64-encoded persistent Android keystore
- `NEONOTE_KEYSTORE_PASSWORD`: keystore password
- `NEONOTE_KEY_ALIAS`: signing key alias
- `NEONOTE_KEY_PASSWORD`: signing key password

The `Android Release` workflow always builds and verifies the release candidate. On a main-branch push or manual dispatch, it additionally builds `NeoNote-1.0.0.apk` when all signing secrets are present. A `v*` tag fails rather than producing an unsigned or temporary-key release.

## Release acceptance checklist

Before creating the `v1.0.0` tag:

1. Install `NeoNote-1.0.0-rc.1.apk` on the target Samsung tablet.
2. Verify S Pen pressure, long handwriting sessions, pan/zoom, selection, object movement, and page switching.
3. Verify rich text, lists, formulas, tables, nested table-cell content, local image import, undo/redo, startup recovery, and autosave.
4. Force-stop and relaunch after editing; verify the document and local images recover.
5. Upgrade from a prior production-signed build when one exists; verify documents remain readable.
6. Configure the four signing secrets and manually dispatch the release workflow.
7. Verify the production APK certificate and SHA-256 artifact, then create the `v1.0.0` tag.

Never regenerate or replace the production signing key after publishing. Android upgrades require every future APK to use the same key.
