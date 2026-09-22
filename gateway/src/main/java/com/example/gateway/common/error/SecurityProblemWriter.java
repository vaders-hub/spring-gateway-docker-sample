package com.example.gateway.common.error;

import com.example.gateway.common.web.RequestContext;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

// 1단계: Controller 진입 전 Security에서 발생한 401/403을 JSON 오류로 작성한다.
// ControllerAdvice와 별도 경로이며 requestId로 같은 요청을 추적한다.
@Component
public final class SecurityProblemWriter {
    private final JsonMapper jsonMapper;

    public SecurityProblemWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public ServerAuthenticationEntryPoint authenticationEntryPoint() {
        return (exchange, exception) ->
                write(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
    }

    public ServerAccessDeniedHandler accessDeniedHandler() {
        return (exchange, exception) ->
                write(exchange, HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED);
    }

    private Mono<Void> write(
            ServerWebExchange exchange,
            HttpStatus status, ErrorCode errorCode) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }
        String requestId = exchange.getRequest().getHeaders().getFirst(RequestContext.REQUEST_ID_HEADER);
        var response = exchange.getResponse();
        // Boot의 JsonMapper로 직렬화하여 헤더/값을 직접 JSON 문자열에 이어붙이지 않는다.
        byte[] body = jsonMapper.writeValueAsBytes(Map.of(
                "type", "urn:problem:" + errorCode.name().toLowerCase(Locale.ROOT).replace('_', '-'),
                "title", status.getReasonPhrase(),
                "status", status.value(),
                "detail", status == HttpStatus.UNAUTHORIZED
                        ? "Authentication is required."
                        : "Access to this resource is denied.",
                "errorCode", errorCode.name(),
                "requestId", requestId == null ? "unknown" : requestId));
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().setCacheControl("no-store");
        if (status == HttpStatus.UNAUTHORIZED) {
            response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
