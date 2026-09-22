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
