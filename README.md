# heartbeat4j

[![Build](https://github.com/nomad4tech/heartbeat4j/actions/workflows/publish.yml/badge.svg)](https://github.com/nomad4tech/heartbeat4j/actions/workflows/publish.yml)
[![Maven](https://img.shields.io/badge/maven-0.1.0-blue)](https://github.com/nomad4tech/heartbeat4j/packages)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A tiny, framework-agnostic library for tracking application health: a
self-cleaning registry of problems, plus heartbeats for background loops
that can silently hang without throwing.

No Spring dependency - construct it with `new ProblemRegistry(Clock)` and
wrap it in a `@Bean` (or equivalent) yourself.

## Why

Most "health check" setups answer one question: is the process alive.
They don't answer "did today's sync job actually run" or "is this
background poller still ticking, or did it die silently three hours ago
without an exception to log." heartbeat4j targets that second class of
problem:

- **Report/clear by key** - the code that detects a problem reports it;
  the same code clears it on the next success. No separate janitor
  process needed.
- **Heartbeats for silent failures** - a loop calls `heartbeat(key,
  maxSilence)` on every successful tick. If it stops ticking, the
  registry surfaces that on its own, without the loop having to throw.
- **Escalation memory** - a problem that keeps recurring after being
  cleared is reported as `ALARM` instead of quietly resetting to
  `NORMAL` each time. How long that memory lasts is configurable per
  problem via `PanicLevel`.

## Installation

Not published to Maven Central. Published to GitHub Packages on every
`v*` tag:

```xml
<dependency>
    <groupId>tech.nomad4</groupId>
    <artifactId>heartbeat4j</artifactId>
    <version>0.1.0</version>
</dependency>
```

> Requires a GitHub token. No token needed via [JitPack](https://jitpack.io)
> instead - see [Package registry setup](#package-registry-setup) below.

## Quick start

```java
ProblemRegistry registry = new ProblemRegistry(Clock.systemUTC());

// somewhere a sync job fails
registry.reportProblem("orders-sync", "upstream returned 500", PanicLevel.MEDIUM);

// and clears itself on the next successful run
registry.clearProblem("orders-sync");

// a loop that ticks periodically proves it's alive on every tick
registry.heartbeat("orders-sync-loop", Duration.ofMinutes(5));

HealthStatusView status = registry.currentStatus();
// status.problems() is empty only when nothing is reported AND every
// heartbeat has ticked within its maxSilence window
```

## Core concepts

- **`ProblemRegistry`** - the registry itself: `reportProblem`,
  `clearProblem`, `heartbeat`, `currentStatus`. Fully in-memory, nothing
  survives a process restart - that's intentional, restarts should start
  clean.
- **`PanicLevel`** (`LOW` / `MEDIUM` / `HIGH` / `PERMANENT`) - how long a
  cleared problem is still "remembered." A re-report inside that window
  escalates to `Severity.ALARM` instead of starting fresh as `NORMAL`.
  `PERMANENT` never forgets: any recurrence, however distant, is an
  alarm.
- **`HealthStatusView`** - a snapshot of the whole registry: `startedAt`,
  `uptime`, and the list of currently active problems. Present even when
  the problem list is empty, so a consumer can always see uptime (useful
  for spotting restart loops).
- **`ProblemView`** - a snapshot of one active problem: `key`, `message`,
  `severity`, `since`.

A missed heartbeat surfaces in `currentStatus()` as a synthetic problem
keyed `heartbeat:<key>` with `Severity.ALARM` - no separate API to poll
for it.

## Spring integration

```java
@Configuration
class HealthConfig {
    @Bean
    ProblemRegistry problemRegistry() {
        return new ProblemRegistry(Clock.systemUTC());
    }
}
```

Then expose it however you like, e.g. a controller returning 200 when
`problems()` is empty and 503 otherwise:

```java
@GetMapping("/health")
ResponseEntity<HealthStatusView> health() {
    HealthStatusView status = problemRegistry.currentStatus();
    return status.problems().isEmpty()
            ? ResponseEntity.ok(status)
            : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(status);
}
```

## Package registry setup

### GitHub Packages

Requires a GitHub account and a personal access token with `read:packages`
scope (GitHub -> Settings -> Developer settings -> Personal access tokens).

Add the repository to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/nomad4tech/heartbeat4j</url>
    </repository>
</repositories>
```

Add credentials to `~/.m2/settings.xml`:

```xml
<servers>
    <server>
        <id>github</id>
        <username>YOUR_GITHUB_USERNAME</username>
        <password>YOUR_GITHUB_TOKEN</password>
    </server>
</servers>
```

### JitPack

No token or credentials required. Add the repository and use the JitPack
`groupId`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

```xml
<dependency>
    <groupId>com.github.nomad4tech</groupId>
    <artifactId>heartbeat4j</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Known limitations

- Registry is fully in-memory - nothing survives a process restart. This
  is intentional (restarts should start clean), not a gap to fix.
- Single JVM only - no shared state across instances, no distributed
  locking. Each process tracks its own problems.
- Java 17 required. Not on Maven Central - GitHub Packages (token
  required) or JitPack (no token) only.

## License

MIT - see [LICENSE](LICENSE) in repository root.
