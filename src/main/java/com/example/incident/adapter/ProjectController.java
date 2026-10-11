package com.example.incident.adapter;

import com.example.incident.domain.Project;
import com.example.incident.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "项目", description = "登记和查询接入故障排查平台的项目")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "登记项目", description = "创建项目后会返回稳定的项目 ID，创建故障事件时需要提供这个 ID。")
    public Project create(@Valid @RequestBody CreateProjectRequest request) {
        return projectService.create(request.name());
    }

    @GetMapping
    @Operation(summary = "查询已登记项目")
    public List<Project> findAll() {
        return projectService.findAll();
    }

    public record CreateProjectRequest(@NotBlank String name) {}
}
