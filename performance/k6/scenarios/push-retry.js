import exec from 'k6/execution';
import { check, sleep } from 'k6';
import {
  createPushRetryMeasurementEvent,
  failPushRetryMeasurement,
  getPushRetryMeasurementStatus,
  login,
  loginTokens,
  replayPushRetryMeasurement,
} from '../lib/api.js';
import { assertCredentials, assertSafeTarget } from '../lib/config.js';

const eventCount = Number.parseInt(__ENV.K6_PUSH_EVENT_COUNT || '1000', 10);
const vus = Number.parseInt(__ENV.K6_PUSH_VUS || '20', 10);

if (!Number.isInteger(eventCount) || eventCount < 1 || eventCount > 1000) {
  throw new Error('K6_PUSH_EVENT_COUNT must be an integer between 1 and 1000.');
}
if (!Number.isInteger(vus) || vus < 1 || vus > 50) {
  throw new Error('K6_PUSH_VUS must be an integer between 1 and 50.');
}

export const options = {
  teardownTimeout: '30m',
  scenarios: {
    push_retry: {
      executor: 'shared-iterations',
      vus,
      iterations: eventCount,
      maxDuration: '5m',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
  },
};

export function setup() {
  assertSafeTarget();
  assertCredentials();
  const tokens = loginTokens(login(__ENV.K6_EMAIL, __ENV.K6_PASSWORD));
  if (!tokens) {
    exec.test.abort('Push retry measurement login failed.');
  }
  const runId = `push-retry-${Date.now()}`;
  const failed = failPushRetryMeasurement(tokens.accessToken, runId);
  if (!failed.ok) {
    exec.test.abort('Push retry measurement endpoint is not enabled.');
  }
  return { accessToken: tokens.accessToken, runId, eventCount };
}

export default function (data) {
  const index = Number(exec.scenario.iterationInTest);
  const result = createPushRetryMeasurementEvent(data.accessToken, data.runId, index);
  check(result, { 'measurement event committed': (response) => response.ok });
}

export function teardown(data) {
  const dltStatus = awaitStatus(
    data,
    (status) => status.createdEvents === data.eventCount && status.dltEvents === data.eventCount,
    15 * 60,
    'DLQ',
  );
  const replay = replayPushRetryMeasurement(data.accessToken, data.runId);
  if (!replay.ok) {
    throw new Error(`DLQ replay failed for ${data.runId}.`);
  }
  const completed = awaitStatus(
    data,
    (status) => status.successfulEvents === data.eventCount,
    10 * 60,
    'replay',
  );
  console.log(`PUSH_RETRY_STAGING_RESULT ${JSON.stringify(completed)}`);
  console.log(`PUSH_RETRY_STAGING_DLT ${JSON.stringify(dltStatus)}`);
}

function awaitStatus(data, completed, timeoutSeconds, phase) {
  const deadline = Date.now() + timeoutSeconds * 1000;
  let latest = null;
  while (Date.now() < deadline) {
    const result = getPushRetryMeasurementStatus(data.accessToken, data.runId);
    if (result.ok && result.body && result.body.data) {
      latest = result.body.data;
      if (completed(latest)) {
        return latest;
      }
    }
    sleep(5);
  }
  throw new Error(`${phase} did not complete: ${JSON.stringify(latest)}`);
}
