package com.example.incident.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LangChain4jConfig {

    @Bean
    public ChatModel chatLanguageModel(
            @Value("${app.llm.api-key}") String apiKey,
            @Value("${app.llm.base-url}") String baseUrl,
            @Value("${app.llm.model}") String modelName,
            @Value("${app.llm.timeout:30s}") Duration timeout,
            @Value("${app.llm.max-retries:1}") int maxRetries) {

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .build();
    }
}
