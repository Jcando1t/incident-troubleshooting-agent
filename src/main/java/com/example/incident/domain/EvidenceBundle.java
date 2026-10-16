package com.example.incident.domain;

import java.util.List;

public record EvidenceBundle(
        List<Evidence> items,
        boolean complete,
        List<InvestigationTrace> investigationTrace) {

    public EvidenceBundle(List<Evidence> items, boolean complete) {
        this(items, complete, List.of());
    }

    public EvidenceBundle {
        items = items == null ? List.of() : List.copyOf(items);
        investigationTrace = investigationTrace == null
                ? List.of()
                : List.copyOf(investigationTrace);
    }
}
