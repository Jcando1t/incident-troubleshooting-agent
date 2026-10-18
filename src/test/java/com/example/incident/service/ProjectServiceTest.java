package com.example.incident.service;

import com.example.incident.domain.Project;
import com.example.incident.port.ProjectRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectServiceTest {
    private static final UUID PROJECT_ID = UUID.fromString("52e50924-a446-44f9-84f9-3ee9c4746a18");
    private static final UUID LEGACY_PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void deletesAnEmptyProject() {
        var repository = mock(ProjectRepository.class);
        when(repository.deleteIfEmpty(PROJECT_ID)).thenReturn(true);

        new ProjectService(repository).delete(PROJECT_ID);

        verify(repository).deleteIfEmpty(PROJECT_ID);
    }

    @Test
    void rejectsDeletingTheLegacyCompatibilityProject() {
        var repository = mock(ProjectRepository.class);

        assertThrows(ProjectDeletionNotAllowedException.class,
                () -> new ProjectService(repository).delete(LEGACY_PROJECT_ID));
    }

    @Test
    void rejectsDeletingAProjectThatStillHasData() {
        var repository = mock(ProjectRepository.class);
        when(repository.deleteIfEmpty(PROJECT_ID)).thenReturn(false);
        when(repository.findById(PROJECT_ID)).thenReturn(Optional.of(
                new Project(PROJECT_ID, "Used project", Instant.now())));

        assertThrows(ProjectInUseException.class,
                () -> new ProjectService(repository).delete(PROJECT_ID));
    }

    @Test
    void reportsWhenTheProjectDoesNotExist() {
        var repository = mock(ProjectRepository.class);
        when(repository.deleteIfEmpty(PROJECT_ID)).thenReturn(false);
        when(repository.findById(PROJECT_ID)).thenReturn(Optional.empty());

        assertThrows(ProjectNotFoundException.class,
                () -> new ProjectService(repository).delete(PROJECT_ID));
    }
}
