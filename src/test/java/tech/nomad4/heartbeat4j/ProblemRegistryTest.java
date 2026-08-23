package tech.nomad4.heartbeat4j;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No Spring context - {@link ProblemRegistry} is constructed via {@code new} with a
 * mutable {@link Clock} so TTL/escalation timing can be moved forward deterministically.
 */
class ProblemRegistryTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };
    private final ProblemRegistry registry = new ProblemRegistry(clock);

    private void advance(Duration d) {
        now.updateAndGet(i -> i.plus(d));
    }

    @Test
    void freshProblem_isNormal() {
        registry.reportProblem("tdapi-down", "connection refused", PanicLevel.HIGH);

        assertThat(problem("tdapi-down").severity()).isEqualTo(Severity.NORMAL);
    }

    @Test
    void reportingWhileStillActive_refreshesMessageWithoutEscalating() {
        registry.reportProblem("tdapi-down", "first", PanicLevel.HIGH);
        registry.reportProblem("tdapi-down", "second", PanicLevel.HIGH);

        ProblemView p = problem("tdapi-down");
        assertThat(p.message()).isEqualTo("second");
        assertThat(p.severity()).isEqualTo(Severity.NORMAL);
    }

    @Test
    void clearedProblem_disappearsFromStatus() {
        registry.reportProblem("tdapi-down", "boom", PanicLevel.LOW);
        registry.clearProblem("tdapi-down");

        assertThat(registry.currentStatus().problems()).isEmpty();
    }

    @Test
    void lowPanic_recurrenceAfterMemoryWindow_isTreatedAsFresh() {
        registry.reportProblem("blip", "boom", PanicLevel.LOW);
        registry.clearProblem("blip");
        advance(Duration.ofMinutes(16));

        registry.reportProblem("blip", "boom again", PanicLevel.LOW);

        assertThat(problem("blip").severity()).isEqualTo(Severity.NORMAL);
    }

    @Test
    void lowPanic_recurrenceWithinMemoryWindow_escalates() {
        registry.reportProblem("blip", "boom", PanicLevel.LOW);
        registry.clearProblem("blip");
        advance(Duration.ofMinutes(5));

        registry.reportProblem("blip", "boom again", PanicLevel.LOW);

        assertThat(problem("blip").severity()).isEqualTo(Severity.ALARM);
    }

    @Test
    void highPanic_recurrenceWithinADay_escalates() {
        registry.reportProblem("token-expired", "401", PanicLevel.HIGH);
        registry.clearProblem("token-expired");
        advance(Duration.ofHours(23));

        registry.reportProblem("token-expired", "401 again", PanicLevel.HIGH);

        assertThat(problem("token-expired").severity()).isEqualTo(Severity.ALARM);
    }

    @Test
    void permanent_neverForgets_evenAfterYears() {
        registry.reportProblem("db-corruption", "detected", PanicLevel.PERMANENT);
        registry.clearProblem("db-corruption");
        advance(Duration.ofDays(365 * 10));

        registry.reportProblem("db-corruption", "detected again", PanicLevel.PERMANENT);

        assertThat(problem("db-corruption").severity()).isEqualTo(Severity.ALARM);
    }

    @Test
    void distinctKeys_areIndependent() {
        registry.reportProblem("a", "boom", PanicLevel.HIGH);

        assertThat(registry.currentStatus().problems()).hasSize(1);
        assertThat(registry.currentStatus().problems().get(0).key()).isEqualTo("a");
    }

    @Test
    void heartbeat_freshWithinMaxSilence_reportsNoProblem() {
        registry.heartbeat("monitor-loop", Duration.ofMinutes(3));
        advance(Duration.ofMinutes(2));

        assertThat(registry.currentStatus().problems()).isEmpty();
    }

    @Test
    void heartbeat_staleBeyondMaxSilence_becomesAlarmProblem() {
        registry.heartbeat("monitor-loop", Duration.ofMinutes(3));
        advance(Duration.ofMinutes(4));

        List<ProblemView> problems = registry.currentStatus().problems();
        assertThat(problems).hasSize(1);
        assertThat(problems.get(0).key()).isEqualTo("heartbeat:monitor-loop");
        assertThat(problems.get(0).severity()).isEqualTo(Severity.ALARM);
    }

    @Test
    void status_reportsUptimeSinceConstruction() {
        advance(Duration.ofSeconds(90));

        assertThat(registry.currentStatus().uptime()).isEqualTo(Duration.ofSeconds(90));
    }

    private ProblemView problem(String key) {
        return registry.currentStatus().problems().stream()
                .filter(p -> p.key().equals(key))
                .findFirst()
                .orElseThrow();
    }
}
