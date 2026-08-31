const DEFAULT_BASE_URL = 'http://127.0.0.1:8080';
const LOCAL_HOSTS = new Set(['127.0.0.1', 'localhost', '::1']);

function required(name) {
  const value = __ENV[name];
  if (!value || value.trim() === '') {
    throw new Error(`${name} environment variable is required.`);
  }
  return value.trim();
}

function integer(name, defaultValue, minimum, maximum) {
  const rawValue = __ENV[name];
  if (!rawValue) {
    return defaultValue;
  }

  const value = Number.parseInt(rawValue, 10);
  if (!Number.isInteger(value) || value < minimum || value > maximum) {
    throw new Error(`${name} must be an integer between ${minimum} and ${maximum}.`);
  }
  return value;
}

function normalizedBaseUrl() {
  return (__ENV.K6_BASE_URL || DEFAULT_BASE_URL).trim().replace(/\/$/, '');
}

export const config = Object.freeze({
  baseUrl: normalizedBaseUrl(),
  email: __ENV.K6_EMAIL ? __ENV.K6_EMAIL.trim() : '',
  password: __ENV.K6_PASSWORD || '',
  meetingId: __ENV.K6_MEETING_ID ? __ENV.K6_MEETING_ID.trim() : '',
  datasetManifest: __ENV.K6_DATASET_MANIFEST ? __ENV.K6_DATASET_MANIFEST.trim() : '',
  loadVus: integer('K6_LOAD_VUS', 5, 1, 30),
  iterationSeconds: integer('K6_ITERATION_SECONDS', 5, 1, 60),
  targetRps: integer('K6_TARGET_RPS', 25, 1, 400),
  isolatedEndpoint: (__ENV.K6_ISOLATED_ENDPOINT || 'categories').trim(),
});

export function assertSafeTarget() {
  const match = /^(https?):\/\/(\[[0-9a-fA-F:]+\]|[A-Za-z0-9.-]+)(?::\d{1,5})?\/?$/.exec(config.baseUrl);
  if (!match) {
    throw new Error('K6_BASE_URL must be a valid HTTP or HTTPS URL.');
  }

  const hostname = match[2].replace(/^\[/, '').replace(/\]$/, '');

  if (LOCAL_HOSTS.has(hostname)) {
    return;
  }

  if (__ENV.K6_ALLOW_REMOTE !== 'true') {
    throw new Error('Remote target is blocked. Set K6_ALLOW_REMOTE=true through the approved runner.');
  }

  if (__ENV.K6_CONFIRM_TARGET !== hostname) {
    throw new Error('K6_CONFIRM_TARGET must exactly match the remote target hostname.');
  }
}

export function assertCredentials() {
  required('K6_EMAIL');
  required('K6_PASSWORD');
}

export function assertMeetingId() {
  const meetingId = required('K6_MEETING_ID');
  if (!/^\d+$/.test(meetingId)) {
    throw new Error('K6_MEETING_ID must be a positive numeric meeting ID.');
  }
}
