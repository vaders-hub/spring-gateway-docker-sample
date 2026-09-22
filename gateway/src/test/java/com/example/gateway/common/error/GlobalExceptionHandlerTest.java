package com.example.gateway.common.error;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.MethodNotAllowedException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    @Test
    void preservesMethodNotAllowedAndAllowHeader() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.put("/auth/token").build());
        var response = new GlobalExceptionHandler().handleUnexpected(
                new MethodNotAllowedException(HttpMethod.PUT, List.of(HttpMethod.POST)), exchange);
        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getHeaders().getFirst("Allow")).isEqualTo("POST");
        assertThat(response.getBody().getProperties()).containsEntry("errorCode", "INVALID_REQUEST");
    }
}
