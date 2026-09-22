package com.example.gateway.filter;

import com.example.gateway.common.web.RequestContext;
import java.security.Principal;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
class RequestHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = RequestContext.normalizeRequestId(
                exchange.getRequest().getHeaders().getFirst(RequestContext.REQUEST_ID_HEADER));

        return exchange.getPrincipal()
                .map(Principal::getName)
                .defaultIfEmpty("anonymous")
                .flatMap(username -> {
                    ServerHttpRequest request = exchange.getRequest().mutate()
                            .headers(headers -> {
                                headers.set(RequestContext.REQUEST_ID_HEADER, requestId);
                                headers.set(RequestContext.GATEWAY_USER_HEADER, username);
                            })
                            .build();
                    exchange.getResponse().getHeaders().set(RequestContext.REQUEST_ID_HEADER, requestId);
                    return chain.filter(exchange.mutate().request(request).build());
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
