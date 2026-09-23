package com.example.backend.common.api;

import com.example.backend.common.error.ErrorCode;
import com.example.backend.common.error.ProblemDetails;
import java.time.Instant;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
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
        return ProblemDetails.forCode(code, requestId);
    }
}
