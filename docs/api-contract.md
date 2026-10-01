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
직접 노출하지 않습니다. Backend의 `HelloService`는 HTTP DTO 대신 업무 결과 record를
반환하며 Controller에서 출력 DTO로 변환합니다. `EchoResponse.received`는 입력 `EchoRequest`와
별도 타입입니다. `requestId`는 업무 Service 인자와 `data`에서 제거했고 `meta.requestId`와
`X-Request-Id` 헤더에 유지합니다. 기존 `data.requestId` 소비자는 `meta.requestId`로 변경해야 합니다.
`data.time`은 UTC `Instant`이며 JSON은 `Z`로 끝나는 ISO-8601 형식입니다.
Hello/Echo의 `data.username`은 Backend가 JWT를 검증한 Principal의 이름입니다.
기존 `data.gatewayUser` 필드는 제거했습니다. 소비자는 `data.username`으로 변경해야 합니다.
`X-Gateway-User` 전달 헤더와 응답의 `username`은 별개이며 Backend는 그 헤더를 인증 근거로 삼지 않습니다.

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
- 없는 리소스/허용하지 않는 메서드는 `NOT_FOUND` / `METHOD_NOT_ALLOWED`
- Gateway 요청 허용량 소진은 `TOO_MANY_REQUESTS`
- Gateway의 Backend 연결 실패/응답 시간 초과는 `BAD_GATEWAY` / `GATEWAY_TIMEOUT`
- 서비스 사용 불가 상태는 `SERVICE_UNAVAILABLE`
- 예상하지 못한 서버 오류는 `INTERNAL_ERROR`

기존 프레임워크 404/405의 `errorCode`는 `INVALID_REQUEST`에서 위 전용 코드로 세분화했습니다.
전용 코드가 없는 4xx/5xx도 원래 HTTP 상태와 본문의 `status`를 보존합니다.

예외 메시지, stack trace, JWT, 비밀번호, 내부 클래스명은 응답에 포함하지 않습니다.

## 공통 응답 생성

새 업무 Controller는 `ApiResponses`, 오류 처리기(Advice·ErrorController·Security writer·전역 handler)는
`ProblemDetails`를 사용합니다. `ApiResponses.fail`은 같은 팩토리로 위임하는 Controller용 진입점입니다.

공유 모듈의 `com.example.platform.response.ApiResponses`는 `success`와 `fail` 두 메서드만 제공합니다.
`ApiResponse`는 성공 JSON 본문을 표현하는 record이며, HTTP 상태·헤더는 `ApiResponses`가 결정합니다.

```java
// 일반 성공: 200 + 기존 data/meta 본문
return ApiResponses.success(SuccessCode.OK, result, requestId);

// 토큰 발급: 200 + Cache-Control: no-store, Pragma: no-cache
return ApiResponses.success(SuccessCode.TOKEN_ISSUED, token, requestId);

// Controller에서 명시적으로 오류 응답을 반환하는 경우: 코드에 해당하는 상태 + ProblemDetail
return ApiResponses.fail(ErrorCode.INVALID_REQUEST, requestId);
```

| 정의 | 담는 정책 | 사용 범위 |
|---|---|---|
| `SuccessCode` | HTTP 상태, 캐시 저장 금지 여부 | `OK`(200), `CREATED`(201), `TOKEN_ISSUED`(200, no-store) |
| `ErrorCode` | HTTP 상태, 공개 가능한 기본 title/detail | 기존 오류 코드 유지; 모든 공통 오류에 no-store, 401에 Bearer challenge |

성공 코드는 내부 응답 정책 선택용이며 JSON에 새 `code`/`message` 필드를 추가하지 않습니다.
문자열을 비교하는 대신 enum을 받아 성공 코드가 `fail`에 전달되는 실수를 컴파일 단계에서 막습니다.
`CREATED`는 persistence 모드의 POST /orders에서 사용합니다. 응답 data.id를 GET /orders/{id}에 사용합니다.
이 예제는 Location 헤더를 추가하지 않으며, Gateway prefix까지 고려한 링크 정책은 별도입니다.

`GlobalExceptionHandler`는 공통 오류에 요청 경로(`instance`), 필드별 검증 오류(`errors`),
잘못된 JSON의 안전한 메시지를 덧붙입니다. 프레임워크 오류는
원래 HTTP 상태를 본문의 `status`에도 반영하며 `Allow` 등 원래 헤더를 보존합니다.
Service는 응답 팩터리를 호출하지 않고 업무 결과를 반환하거나 예외를 던집니다.

