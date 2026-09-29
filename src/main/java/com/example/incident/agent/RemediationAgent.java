package com.example.incident.agent;

import com.example.incident.domain.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class RemediationAgent implements IncidentAgent<RcaResult, RemediationPlan> {
    private static final Pattern SAFE_PLACEHOLDER =
            Pattern.compile("\\{\\{PLACEHOLDER:[A-Z][A-Z0-9_]{0,63}\\}\\}");
    // This project currently defines no runbook registry; do not infer executable IDs from model output.
    private static final Set<String> ALLOWED_RUNBOOK_IDS = Set.of();

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public RemediationAgent(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    @Override public String name() { return "remediation-agent"; }

    @Override
    public RemediationPlan execute(Incident incident, RcaResult rca) {
        var evidence = incident == null || incident.evidence() == null
                ? new EvidenceBundle(List.of(), false)
                : incident.evidence();
        var evidenceText = evidence.items() == null || evidence.items().isEmpty()
                ? "（无可用证据）"
                : evidence.items().stream()
                .map(item -> "- source=" + item.source()
                        + ", summary=" + item.summary()
                        + ", reference=" + item.reference()
                        + ", observedAt=" + item.observedAt())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("（无可用证据）");

        var prompt = """
                你是生产系统故障修复专家。请根据事件、观测证据和根因分析生成可执行、可回滚的修复步骤。
                只能使用输入中的事实，不要编造不存在的资源、主机、版本或命令；命令只能使用形如 {{PLACEHOLDER:NAME}} 的占位符，或项目白名单中的 runbook 标识。
                每个步骤必须同时包含 description、command、rollbackCommand、risk 四个字段；risk 只能是 LOW、MEDIUM、HIGH。
                涉及删除数据、修改生产配置、切换流量、回滚发布、重启服务或其他可能造成中断的操作，risk 必须为 HIGH。
                至少先给出一个可验证的低风险步骤，再给出必要的修复步骤；所有步骤都必须提供对应 rollbackCommand。

                事件标题：%s
                服务：%s
                环境：%s
                严重程度：%s
                事件描述：%s
                证据是否完整：%s
                观测证据：
                %s
                根因：%s
                影响因素：%s
                RCA 置信度：%s

                只返回 JSON，不要返回 Markdown 或其他说明文字：
                {
                  "steps": [
                    {
                      "description": "修复或验证动作",
                      "command": "{{PLACEHOLDER:SAFE_ACTION}} 或白名单 runbook 标识",
                      "rollbackCommand": "{{PLACEHOLDER:ROLLBACK_ACTION}} 或白名单 runbook 标识",
                      "risk": "LOW|MEDIUM|HIGH"
                    }
                  ]
                }
                """.formatted(
                incident == null ? null : incident.title(),
                incident == null ? null : incident.service(),
                incident == null ? null : incident.environment(),
                incident == null ? null : incident.severity(),
                incident == null ? null : incident.description(),
                evidence.complete(),
                evidenceText,
                rca == null ? null : rca.primaryCause(),
                rca == null || rca.contributingFactors() == null
                        ? "[]" : rca.contributingFactors(),
                rca == null ? null : rca.confidence());

        var response = normalizeResponse(chatModel.chat(prompt));
        try {
            var parsed = objectMapper.readValue(response, RemediationResponse.class);
            if (parsed == null || parsed.steps() == null || parsed.steps().isEmpty()) {
                throw new IllegalStateException("Remediation Agent 未返回有效的 steps");
            }
            var steps = parsed.steps().stream().map(this::validateAndCopy).toList();
            if (steps.stream().noneMatch(step -> step.risk() == RiskLevel.LOW)) {
                throw new IllegalStateException(
                        "Remediation Agent 至少必须返回一个 LOW 风险步骤");
            }
            var requiresApproval = steps.stream().anyMatch(step -> step.risk() == RiskLevel.HIGH);
            return new RemediationPlan(steps, requiresApproval);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Remediation Agent 返回的内容不是合法 JSON: " + response, e);
        }
    }

    private RemediationStep validateAndCopy(RemediationStep step) {
        if (step == null || isBlank(step.description()) || isBlank(step.command())
                || isBlank(step.rollbackCommand()) || step.risk() == null) {
            throw new IllegalStateException(
                    "Remediation Agent 返回的每个步骤都必须包含 description、command、rollbackCommand、risk");
        }
        var command = step.command().trim();
        var rollbackCommand = step.rollbackCommand().trim();
        validateSafeCommand(command, "command");
        validateSafeCommand(rollbackCommand, "rollbackCommand");
        return new RemediationStep(step.description().trim(), command,
                rollbackCommand, step.risk());
    }

    private void validateSafeCommand(String value, String fieldName) {
        if (SAFE_PLACEHOLDER.matcher(value).matches() || ALLOWED_RUNBOOK_IDS.contains(value)) {
            return;
        }
        throw new IllegalStateException("Remediation Agent 返回了不安全或未识别的 " + fieldName
                + "；只允许 {{PLACEHOLDER:NAME}} 或项目白名单中的 runbook 标识");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeResponse(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("Remediation Agent 返回为空");
        }
        var normalized = response.trim();
        if (normalized.startsWith("```") && normalized.endsWith("```")) {
            var firstLineEnd = normalized.indexOf('\n');
            normalized = firstLineEnd >= 0
                    ? normalized.substring(firstLineEnd + 1, normalized.length() - 3).trim()
                    : normalized.substring(3, normalized.length() - 3).trim();
        }
        return normalized;
    }

    private record RemediationResponse(List<RemediationStep> steps) {}
}
