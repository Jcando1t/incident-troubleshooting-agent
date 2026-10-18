package com.example.incident.service;

import java.util.UUID;

public class ProjectInUseException extends RuntimeException {
    public ProjectInUseException(UUID id) {
        super("Project has incidents or knowledge data and cannot be deleted: " + id);
    }
}
