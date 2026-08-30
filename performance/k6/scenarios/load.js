import exec from 'k6/execution';
import { check, sleep } from 'k6';
import {
  getCategories,
  getMeetingDetail,
  getMeetings,
  getMyProfile,
  login,
  loginTokens,
} from '../lib/api.js';
import {
  assertCredentials,
  assertMeetingId,
  assertSafeTarget,
  config,
} from '../lib/config.js';
import { datasetMeetingIds } from '../lib/dataset.js';

export const options = {
  scenarios: {
    read_flow: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: config.loadVus },
        { duration: '4m', target: config.loadVus },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '15s',
      exec: 'readFlow',
    },
  },
  tags: {
    test_type: 'load',
    load_vus: String(config.loadVus),
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
  },
};

export function setup() {
  assertSafeTarget();
  assertCredentials();
  if (datasetMeetingIds.length === 0) {
    assertMeetingId();
  }

  const result = login(config.email, config.password);
  const tokens = loginTokens(result);
  if (!tokens) {
    exec.test.abort('Load setup login failed. No load requests were started.');
  }

  return { accessToken: tokens.accessToken };
}

export function readFlow(data) {
  const iterationStartedAt = Date.now();
  const iterationNumber = Number(exec.scenario.iterationInTest);
  const meetingId = datasetMeetingIds.length > 0
    ? datasetMeetingIds[iterationNumber % datasetMeetingIds.length]
    : config.meetingId;
  const meetingPage = datasetMeetingIds.length > 0
    ? iterationNumber % Math.ceil(datasetMeetingIds.length / 20)
    : 0;

  const categories = getCategories();
  check(categories, { 'categories contract succeeds': (result) => result.ok });

  const meetings = getMeetings(data.accessToken, meetingPage);
  check(meetings, { 'meeting list contract succeeds': (result) => result.ok });

  const detail = getMeetingDetail(data.accessToken, meetingId);
  check(detail, { 'meeting detail contract succeeds': (result) => result.ok });

  const profile = getMyProfile(data.accessToken);
  check(profile, { 'member profile contract succeeds': (result) => result.ok });

  const elapsedSeconds = (Date.now() - iterationStartedAt) / 1000;
  sleep(Math.max(0, config.iterationSeconds - elapsedSeconds));
}
