package com.example.incident.adapter;

import com.example.incident.domain.*;
import com.example.incident.service.IncidentStateConflictException;
import com.example.incident.service.IncidentNotFoundException;
import com.example.incident.service.IncidentOrchestrator;
import com.example.incident.service.ProjectNotFoundException;
import com.example.incident.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/incidents")
@Tag(name = "故障事件", description = "接收、查询和审批故障事件")
public class IncidentController {
    private final IncidentOrchestrator orchestrator;
    private final ProjectService projectService;

    public IncidentController(
            IncidentOrchestrator orchestrator,
            ProjectService projectService) {
        this.orchestrator = orchestrator;
        this.projectService = projectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "创建并分析故障事件", description = "提交事件后，系统会依次执行分诊、证据收集、根因分析和修复建议生成。")
    public Incident create(@Valid @RequestBody CreateIncidentRequest request) {
        projectService.get(request.projectId());
        return orchestrator.create(request.projectId(), request.title(), request.service(), request.environment(),
                request.severity(), request.description());
    }

    @GetMapping("/{id}")
    @Operation(summary = "按 ID 查询故障事件", description = "可选 projectId 用于确认事件属于指定项目")
    public Incident get(
            @PathVariable UUID id,
            @Parameter(description = "可选项目 ID；传入后会校验事件归属")
            @RequestParam(required = false) UUID projectId) {
        if (projectId == null) {
            return orchestrator.get(id);
        }
        projectService.get(projectId);
        return orchestrator.get(projectId, id);
    }

    @GetMapping
    @Operation(summary = "查询最近的故障事件", description = "可以按状态、服务名称和创建时间范围筛选，并使用 limit、offset 控制分页。")
    public List<Incident> findRecent(
            @RequestParam(defaultValue = "20")
            int limit,
            @RequestParam(defaultValue = "0")
            int offset,
            @RequestParam(required = false)
            IncidentStatus status,
            @RequestParam(required = false)
            String service,
            @Parameter(description = "按项目 ID 筛选，避免混合查看不同项目的事件")
            @RequestParam(required = false)
            UUID projectId,
            @Parameter(description = "创建时间起点（包含），ISO-8601 格式，例如 2026-10-01T00:00:00Z")
            @RequestParam(required = false)
            Instant createdFrom,
            @Parameter(description = "创建时间终点（包含），ISO-8601 格式，例如 2026-10-10T23:59:59Z")
            @RequestParam(required = false)
            Instant createdTo) {
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
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "createdFrom must be earlier than or equal to createdTo"
            );
        }
        if (projectId == null) {
            return orchestrator.findRecent(
                    limit, offset, status, service, createdFrom, createdTo);
        }
        // 先确认项目存在，避免“项目 ID 写错”和“项目暂无事件”都返回空数组。
        projectService.get(projectId);
        return orchestrator.findRecent(
                projectId, limit, offset, status, service, createdFrom, createdTo);
    }

    @PostMapping("/{id}/approval")
    @Operation(summary = "审批或拒绝修复方案", description = "只有处于 AWAITING_APPROVAL 状态的事件可以审批。")
    public Incident approval(
            @PathVariable UUID id,
            @Parameter(description = "可选项目 ID；传入后会校验事件归属")
            @RequestParam(required = false) UUID projectId,
            @Valid @RequestBody ApprovalRequest request) {
        if (projectId != null) {
            projectService.get(projectId);
            return orchestrator.approve(projectId, id, request.approved(), request.comment());
        }
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

    @ExceptionHandler(ProjectNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleProjectNotFound(ProjectNotFoundException exception) {
        return new ErrorResponse("PROJECT_NOT_FOUND", exception.getMessage());
    }

    public record CreateIncidentRequest(@NotNull UUID projectId, @NotBlank String title, @NotBlank String service,
                                        @NotBlank String environment, Severity severity,
                                        @NotBlank String description) {}
    public record ApprovalRequest(boolean approved, String comment) {}

    public record ErrorResponse(String code, String message) {}
}
