package com.example.incident.agent;

import com.example.incident.domain.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Objects;

@Component
public class RcaAgent implements IncidentAgent<EvidenceBundle, RcaResult> {
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public RcaAgent(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    @Override public String name() { return "rca-agent"; }

    @Override
    public RcaResult execute(Incident incident, EvidenceBundle evidence) {
        var bundle = evidence == null ? new EvidenceBundle(List.of(), false) : evidence;
        var items = bundle.items() == null ? List.<Evidence>of() : bundle.items();
        var evidenceText = items.stream()
                .map(this::formatEvidence)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("（无可用证据）");
        var references = items.stream()
                .map(Evidence::reference)
                .filter(Objects::nonNull)
                .toList();

        var prompt = """
                你是研发故障排查平台的根因分析（RCA）专家。
                请根据事件信息和观测证据，判断最可能的根因，并说明影响因素。
                只能使用输入中的事实，不要编造不存在的变更、指标或依赖关系。
                evidenceReferences 只能从输入证据的 reference 原样选择；如果没有证据支持，返回空数组。

                事件标题：%s
                服务：%s
                环境：%s
                严重程度：%s
                事件描述：%s
                证据是否完整：%s
                观测证据：
                %s

                只返回 JSON，不要返回 Markdown 或其他说明文字：
                {
                  "primaryCause": "最可能的根因",
                  "contributingFactors": ["影响因素"],
                  "evidenceReferences": ["支持判断的证据 reference"],
                  "confidence": 0.0
                }

                confidence 必须是 0 到 1 之间的小数。
                """.formatted(
                incident.title(),
                incident.service(),
                incident.environment(),
                incident.severity(),
                incident.description(),
                bundle.complete(),
                evidenceText
        );

        var response = normalizeResponse(chatModel.chat(prompt));
        try {
            var result = objectMapper.readValue(response, RcaResult.class);
            validate(result);
            var citedReferences = result.evidenceReferences().stream()
                    .filter(references::contains)
                    .distinct()
                    .toList();
            return new RcaResult(
                    result.primaryCause().trim(),
                    result.contributingFactors().stream()
                            .filter(Objects::nonNull)
                            .map(String::trim)
                            .filter(value -> !value.isEmpty())
                            .toList(),
                    citedReferences,
                    result.confidence());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "RCA Agent 返回的内容不是合法 JSON: " + response, e);
        }
    }

    private String formatEvidence(Evidence evidence) {
        return "- source=" + evidence.source()
                + ", summary=" + evidence.summary()
                + ", reference=" + evidence.reference()
                + ", observedAt=" + evidence.observedAt();
    }

    private String normalizeResponse(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("RCA Agent 返回为空");
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

    private void validate(RcaResult result) {
        if (result == null || result.primaryCause() == null || result.primaryCause().isBlank()) {
            throw new IllegalStateException("RCA Agent 未返回有效的 primaryCause");
        }
        if (result.contributingFactors() == null || result.evidenceReferences() == null) {
            throw new IllegalStateException("RCA Agent 返回的列表字段不能为 null");
        }
        if (Double.isNaN(result.confidence()) || result.confidence() < 0 || result.confidence() > 1) {
            throw new IllegalStateException("RCA Agent 返回的 confidence 必须在 0 到 1 之间");
        }
    }
}
