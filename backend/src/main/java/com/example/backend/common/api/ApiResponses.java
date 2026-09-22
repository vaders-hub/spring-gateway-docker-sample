package com.example.backend.common.api;

import com.example.backend.common.error.ErrorCode;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

// Controller/예외 처리기/Security writer가 공유하는 HTTP 응답 생성 진입점이다.
public final class ApiResponses {

    private ApiResponses() {
    }

    public static <T> ResponseEntity<ApiResponse<T>> success(
            SuccessCode code, T data, String requestId) {
        var response = ResponseEntity.status(code.status());
        if (code.noStore()) {
            response.cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.PRAGMA, "no-cache");
        }
        return response.body(new ApiResponse<>(data, new ApiResponse.Meta(requestId, Instant.now())));
    }

    public static ResponseEntity<ProblemDetail> fail(ErrorCode code, String requestId) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), code.detail());
        problem.setTitle(code.title());
        problem.setType(URI.create("urn:problem:" + code.name().toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setProperty("errorCode", code.name());
        problem.setProperty("requestId", requestId == null ? "unknown" : requestId);
        var response = ResponseEntity.status(code.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore());
        // 401은 인증 challenge도 전달한다. 403 응답에는 붙이지 않는다.
        if (code.status() == HttpStatus.UNAUTHORIZED) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return response.body(problem);
    }
}
