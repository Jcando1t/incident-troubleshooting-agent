package com.example.incident.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final String configuredApiKey;
    private final ObjectMapper objectMapper;

    public ApiKeyInterceptor(
            @Value("${app.security.api-key:}") String configuredApiKey,
            ObjectMapper objectMapper) {
        this.configuredApiKey = configuredApiKey;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws IOException {
        if (configuredApiKey == null || configuredApiKey.isBlank()) {
            writeError(
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "API_KEY_NOT_CONFIGURED",
                    "The server API key has not been configured"
            );
            return false;
        }

        var suppliedApiKey = request.getHeader(API_KEY_HEADER);
        if (suppliedApiKey == null || !MessageDigest.isEqual(
                configuredApiKey.getBytes(StandardCharsets.UTF_8),
                suppliedApiKey.getBytes(StandardCharsets.UTF_8))) {
            writeError(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "A valid X-API-Key header is required"
            );
            return false;
        }

        return true;
    }

    private void writeError(
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), new ApiError(code, message));
    }

    private record ApiError(String code, String message) {}
}
