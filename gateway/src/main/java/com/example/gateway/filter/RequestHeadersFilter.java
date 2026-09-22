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

// 1단계: route가 선택된 요청의 Gateway filter이다. 전체 HTTP 요청용 RequestIdWebFilter와 구분한다.
// HIGHEST_PRECEDENCE는 Gateway filter들 사이의 순서이며 Security WebFilter보다 앞선다는 뜻은 아니다.
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
                                // 외부에서 주입한 사용자 헤더를 검증된 Principal 값으로 덮어쓴다. Backend 인증 근거는 JWT이다.
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
