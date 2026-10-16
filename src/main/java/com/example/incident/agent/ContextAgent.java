package com.example.incident.agent;

import com.example.incident.domain.*;
import com.example.incident.port.EvidenceSource;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class ContextAgent implements IncidentAgent<Void, EvidenceBundle> {
    private final List<EvidenceSource> sources;
    private final ChatModel chatModel;
    private final InvestigationAgent investigationAgent;

    public ContextAgent(List<EvidenceSource> sources, ChatModel chatModel) {
        this(sources, chatModel, null);
    }

    @Autowired
    public ContextAgent(InvestigationAgent investigationAgent) {
        this.sources = List.of();
        this.chatModel = null;
        this.investigationAgent = investigationAgent;
    }

    private ContextAgent(
            List<EvidenceSource> sources,
            ChatModel chatModel,
            InvestigationAgent investigationAgent) {
        this.sources = sources;
        this.chatModel = chatModel;
        this.investigationAgent = investigationAgent;
    }

    @Override public String name() { return "context-agent"; }

    @Override
    public EvidenceBundle execute(Incident incident, Void ignored) {
        if (investigationAgent != null) {
            return investigationAgent.execute(incident, ignored);
        }
        var evidence = sources.stream()
                .flatMap(source -> source.collect(incident).stream())
                .toList();

        if (evidence.isEmpty()) {
            return new EvidenceBundle(evidence, false);
        }

        var evidenceText = evidence.stream()
                .map(item -> item.source() + ": " + item.summary() + " (" + item.reference() + ")")
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

        var prompt = "你是研发故障排查平台的上下文分析Agent。\n"
                + "请根据事件和已有证据，用中文给出一段简洁的上下文摘要。\n"
                + "不要编造证据，只能使用输入内容。\n"
                + "事件：" + incident.title() + "，服务：" + incident.service()
                + "，环境：" + incident.environment() + "，描述：" + incident.description() + "\n"
                + "已有证据：\n" + evidenceText;

        var summary = chatModel.chat(prompt);
        var enrichedEvidence = new ArrayList<>(evidence);
        enrichedEvidence.add(new Evidence(
                "context-agent",
                summary,
                "llm://deepseek/context",
                Instant.now()));

        return new EvidenceBundle(enrichedEvidence, true);
    }
}
