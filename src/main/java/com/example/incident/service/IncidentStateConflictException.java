package com.example.incident.service;

public class IncidentStateConflictException extends RuntimeException {
    public IncidentStateConflictException(String message) {
        super(message);
    }
}
