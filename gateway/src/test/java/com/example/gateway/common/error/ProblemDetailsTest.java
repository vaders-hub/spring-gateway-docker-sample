package com.example.gateway.common.error;

import com.example.gateway.common.code.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemDetailsTest {
    @Test
    void preservesUnmappedStatusAndProtocolHeadersWithoutStaleBodyHeaders() {
        var headers = new HttpHeaders();
        headers.set("Retry-After", "7");
        headers.set("Allow", "POST");
        headers.setContentLength(999);
        headers.set("Content-Encoding", "gzip");
        headers.set("Transfer-Encoding", "chunked");
        var response = ProblemDetails.forStatus(HttpStatusCode.valueOf(418), "request-1", "/echo", headers);
        assertThat(response.getStatusCode().value()).isEqualTo(418);
        var body = response.getBody();
        assertThat(body.getStatus()).isEqualTo(418);
        assertThat(body.getInstance().toString()).isEqualTo("/echo");
        assertThat(body.getProperties()).containsEntry("errorCode", "INVALID_REQUEST")
                .containsEntry("requestId", "request-1");
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("7");
        assertThat(response.getHeaders().getFirst("Allow")).isEqualTo("POST");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().headerNames()).doesNotContain("Content-Length", "Content-Encoding", "Transfer-Encoding");
        assertThat(headers.getContentLength()).isEqualTo(999);
    }

    @Test
    void errorsDoNotShareMutableBodiesAndBearerChallengeIsOnlyFor401() {
        var first = ProblemDetails.forCode(ErrorCode.UNAUTHORIZED, "first");
        var second = ProblemDetails.forCode(ErrorCode.UNAUTHORIZED, "second");
        first.getBody().setProperty("errors", "only-first");
        assertThat(second.getBody().getProperties()).doesNotContainKey("errors");
        assertThat(second.getBody().getProperties()).containsEntry("requestId", "second");
        assertThat(first.getHeaders().getFirst("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(ProblemDetails.forCode(ErrorCode.ACCESS_DENIED, "third").getHeaders().getFirst("WWW-Authenticate"))
                .isNull();
    }
}
