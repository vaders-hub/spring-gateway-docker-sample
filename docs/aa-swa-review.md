# AA/SWA 검토와 반영 범위

검토일: 2026-09-23. 이 저장소는 Spring Boot 학습과 이후 업무 코드 참고를 함께 목적으로 합니다.
검토 의견을 그대로 도입하기보다 기존 계약과 실제 오류 처리 경계를 기준으로 판단했습니다.

## 반영한 내용

| 검토 항목 | 반영 및 근거 |
|---|---|
| 1-1 업무 계층의 requestId | Backend Service 인자와 응답 data에서 제거. Controller에서 meta/header를 처리한다. 자동 envelope Advice는 사용하지 않는다. |
| 1-2 Service와 웹 DTO 결합 | Service는 HelloResult/EchoResult record 반환. Controller가 응답 DTO로 변환하며 Echo 입력/출력 타입도 분리했다. |
| 1-3 패키지 순환 | ApiResponses.fail을 유지하면서 common.error의 ProblemDetails로 위임. 오류 처리기에서 common.api 참조를 제거했다. 파일 이동/삭제 없이 의존 방향을 단순화했다. |
| 2-1 오류 상태 복원 중복 | ProblemDetails.forStatus가 실제 HTTP 상태와 안전한 본문/헤더를 함께 생성. 매핑되지 않은 418도 보존하며 Gateway writer의 본문 재수정을 제거했다. |
| 2-2 혼동되는 설정 | Backend의 중복 spring.mvc.problemdetails.enabled를 제거. 실제 오류 계약은 프로젝트 Advice/ErrorController가 담당한다. |
| 2-3 5xx 원인 추적 | 예외 타입과 cause별 첫 stack frame을 최대 8개 기록. 순환 cause를 방어하고 예외 메시지/전체 stack은 기록하지 않는다. |
| 3-1 데모 발급 조건 | Controller/Service/Encoder가 하나의 ConditionalOnDemoIssuer를 사용. 기존 local/dev/test 및 enabled 조건은 동일하다. |
| 4-2 키 Bean | 키를 JwtConfig 내부 private 함수에서 생성. 이는 같은 JVM 내 보안 격리가 아니라 불필요한 주입 가능성과 Bean 모호성을 줄이는 변경이다. |
| 4-3 audience 검증 | 표준 JwtClaimValidator로 교체. 잘못되거나 누락된 audience는 기존대로 401이다. |
| 4-5 requestId 조회 | Gateway AuthController와 RequestHeadersFilter에서 RequestContext.requestId를 사용한다. |
| 5-1 로컬 key-value 로그 | 공통 logging.pattern.level에 %kvp 추가. HTTP 테스트 로그에서 requestId/method/path/status/durationMs가 실제 출력됨을 확인한다. ECS profile은 기존 구조화 출력을 유지한다. |
| 5-3 probe 로그 | 정상 /actuator/health 및 하위 경로는 DEBUG. 장애 응답 로그는 INFO를 유지한다. local에서 DEBUG를 켜면 probe가 보이는 것은 의도한 동작이다. |
| Clock | HelloService/TokenService에 UTC Clock 주입. 업무 시각은 Instant 사용. 고정 시계로 발급 시각과 만료 간격을 검증한다. |
| attribute 이름 | Backend request attribute를 네임스페이스 문자열로 분리. HTTP 헤더 이름 X-Request-Id는 변경하지 않는다. |
| final Spring Bean | SecurityProblemWriter/ProblemResponseWriter의 final 제거. static 유틸리티 클래스는 final 유지. |

## 보안 정책: 승인 후 반영

사용자 승인 후 다음 두 정책을 반영했다. 업무 토큰의 scope와 관리 경로 접근 범위를 구분한다.

- Gateway: `/api/actuator`, `/api/actuator/**`, `/api/error`, `/api/error/**`를 업무 scope 허용보다 먼저 거부한다.
  토큰이 없으면 401, 유효한 토큰이 있으면 403이며 rate limiter와 Backend까지 전달하지 않는다.
- Backend: 명시한 업무/관리 경로와 내부 ERROR dispatch 외에는 denyAll을 적용한다.
  유효한 JWT를 사용한 미등록 경로·메서드는 403이다. 이전 인증된 `/missing`·`/error`의 404가 403으로 바뀐다.
- `/hello` GET/HEAD와 `/echo` POST는 기존 scope 규칙을 유지한다.
  새 업무 Controller를 만들 때 경로·메서드·scope를 SecurityConfig에 함께 등록해야 한다.
- 직접 `/actuator/health/**`·`/actuator/info`와 공개 여부 설정에 따른 `/actuator/prometheus` 정책은 유지한다.
  학습 Compose의 Prometheus scrape 주소는 `/api`가 붙지 않은 서비스별 `/actuator/prometheus`이다.
- Backend의 `DispatcherType.ERROR`는 계속 허용한다. Servlet filter 예외/sendError가 인증 오류로
  가려지지 않고 원래 500/405/503 등과 공통 오류 본문을 유지한다.
- Gateway의 일반 미등록 경로는 기존 정책을 유지하므로 인증 후 `/missing`은 여전히 404다.
  두 서비스의 모든 오류가 403으로 바뀌었다는 의미가 아니다.

