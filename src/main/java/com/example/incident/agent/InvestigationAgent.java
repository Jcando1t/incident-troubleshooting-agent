package com.example.incident.agent;

import com.example.incident.domain.Evidence;
import com.example.incident.domain.EvidenceBundle;
import com.example.incident.domain.Incident;
import com.example.incident.port.EvidenceSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A bounded, read-only investigation loop. The model may choose only one of
 * the registered evidence sources, or STOP; it cannot provide an executable
 * command or an arbitrary URL.
 */
@Component
public class InvestigationAgent implements IncidentAgent<Void, EvidenceBundle> {
    private static final Logger log = LoggerFactory.getLogger(InvestigationAgent.class);
    private static final String STOP = "STOP";

    private final Map<String, EvidenceSource> sources;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final int maxSteps;

    public InvestigationAgent(
            List<EvidenceSource> evidenceSources,
            ChatModel chatModel,
            ObjectMapper objectMapper,
            @Value("${app.investigation.max-steps:3}") int maxSteps) {
        if (maxSteps < 1 || maxSteps > 5) {
            throw new IllegalArgumentException("app.investigation.max-steps must be between 1 and 5");
        }
        this.sources = evidenceSources.stream()
                .collect(Collectors.toUnmodifiableMap(EvidenceSource::name, Function.identity()));
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.maxSteps = maxSteps;
    }

    @Override
    public String name() {
        return "investigation-agent";
    }

    @Override
    public EvidenceBundle execute(Incident incident, Void ignored) {
        var collected = new ArrayList<Evidence>();
        var usedTools = new HashSet<String>();

        for (var step = 0; step < maxSteps; step++) {
            var decision = decideNextTool(incident, collected, usedTools);
            if (decision == null || STOP.equals(decision.tool())) {
                break;
            }
            if (!sources.containsKey(decision.tool()) || !usedTools.add(decision.tool())) {
                log.warn("Investigation Agent stopped on an invalid or repeated tool: {}", decision.tool());
                break;
            }
            collected.addAll(sources.get(decision.tool()).collect(incident));
        }

        if (collected.isEmpty()) {
            return new EvidenceBundle(List.of(), false);
        }

        var evidenceText = collected.stream()
                .map(item -> item.source() + ": " + item.summary() + " (" + item.reference() + ")")
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        var summaryPrompt = "你是研发故障排查平台的上下文分析Agent。\n"
                + "请根据事件和已有证据，用中文给出一段简洁的上下文摘要。\n"
                + "不要编造证据，只能使用输入内容。\n"
                + "事件：" + incident.title() + "，服务：" + incident.service()
                + "，环境：" + incident.environment() + "，描述：" + incident.description() + "\n"
                + "已有证据：\n" + evidenceText;
        collected.add(new Evidence(
                "context-agent",
                chatModel.chat(summaryPrompt),
                "llm://deepseek/context",
                Instant.now()));
        return new EvidenceBundle(List.copyOf(collected), true);
    }

    private Decision decideNextTool(Incident incident, List<Evidence> collected, HashSet<String> usedTools) {
        var available = sources.keySet().stream().sorted().toList();
        var prompt = """
                你是一个受限的故障调查Agent。你只能从允许的只读工具中选择下一个工具，或者返回 STOP。
                不要返回命令、URL、SQL 或其他工具名。每个工具最多调用一次。
                允许的工具：%s
                已使用的工具：%s
                事件：%s，服务：%s，环境：%s，描述：%s
                已收集证据：%s
                只返回 JSON：{"tool":"工具名或STOP","reason":"简短理由"}
                """.formatted(
                available,
                usedTools,
                incident.title(),
                incident.service(),
                incident.environment(),
                incident.description(),
                collected.stream().map(Evidence::summary).toList());
        try {
            var response = chatModel.chat(prompt);
            return objectMapper.readValue(normalize(response), Decision.class);
        } catch (JsonProcessingException | RuntimeException exception) {
            log.warn("Investigation Agent could not parse its next-tool decision: {}",
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    private String normalize(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("Investigation Agent returned an empty decision");
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

    private record Decision(String tool, String reason) {}
}
