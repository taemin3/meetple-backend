import exec from 'k6/execution';
import { check } from 'k6';
import { getAuthProbe, login, loginTokens } from '../lib/api.js';
import { assertCredentials, assertSafeTarget, config } from '../lib/config.js';

function targetRps() {
  const raw = (__ENV.K6_AUTH_PROBE_TARGET_RPS || '300').trim();
  const value = Number(raw);
  if (!Number.isInteger(value) || value < 1 || value > 500) {
    throw new Error('K6_AUTH_PROBE_TARGET_RPS must be an integer between 1 and 500.');
  }
  return value;
}

const requestedRps = targetRps();
const preAllocatedVUs = Math.max(10, requestedRps);

export const options = {
  scenarios: {
    auth_probe: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs,
      maxVUs: preAllocatedVUs * 2,
      stages: [
        { duration: '30s', target: requestedRps },
        { duration: '2m', target: requestedRps },
        { duration: '30s', target: 0 },
      ],
      gracefulStop: '30s',
      exec: 'authProbeRequest',
    },
  },
  tags: {
    test_type: 'auth-probe',
    endpoint: 'auth-probe',
    target_rps: String(requestedRps),
  },
  summaryTrendStats: ['avg', 'min', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    checks: [{ threshold: 'rate==1', abortOnFail: true, delayAbortEval: '15s' }],
    api_success_rate: [{ threshold: 'rate==1', abortOnFail: true, delayAbortEval: '15s' }],
    http_req_failed: [{ threshold: 'rate==0', abortOnFail: true, delayAbortEval: '15s' }],
    http_5xx: [{ threshold: 'count==0', abortOnFail: true, delayAbortEval: '1s' }],
    auth_failures: [{ threshold: 'count==0', abortOnFail: true, delayAbortEval: '1s' }],
    business_failures: [{ threshold: 'count==0', abortOnFail: true, delayAbortEval: '15s' }],
    contract_failures: [{ threshold: 'count==0', abortOnFail: true, delayAbortEval: '15s' }],
    dropped_iterations: ['count==0'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
  },
};

export function setup() {
  assertSafeTarget();
  assertCredentials();

  const result = login(config.email, config.password);
  const tokens = loginTokens(result);
  if (!tokens) {
    exec.test.abort('Auth probe setup login failed. No probe requests were started.');
  }

  return { accessToken: tokens.accessToken };
}

export function authProbeRequest(data) {
  const result = getAuthProbe(data.accessToken);
  check(result, { 'authenticated probe returns 204': (response) => response.ok });
}
