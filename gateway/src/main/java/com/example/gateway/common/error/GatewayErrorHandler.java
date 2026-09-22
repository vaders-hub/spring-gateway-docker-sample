package com.example.gateway.common.error;

import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// Boot 기본 handler(-1) 앞에서 Advice가 처리하지 못한 WebFlux/filter 예외를 처리한다.
@Component
@Order(-2)
class GatewayErrorHandler implements ErrorWebExceptionHandler {
    private final ProblemResponseWriter writer;

    GatewayErrorHandler(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (exchange.getResponse().isCommitted()) {
            // 스트리밍 등 이미 전송된 응답은 새 JSON으로 덮어쓸 수 없다.
            return Mono.error(exception);
        }
        return writer.write(exchange, GatewayErrorResponses.from(exchange, exception));
    }
}
