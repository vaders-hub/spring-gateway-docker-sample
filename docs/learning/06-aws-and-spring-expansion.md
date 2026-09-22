# 6단계: AWS 대응 학습과 Spring Boot 확장 계획

[전체 순서](README.md) · 이전: [Gateway API](05-gateway-api.md)

## 6-1. 로컬에서 배운 것과 실제 EKS의 차이

이 표는 역할 비교이지 제품의 1:1 호환표가 아닙니다. AWS 배포 명령은 포함하지 않습니다.

| 로컬에서 학습한 것 | EKS에서 연결해 공부할 것 | 로컬에서 검증하지 못한 것 |
|---|---|---|
| kind control plane / Docker 노드 | 관리형 EKS control plane / compute | AWS 업그레이드·AZ 장애·AWS SLA |
| Deployment·Service·probe·rollout | 같은 Kubernetes 기본 리소스 | 실제 노드/네트워크 부하 |
| `kind load docker-image` | ECR 등 registry에서 image pull | IAM image pull 권한·registry 정책 |
| NodePort / Envoy Gateway API | ALB·AWS Load Balancer Controller | VPC subnet·보안그룹·LB 비용/동작 |
| Spring API 정책 | 선택적으로 AWS API Gateway | AWS JWT Authorizer·VPC Link 제한 |
| ConfigMap / Kubernetes Secret | Secret Manager 연동, IAM 권한 | 실제 secret 회전·감사·암호화 |
| Pod 접근 범위·ServiceAccount | RBAC와 EKS Pod Identity | AWS API 접근 권한 |
| emptyDir / 로컬 volume | PV/PVC와 EBS/EFS 등 CSI | AZ·attach·백업/재해 복구 |
| Prometheus/Grafana | AWS 관측 서비스 또는 자체 운영 | 관리형 비용·권한·보존 정책 |

