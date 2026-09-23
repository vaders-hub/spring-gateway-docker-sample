package com.example.backend.common.error;

import com.example.backend.common.web.RequestContext;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityProblemWriterTest {
    @Test
    void writesValidJsonAndBearerChallengeWithoutCredentialDetails() throws Exception {
        // 단위 테스트도 Boot의 ProblemDetail 직렬화 규칙을 사용한다.
        var mapper = JsonMapper.builder()
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.setAttribute(RequestContext.REQUEST_ID_ATTRIBUTE, "id-with-\"quote");
        new SecurityProblemWriter(mapper).authenticationEntryPoint()
                .commence(request, response, new BadCredentialsException("private-token"));
        Map<?, ?> body = mapper.readValue(response.getContentAsByteArray(), Map.class);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(body.get("requestId")).isEqualTo("id-with-\"quote");
        assertThat(body.get("errorCode")).isEqualTo("UNAUTHORIZED");
        assertThat(body.get("status")).isEqualTo(401);
        assertThat(body.containsKey("properties")).isFalse();
        assertThat(body.toString()).doesNotContain("private-token");
    }
}
