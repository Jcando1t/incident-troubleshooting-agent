package com.example.incident.domain;

import java.util.List;

public record RemediationPlan(List<RemediationStep> steps, boolean requiresApproval) {}
