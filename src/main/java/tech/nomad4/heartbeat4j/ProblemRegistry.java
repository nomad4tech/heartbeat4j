package tech.nomad4.heartbeat4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Framework-agnostic in-memory problem registry - no Spring, constructible via {@code new}.
 * Callers report/clear problems by key; a cleared problem re-reported within its
 * {@link PanicLevel#memoryWindow()} escalates to {@link Severity#ALARM} instead of starting
 * fresh as {@link Severity#NORMAL}. Nothing here survives a process restart by design.
 */
public class ProblemRegistry {

    private final Clock clock;
    private final Instant startedAt;
    private final Map<String, ProblemRecord> problems = new ConcurrentHashMap<>();
    private final Map<String, HeartbeatRecord> heartbeats = new ConcurrentHashMap<>();

    public ProblemRegistry(Clock clock) {
        this.clock = clock;
        this.startedAt = clock.instant();
    }

    public void reportProblem(String key, String message, PanicLevel panicLevel) {
        Instant now = clock.instant();
        problems.compute(key, (k, existing) -> {
            if (existing != null && existing.active) {
                return existing.withMessage(message);
            }
            boolean recurrence = existing != null && existing.panicLevel.memoryWindow()
                    .map(window -> now.isBefore(existing.clearedAt.plus(window)))
                    .orElse(true);
            return new ProblemRecord(message, panicLevel, true,
                    recurrence ? Severity.ALARM : Severity.NORMAL, now, null);
        });
    }

    public void clearProblem(String key) {
        Instant now = clock.instant();
        problems.computeIfPresent(key, (k, existing) -> existing.active ? existing.cleared(now) : existing);
    }

    public void heartbeat(String key, Duration maxSilence) {
        heartbeats.put(key, new HeartbeatRecord(clock.instant(), maxSilence));
    }

    public HealthStatusView currentStatus() {
        Instant now = clock.instant();
        List<ProblemView> active = new ArrayList<>();
        problems.forEach((key, record) -> {
            if (record.active) {
                active.add(new ProblemView(key, record.message, record.severity, record.since));
            }
        });
        heartbeats.forEach((key, hb) -> {
            if (now.isAfter(hb.lastSeenAt.plus(hb.maxSilence))) {
                active.add(new ProblemView("heartbeat:" + key,
                        "no heartbeat since " + hb.lastSeenAt, Severity.ALARM, hb.lastSeenAt));
            }
        });
        return new HealthStatusView(startedAt, Duration.between(startedAt, now), List.copyOf(active));
    }

    private record ProblemRecord(String message, PanicLevel panicLevel, boolean active,
                                  Severity severity, Instant since, Instant clearedAt) {
        ProblemRecord withMessage(String newMessage) {
            return new ProblemRecord(newMessage, panicLevel, active, severity, since, clearedAt);
        }

        ProblemRecord cleared(Instant clearedAt) {
            return new ProblemRecord(message, panicLevel, false, severity, since, clearedAt);
        }
    }

    private record HeartbeatRecord(Instant lastSeenAt, Duration maxSilence) {
    }
}
