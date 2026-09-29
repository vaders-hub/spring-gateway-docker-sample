import Keycloak from './vendor/keycloak.js';

// 공식 adapter가 state/nonce/PKCE, code 교환과 refresh를 처리한다. 토큰을 storage/log에 저장하지 않는다.
const keycloak = new Keycloak({ url: 'http://localhost:8180', realm: 'gateway-lab', clientId: 'lab-browser' });
const status = document.querySelector('#status');
const result = document.querySelector('#result');
const redirectUri = 'http://localhost:3001/';
function render() {
  status.textContent = keycloak.authenticated ? `로그인: ${keycloak.tokenParsed.preferred_username}` : '로그인되지 않았습니다.';
  document.querySelector('#login').disabled = !!keycloak.authenticated;
  document.querySelector('#logout').disabled = !keycloak.authenticated;
  document.querySelectorAll('[data-path]').forEach(button => button.disabled = !keycloak.authenticated);
}
try {
  await keycloak.init({ pkceMethod: 'S256', checkLoginIframe: false, redirectUri });
  render();
} catch {
  status.textContent = '인증 초기화 실패. Keycloak 실행 상태와 localhost 주소를 확인하세요.';
}
document.querySelector('#login').onclick = () => keycloak.login({ redirectUri });
document.querySelector('#logout').onclick = () => keycloak.logout({ redirectUri });
for (const button of document.querySelectorAll('[data-path]')) {
  button.onclick = async () => {
    try {
      await keycloak.updateToken(30);
      const response = await fetch(`http://localhost:8080${button.dataset.path}`, {
        headers: { Authorization: `Bearer ${keycloak.token}` }
      });
      result.textContent = `HTTP ${response.status}\n${JSON.stringify(await response.json(), null, 2)}`;
    } catch {
      result.textContent = '요청 실패. 인증 만료 또는 서비스 연결 상태를 확인하세요.';
      render();
    }
  };
}
