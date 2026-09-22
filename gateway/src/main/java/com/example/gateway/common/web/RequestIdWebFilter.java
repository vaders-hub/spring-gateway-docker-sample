package com.example.gateway.common.web;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

// 1·2단계: Security보다 먼저 요청 ID를 준비하여 라우팅 이전의 401 응답도 로그와 연결한다.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestIdWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = RequestContext.normalizeRequestId(exchange.getRequest().getHeaders()
                .getFirst(RequestContext.REQUEST_ID_HEADER));
        long startedAt = System.nanoTime();

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(RequestContext.REQUEST_ID_HEADER, requestId))
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(request).build();
        mutatedExchange.getResponse().getHeaders()
                .set(RequestContext.REQUEST_ID_HEADER, requestId);

        // WebFlux 비동기 체인이 종료되는 시점에 소요시간을 기록한다. 토큰·본문은 로그에 넣지 않는다.
        return chain.filter(mutatedExchange)
                .doFinally(signalType -> {
                    HttpStatusCode status = mutatedExchange.getResponse().getStatusCode();
                    long durationMs = TimeUnit.NANOSECONDS.toMillis(
                            System.nanoTime() - startedAt);
                    log.atInfo()
                            .addKeyValue("requestId", requestId)
                            .addKeyValue("method", request.getMethod())
                            .addKeyValue("path", request.getPath().value())
                            .addKeyValue("status", status == null ? 0 : status.value())
                            .addKeyValue("durationMs", durationMs)
                            .log("http_request");
                });
    }
}
