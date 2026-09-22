package com.example.backend.common.error;

import com.example.backend.common.web.RequestContext;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityProblemWriterTest {
    @Test
    void writesValidJsonAndBearerChallengeWithoutCredentialDetails() throws Exception {
        var mapper = JsonMapper.builder().build();
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.setAttribute(RequestContext.REQUEST_ID, "id-with-\"quote");
        new SecurityProblemWriter(mapper).authenticationEntryPoint()
                .commence(request, response, new BadCredentialsException("private-token"));
        var body = mapper.readValue(response.getContentAsByteArray(), Map.class);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(body.get("requestId")).isEqualTo("id-with-\"quote");
        assertThat(body.get("errorCode")).isEqualTo("UNAUTHORIZED");
        assertThat(body.toString()).doesNotContain("private-token");
    }
}
