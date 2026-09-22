package com.example.gateway.common.error;

import com.example.gateway.common.web.RequestContext;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityProblemWriterTest {
    @Test
    void writesValidJsonAndBearerChallengeWithoutCredentialDetails() throws Exception {
        // 단위 테스트도 Boot의 ProblemDetail 직렬화 규칙을 사용한다.
        var mapper = JsonMapper.builder()
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/hello")
                .header(RequestContext.REQUEST_ID_HEADER, "id-with-\"quote").build());
        new SecurityProblemWriter(new ProblemResponseWriter(mapper)).authenticationEntryPoint()
                .commence(exchange, new BadCredentialsException("private-token")).block();
        Map<?, ?> body = mapper.readValue(exchange.getResponse().getBodyAsString().block(), Map.class);
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        assertThat(exchange.getResponse().getHeaders().getFirst("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(body.get("requestId")).isEqualTo(exchange.getResponse().getHeaders()
                .getFirst(RequestContext.REQUEST_ID_HEADER));
        assertThat(body.get("requestId").toString()).matches("[A-Za-z0-9._-]{1,64}");
        assertThat(body.get("errorCode")).isEqualTo("UNAUTHORIZED");
        assertThat(body.get("status")).isEqualTo(401);
        assertThat(body.containsKey("properties")).isFalse();
        assertThat(body.toString()).doesNotContain("private-token");
    }
}
