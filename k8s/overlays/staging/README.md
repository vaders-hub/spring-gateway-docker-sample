# staging 렌더링과 배포 준비

이 overlay는 namespace·복제본·설정 차이를 확인하는 렌더링 학습용입니다.
`staging` 프로필의 Backend는 기본 JPA 저장소를 사용하므로 PostgreSQL 연결도 준비해야 합니다.
API 계약은 local fixture와 같지만 주문 데이터는 DB에 저장합니다.

## DB 연결 계약

`backend-patch.yaml`은 Backend에 다음 환경변수만 명시적으로 주입합니다.
Secret은 Backend와 같은 `gateway-lab-staging` namespace에 있어야 합니다.

| 환경변수 | Secret 이름 | 필수 키 | 값의 의미 |
|---|---|---|---|
| `DB_URL` | `backend-db-secret` | `DB_URL` | 접근 가능한 PostgreSQL의 JDBC URL |
| `DB_USERNAME` | `backend-db-secret` | `DB_USERNAME` | DB 계정 |
| `DB_PASSWORD` | `backend-db-secret` | `DB_PASSWORD` | DB 암호 |

Secret이나 필수 키가 없으면 컨테이너가 시작되지 않습니다. 값은 YAML/ConfigMap에 넣지 않고
조직의 Secret 전달 방식으로 공급합니다. 로컬에서 준비 파일이 필요하면 Git에서 제외된
`secrets/staging-db.env`에 위 키를 보관하고 파일 접근 권한을 제한합니다.
`apply-local-secret.sh`는 이 DB Secret을 생성하지 않습니다.

## 배포 전에 준비할 항목

- PostgreSQL 서버·DB·계정과 Pod에서의 DNS/네트워크 접근. 이 overlay는 PostgreSQL을 배포하지 않습니다.
- 위 Secret과 Flyway migration에 필요한 DB 권한. Flyway가 스키마를 변경하고 Hibernate는 `ddl-auto=validate`로 검사합니다.
- 인증 Secret과 신뢰하는 발급자. 현재 base의 `JWT_SECRET` 참조와 staging의 HS256 검증 설정은 유지됩니다.
  issuer 문자열만 바꾸면 OIDC로 전환되지 않습니다. OIDC를 사용하려면 양쪽 앱의 `oidc` 프로필과 issuer/JWKS/audience 설정,
  Secret 참조를 함께 구성해야 합니다. [Keycloak 실습](../../../docs/learning/16-keycloak-and-method-security.md)을 참고하세요.
- 실제 CORS origin·이미지·클러스터 용량. Backend 복제본은 2개이고 각 Hikari pool 상한은 5입니다. rollout 중 추가 Pod의 연결도 고려합니다.

local용 Secret을 staging 인증 구성으로 간주하지 않습니다. 데모 로그인 발급기는 staging에서 비활성화됩니다.
기존 NetworkPolicy는 ingress만 제한합니다. 외부 DB 연결 허용 여부는 클러스터의 네트워크 정책도 확인합니다.

## 배포 없이 확인

저장소 루트에서 실행합니다. 클러스터 접속이나 실제 Secret 값은 필요하지 않습니다.

```bash
kubectl kustomize k8s/overlays/staging
bash scripts/verify.sh
```

렌더링 결과에서 `gateway-lab-staging`, Backend 복제본 2개, `staging` 프로필과 위 Secret 키 참조를 확인합니다.
렌더링 성공은 Secret 존재·DB 연결·migration·JWT 인증·readiness 성공을 검증하지 않습니다.
공유/운영 배포는 조직의 별도 준비·검증 절차를 따릅니다.
