# License Audit (Draft)

## Project License

- Application code: Apache License 2.0.
- Upstream base: `android/cahier` (Apache License 2.0).

## Third-Party Components (High-Level)

- AndroidX Jetpack libraries
- Kotlin and Kotlinx Serialization
- Hilt
- Room
- Coil
- Protobuf

## Action Items Before Public Release

- Generate an exact dependency tree from Gradle and archive it with release artifacts.
- Verify each dependency license and NOTICE requirements.
- Include full third-party notices in the distributed package and repository.
- Confirm transitive dependency obligations for static assets and fonts, if any are added later.