회귀 테스트는 실제 JWT로 차단 경로의 GET/HEAD/POST/PUT/PATCH/DELETE/OPTIONS,
인코딩된 경로, rate limiter와 upstream 미호출을 확인한다. 세미콜론 파라미터 URL은 기본
HTTP firewall이 먼저 400으로 거부하며, 이 경우도 rate limiter와 upstream은 호출되지 않는다.
Backend는 인가 규칙이 없는 테스트 전용 Controller도 차단하는지 확인하고,
직접 관리 endpoint·공개/인증 필요 Prometheus·내부 ERROR dispatch를 함께 검증한다.
일반 CORS preflight는 CORS 필터에서 먼저 처리될 수 있으며 그 자체는 Backend 라우팅을 뜻하지 않는다.

## 그대로 적용하지 않은 제안

| 제안 | 판단 |
|---|---|
| 모든 성공 응답을 Advice로 자동 감싸기 | 기존 명시적 success/fail 사용 방식 유지. Actuator, Prometheus, 이미 감싼 응답, streaming과 충돌하는 별도 제외 규칙을 늘리지 않는다. |
| ResponseEntityExceptionHandler 전면 상속 전환 | 표준 기반 대안으로 타당하지만 이번에는 기존 Advice 구조를 유지하고 중복 팩토리만 추출했다. 현재 ErrorResponse 경로가 상태/Allow 등을 보존한다. 향후 변경 시 표준 예외별 매핑과 안전한 detail, validation errors를 함께 회귀 검증해야 한다. |
| EnvironmentPostProcessor로 guard 이동 | 조기 실패에 유리하나 이번에는 기존 PostConstruct 시작 차단을 유지했다. Bean 할당 전 실패를 보장하는 구조는 아니며 초기화 순서 개선은 후속 과제이다. |
| 설정 검증 전면 Bean Validation 통일 | 혼합 자체가 오류는 아니다. 생성자는 값 객체 불변식, Bean Validation은 바인딩 제약을 담당한다. 오류 보고 통일은 추후 개선 가능하지만 DurationMin만으로 정수 초 조건이 보장되지 않으며 ExactOrigin 전용 애너테이션은 현재 규모에 비해 크다. |
| JWT 키 Base64 전환 | 기존 UTF-8 키 계약과 토큰 호환성을 바꾼다. Base64는 인코딩이며 반복 문자열/낮은 엔트로피 문제를 해결하지 않는다. 현재 최소 32문자 검사는 UTF-8 최소 32바이트보다 보수적이며 안전한 무작위 생성 스크립트를 사용한다. |
| requestId를 baggage로 전면 대체 | traceId와 업무 requestId는 역할이 다르다. baggage 설정만으로 모든 Reactor 로그의 MDC 전파가 보장되지는 않는다. tracing/context propagation·sampling·외부 입력 신뢰 정책을 함께 검증한 뒤 판단한다. |
| DEBUG에 전체 stack 출력 | local/dev DEBUG에서도 비밀값을 포함한 exception message가 노출될 수 있어 적용하지 않는다. 필요한 발생 위치를 별도 안전 필드로 남긴다. |
| ApiResponses의 Clock Bean 주입 | 메타데이터는 정책 계산 시각이 아닌 응답 생성 시각이다. static success/fail API를 유지하며 정밀 시간 검증이 필요한 Service에 우선 Clock을 도입했다. |
| 공통 JSON fixture와 slice 테스트 추가 | 독립 모듈/Docker build 경계를 유지하고 현재 실제 HTTP 테스트를 보강했다. 공유 fixture 배포와 별도 slice context는 CI/계약 모듈 도입 시 검토한다. |

## 호환성과 검증

- 응답 변경: `data.requestId` 소비자는 `meta.requestId`로 전환. `data.time`은 UTC Instant 문자열.
- 유지: 성공 data/meta envelope, success/fail 두 메서드, JWT 알고리즘·키 형식, Redis 제한 정책.
- 오류 분류와 실제 HTTP 상태는 구분한다. 예를 들어 418은 INVALID_REQUEST 범주지만 status는 418이다.
- Servlet filter 오류는 ERROR dispatch, WebFlux filter/route 오류는 전역 handler가 담당한다.
  ResponseBodyAdvice나 ControllerAdvice 하나로 모든 오류를 잡을 수 있다는 의미가 아니다.
- 현재 Servlet filter 요청 로그의 종료 시점과 후속 ERROR dispatch의 종료 시점은 다를 수 있다.
  미처리 예외의 최종 상태는 같은 requestId의 servlet_request_error 로그와 오류 응답도 함께 본다.

검증 결과: Backend 29개, Gateway 45개, 총 74개 테스트 통과(실패·오류·건너뜀 0). 두 모듈 컴파일 및 git diff --check도 통과했다.

검증 명령(WSL 저장소 루트):

```bash
MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=false bash ./gradlew --offline --no-daemon \
  --project-cache-dir "$HOME/.gradle/project-caches/spring-gateway-docker-sample" \
  :backend:test :gateway:test
```

기존 JWT·scope·DTO 검증·Servlet ERROR dispatch·Gateway 429/502/504·Backend 오류 전달 테스트와,
418/헤더 보존·응답 객체 격리·안전한 cause 진단·고정 시계·data.requestId 제거 검증을 실행했다.
승인된 보안 정책에 대해 관리 우회 경로 차단·인가 규칙 없는 Controller 차단·공개/인증 필요 Prometheus와 health/info 접근도 통과했다.
테스트는 별도 로컬 HTTP 서버를 사용하며 실제 Redis 판정은 일부 spy로 고정한다.
Compose/kind 컨테이너 재기동·이미지 재빌드·부하 검증은 수행하지 않는다.
