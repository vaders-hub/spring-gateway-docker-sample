package com.example.gateway.common.security;

import com.example.gateway.common.exception.ProblemResponseWriter;
import com.example.platform.exception.ProblemDetails;
import com.example.platform.code.CommonErrorCode;
import com.example.gateway.common.security.token.TokenStoreUnavailableException;
import com.example.gateway.common.web.RequestContext;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// 1단계: Controller 진입 전 Security에서 발생한 401/403을 JSON 오류로 작성한다.
// ControllerAdvice와 별도 경로이며 requestId로 같은 요청을 추적한다.
@Component
public class SecurityProblemWriter {
    private final ProblemResponseWriter writer;

    public SecurityProblemWriter(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    public ServerAuthenticationEntryPoint authenticationEntryPoint() {
        return (exchange, exception) ->
                write(exchange, TokenStoreUnavailableException.causedBy(exception)
                        ? CommonErrorCode.SERVICE_UNAVAILABLE : CommonErrorCode.UNAUTHORIZED);
    }

    public ServerAccessDeniedHandler accessDeniedHandler() {
        return (exchange, exception) ->
                write(exchange, CommonErrorCode.ACCESS_DENIED);
    }

    private Mono<Void> write(
            ServerWebExchange exchange,
            CommonErrorCode errorCode) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }
        return writer.write(exchange, ProblemDetails.forCode(errorCode, RequestContext.requestId(exchange),
                exchange.getAttributeOrDefault(RequestContext.REQUEST_PATH_ATTRIBUTE,
                        exchange.getRequest().getPath().value())));
    }
}
