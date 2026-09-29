package com.example.incident.agent;

import com.example.incident.domain.Incident;
import com.example.incident.domain.TriageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Component;

@Component
public class TriageAgent implements IncidentAgent<Void, TriageResult> {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public TriageAgent(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "triage-agent";
    }

    @Override
    public TriageResult execute(Incident incident, Void ignored) {
        String prompt = """
                你是研发故障分诊专家。
                请根据下面的故障信息判断严重程度和影响范围。

                故障标题：%s
                服务：%s
                环境：%s
                原始严重程度：%s
                描述：%s

                只返回 JSON，不要返回 Markdown：
                {
                  "severity": "P0|P1|P2|P3",
                  "affectedServices": ["服务名"],
                  "blastRadius": "影响范围",
                  "confidence": 0.0
                }

                confidence 必须是 0 到 1 之间的小数。
                """.formatted(
                incident.title(),
                incident.service(),
                incident.environment(),
                incident.severity(),
                incident.description()
        );

        String response = chatModel.chat(prompt)
                .replace("```json", "")
                .replace("```", "")
                .trim();

        try {
            return objectMapper.readValue(response, TriageResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Triage Agent 返回的内容不是合法 JSON: " + response, e
            );
        }
    }
}