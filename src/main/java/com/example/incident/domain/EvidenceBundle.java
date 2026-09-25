package com.example.incident.domain;

import java.util.List;

public record EvidenceBundle(List<Evidence> items, boolean complete) {}
