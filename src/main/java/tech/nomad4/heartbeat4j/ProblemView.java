package tech.nomad4.heartbeat4j;

import java.time.Instant;

public record ProblemView(String key, String message, Severity severity, Instant since) {
}
