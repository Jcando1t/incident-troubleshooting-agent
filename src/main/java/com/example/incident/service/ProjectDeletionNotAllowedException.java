package com.example.incident.service;

public class ProjectDeletionNotAllowedException extends RuntimeException {
    public ProjectDeletionNotAllowedException() {
        super("The legacy compatibility project cannot be deleted");
    }
}
