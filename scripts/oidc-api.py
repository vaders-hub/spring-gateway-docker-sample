#!/usr/bin/env python3
"""Client Credentials 호출 예제. 표준 라이브러리만 사용하며 비밀값/토큰은 출력하지 않는다."""
import json
import sys
import time
from pathlib import Path
from urllib import request, parse, error

ISSUER = 'http://localhost:8180/realms/gateway-lab'
BASE = 'http://localhost:8080'

class NoRedirect(request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None  # Bearer/자격 증명을 다른 주소로 전달하지 않는다.

opener = request.build_opener(NoRedirect(), request.ProxyHandler({}))

def call(url, method='GET', token=None, data=None, content_type='application/json'):
    headers = {'Content-Type': content_type}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = request.Request(url, data=data, headers=headers, method=method)
    try:
        with opener.open(req, timeout=15) as response:
            return response.status, response.read()
    except error.HTTPError as response:
        return response.code, response.read()

def credentials():
    env = {}
    for line in (Path(__file__).resolve().parent.parent / '.env').read_text().splitlines():
        if line.startswith(('KC_READER_SECRET=', 'KC_WRITER_SECRET=')):
            key, value = line.split('=', 1)
            if key in env or not value:
                raise RuntimeError('Duplicate or empty credential entry')
            env[key] = value
    return env

def token(kind):
    secret = credentials()['KC_' + kind.upper() + '_SECRET']
    data = parse.urlencode({'grant_type': 'client_credentials', 'client_id': 'partner-' + kind,
                            'client_secret': secret}).encode()
    status, body = call(ISSUER + '/protocol/openid-connect/token', 'POST', data=data,
                        content_type='application/x-www-form-urlencoded')
    if status != 200:
        raise RuntimeError(f'Client authentication failed: HTTP {status}; check realm import/credentials')
    return json.loads(body)['access_token']

def check():
    reader, writer = token('reader'), token('writer')
    cases = [(None, 'GET', '/api/members', None, 401),
             ('invalid', 'GET', '/api/members', None, 401),
             (reader, 'GET', '/api/members', None, 200),
             (reader, 'GET', '/api/members/admin', None, 403),
             (reader, 'POST', '/api/echo', b'{"name":"lab","value":1}', 403),
             (writer, 'POST', '/api/echo', b'{"name":"lab","value":1}', 200),
             (writer, 'GET', '/api/members/admin', None, 403),
             (reader, 'GET', '/api/actuator/env', None, 403)]
    for credential, method, path, body, expected in cases:
        time.sleep(0.6)  # 이 검증은 JWT/권한용이다. 별도 429 실습과 충돌하지 않게 한다.
        actual, response = call(BASE + path, method, credential, body)
        if actual != expected:
            raise RuntimeError(f'{method} {path}: expected {expected}, got {actual}')
        if expected in (401, 403) and json.loads(response).get('errorCode') not in ('UNAUTHORIZED', 'ACCESS_DENIED'):
            raise RuntimeError('Unexpected error response contract')
        print(f'PASS {method} {path}: HTTP {actual}')
    print('Client Credentials checks passed; credentials and tokens were not printed.')

if __name__ == '__main__':
    try:
        if sys.argv[1:] == ['check']:
            check()
        elif len(sys.argv) in (4, 5) and sys.argv[1] in ('reader', 'writer'):
            kind, method, path = sys.argv[1:4]
            if not path.startswith('/api/') or any(c in path for c in '\r\n'):
                raise RuntimeError('Only local /api/ paths are allowed')
            body = sys.argv[4].encode() if len(sys.argv) == 5 else None
            status, response = call(BASE + path, method, token(kind), body)
            print(f'HTTP {status}')
            print(json.dumps(json.loads(response), ensure_ascii=False, indent=2))
            sys.exit(0 if status < 400 else 1)
        else:
            raise RuntimeError('Usage: python3 scripts/oidc-api.py check | reader|writer METHOD /api/path [JSON]')
    except (RuntimeError, KeyError, OSError, ValueError) as failure:
        print('OIDC call failed: ' + (str(failure) if isinstance(failure, RuntimeError) else type(failure).__name__), file=sys.stderr)
        sys.exit(1)