EKS는 Kubernetes와 AWS 인프라가 결합된 서비스입니다.
[EKS 개요](https://docs.aws.amazon.com/eks/latest/userguide/what-is-eks.html)
Pod Identity는 ServiceAccount와 IAM role을 연결하는 AWS 접근 기능이며,
사용자 요청의 JWT 로그인이나 Kubernetes RBAC와 같은 기능이 아닙니다.
[EKS Pod Identity](https://docs.aws.amazon.com/eks/latest/userguide/pod-identities.html)

과제: 현재 요청 경로를 그리고 각 화살표에 DNS 이름/포트/인증 책임을 적으세요.
다음으로 ALB와 API Gateway를 넣은 그림을 따로 그려 어떤 정책을 유지/이전할지 적습니다.
설계 기준은 [진입점 책임 문서](../traffic-entry.md)를 사용합니다.
API Gateway의 route throttling과 Redis의 subject별 제한을 같은 정책으로 취급하지 않습니다.

비용 경계: 실제 EKS는 표준 Kubernetes 지원 기준 클러스터 시간 요금이 있고
노드·스토리지 등도 별도입니다. 설치용 Helm chart가 무료여도 AWS 컨트롤러가 만든
클라우드 리소스는 무료가 아닐 수 있습니다. AWS 문서를 읽더라도 생성 버튼이나
배포 명령까지 실행하지 않는 것이 이 과정의 원칙입니다.
[EKS 요금](https://aws.amazon.com/eks/pricing/)

## 6-2. 현재 Spring 설정을 학습하는 순서

1. `application.yml` 공통값과 `application-{profile}.yml` 차이를 읽습니다.
2. Compose environment → kind ConfigMap/Secret → ConfigurationProperties로 이어지는
   값을 추적합니다. Spring 자체가 `.env` 파일을 자동 읽는다고 가정하지 않습니다.
3. `config/properties`의 검증과 `config/runtime`의 환경 보호를 확인합니다.
4. `config/security`의 JWT/CORS/인가 정책과 controller/service/dto 경계를 읽습니다.
5. `common/error`, 요청 ID, probe, 종료 시간을 검토합니다.

주요 운영 설정 간 우선순위는 명령행 > JVM 시스템 속성 > OS 환경변수 > 설정 파일입니다.
이는 전체 PropertySource 목록의 일부이며 테스트 속성 등 별도 source도 있습니다.
공식 문서의 버전을 프로젝트 Boot 4.0.x와 맞춰 확인하고, 최신 문서 예제만 보고
프로젝트 의존성을 자동 업그레이드하지 않습니다.
[Spring 외부화 설정](https://docs.spring.io/spring-boot/reference/features/external-config.html)

추가 업무 API 구현 순서 (아직 구현되지 않은 과제):

1. 실제 업무를 선택하고 `<feature>/controller`, `service`, `dto/request`, `dto/response`를 만듭니다.
2. HTTP 검증과 변환은 Controller, 업무 규칙은 Service로 둡니다.
3. 공통 성공/오류 계약과 생성자 주입을 재사용합니다.
4. 설정을 추가한다면 타입 바인딩, fail-fast 검증, 환경변수, 문서를 한 묶음으로 추가합니다.
5. 빌드할 시점에 단위/통합 테스트를 실행하고 새 이미지 태그를 만들어 kind에 올립니다.

호스트 JDK 25가 준비됐을 때의 선택 검증 명령입니다. 이번 문서 작성 중 실행하지 않았습니다.

```bash
bash ./gradlew gateway:test backend:test
```

## 6-3. Oracle + JPA + MyBatis 후속 단계

현재 이 세 구성은 **아직 연결/구현되지 않았습니다.** Kubernetes 기초보다 먼저
추가하지 않습니다. Oracle 버전과 기존 schema 사용 여부부터 결정합니다.
로컬 Oracle Database Free는 선택지이지만 운영 Oracle 19c 등과 기능/타입/SQL이
완전히 같다고 가정하지 않습니다. 무료 에디션의 조건·자원 제한과 CPU 아키텍처를
확인하고 이미지를 고정하세요. RDS 또는 OCI 인스턴스를 만들 필요는 없습니다.
[Oracle Free 공식 안내](https://www.oracle.com/database/free/),
[공식 컨테이너 사용법](https://github.com/oracle/docker-images/blob/main/OracleDatabase/SingleInstance/README.md)

| 구현 순서 | 추가할 대상 | 완료 체크 |
|---|---|---|
| 1 | 별도 Oracle Compose overlay, DB 전용 volume, 외부화 자격증명 | 재기동 후 데이터 유지; 다른 DB와 포트/volume 충돌 없음 |
| 2 | Backend의 Oracle JDBC·JPA·MyBatis 의존성 | Boot/Java/driver 호환 조합 확인 |
| 3 | 공용 DataSource/Hikari와 migration | 시작 시 연결/스키마 검증, OSIV off, prod ddl-auto validate/none |
| 4 | `persistence/jpa/entity`, `repository` | Entity가 HTTP 응답에 직접 노출되지 않음 |
| 5 | `persistence/mybatis/mapper`, `model`, resources/mapper | SQL Mapper와 DTO 변환기 이름 구분 |
| 6 | Service 트랜잭션 경계 | JPA+MyBatis 혼합 작업 실패 시 둘 다 rollback |
| 7 | 같은 이미지를 kind에 배포, DB 연결 주소 분리 | Pod의 localhost가 호스트 DB가 아님을 반영 |

추천 이름은 `CustomerEntity`, `CustomerJpaRepository`, `CustomerSqlMapper`,
`CustomerRow`, `CreateCustomerRequest`, `CustomerResponse`입니다. 둘 다 사용 가능하게
한다는 것이 모든 작업을 양쪽으로 중복 구현한다는 뜻은 아닙니다.

Boot 4에는 MyBatis Starter 4.0 계열 호환표를 확인합니다.
[MyBatis Starter](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)
단일 DB 혼합 트랜잭션은 같은 DataSource, 적절한 JpaTransactionManager/JpaDialect,
Spring-managed MyBatis 세션을 사용하도록 설계하고 실제 Oracle rollback 테스트로 증명합니다.
JPA flush/영속성 컨텍스트와 MyBatis SQL 변경의 정합성도 별도 검증합니다.
[Spring JpaTransactionManager](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/orm/jpa/JpaTransactionManager.html),
[MyBatis 트랜잭션](https://mybatis.org/spring/transactions.html)

현재 없는 Oracle Compose 파일/Service를 기동하는 명령을 제공하지 않습니다.
버전 결정과 구현이 끝나면 별도 DB 실행 문서를 작성합니다.
참고: [영속성 도입 기준](../persistence-policy.md), [패키지 기준](../package-structure.md)

## 6-4. 무료 로컬 확장 과제의 우선순위

우선 보완: Redis 오류 fallback과 readiness를 분리해 요청 단위 fail-closed를 설계합니다.
인증된 요청이 Pod 직접 접근/기존 연결에서도 통과하지 않는지 통합 테스트로 검증해야 합니다.
현재 RedisRateLimiter 내부에서 오류가 허용 응답으로 바뀌므로 외부에 onErrorResume만
붙이는 것으로 해결된다고 가정하지 않습니다.

1. NetworkPolicy: 현재 CNI 구현/버전을 확인하고 허용/차단 Pod의 접근을 각각 실험.
   YAML 생성 성공만으로 차단된다고 판단하지 않음. 필요하면 별도 학습 클러스터에
   정책 지원 CNI를 설치하고 기존 클러스터의 CNI를 즉석 교체하지 않음.
2. 관측성 이식: Compose에서 익힌 scrape/dashboard를 kind용 배포로 이전.
   운영 앱의 metrics 인증을 끄는 대신 전용 scrape 인증/네트워크를 설계.
3. 자원/자동 확장: metrics-server 설치·검증 후 HPA. Prometheus 설치만으로 HPA가 동작하지 않음.
4. 저장소: PVC 기반 로컬 persistence를 학습하고 삭제/백업/복원 실험. EBS 검증과는 별개.
5. OIDC·trace: 로컬 IdP와 Collector/trace backend를 각각 추가. 무료 로컬 서버와
   외부 SaaS 무료 체험을 구분하고 자원 사용량을 확인.

모두 후속 구현 과제입니다. AWS 자격증명이나 유료 에뮬레이터를 필수 조건으로 만들지 않습니다.
LocalStack/EKS Anywhere 같은 추가 도구도 현재 목표에는 필수가 아닙니다.

## 최종 체크

- [ ] Kubernetes 기본 리소스와 AWS 전용 리소스를 구분한다.
- [ ] Gateway API / Spring Cloud Gateway / AWS API Gateway를 구분한다.
- [ ] 설정·Secret·권한·트랜잭션의 경계가 서로 다름을 설명한다.
- [ ] 현재 구현된 기능과 후속 과제를 구분해 기록했다.
- [ ] 로컬 성공을 운영 EKS/Oracle 검증 완료라고 표현하지 않는다.

## 구조·코드 이해 체크

실행 결과를 확인한 뒤 아래 항목을 관련 파일과 연결해 설명합니다. 모든 클래스를 암기하기보다 요청 한 건과 설정 한 개를 끝까지 추적하세요.

**읽을 파일:** [패키지 구조](../package-structure.md) · [영속성 설계](../persistence-policy.md) · [장애 정책](../resilience-policy.md) · [Gateway 속성 검증](../../gateway/src/main/java/com/example/gateway/config/properties/SecurityProperties.java) · [Backend Service](../../backend/src/main/java/com/example/backend/service/HelloService.java)

- [ ] kind의 Kubernetes 공통 개념과 EKS의 IAM/VPC/ALB/EBS 등 별도 검증 대상을 대응시켜 설명한다.
- [ ] Gateway API·Spring Cloud Gateway·AWS API Gateway의 책임을 구분하며 로컬 실행이 AWS 리소스를 만든 것은 아님을 설명한다.
- [ ] 환경변수 → application.yml/profile → ConfigurationProperties 검증 → Bean 생성 → RuntimeProfileGuard 경계를 추적했다.
- [ ] Controller의 HTTP/검증 책임, Service의 업무 책임, DTO와 향후 Entity/Repository/Mapper의 책임을 구분한다.
- [ ] 현재 HS256 공유키 검증과 향후 OIDC/JWK 도입의 차이를 설명한다. issuer 문자열 변경만으로 외부 IdP 연동이 되지 않는다.
- [ ] Oracle/JPA/MyBatis/migration/혼합 트랜잭션은 후속 구현임을 구분하고, 실제 DB rollback 검증이 필요한 이유를 설명한다.
- [ ] 현재 구현·실제로 확인한 기능·미구현 과제를 나누어 기록하고 다음에는 관측성/정책/저장소 중 한 과제만 선택한다.

**학습 기록:** 예상 경로 → 관찰한 HTTP 코드·로그·지표 → 근거 파일 → 복구 결과(해당 시) → 아직 설명하지 못하는 부분을 적습니다. 비밀번호·JWT·Secret 값은 적지 않습니다.

**다음 학습:** 선택한 확장 과제를 작은 단위로 구현하고 같은 요청 흐름·설정·장애·복구 기준으로 검증합니다. 종료/재개에는 [7번 문서](07-troubleshooting-and-cleanup.md)를 사용합니다.
