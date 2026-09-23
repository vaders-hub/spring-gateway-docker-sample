package com.example.gateway.filter;

import com.example.gateway.common.web.RequestContext;
import java.security.Principal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestHeadersFilterTest {
    private final RequestHeadersFilter filter = new RequestHeadersFilter();

    @Test
    void principalReplacesAllSpoofedUserValuesAndChainRunsOnce() {
        var exchange = exchange().mutate().principal(Mono.just((Principal) () -> "verified-user")).build();
        assertForwarded(exchange, List.of("verified-user"));
    }

    @Test
    void absentPrincipalStripsSpoofedUserHeaderAndStillRunsChainOnce() {
        assertForwarded(exchange(), null);
    }

    @Test
    void principalLookupFailureDoesNotForwardRequest() {
        var failure = new IllegalStateException("principal unavailable");
        var exchange = exchange().mutate().principal(Mono.error(failure)).build();
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> filter.filter(exchange, forwarded -> {
            calls.incrementAndGet();
            return Mono.empty();
        }).block(Duration.ofSeconds(1))).isSameAs(failure);
        assertThat(calls.get()).isZero();
    }

    private ServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/hello")
                .header(RequestContext.REQUEST_ID_HEADER, "request-test")
                .header(RequestContext.GATEWAY_USER_HEADER, "forged-user", "second-forged-user"));
    }

    private void assertForwarded(ServerWebExchange exchange, List<String> expectedUserValues) {
        var calls = new AtomicInteger();
        filter.filter(exchange, forwarded -> {
            calls.incrementAndGet();
            var headers = forwarded.getRequest().getHeaders();
            assertThat(headers.get(RequestContext.GATEWAY_USER_HEADER)).isEqualTo(expectedUserValues);
            assertThat(headers.getFirst(RequestContext.REQUEST_ID_HEADER)).isEqualTo("request-test");
            return Mono.empty();
        }).block(Duration.ofSeconds(1));
        assertThat(calls.get()).isEqualTo(1);
        assertThat(exchange.getResponse().getHeaders().getFirst(RequestContext.REQUEST_ID_HEADER))
                .isEqualTo("request-test");
    }
}