인증/인가 오류는 Controller 전에 발생하므로 각 모듈의 `common/security/SecurityProblemWriter`를 사용합니다.
writer는 `platform/exception/ProblemDetails`의 상태·헤더·본문을 Servlet/WebFlux 응답에 옮기며,
Boot의 `JsonMapper`로 ProblemDetail 확장 필드를 최상위 JSON 속성으로 직렬화합니다.

`ApiResponses.fail`도 같은 `ProblemDetails.forCode`로 위임합니다. 오류 처리기는 `platform/response`를
참조하지 않으므로 의존 방향은 `platform/response → platform/exception` 한 방향입니다.
`SuccessCode`, `CommonErrorCode`, `ErrorCode` 인터페이스는 공유 모듈의 `platform/code`에 두고,
오류 진단 유틸리티 `ErrorDiagnostics`는 `platform/util`에 둡니다. 코드·메시지·HTTP 계약은 동일합니다.
프레임워크 상태는 `ProblemDetails.forStatus`가 처리합니다. `ErrorCode`에 없는 418 같은 상태도
그대로 유지하고, 응답 본문을 새로 만들면서 기존 Content-Length/Content-Encoding은 제거합니다.
`Allow`/`Retry-After`/rate-limit 헤더는 보존합니다. WebFlux writer는 생성된 본문을 다시 수정하지 않습니다.

## 시스템별 오류 처리 경계

| 발생 경로 | 처리 코드 | 결과 |
|---|---|---|
| Controller/DTO 검증/일반 Service 예외 | 각 모듈 `common/exception/GlobalExceptionHandler` | 공통 Problem Details, 안전한 메시지, 필드별 `errors` |
| 데모 자격증명 오류 | Gateway `auth/web/AuthExceptionHandler` | 기존 INVALID_CREDENTIALS 401, Bearer/no-store 유지 |
| 인증/인가 거절 | 각 모듈 `common/security/SecurityProblemWriter` | 401/403과 인증 헤더 유지 |
| Backend Servlet filter 예외 또는 `sendError` | [ApiErrorController](../backend/src/main/java/com/example/backend/common/exception/ApiErrorController.java) | 컨테이너 ERROR dispatch의 원래 상태·경로·requestId로 JSON 작성 |
| Gateway WebFilter/라우팅 예외 | Advice 또는 [GatewayErrorHandler](../gateway/src/main/java/com/example/gateway/common/exception/GatewayErrorHandler.java) → `GatewayErrorResponses` | 미처리 오류 500, 연결 실패 502, 응답 timeout 504 등 |
| Gateway 요청 제한 거절 | YAML의 `throw-on-limit: true` → 공통 `GatewayErrorResponses` | 429 본문 작성, rate-limit 헤더 유지, Backend 미호출 |

Backend는 Boot 4의 `spring.web.error.path`(기본 `/error`)를 사용하며, Security에서
`DispatcherType.ERROR`를 명시적으로 허용합니다. 외부의 `/error` 직접 호출은 기본 거부 정책을
따르므로 토큰이 없으면 401, 유효한 토큰이 있으면 403입니다. 내부 오류 dispatch의 원래
500/405/503 등은 그대로 보존하며 모든 오류 상태를 403으로 바꾸지는 않습니다.

Gateway 라우팅 예외는 `GlobalExceptionHandler`에 전달되기도 하므로, Advice와 전역 handler가
[GatewayErrorResponses](../gateway/src/main/java/com/example/gateway/common/exception/GatewayErrorResponses.java)의 상태·헤더 매핑을 함께 사용합니다.
Gateway의 [ProblemResponseWriter](../gateway/src/main/java/com/example/gateway/common/exception/ProblemResponseWriter.java)는
Security/전역 handler의 직렬화·헤더 쓰기를 공유합니다. 정규화한 requestId와 원래 요청 경로는
exchange attribute에 두어 요청 mutate/StripPrefix 이후에도 유지합니다. HEAD 응답에는 본문을 쓰지 않습니다.
프레임워크의 `Allow`, rate-limit 헤더, 기존 `Retry-After`를 보존하며, 계산 근거 없는 Retry-After를 새로 만들지 않습니다.

