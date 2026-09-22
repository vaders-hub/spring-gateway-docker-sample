package com.example.gateway.common.error;

import com.example.gateway.common.web.RequestContext;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityProblemWriterTest {
    @Test
    void writesValidJsonAndBearerChallengeWithoutCredentialDetails() throws Exception {
        var mapper = JsonMapper.builder().build();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/hello")
                .header(RequestContext.REQUEST_ID_HEADER, "id-with-\"quote").build());
        new SecurityProblemWriter(mapper).authenticationEntryPoint()
                .commence(exchange, new BadCredentialsException("private-token")).block();
        var body = mapper.readValue(exchange.getResponse().getBodyAsString().block(), Map.class);
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        assertThat(exchange.getResponse().getHeaders().getFirst("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(body.get("requestId")).isEqualTo("id-with-\"quote");
        assertThat(body.get("errorCode")).isEqualTo("UNAUTHORIZED");
        assertThat(body.toString()).doesNotContain("private-token");
    }
}
