package com.example.backend.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// 1·2단계: Servlet 요청 진입 시 안전한 requestId를 응답 헤더와 request attribute에 둔다.
// Gateway에서 전달한 유효 ID를 유지해 양쪽 로그를 연결한다.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = Optional.ofNullable(request.getHeader(RequestContext.REQUEST_ID_HEADER))
                .filter(SAFE_REQUEST_ID.asMatchPredicate())
                .orElseGet(() -> UUID.randomUUID().toString());
        long startedAt = System.nanoTime();

        response.setHeader(RequestContext.REQUEST_ID_HEADER, requestId);
        request.setAttribute(RequestContext.REQUEST_ID_ATTRIBUTE, requestId);

        try {
            filterChain.doFilter(request, response);
        }
        // 예외가 나도 요청 종료 로그를 남긴다. WebFlux doFinally와 달리 Servlet의 동기 호출 경계이다.
        finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            // 정상 health probe는 DEBUG로 내려 업무 요청 로그의 잡음을 줄인다.
            ((request.getRequestURI().equals("/actuator/health")
                    || request.getRequestURI().startsWith("/actuator/health/"))
                    && response.getStatus() < 400 ? log.atDebug() : log.atInfo())
                    .addKeyValue("requestId", requestId)
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("path", request.getRequestURI())
                    .addKeyValue("status", response.getStatus())
                    .addKeyValue("durationMs", durationMs)
                    .log("http_request");
        }
    }
}
