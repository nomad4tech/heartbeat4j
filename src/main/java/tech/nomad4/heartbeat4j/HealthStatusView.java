package tech.nomad4.heartbeat4j;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record HealthStatusView(Instant startedAt, Duration uptime, List<ProblemView> problems) {
}
