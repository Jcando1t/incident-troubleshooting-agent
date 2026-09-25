package com.example.incident.domain;

import java.util.List;

public record TriageResult(Severity severity, List<String> affectedServices,
                           String blastRadius, double confidence) {}
