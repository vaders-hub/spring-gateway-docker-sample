package com.example.gateway.common.web;

import java.util.UUID;
import java.util.regex.Pattern;

public final class RequestContext {
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String GATEWAY_USER_HEADER = "X-Gateway-User";
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private RequestContext() {
    }

    public static String normalizeRequestId(String value) {
        return value != null && SAFE_REQUEST_ID.matcher(value).matches()
                ? value : UUID.randomUUID().toString();
    }
}
