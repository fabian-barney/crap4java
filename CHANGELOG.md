# Changelog

## Unreleased

## 0.5.0 - 2026-05-10

### Added

- Added configurable CRAP thresholds across the CLI, Maven plugin, and Gradle plugin, and exposed the active threshold in primary reports.
- Added AI-agent-friendly primary report controls across the CLI, Maven plugin, and Gradle plugin: `agent`, `failuresOnly`, `omitRedundancy`, report-output path controls, and the `none` primary report format.
- Added dedicated Maven and Gradle report controls for primary output, JUnit sidecars, and consumer-configurable report paths.
- Added the dedicated `cognitive-java Gate` CI job and documented the matching local self-hosting gate workflow.

### Changed

- CRAP scoring now uses the worse available JaCoCo instruction or branch coverage and records the selected coverage kind per method.
- Simplified machine-readable report schemas to a top-level status and threshold plus method entries, and redefined agent mode as a composite shortcut that can still be overridden explicitly.
- Switched JSON and JUnit XML report marshaling to Jackson and improved the text report table formatting.
- Resolved Maven report paths against the execution root, clarified Maven Central versus Plugin Portal consumption guidance, and documented self-hosting gate ownership more precisely.

### Fixed

- Hardened Gradle report state tracking and cleanup for stale sidecars, aliased paths, case-collision paths, symlinked targets, concurrent task state, and failed report replacement scenarios.
- Kept Gradle module and JaCoCo report mappings configuration-cache-friendly while validating report-path collisions and internal-task file misuse earlier.

### Dependencies

- Updated Java build and publishing dependencies, including JUnit `6.0.3`, Jackson `2.21.3`, Error Prone `2.49.0`, NullAway `0.13.4`, Maven plugin tooling, and the Sonatype Central publishing plugin `0.10.0`.

## 0.4.1 - 2026-04-08

### Changed

- Resolved Gradle plugin module and JaCoCo report mappings during configuration so `crap-java-check` no longer performs execution-time build-marker discovery.
- Added root-plus-subproject Gradle coverage tests and an explicit configuration-cache reuse test before advertising support in the published plugin metadata.

## 0.4.0 - 2026-04-06

### Changed

- Renamed the public Java package namespace from `media.barney.crapjava.*` to `media.barney.crap.*`.
- Continued publishing all Java artifacts and the Gradle plugin marker through Maven Central.

### Fixed

- Wait for forcibly terminated subprocesses to exit after a timeout so Windows test and release runs do not leave the working directory locked.

### Publishing

- Re-submitted the Gradle plugin publication after adding Gradle's requested DNS verification for `barney.media`.
- Kept Maven Central as the secondary Gradle plugin channel while Plugin Portal approval remains pending.

## 0.3.2 - 2026-04-05

### Added

- Published the Gradle plugin implementation artifact to Maven Central as a secondary channel.
- Published the Gradle plugin marker artifact to Maven Central so Gradle builds can resolve `media.barney.crap-java` via `pluginManagement.repositories`.

### Changed

- Updated the release workflow to upload the Gradle plugin Maven publications through Sonatype's OSSRH compatibility endpoint and finalize the deployment automatically.
- Expanded publishing preflight to build and sign the Gradle plugin's Maven publications locally.
- Documented the Maven Central plugin-consumption path in the README for use before Plugin Portal approval completes.

## 0.3.1 - 2026-04-05

### Fixed

- Kept published Maven artifact `url` and `scm` metadata pointed at the repository root instead of artifact-specific child paths.

### Changed

- Removed release-process details from the README so it stays consumer-facing.
- Updated the contribution guide to recommend repository-neutral branch names.
- Removed the bootstrap `spec.md` document.

## 0.3.0 - 2026-04-04

### Added

- JSpecify annotations and NullAway validation in CI.
- Coverage gate tests across the CLI, Gradle plugin, and Maven plugin.
- Apache License 2.0 metadata for published artifacts.

### Changed

- Improved changed-file detection, CLI analysis flow, and Java method parsing robustness.
- Refined CRAP analysis behavior, module resolution, and Maven plugin checks for multi-module projects.
- Updated the public project description and versioned CLI examples to match the `0.3.0` release.

### Publishing

- Switched public distribution to Maven Central and the Gradle Plugin Portal.
- Added release signing, Central Portal publishing, Plugin Portal publishing, and publishing preflight automation.
- GitHub releases are now generated from the matching `CHANGELOG.md` entry for the tagged version.
