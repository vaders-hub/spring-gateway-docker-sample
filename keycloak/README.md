# Realm seed

`gateway-lab-realm.json`은 최초 기동용 seed이다. 기존 realm이 있으면 Keycloak은 import를 건너뛴다.
운영 중 변경은 관리 API/Console 또는 별도 migration으로 적용한다. DB 볼륨을 지워 동기화하지 않는다.

- `${KC_*}`는 Keycloak이 컨테이너 환경변수에서 치환한다. 실제 비밀값은 Git에 넣지 않는다.
- 브라우저: Authorization Code + S256 PKCE, 정확한 redirect URI, secret 없음.
- 서비스: Client Credentials, reader와 writer의 자격 증명/권한 분리.
- `permissions`는 사용자/서비스의 realm role과 client scope mapping의 교집합이다.
- 서버는 `permissions`를 `SCOPE_` authority로 변환한다. 요청의 `scope`로 관리자 권한을 얻을 수 없다.
- 기본 client scope `basic`은 sub를 제공한다. profile/email만 설정하면 사용자 식별자가 누락될 수 있다.
- `aud=gateway-sample-api`, RS256, Access Token 5분, Refresh Token rotation.
- demo와 lab-admin은 로컬 학습 사용자다. realm 관리자 계정과 구분한다.

실행/종료/로그아웃 정책: [16단계](../docs/learning/16-keycloak-and-method-security.md).
