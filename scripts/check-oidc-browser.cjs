// Optional real-browser check: Node.js + Playwright/Chromium. Never log credentials or token responses.
const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const env = Object.fromEntries(fs.readFileSync(path.join(__dirname, '..', '.env'), 'utf8')
  .split(/\r?\n/).filter(line => /^KC_(DEMO|LAB_ADMIN)_PASSWORD=/.test(line))
  .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
let step = 'launch';
(async () => {
  const browser = await chromium.launch({ headless: true });
  try {
    for (const [username, key, adminStatus] of [
      ['demo', 'KC_DEMO_PASSWORD', 403], ['lab-admin', 'KC_LAB_ADMIN_PASSWORD', 200]
    ]) {
      const context = await browser.newContext();
      const page = await context.newPage();
      page.on('response', response => {
        const url = new URL(response.url());
        if (url.origin === 'http://localhost:8080') console.log(`API ${url.pathname}: HTTP ${response.status()}`);
      });
      let issued;
      page.on('response', async response => {
        if (response.url() === 'http://localhost:8180/realms/gateway-lab/protocol/openid-connect/token'
            && response.status() === 200) issued = await response.json();
      });
      step = username + ': initialize';
      await page.goto('http://localhost:3001/');
      await page.locator('#login').click();
      step = username + ': credentials';
      await page.locator('#username').fill(username);
      await page.locator('#password').fill(env[key]);
      await page.locator('#kc-login').click();
      await page.waitForURL('http://localhost:3001/**');
      await page.waitForFunction(name => document.querySelector('#status').textContent.includes('로그인: ' + name), username);
      assert(JSON.parse(Buffer.from(issued.access_token.split('.')[1], 'base64url')).sub);
      step = username + ': members';
      await page.locator('[data-path="/api/members"]').click();
      await page.waitForFunction(() => document.querySelector('#result').textContent.startsWith('HTTP 200'));
      step = username + ': admin';
      await page.locator('[data-path="/api/members/admin"]').click();
      await page.waitForFunction(code => document.querySelector('#result').textContent.startsWith('HTTP ' + code), adminStatus);
      assert(issued?.access_token && issued?.refresh_token);
      // UI must not persist tokens. Adapter may temporarily persist only OAuth callback state.
      const values = await page.evaluate(() => [...Object.values(localStorage), ...Object.values(sessionStorage)]);
      assert(values.every(value => !value.includes(issued.access_token) && !value.includes(issued.refresh_token)));
      step = username + ': logout';
      await page.locator('#logout').click();
      await page.waitForURL('http://localhost:3001/**');
      await page.waitForFunction(() => document.querySelector('#status').textContent === '로그인되지 않았습니다.');
      const refresh = await context.request.post('http://localhost:8180/realms/gateway-lab/protocol/openid-connect/token', {
        form: { grant_type: 'refresh_token', client_id: 'lab-browser', refresh_token: issued.refresh_token }
      });
      assert.equal(refresh.status(), 400); // Session ended; cannot refresh.
      const oldAccess = await context.request.get('http://localhost:8080/api/members', {
        headers: { Authorization: 'Bearer ' + issued.access_token }
      });
      assert.equal(oldAccess.status(), 200); // Explicit policy: existing JWT lives until expiry.
      console.log(`PASS ${username}: PKCE login, members 200, admin ${adminStatus}, logout, refresh denied, old access valid until expiry`);
      await context.close();
    }
  } finally { await browser.close(); }
})().catch(error => { console.error(`FAIL ${step}: ${error.name}; secret-bearing diagnostics suppressed`); process.exitCode = 1; });
