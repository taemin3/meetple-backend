import exec from 'k6/execution';
import http from 'k6/http';
import { check, sleep } from 'k6';
import {
  createPushRetryMeasurementEvent,
  getPushRetryMeasurementStatus,
  login,
  loginTokens,
  succeedPushRetryMeasurement,
} from '../lib/api.js';
import { assertCredentials, assertSafeTarget } from '../lib/config.js';

const eventCount = Number.parseInt(__ENV.K6_CDC_EVENT_COUNT || '100', 10);
const vus = Number.parseInt(__ENV.K6_CDC_VUS || '10', 10);
const pauseSeconds = Number.parseInt(__ENV.K6_CDC_PAUSE_SECONDS || '60', 10);
const connectUrl = (__ENV.K6_CONNECT_URL || 'http://localhost:18083').replace(/\/$/, '');
const connector = 'meetple-outbox-connector';

if (!Number.isInteger(eventCount) || eventCount < 1 || eventCount > 1000) {
  throw new Error('K6_CDC_EVENT_COUNT must be an integer between 1 and 1000.');
}
if (!Number.isInteger(vus) || vus < 1 || vus > 50) {
  throw new Error('K6_CDC_VUS must be an integer between 1 and 50.');
}
if (!Number.isInteger(pauseSeconds) || pauseSeconds < 10 || pauseSeconds > 300) {
  throw new Error('K6_CDC_PAUSE_SECONDS must be an integer between 10 and 300.');
}
if (!/^http:\/\/(localhost|127\.0\.0\.1):\d+$/.test(connectUrl)) {
  throw new Error('K6_CONNECT_URL must point to a localhost Kafka Connect tunnel.');
}

export const options = {
  teardownTimeout: '15m',
  scenarios: {
    outbox_cdc_recovery: {
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
    exec.test.abort('Outbox CDC measurement login failed.');
  }
  const runId = `outbox-cdc-${Date.now()}`;
  const success = succeedPushRetryMeasurement(tokens.accessToken, runId);
  if (!success.ok) {
    exec.test.abort('Push retry measurement endpoint is not enabled.');
  }
  requireConnectorState('RUNNING', 60);
  const paused = http.put(`${connectUrl}/connectors/${connector}/pause`, null, {
    tags: { name: 'kafka_connect_pause' },
  });
  if (paused.status < 200 || paused.status >= 300) {
    exec.test.abort(`Kafka Connect pause failed: HTTP ${paused.status}`);
  }
  try {
    requireConnectorState('PAUSED', 60);
  } catch (error) {
    resumeConnector();
    throw error;
  }
  return { accessToken: tokens.accessToken, runId, eventCount };
}

export default function (data) {
  const index = Number(exec.scenario.iterationInTest);
  const result = createPushRetryMeasurementEvent(data.accessToken, data.runId, index);
  check(result, { 'outbox event committed': (response) => response.ok });
}

export function teardown(data) {
  let pausedStatus = null;
  let resumeRequestedAt = null;
  try {
    pausedStatus = awaitStatus(
      data,
      (status) => status.createdEvents === data.eventCount
        && status.committedOutboxEvents === data.eventCount
        && status.mainTopicEvents === 0,
      60,
      'paused backlog',
    );
    console.log(`OUTBOX_CDC_PAUSED ${JSON.stringify(pausedStatus)}`);
    sleep(pauseSeconds);
  } finally {
    resumeConnector();
    resumeRequestedAt = Date.now();
  }

  requireConnectorState('RUNNING', 60);
  const recovered = awaitStatus(
    data,
    (status) => status.mainTopicEvents === data.eventCount
      && status.successfulEvents === data.eventCount,
    5 * 60,
    'CDC catch-up',
  );
  console.log(`OUTBOX_CDC_RECOVERED ${JSON.stringify({
    ...recovered,
    connectorPauseSeconds: pauseSeconds,
    catchUpDurationMs: Date.now() - resumeRequestedAt,
  })}`);
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
    sleep(2);
  }
  throw new Error(`${phase} did not complete: ${JSON.stringify(latest)}`);
}

function requireConnectorState(expected, timeoutSeconds) {
  const deadline = Date.now() + timeoutSeconds * 1000;
  let latest = null;
  while (Date.now() < deadline) {
    const response = http.get(`${connectUrl}/connectors/${connector}/status`, {
      tags: { name: 'kafka_connect_status' },
    });
    if (response.status === 200) {
      latest = response.json();
      const connectorState = latest.connector && latest.connector.state;
      const taskStates = Array.isArray(latest.tasks) ? latest.tasks.map((task) => task.state) : [];
      if (connectorState === expected
        && taskStates.length > 0
        && taskStates.every((state) => state === expected)) {
        return;
      }
    }
    sleep(2);
  }
  throw new Error(`Connector did not become ${expected}: ${JSON.stringify(latest)}`);
}

function resumeConnector() {
  let latestStatus = 0;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const response = http.put(`${connectUrl}/connectors/${connector}/resume`, null, {
      tags: { name: 'kafka_connect_resume' },
    });
    latestStatus = response.status;
    if (response.status >= 200 && response.status < 300) {
      return;
    }
    sleep(1);
  }
  throw new Error(`Kafka Connect resume failed: HTTP ${latestStatus}`);
}
