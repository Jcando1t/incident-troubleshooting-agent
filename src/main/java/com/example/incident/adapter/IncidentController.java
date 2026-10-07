package com.example.incident.adapter;

import com.example.incident.domain.*;
import com.example.incident.service.IncidentStateConflictException;
import com.example.incident.service.IncidentOrchestrator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {
    private final IncidentOrchestrator orchestrator;
    public IncidentController(IncidentOrchestrator orchestrator) { this.orchestrator = orchestrator; }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Incident create(@Valid @RequestBody CreateIncidentRequest request) {
        return orchestrator.create(request.title(), request.service(), request.environment(),
                request.severity(), request.description());
    }

    @GetMapping("/{id}")
    public Incident get(@PathVariable UUID id) { return orchestrator.get(id); }

    @PostMapping("/{id}/approval")
    public Incident approval(@PathVariable UUID id, @Valid @RequestBody ApprovalRequest request) {
        return orchestrator.approve(id, request.approved(), request.comment());
    }

    @ExceptionHandler(IncidentStateConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleStateConflict(IncidentStateConflictException exception) {
        return new ErrorResponse("INCIDENT_STATE_CONFLICT", exception.getMessage());
    }

    public record CreateIncidentRequest(@NotBlank String title, @NotBlank String service,
                                        @NotBlank String environment, Severity severity,
                                        @NotBlank String description) {}
    public record ApprovalRequest(boolean approved, String comment) {}

    public record ErrorResponse(String code, String message) {}
}
