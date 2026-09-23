package com.example.gateway.common.error;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

// 오류 계약의 단일 생성 지점. common.error는 common.api를 참조하지 않는다.
public final class ProblemDetails {
    private ProblemDetails() {}

    public static ResponseEntity<ProblemDetail> forCode(ErrorCode code, String requestId) {
        return forCode(code, requestId, null);
    }

    public static ResponseEntity<ProblemDetail> forCode(ErrorCode code, String requestId, String path) {
        return response(code, code.status(), code.title(), code.detail(), requestId, path, HttpHeaders.EMPTY);
    }

    // ErrorCode는 오류 분류이며 실제 HTTP 상태의 대체물이 아니다. 418 등도 그대로 보존한다.
    public static ResponseEntity<ProblemDetail> forStatus(
            HttpStatusCode status, String requestId, String path, HttpHeaders headers) {
        if (!status.isError()) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        ErrorCode code = ErrorCode.fromStatus(status.value());
        HttpStatus known = HttpStatus.resolve(status.value());
        String title = known == null ? "Request failed" : known.getReasonPhrase();
        String detail = code == ErrorCode.INVALID_REQUEST ? "The request could not be processed." : code.detail();
        return response(code, status, title, detail, requestId, path, headers);
    }

    private static ResponseEntity<ProblemDetail> response(
            ErrorCode code, HttpStatusCode status, String title, String detail,
            String requestId, String path, HttpHeaders originalHeaders) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        body.setType(URI.create("urn:problem:" + code.name().toLowerCase(Locale.ROOT).replace('_', '-')));
        body.setProperty("errorCode", code.name());
        body.setProperty("requestId", requestId == null ? "unknown" : requestId);
        if (path != null) {
            body.setInstance(URI.create(path));
        }
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(originalHeaders);
        // 새 JSON 본문에 이전 본문의 길이/압축 정보는 적용할 수 없다.
        headers.remove(HttpHeaders.CONTENT_LENGTH);
        headers.remove(HttpHeaders.CONTENT_ENCODING);
        headers.remove(HttpHeaders.TRANSFER_ENCODING);
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        headers.setCacheControl(CacheControl.noStore());
        if (status.value() == 401) {
            headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return new ResponseEntity<>(body, headers, status);
    }
}
