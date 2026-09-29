#!/usr/bin/env python3
"""기존 .env 값은 보존하고 Keycloak 전용 비밀값만 최초 생성한다. 값은 출력하지 않는다."""
from pathlib import Path
import os
import secrets

root = Path(__file__).resolve().parent.parent
path = root / '.env'
os.umask(0o077)
text = path.read_text() if path.exists() else ''
required = ['KC_DB_PASSWORD', 'KC_BOOTSTRAP_PASSWORD', 'KC_DEMO_PASSWORD',
            'KC_LAB_ADMIN_PASSWORD', 'KC_READER_SECRET', 'KC_WRITER_SECRET']
for key in required:
    matches = [line for line in text.splitlines() if line.startswith(key + '=')]
    if len(matches) > 1 or (matches and not matches[0].split('=', 1)[1].strip()):
        raise SystemExit(f'{key}: remove duplicate/empty entry before initialization; existing values were not changed')
missing = [key for key in required if not any(line.startswith(key + '=') for line in text.splitlines())]
if missing:
    with path.open('a') as out:
        out.write('\n# Keycloak local credentials; never commit or print these values.\n')
        for key in missing:
            out.write(f'{key}={secrets.token_hex(24)}\n')
print(f'Keycloak environment ready; {len(missing)} values generated, existing values preserved.')
