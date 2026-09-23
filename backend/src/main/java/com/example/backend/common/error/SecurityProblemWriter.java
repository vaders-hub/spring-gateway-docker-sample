package com.example.backend.common.error;

import com.example.backend.common.code.ErrorCode;
import com.example.backend.common.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// 1단계: Controller 진입 전 Security에서 발생한 401/403을 JSON 오류로 작성한다.
// ControllerAdvice와 별도 경로이며 requestId로 같은 요청을 추적한다.
@Component
public class SecurityProblemWriter {
    private final JsonMapper jsonMapper;

    public SecurityProblemWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, exception) ->
                write(request, response, ErrorCode.UNAUTHORIZED);
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) ->
                write(request, response, ErrorCode.ACCESS_DENIED);
    }

    private void write(
            HttpServletRequest request, HttpServletResponse response,
            ErrorCode errorCode) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        Object requestId = request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE);
        var entity = ProblemDetails.forCode(errorCode, requestId == null ? null : requestId.toString());
        // Boot의 JsonMapper가 ProblemDetail 확장 필드를 최상위 JSON 속성으로 직렬화한다.
        byte[] body = jsonMapper.writeValueAsBytes(entity.getBody());
        response.setStatus(entity.getStatusCode().value());
        response.setCharacterEncoding("UTF-8");
        entity.getHeaders().forEach((name, values) -> {
            response.setHeader(name, values.getFirst());
            values.stream().skip(1).forEach(value -> response.addHeader(name, value));
        });
        response.getOutputStream().write(body);
    }
}
