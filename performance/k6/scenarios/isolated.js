import exec from 'k6/execution';
import { check } from 'k6';
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

const endpointExecutors = Object.freeze({
  categories: 'categoriesRequest',
  'meeting-list': 'meetingListRequest',
  'meeting-detail': 'meetingDetailRequest',
  'member-me': 'memberProfileRequest',
});

const selectedExecutor = endpointExecutors[config.isolatedEndpoint];
if (!selectedExecutor) {
  throw new Error('K6_ISOLATED_ENDPOINT must be categories, meeting-list, meeting-detail, or member-me.');
}

const preAllocatedVUs = Math.max(10, config.targetRps);

export const options = {
  scenarios: {
    isolated_endpoint: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs,
      maxVUs: preAllocatedVUs * 2,
      stages: [
        { duration: '30s', target: config.targetRps },
        { duration: '2m', target: config.targetRps },
        { duration: '30s', target: 0 },
      ],
      gracefulStop: '30s',
      exec: selectedExecutor,
    },
  },
  tags: {
    test_type: 'isolated',
    endpoint: config.isolatedEndpoint,
    target_rps: String(config.targetRps),
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
    dropped_iterations: [{ threshold: 'count==0', abortOnFail: true, delayAbortEval: '5s' }],
    http_req_duration: [
      'p(95)<500',
      'p(99)<1000',
    ],
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
    exec.test.abort('Isolated setup login failed. No isolated requests were started.');
  }

  return { accessToken: tokens.accessToken };
}

function meetingIndex() {
  return Number(exec.scenario.iterationInTest);
}

export function categoriesRequest() {
  const result = getCategories();
  check(result, { 'categories contract succeeds': (response) => response.ok });
}

export function meetingListRequest(data) {
  const pageCount = datasetMeetingIds.length > 0
    ? Math.ceil(datasetMeetingIds.length / 20)
    : 1;
  const result = getMeetings(data.accessToken, meetingIndex() % pageCount);
  check(result, { 'meeting list contract succeeds': (response) => response.ok });
}

export function meetingDetailRequest(data) {
  const meetingId = datasetMeetingIds.length > 0
    ? datasetMeetingIds[meetingIndex() % datasetMeetingIds.length]
    : config.meetingId;
  const result = getMeetingDetail(data.accessToken, meetingId);
  check(result, { 'meeting detail contract succeeds': (response) => response.ok });
}

export function memberProfileRequest(data) {
  const result = getMyProfile(data.accessToken);
  check(result, { 'member profile contract succeeds': (response) => response.ok });
}
