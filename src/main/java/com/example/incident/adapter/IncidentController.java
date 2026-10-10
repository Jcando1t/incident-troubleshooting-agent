package com.example.incident.adapter;

import com.example.incident.domain.*;
import com.example.incident.service.IncidentStateConflictException;
import com.example.incident.service.IncidentNotFoundException;
import com.example.incident.service.IncidentOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/incidents")
@Tag(name = "故障事件", description = "接收、查询和审批故障事件")
public class IncidentController {
    private final IncidentOrchestrator orchestrator;
    public IncidentController(IncidentOrchestrator orchestrator) { this.orchestrator = orchestrator; }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "创建并分析故障事件", description = "提交事件后，系统会依次执行分诊、证据收集、根因分析和修复建议生成。")
    public Incident create(@Valid @RequestBody CreateIncidentRequest request) {
        return orchestrator.create(request.title(), request.service(), request.environment(),
                request.severity(), request.description());
    }

    @GetMapping("/{id}")
    @Operation(summary = "按 ID 查询故障事件")
    public Incident get(@PathVariable UUID id) { return orchestrator.get(id); }

    @GetMapping
    @Operation(summary = "查询最近的故障事件", description = "可以按状态和服务名称筛选，并使用 limit、offset 控制分页。")
    public List<Incident> findRecent(
            @RequestParam(defaultValue = "20")
            int limit,
            @RequestParam(defaultValue = "0")
            int offset,
            @RequestParam(required = false)
            IncidentStatus status,
            @RequestParam(required = false)
            String service) {
        if (limit < 1 || limit > 100 || offset < 0 || offset > 100_000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "limit must be between 1 and 100, and offset between 0 and 100000"
            );
        }
        if (service != null && service.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "service cannot be blank"
            );
        }
        return orchestrator.findRecent(limit, offset, status, service);
    }

    @PostMapping("/{id}/approval")
    @Operation(summary = "审批或拒绝修复方案", description = "只有处于 AWAITING_APPROVAL 状态的事件可以审批。")
    public Incident approval(@PathVariable UUID id, @Valid @RequestBody ApprovalRequest request) {
        return orchestrator.approve(id, request.approved(), request.comment());
    }

    @ExceptionHandler(IncidentStateConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleStateConflict(IncidentStateConflictException exception) {
        return new ErrorResponse("INCIDENT_STATE_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(IncidentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleIncidentNotFound(IncidentNotFoundException exception) {
        return new ErrorResponse("INCIDENT_NOT_FOUND", exception.getMessage());
    }

    public record CreateIncidentRequest(@NotBlank String title, @NotBlank String service,
                                        @NotBlank String environment, Severity severity,
                                        @NotBlank String description) {}
    public record ApprovalRequest(boolean approved, String comment) {}

    public record ErrorResponse(String code, String message) {}
}
