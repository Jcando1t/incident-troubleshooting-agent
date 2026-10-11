package com.example.incident.service;

import com.example.incident.domain.Project;
import com.example.incident.port.ProjectRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {
    private final ProjectRepository repository;

    public ProjectService(ProjectRepository repository) {
        this.repository = repository;
    }

    public Project create(String name) {
        return repository.save(new Project(UUID.randomUUID(), name.trim(), Instant.now()));
    }

    public Project get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ProjectNotFoundException(id));
    }

    public List<Project> findAll() {
        return repository.findAll();
    }
}
