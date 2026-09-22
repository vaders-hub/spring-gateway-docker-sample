package com.example.backend.common.error;

import com.example.backend.common.api.ApiResponses;
import com.example.backend.common.web.RequestContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
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
        Object rawId = request.getAttribute(RequestContext.REQUEST_ID);
        String requestId = rawId == null ? UUID.randomUUID().toString() : rawId.toString();
        servletResponse.setHeader(RequestContext.REQUEST_ID, requestId);
        Object originalUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = originalUri instanceof String value ? value : request.getRequestURI();

        var entity = ApiResponses.fail(ErrorCode.fromStatus(status), requestId);
        ProblemDetail body = entity.getBody();
        body.setStatus(status);
        body.setInstance(URI.create(path));
        HttpStatus knownStatus = HttpStatus.resolve(status);
        body.setTitle(knownStatus == null ? "Request failed" : knownStatus.getReasonPhrase());
        if (ErrorCode.fromStatus(status) == ErrorCode.INVALID_REQUEST) {
            body.setDetail("The request could not be processed.");
        }
        if (status >= 500) {
            Object cause = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
            log.atError().addKeyValue("requestId", requestId).addKeyValue("status", status)
                    .addKeyValue("path", path)
                    .addKeyValue("exceptionType", cause == null ? "unknown" : cause.getClass().getName())
                    .log("servlet_request_error");
        }
        // 컨테이너에 이미 설정된 Allow/Retry-After 등은 지우지 않는다. 예외 원문은 본문에 넣지 않는다.
        return ResponseEntity.status(status).headers(entity.getHeaders()).body(body);
    }
}