Backend가 이미 HTTP 오류 응답을 반환한 경우 Gateway는 본문을 그대로 전달합니다.
Actuator/Prometheus의 고유 응답, 예외 없이 직접 종료하는 다른 필터의 응답(예: CORS 거절),
이미 전송을 시작한 응답, 클라이언트 연결 단절, Envoy/LB에서 생성한 오류까지 강제로 재작성하지 않습니다.
Redis 오류의 기본 허용 fallback도 바꾸지 않았으며 요청별 fail-closed는 별도 과제입니다.

참고: [Gateway 5.0.3 RequestRateLimiter](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/requestratelimiter-factory.html),
[Servlet 오류 처리](https://docs.spring.io/spring-boot/reference/web/servlet.html#error-handling),
[WebFlux 오류 처리](https://docs.spring.io/spring-boot/reference/web/reactive.html#error-handling)

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

Gateway는 위 scope 허용 규칙보다 먼저 `/api/actuator`·`/api/actuator/**`와
`/api/error`·`/api/error/**`를 거부합니다. 업무 토큰으로 Backend 관리 endpoint를 호출하는 우회를
차단하며, rate limiter와 Backend 호출 전에 종료합니다. 토큰 누락은 401, 유효한 토큰은 403입니다.

Backend는 위 업무 경로·메서드와 명시한 관리 경로/내부 ERROR dispatch 외에는 기본 거부합니다.
인증된 `/missing`, `/error`, GET `/echo`, POST `/hello` 등은 403입니다.
새 Controller는 기능 api 패키지의 FeatureRoutes Bean으로 경로를 등록하고 handler에 메서드 권한을 선언합니다.
직접 `/actuator/health/**`·`/actuator/info`와 공개 여부 설정에 따른 `/actuator/prometheus` 접근은 유지합니다.

Gateway의 위 목록 밖 API 메서드는 deny이며 CORS preflight는 CORS 정책으로 처리합니다.
echo는 영속 쓰기가 아니지만 메서드별 권한 분리 학습을 위해 write scope를 요구합니다.
데모 발급기는 두 scope를 함께 부여합니다. 운영에서는 사용자/클라이언트별 권한을 발급자가 결정합니다.

각 모듈의 `SecurityHttpIntegrationTest`는 실제 HTTP/서명 토큰으로 401/403/200과
Problem Details를 확인하도록 작성했습니다. Gateway는 테스트 전용 Controller를 호출하며,
실제 Backend 프록시·Redis 제한·장애 경로는 이 테스트의 범위 밖입니다.
추가한 `GatewayErrorHttpIntegrationTest`는 실제 Netty HTTP upstream, 프로젝트 YAML route 및 테스트 전용 장애 route로
429·502·504·404·405·500, HEAD, 기존 Backend 오류 통과를 검증합니다. Redis의 허용/거절 판정만
spy로 고정하므로 실제 Redis 버킷/장애 검증과는 다릅니다. Backend의 HTTP 테스트는 Servlet filter
예외와 `sendError`를 발생시켜 실제 Tomcat ERROR dispatch와 Security의 상호작용도 검증합니다.
committed 응답 보존은 단위 테스트로 확인하며 Compose/kind 재배포와 부하·Redis 장애 실험은 별도입니다.
[후속 장애 정책](resilience-policy.md)을 참고합니다.

## 계층 경계

- `<feature>/web`: HTTP mapping, validation 시작, `ApiResponses.success` 호출
- `<feature>/service`: 업무 인터페이스·공개 값; service/impl에 업무 구현
- `<feature>/web/dto`: 외부 요청/응답 계약
- `common`: 요청 추적, 공통 응답과 오류 처리
- `<feature>/model`: 업무 모델·규칙이 필요할 때 생성하며 API DTO와 분리
- `<feature>/repository`: 저장소 인터페이스와 JPA/메모리 구현; service를 참조하지 않음

Gateway와 Backend의 공통 응답/오류 계약은 libs/platform-core에 공유합니다. Servlet/WebFlux 처리기는 각 서비스가 소유합니다.

## 기능 API와 저장소

모든 프로필에서 member/product/order API를 제공합니다. local/test 기본은 메모리 저장소, persistence 추가 또는 dev/staging/prod는 JPA입니다. 추가 mybatis 프로필은 같은 기존 API를 SQL 저장소로 제공합니다.
GET은 api.read, 주문 POST는 api.write, 회원 관리자 GET은 api.read와 member.admin을 요구합니다.
POST /orders/preview는 200 견적, POST /orders는 201 주문, GET /orders/{UUID}는 본인 주문만 반환합니다.
mybatis 선택 시 GET /orders(api.read)에 본인 주문 검색을 추가합니다. memberId, productName, minimumTotal, page(0~10000), size(1~100)를 받으며 data={items,total,page,size}를 반환합니다. 기본 page=0,size=20이고 소유자는 JWT에서만 가져옵니다. 상세 계약은 [18단계](learning/18-mybatis-and-sql-queries.md)를 따릅니다.
같은 POST 재전송은 별도 주문을 만듭니다. 결제·재고 차감은 구현하지 않았습니다.

GET 부재는 MEMBER_NOT_FOUND / PRODUCT_NOT_FOUND / ORDER_NOT_FOUND(404),
주문 본문의 없는 회원/상품 참조는 INVALID_MEMBER_REFERENCE / INVALID_PRODUCT_REFERENCE(422)입니다.
수량·타입·필수 입력 오류는 400 INVALID_REQUEST입니다. 인증은 DTO 바인딩 전, Backend 메서드 권한은 바인딩 뒤 검사합니다.
Backend 등록 경로의 미지원 메서드는 405가 될 수 있으며, 미등록 경로는 기본 거부합니다.
자세한 변경 계약과 예제는 [17단계](learning/17-reference-architecture.md)를 참고합니다.


## 로그인·로그아웃 (14단계)

`POST /auth/login`은 기존 `/auth/token`과 같은 JSON 요청/응답 계약입니다. 두 경로 모두 Redis 활성 토큰 등록 성공 후 발급 결과를 반환합니다.
`POST /auth/logout`은 현재 Bearer 토큰만 삭제하고 200 `data: null` + `meta`를 반환합니다.
이후 같은 토큰은 Gateway/Backend에서 401입니다. 등록/조회/삭제 저장소 장애는 503이며, 모든 인증 응답은 no-store입니다.
로그인 없이 로그아웃하거나 이미 종료한 토큰을 재사용하면 401입니다. 다른 로그인 토큰은 유지합니다.
자세한 상태·호환성·실습은 [14단계](learning/14-login-and-logout.md)를 참고합니다.


## 보안 설정 및 member 관리 조회 (15단계)

SecurityConfig는 stateless/인가/JWT 구성을 분리합니다. Backend는 FeatureRoutes를 모아 인증하고 scope는 메서드 보안에 둡니다. Gateway는 coarse scope 정책을 유지합니다.
`GET /api/members/admin`은 일반 목록 handler/DTO/서비스를 재사용하며 Backend에서 `api.read`와 `member.admin`을 모두 요구합니다.
`/members/{id}`보다 관리 경로 규칙을 먼저 선언합니다. 기존 member ID는 양수만 허용하며 0·음수는 400입니다.
HTTP 권한과 feature 업무 권한을 구분하고, [15단계](learning/15-security-rules-and-member-tests.md)에 테스트 절차와 확장 기준을 기록했습니다.


## 인증 모드별 계약 (16단계)

기본 데모 모드의 /auth/login·/auth/token·/auth/logout은 기존 계약을 유지한다.
`oidc` profile에서는 해당 Controller/발급기를 생성하지 않는다. 로그인/로그아웃/갱신은 Keycloak 표준 endpoint를 사용한다.
업무 API 성공 envelope와 Problem Details 401/403 계약은 두 모드에서 동일하다.
`permissions` 배열을 SCOPE_ authority로 매핑하며 sub는 불변 사용자/서비스 계정 ID다.
hello/echo의 기존 username 필드는 Principal.getName()(sub)이므로 OIDC에서는 사용자 UUID가 표시될 수 있다.
서비스 계정은 사람의 로그인 계정과 구분하고 API 호출에는 Access Token을 사용한다(ID Token 사용 금지).
OIDC 로그아웃은 이미 발급된 Access Token의 즉시 무효화를 보장하지 않는다. 자세한 정책은 [16단계](learning/16-keycloak-and-method-security.md)를 따른다.
