package com.example.backend.common.error;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    @Test
    void unexpectedFailureKeepsSafeMessageAndRequestContext() {
        var response = new GlobalExceptionHandler().handleUnexpected(
                new IllegalStateException("private-password"), new MockHttpServletRequest("GET", "/example"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody().getDetail()).isEqualTo("An unexpected error occurred.");
        assertThat(response.getBody().getProperties()).containsEntry("errorCode", "INTERNAL_ERROR")
                .containsEntry("requestId", "unknown");
        assertThat(response.getBody().getInstance().toString()).isEqualTo("/example");
    }

    @Test
    void preservesMethodNotAllowedAndAllowHeader() {
        var response = new GlobalExceptionHandler().handleUnexpected(
                new HttpRequestMethodNotSupportedException("PUT", List.of("GET")),
                new MockHttpServletRequest("PUT", "/hello"));
        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody().getStatus()).isEqualTo(405);
        assertThat(response.getBody().getTitle()).isEqualTo("Method Not Allowed");
        assertThat(response.getHeaders().getFirst("Allow")).isEqualTo("GET");
        assertThat(response.getBody().getProperties()).containsEntry("errorCode", "INVALID_REQUEST");
    }
}
