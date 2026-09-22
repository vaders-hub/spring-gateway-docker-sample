package com.example.backend.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    @Test
    void preservesMethodNotAllowedAndAllowHeader() {
        var response = new GlobalExceptionHandler().handleUnexpected(
                new HttpRequestMethodNotSupportedException("PUT", new String[]{"GET"}),
                new MockHttpServletRequest("PUT", "/hello"));
        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getHeaders().getFirst("Allow")).isEqualTo("GET");
        assertThat(response.getBody().getProperties()).containsEntry("errorCode", "INVALID_REQUEST");
    }
}
