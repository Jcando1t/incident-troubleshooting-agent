package com.example.incident.domain;

public record RemediationStep(String description, String command, String rollbackCommand,
                              RiskLevel risk) {}
