package com.example.incident.domain;

import java.time.Instant;

public record AuditEntry(Instant at, String actor, String action, String detail) {}
