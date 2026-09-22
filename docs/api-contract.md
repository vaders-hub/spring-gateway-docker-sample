# API response and error contract

## 성공 응답

모든 애플리케이션 Controller 성공 응답은 다음 envelope를 사용합니다.

```json
{
  "data": {
    "service": "backend"
  },
  "meta": {
    "requestId": "1ff1a3be-3ac5-4597-886b-2ecb195c82a6",
    "timestamp": "2026-09-21T10:00:00Z"
  }
}
```

`data`는 API별 DTO이고 `meta`는 공통 요청 추적 정보입니다. Entity를 API 응답으로
직접 노출하지 않습니다.

## 오류 응답

오류는 RFC 9457 Problem Details에 안정적인 `errorCode`와 `requestId`를 추가합니다.

```json
{
  "type": "urn:problem:invalid-request",
  "title": "Invalid request",
  "status": 400,
  "detail": "One or more request fields are invalid.",
  "instance": "/api/echo",
  "errorCode": "INVALID_REQUEST",
  "requestId": "1ff1a3be-3ac5-4597-886b-2ecb195c82a6"
}
```

- validation과 body parsing 오류는 `INVALID_REQUEST`
- 잘못된 로컬 데모 자격증명은 `INVALID_CREDENTIALS`
- 인증 누락/실패는 `UNAUTHORIZED`
- scope 부족은 `ACCESS_DENIED`
- 예상하지 못한 서버 오류는 `INTERNAL_ERROR`

예외 메시지, stack trace, JWT, 비밀번호, 내부 클래스명은 응답에 포함하지 않습니다.

## 공통 응답 생성

각 모듈의 `common/api/ApiResponses`는 `success`와 `fail` 두 메서드만 제공합니다.
`ApiResponse`는 성공 JSON 본문을 표현하는 record이며, HTTP 상태·헤더는 `ApiResponses`가 결정합니다.

```java
// 일반 성공: 200 + 기존 data/meta 본문
return ApiResponses.success(SuccessCode.OK, result, requestId);

// 토큰 발급: 200 + Cache-Control: no-store, Pragma: no-cache
return ApiResponses.success(SuccessCode.TOKEN_ISSUED, token, requestId);

// 예외 처리기 또는 Security writer: 코드에 해당하는 HTTP 상태 + ProblemDetail
return ApiResponses.fail(ErrorCode.INVALID_REQUEST, requestId);
```

| 정의 | 담는 정책 | 사용 범위 |
|---|---|---|
| `SuccessCode` | HTTP 상태, 캐시 저장 금지 여부 | `OK`(200), `CREATED`(201), `TOKEN_ISSUED`(200, no-store) |
| `ErrorCode` | HTTP 상태, 공개 가능한 기본 title/detail | 기존 오류 코드 유지; 모든 공통 오류에 no-store, 401에 Bearer challenge |

성공 코드는 내부 응답 정책 선택용이며 JSON에 새 `code`/`message` 필드를 추가하지 않습니다.
문자열을 비교하는 대신 enum을 받아 성공 코드가 `fail`에 전달되는 실수를 컴파일 단계에서 막습니다.
`CREATED`는 생성 API 확장용으로 제공하며 현재 사용하는 API는 없습니다. 리소스 생성 API를
추가하면 `Location` 같은 요청별 헤더는 해당 Controller에서 추가합니다.

`GlobalExceptionHandler`는 공통 오류에 요청 경로(`instance`), 필드별 검증 오류(`errors`),
잘못된 JSON의 안전한 메시지를 덧붙입니다. 프레임워크의 405 등은 코드의 기본 400 대신
원래 HTTP 상태를 본문의 `status`에도 반영하며 `Allow` 등 원래 헤더를 보존합니다.
Service는 응답 팩터리를 호출하지 않고 업무 결과를 반환하거나 예외를 던집니다.

인증/인가 오류는 Controller 전에 발생하므로 `SecurityProblemWriter`를 유지합니다.
writer는 `ApiResponses.fail`의 상태·헤더·본문을 Servlet/WebFlux 응답에 옮기며,
Boot의 `JsonMapper`로 ProblemDetail 확장 필드를 최상위 JSON 속성으로 직렬화합니다.
Gateway의 rate-limit/프록시 오류와 Envoy 응답까지 이 팩터리로 자동 통일되는 것은 아닙니다.

## JWT와 메서드별 권한

Gateway와 Backend는 서명, issuer, audience, 만료를 검증합니다.
`JWT_AUDIENCE` 기본값은 `gateway-sample-api`이며 두 앱에서 같아야 합니다.
audience가 없거나 다른 토큰은 401입니다. 기존 토큰은 재발급해야 합니다.

| 호출 | 필요한 scope |
|---|---|
| Gateway GET/HEAD `/api/**` | `api.read` |
| Gateway POST/PUT/PATCH/DELETE `/api/**` | `api.write` |
| Backend GET/HEAD `/hello` | `api.read` |
| Backend POST `/echo` | `api.write` |

Gateway의 위 목록 밖 API 메서드는 deny이며 CORS preflight는 CORS 정책으로 처리합니다.
echo는 영속 쓰기가 아니지만 메서드별 권한 분리 학습을 위해 write scope를 요구합니다.
데모 발급기는 두 scope를 함께 부여합니다. 운영에서는 사용자/클라이언트별 권한을 발급자가 결정합니다.

각 모듈의 `SecurityHttpIntegrationTest`는 실제 HTTP/서명 토큰으로 401/403/200과
Problem Details를 확인하도록 작성했습니다. Gateway는 테스트 전용 Controller를 호출하며,
실제 Backend 프록시·Redis 제한·장애 경로는 이 테스트의 범위 밖입니다.
Gateway 기본 rate-limit 429, 프록시 오류, Envoy 오류가 모두 위 Problem Details를
따른다고 보장하지 않습니다. 이 경계는 [후속 장애 정책](resilience-policy.md)에서 다룹니다.

## 계층 경계

- `controller`: HTTP mapping, validation 시작, `ApiResponses.success` 호출
- `service`: use case와 업무 로직
- `dto`: 외부 요청/응답 계약
- `common`: 요청 추적, 공통 응답과 오류 처리
- `entity`: DB/JPA가 도입될 때만 생성하며 DTO와 분리

Gateway와 Backend의 envelope 형태는 같지만, 두 배포 단위를 하나의 공유 Java 모듈에
강하게 결합하지 않기 위해 각 서비스 내부 common 패키지에 둡니다. 세 번째 소비자가
생기거나 독립 계약 배포가 필요해질 때 버전이 있는 contract 모듈로 승격합니다.
