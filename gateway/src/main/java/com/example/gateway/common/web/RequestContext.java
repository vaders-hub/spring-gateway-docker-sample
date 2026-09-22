package com.example.gateway.common.web;

import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.web.server.ServerWebExchange;

public final class RequestContext {
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String GATEWAY_USER_HEADER = "X-Gateway-User";
    public static final String REQUEST_ID_ATTRIBUTE = RequestContext.class.getName() + ".requestId";
    public static final String REQUEST_PATH_ATTRIBUTE = RequestContext.class.getName() + ".requestPath";
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private RequestContext() {
    }

    // 전역 오류 handler는 mutate 전 exchange를 받을 수 있으므로 공유 attribute의 정규화된 ID를 우선한다.
    public static String requestId(ServerWebExchange exchange) {
        String value = exchange.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (value == null) {
            value = normalizeRequestId(exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER));
            exchange.getAttributes().put(REQUEST_ID_ATTRIBUTE, value);
        }
        return value;
    }

    // 클라이언트 요청 ID는 길이/문자 범위를 제한하고 부적합하면 새 UUID로 대체한다. 인증 식별자는 아니다.
    public static String normalizeRequestId(String value) {
        return value != null && SAFE_REQUEST_ID.matcher(value).matches()
                ? value : UUID.randomUUID().toString();
    }
}
