package com.example.incident.domain;

import java.time.Instant;

public record Evidence(String source, String summary, String reference, Instant observedAt) {}
