package com.example.gateway.common.error;

import com.example.gateway.common.api.ApiResponses;
import com.example.gateway.common.web.RequestContext;
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
                write(exchange, ErrorCode.UNAUTHORIZED);
    }

    public ServerAccessDeniedHandler accessDeniedHandler() {
        return (exchange, exception) ->
                write(exchange, ErrorCode.ACCESS_DENIED);
    }

    private Mono<Void> write(
            ServerWebExchange exchange,
            ErrorCode errorCode) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }
        String requestId = exchange.getRequest().getHeaders().getFirst(RequestContext.REQUEST_ID_HEADER);
        var response = exchange.getResponse();
        var entity = ApiResponses.fail(errorCode, requestId);
        // Boot의 JsonMapper가 ProblemDetail 확장 필드를 최상위 JSON 속성으로 직렬화한다.
        byte[] body = jsonMapper.writeValueAsBytes(entity.getBody());
        response.setStatusCode(entity.getStatusCode());
        response.getHeaders().putAll(entity.getHeaders());
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
