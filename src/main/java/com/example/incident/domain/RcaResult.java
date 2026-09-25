package com.example.incident.domain;

import java.util.List;

public record RcaResult(String primaryCause, List<String> contributingFactors,
                        List<String> evidenceReferences, double confidence) {}
