import exec from 'k6/execution';
import { check } from 'k6';
import {
  getCategories,
  getMeetingDetail,
  getMeetings,
  getMeetingSummaries,
  getMyProfile,
  getNearbyMeetings,
  login,
  loginTokens,
  searchMeetings,
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
  'meeting-list-summary': 'meetingListSummaryRequest',
  'meeting-detail': 'meetingDetailRequest',
  'meeting-search': 'meetingSearchRequest',
  'meeting-nearby': 'meetingNearbyRequest',
  'member-me': 'memberProfileRequest',
});

const selectedExecutor = endpointExecutors[config.isolatedEndpoint];
if (!selectedExecutor) {
  throw new Error(
    'K6_ISOLATED_ENDPOINT must be categories, meeting-list, meeting-list-summary, meeting-detail, meeting-search, meeting-nearby, or member-me.',
  );
}

const preAllocatedVUs = Math.max(10, config.targetRps);
const SEARCH_KEYWORD = (__ENV.K6_SEARCH_KEYWORD || '러닝').trim();
const NEARBY_LATITUDE = Number(__ENV.K6_NEARBY_LATITUDE || '37.5700');
const NEARBY_LONGITUDE = Number(__ENV.K6_NEARBY_LONGITUDE || '126.8200');
const NEARBY_RADIUS_METERS = Number(__ENV.K6_NEARBY_RADIUS_METERS || '5000');

function assertSearchAndNearbyConfig() {
  if (config.isolatedEndpoint === 'meeting-search' && SEARCH_KEYWORD.length === 0) {
    throw new Error('K6_SEARCH_KEYWORD must not be blank.');
  }
  if (config.isolatedEndpoint !== 'meeting-nearby') {
    return;
  }
  if (!Number.isFinite(NEARBY_LATITUDE) || NEARBY_LATITUDE < -90 || NEARBY_LATITUDE > 90) {
    throw new Error('K6_NEARBY_LATITUDE must be between -90 and 90.');
  }
  if (!Number.isFinite(NEARBY_LONGITUDE) || NEARBY_LONGITUDE < -180 || NEARBY_LONGITUDE > 180) {
    throw new Error('K6_NEARBY_LONGITUDE must be between -180 and 180.');
  }
  if (!Number.isInteger(NEARBY_RADIUS_METERS)
      || NEARBY_RADIUS_METERS < 100
      || NEARBY_RADIUS_METERS > 50000) {
    throw new Error('K6_NEARBY_RADIUS_METERS must be an integer between 100 and 50000.');
  }
}

export const options = {
  scenarios: {
    isolated_endpoint: {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs,
      maxVUs: preAllocatedVUs * 2,
      stages: [
        { duration: '20s', target: config.targetRps },
        { duration: '80s', target: config.targetRps },
        { duration: '20s', target: 0 },
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
    //dropped_iterations: [{ threshold: 'count==0', abortOnFail: true, delayAbortEval: '5s' }],
      dropped_iterations: ['count==0'],
    http_req_duration: [
      'p(95)<500',
      'p(99)<1000',
    ],
  },
};

export function setup() {
  assertSafeTarget();
  assertCredentials();
  assertSearchAndNearbyConfig();
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

export function meetingListSummaryRequest(data) {
  const pageCount = datasetMeetingIds.length > 0
    ? Math.ceil(datasetMeetingIds.length / 20)
    : 1;
  const result = getMeetingSummaries(data.accessToken, meetingIndex() % pageCount);
  check(result, { 'meeting list summary contract succeeds': (response) => response.ok });
}

export function meetingDetailRequest(data) {
  const meetingId = datasetMeetingIds.length > 0
    ? datasetMeetingIds[meetingIndex() % datasetMeetingIds.length]
    : config.meetingId;
  const result = getMeetingDetail(data.accessToken, meetingId);
  check(result, { 'meeting detail contract succeeds': (response) => response.ok });
}

export function meetingSearchRequest(data) {
  const result = searchMeetings(data.accessToken, SEARCH_KEYWORD, 0);
  check(result, { 'meeting search contract succeeds': (response) => response.ok });
}

export function meetingNearbyRequest(data) {
  const result = getNearbyMeetings(
    data.accessToken,
    NEARBY_LATITUDE,
    NEARBY_LONGITUDE,
    NEARBY_RADIUS_METERS,
    0,
  );
  check(result, { 'meeting nearby contract succeeds': (response) => response.ok });
}

export function memberProfileRequest(data) {
  const result = getMyProfile(data.accessToken);
  check(result, { 'member profile contract succeeds': (response) => response.ok });
}
