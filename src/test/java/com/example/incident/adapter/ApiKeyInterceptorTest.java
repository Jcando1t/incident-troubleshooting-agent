package com.example.incident.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyInterceptorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void allowsRequestWhenApiKeyMatches() throws Exception {
        var interceptor = new ApiKeyInterceptor("test-secret", objectMapper);
        var request = requestWithApiKey("test-secret");
        var response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void rejectsRequestWhenApiKeyIsMissing() throws Exception {
        var interceptor = new ApiKeyInterceptor("test-secret", objectMapper);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("UNAUTHORIZED"));
    }

    @Test
    void rejectsRequestWhenApiKeyDoesNotMatch() throws Exception {
        var interceptor = new ApiKeyInterceptor("test-secret", objectMapper);
        var request = requestWithApiKey("wrong-secret");
        var response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
        assertFalse(response.getContentAsString().contains("wrong-secret"));
    }

    @Test
    void rejectsApiRequestsWhenServerKeyIsNotConfigured() throws Exception {
        var interceptor = new ApiKeyInterceptor(" ", objectMapper);
        var request = requestWithApiKey("test-secret");
        var response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("API_KEY_NOT_CONFIGURED"));
    }

    private MockHttpServletRequest requestWithApiKey(String apiKey) {
        var request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", apiKey);
        return request;
    }
}
