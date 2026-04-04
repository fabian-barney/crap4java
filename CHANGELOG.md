# Changelog

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
