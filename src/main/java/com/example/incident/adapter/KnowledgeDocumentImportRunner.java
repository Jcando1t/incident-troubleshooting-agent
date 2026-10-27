package com.example.incident.adapter;

import com.example.incident.domain.KnowledgeDocument;
import com.example.incident.domain.Project;
import com.example.incident.service.KnowledgeDocumentService;
import com.example.incident.service.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 启动时把指定目录下的 Markdown 知识资料批量导入项目知识库，并自动建立向量索引。
 *
 * 只在 knowledge-import profile 下生效，普通启动不会触发：
 *   mvn spring-boot:run -Dspring-boot.run.profiles=knowledge-import
 *
 * 可配置项见 application.yml 的 app.knowledge.import（项目名、资料目录）。
 * 幂等：按资料标题去重，重复执行不会产生重复资料，也不会重复建索引。
 */
@Component
@Profile("knowledge-import")
public class KnowledgeDocumentImportRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentImportRunner.class);

    private final ProjectService projectService;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final String projectName;
    private final String directory;

    public KnowledgeDocumentImportRunner(
            ProjectService projectService,
            KnowledgeDocumentService knowledgeDocumentService,
            @Value("${app.knowledge.import.project-name:求职 Agent}") String projectName,
            @Value("${app.knowledge.import.dir:knowledge-import/job-agent-documents}") String directory) {
        this.projectService = projectService;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.projectName = projectName;
        this.directory = directory;
    }

    @Override
    public void run(String... args) {
        Path dir = Path.of(directory).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            log.warn("知识资料目录不存在，跳过导入：{}", dir);
            return;
        }

        List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException exception) {
            log.warn("无法读取知识资料目录 {}：{}", dir, exception.getMessage());
            return;
        }
        if (files.isEmpty()) {
            log.warn("目录中没有 .md 知识资料：{}", dir);
            return;
        }

        Project project = projectService.findAll().stream()
                .filter(item -> item.name().equals(projectName))
                .findFirst()
                .orElseGet(() -> {
                    Project created = projectService.create(projectName);
                    log.info("已登记项目：{}（ID: {}）", created.name(), created.id());
                    return created;
                });

        Set<String> existingTitles = new HashSet<>();
        for (KnowledgeDocument document : knowledgeDocumentService.findByProjectId(project.id())) {
            existingTitles.add(document.title());
        }

        int created = 0;
        int skipped = 0;
        int failed = 0;
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            String title = fileName.substring(0, fileName.length() - ".md".length());
            if (existingTitles.contains(title)) {
                skipped++;
                continue;
            }
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                KnowledgeDocument document = knowledgeDocumentService.create(project.id(), title, content);
                KnowledgeDocumentService.EmbeddingResult result =
                        knowledgeDocumentService.reindex(project.id(), document.id());
                created++;
                log.info("已导入并建索引：{}（{} 段向量，模型 {}）", title, result.chunks(), result.model());
            } catch (RuntimeException exception) {
                failed++;
                log.warn("导入失败：{} —— {}", title, exception.getMessage());
            } catch (IOException exception) {
                failed++;
                log.warn("读取失败：{} —— {}", title, exception.getMessage());
            }
        }

        log.info("知识资料导入完成：项目 {}（ID: {}），新建 {} 份，跳过 {} 份，失败 {} 份。",
                project.name(), project.id(), created, skipped, failed);
    }
}
