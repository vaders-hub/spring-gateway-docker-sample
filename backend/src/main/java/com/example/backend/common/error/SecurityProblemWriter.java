package com.example.backend.common.error;

import com.example.backend.common.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// 1단계: Controller 진입 전 Security에서 발생한 401/403을 JSON 오류로 작성한다.
// ControllerAdvice와 별도 경로이며 requestId로 같은 요청을 추적한다.
@Component
public final class SecurityProblemWriter {
    private final JsonMapper jsonMapper;

    public SecurityProblemWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, exception) ->
                write(request, response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) ->
                write(request, response, HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED);
    }

    private void write(
            HttpServletRequest request, HttpServletResponse response,
            HttpStatus status, ErrorCode errorCode) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        Object requestId = request.getAttribute(RequestContext.REQUEST_ID);
        // Boot의 JsonMapper로 직렬화하여 헤더/값을 직접 JSON 문자열에 이어붙이지 않는다.
        byte[] body = jsonMapper.writeValueAsBytes(Map.of(
                "type", "urn:problem:" + errorCode.name().toLowerCase(Locale.ROOT).replace('_', '-'),
                "title", status.getReasonPhrase(),
                "status", status.value(),
                "detail", status == HttpStatus.UNAUTHORIZED
                        ? "Authentication is required."
                        : "Access to this resource is denied.",
                "errorCode", errorCode.name(),
                "requestId", requestId == null ? "unknown" : requestId.toString()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        response.getOutputStream().write(body);
    }
}
