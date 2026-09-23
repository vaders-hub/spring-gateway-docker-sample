package com.example.backend.common.error;

import com.example.backend.common.util.ErrorDiagnostics;
import com.example.backend.common.web.RequestContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.RequestDispatcher;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Servlet 컨테이너의 ERROR dispatch를 받는다. Advice 밖의 filter 예외/sendError도 이 경로로 온다.
@RestController
@RequestMapping("${spring.web.error.path:${error.path:/error}}")
class ApiErrorController implements ErrorController {
    private static final Logger log = LoggerFactory.getLogger(ApiErrorController.class);

    @RequestMapping
    ResponseEntity<ProblemDetail> error(HttpServletRequest request, HttpServletResponse servletResponse) {
        Object rawStatus = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = 404;
        if (request.getDispatcherType() == DispatcherType.ERROR) {
            status = rawStatus instanceof Integer value && value >= 400 && value <= 599 ? value : 500;
        }
        Object rawId = request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE);
        String requestId = rawId == null ? UUID.randomUUID().toString() : rawId.toString();
        servletResponse.setHeader(RequestContext.REQUEST_ID_HEADER, requestId);
        Object originalUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = originalUri instanceof String value ? value : request.getRequestURI();

        if (status >= 500) {
            Object cause = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
            log.atError().addKeyValue("requestId", requestId).addKeyValue("status", status)
                    .addKeyValue("path", path)
                    .addKeyValue("exceptionType", cause == null ? "unknown" : cause.getClass().getName())
                    .addKeyValue("causes", ErrorDiagnostics.causes(cause instanceof Throwable error ? error : null))
                    .log("servlet_request_error");
        }
        // 컨테이너에 이미 설정된 Allow/Retry-After 등은 지우지 않는다. 예외 원문은 본문에 넣지 않는다.
        return ProblemDetails.forStatus(HttpStatusCode.valueOf(status), requestId, path, HttpHeaders.EMPTY);
    }
}
