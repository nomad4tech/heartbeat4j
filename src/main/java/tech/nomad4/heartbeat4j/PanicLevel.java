package tech.nomad4.heartbeat4j;

import java.time.Duration;
import java.util.Optional;

/**
 * How long the registry remembers a cleared problem - a re-report within this window
 * escalates severity to {@link Severity#ALARM} instead of starting fresh as
 * {@link Severity#NORMAL}. {@link #PERMANENT} never forgets: any recurrence, however
 * distant, is treated as an alarm.
 */
public enum PanicLevel {
    LOW(Duration.ofMinutes(15)),
    MEDIUM(Duration.ofHours(2)),
    HIGH(Duration.ofHours(24)),
    PERMANENT(null);

    private final Duration memoryWindow;

    PanicLevel(Duration memoryWindow) {
        this.memoryWindow = memoryWindow;
    }

    public Optional<Duration> memoryWindow() {
        return Optional.ofNullable(memoryWindow);
    }
}
