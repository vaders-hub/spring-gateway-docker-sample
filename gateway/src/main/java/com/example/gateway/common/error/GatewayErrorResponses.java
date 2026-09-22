package com.example.gateway.common.error;

import com.example.gateway.common.api.ApiResponses;
import com.example.gateway.common.web.RequestContext;
import io.netty.channel.ConnectTimeoutException;
import java.net.URI;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ServerWebExchange;
import reactor.netty.http.client.PrematureCloseException;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

// 라우팅 예외는 Advice에도 전달될 수 있으므로 두 처리 경로가 같은 상태/헤더 매핑을 쓴다.
final class GatewayErrorResponses {
    private static final Logger log = LoggerFactory.getLogger(GatewayErrorResponses.class);

    private GatewayErrorResponses() {
    }

    static ResponseEntity<ProblemDetail> from(ServerWebExchange exchange, Throwable exception) {
        int status = 500;
        HttpHeaders headers = new HttpHeaders();
        if (exception instanceof ErrorResponse error) {
            status = error.getStatusCode().value();
            headers.putAll(error.getHeaders());
        }
        else if (exception instanceof HttpClientErrorException error) {
            // RequestRateLimiter의 throwOnLimit이 전달한 429와 rate-limit 헤더를 보존한다.
            status = error.getStatusCode().value();
            if (error.getResponseHeaders() != null) {
                headers.putAll(error.getResponseHeaders());
            }
        }
        else if (exception instanceof AccessDeniedException) {
            status = 403;
        }
        else if (exchange.getAttribute(GATEWAY_ROUTE_ATTR) != null) {
            status = transportStatus(exception);
        }
        if (status < 400 || status > 599) {
            status = 500;
        }
        String requestId = RequestContext.requestId(exchange);
        var entity = ApiResponses.fail(ErrorCode.fromStatus(status), requestId);
        ProblemDetail body = entity.getBody();
        body.setStatus(status);
        body.setInstance(URI.create(exchange.getAttributeOrDefault(RequestContext.REQUEST_PATH_ATTRIBUTE,
                exchange.getRequest().getPath().value())));
        HttpStatus knownStatus = HttpStatus.resolve(status);
        body.setTitle(knownStatus == null ? "Request failed" : knownStatus.getReasonPhrase());
        if (ErrorCode.fromStatus(status) == ErrorCode.INVALID_REQUEST) {
            body.setDetail("The request could not be processed.");
        }
        if (status >= 500) {
            log.atError().addKeyValue("requestId", requestId).addKeyValue("status", status)
                    .addKeyValue("exceptionType", exception.getClass().getName())
                    .log("gateway_request_error");
        }
        return ResponseEntity.status(status).headers(headers)
                .headers(entity.getHeaders()).body(body);
    }

    private static int transportStatus(Throwable exception) {
        Throwable cause = exception;
        for (int depth = 0; cause != null && depth < 10; depth++, cause = cause.getCause()) {
            if (cause instanceof ConnectTimeoutException || cause instanceof TimeoutException
                    || cause instanceof io.netty.handler.timeout.TimeoutException) {
                return 504;
            }
            if (cause instanceof ConnectException || cause instanceof UnknownHostException
                    || cause instanceof PrematureCloseException || cause instanceof SSLException) {
                return 502;
            }
        }
        return 500;
    }
}
