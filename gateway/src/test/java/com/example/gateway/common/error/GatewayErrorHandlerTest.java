package com.example.gateway.common.error;

import com.example.gateway.common.web.RequestContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayErrorHandlerTest {
    @Test
    void committedResponseIsNotReplaced() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/stream").build());
        exchange.getResponse().getHeaders().set("X-Upstream", "preserved");
        exchange.getResponse().setComplete().block();
        var original = new IllegalStateException("connection closed after headers");
        var handler = new GatewayErrorHandler(new ProblemResponseWriter(JsonMapper.builder().build()));
        assertThatThrownBy(() -> handler.handle(exchange, original).block()).isSameAs(original);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Upstream")).isEqualTo("preserved");
        assertThat(exchange.getResponse().getHeaders().getFirst(RequestContext.REQUEST_ID_HEADER)).isNull();
    }
}
