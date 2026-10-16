package com.example.incident.domain;

import java.time.Instant;

/** A safe, non-secret record of one bounded investigation decision. */
public record InvestigationTrace(
        int step,
        String tool,
        String outcome,
        String reason,
        Instant at) {
}
