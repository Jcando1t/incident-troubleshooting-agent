package com.example.incident.port;

import com.example.incident.domain.Project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository {
    Project save(Project project);
    Optional<Project> findById(UUID id);
    List<Project> findAll();
    boolean deleteIfEmpty(UUID id);
}
