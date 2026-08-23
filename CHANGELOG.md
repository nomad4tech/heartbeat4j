# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [0.1.0] - 2026-08-23

### Added

- `ProblemRegistry` - report/clear a problem by key, heartbeats for
  silently hanging loops, `currentStatus()` snapshot.
- `PanicLevel` (`LOW` / `MEDIUM` / `HIGH` / `PERMANENT`) - escalation
  memory for recurring problems.
- `Severity`, `ProblemView`, `HealthStatusView`.
- Extracted from `tech.nomad4.telescopeone.health.engine` as a
  framework-agnostic, zero-Spring-dependency library.
- Build JDK for JitPack pinned to 17 via `jitpack.yml` (`record` types
  need JDK 16+; JitPack's default build image predates that).
