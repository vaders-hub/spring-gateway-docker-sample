package com.example.gateway.common.error;

import com.example.gateway.common.web.RequestContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

// WebFlux의 Security/전역 오류 처리에서 HTTP 쓰기 방식을 공유한다.
@Component
public class ProblemResponseWriter {
    private final JsonMapper jsonMapper;

    public ProblemResponseWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, ResponseEntity<ProblemDetail> entity) {
        return Mono.defer(() -> {
            var response = exchange.getResponse();
            if (response.isCommitted()) {
                return Mono.empty();
            }
            String requestId = RequestContext.requestId(exchange);
            ProblemDetail body = entity.getBody();
            byte[] bytes = jsonMapper.writeValueAsBytes(body);
            response.setStatusCode(entity.getStatusCode());
            response.getHeaders().putAll(entity.getHeaders());
            response.getHeaders().set(RequestContext.REQUEST_ID_HEADER, requestId);
            // 이전 본문용 길이/인코딩은 재사용하지 않는다. rate-limit/Allow/Retry-After 헤더는 유지한다.
            response.getHeaders().remove(HttpHeaders.CONTENT_ENCODING);
            response.getHeaders().remove(HttpHeaders.TRANSFER_ENCODING);
            response.getHeaders().setContentLength(bytes.length);
            if (exchange.getRequest().getMethod() == HttpMethod.HEAD) {
                return response.setComplete();
            }
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        });
    }
}
