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

- `controller`: HTTP mapping, validation 시작, success envelope 조립
- `service`: use case와 업무 로직
- `dto`: 외부 요청/응답 계약
- `common`: 요청 추적, 공통 응답과 오류 처리
- `entity`: DB/JPA가 도입될 때만 생성하며 DTO와 분리

Gateway와 Backend의 envelope 형태는 같지만, 두 배포 단위를 하나의 공유 Java 모듈에
강하게 결합하지 않기 위해 각 서비스 내부 common 패키지에 둡니다. 세 번째 소비자가
생기거나 독립 계약 배포가 필요해질 때 버전이 있는 contract 모듈로 승격합니다.
