import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
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

const PEAK_RPS = 350;
const preAllocatedVUsPerEndpoint = Math.ceil((PEAK_RPS / 4) * 1.25);
const maxVUsPerEndpoint = preAllocatedVUsPerEndpoint * 2;

const stageMetrics = Object.freeze({
  300: createStageMetrics(300),
  350: createStageMetrics(350),
});

function createStageMetrics(rps) {
  return Object.freeze({
    requests: new Counter(`jfr_${rps}_requests`),
    all: new Trend(`jfr_${rps}_duration`, true),
    categories: new Trend(`jfr_${rps}_categories_duration`, true),
    meeting_list: new Trend(`jfr_${rps}_meeting_list_duration`, true),
    meeting_detail: new Trend(`jfr_${rps}_meeting_detail_duration`, true),
    member_me: new Trend(`jfr_${rps}_member_me_duration`, true),
  });
}

function arrivalScenario(execName) {
  return {
    executor: 'ramping-arrival-rate',
    startRate: 1,
    timeUnit: '4s',
    preAllocatedVUs: preAllocatedVUsPerEndpoint,
    maxVUs: maxVUsPerEndpoint,
    stages: [
      { duration: '30s', target: 300 },
      { duration: '1m', target: 300 },
      { duration: '30s', target: 350 },
      { duration: '2m', target: 350 },
      { duration: '30s', target: 0 },
    ],
    gracefulStop: '30s',
    exec: execName,
  };
}

export const options = {
  scenarios: {
    categories: arrivalScenario('categoriesRequest'),
    meeting_list: arrivalScenario('meetingListRequest'),
    meeting_detail: arrivalScenario('meetingDetailRequest'),
    member_me: arrivalScenario('memberProfileRequest'),
  },
  tags: {
    test_type: 'jfr_profile',
    peak_rps: String(PEAK_RPS),
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
    http_req_duration: [
      'p(95)<500',
      'p(99)<1000',
      { threshold: 'p(99)<3000', abortOnFail: true, delayAbortEval: '30s' },
    ],
    'http_req_duration{jfr_stage:300}': ['p(95)<500', 'p(99)<1000'],
    'http_req_duration{jfr_stage:350}': ['p(95)<500', 'p(99)<1000'],
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
    exec.test.abort('JFR profile setup login failed. No profile requests were started.');
  }

  return { accessToken: tokens.accessToken };
}

function currentPhase() {
  const elapsedMs = Date.now() - exec.scenario.startTime;
  if (elapsedMs < 30_000) return 'ramp_300';
  if (elapsedMs < 90_000) return '300';
  if (elapsedMs < 120_000) return 'ramp_350';
  if (elapsedMs < 240_000) return '350';
  return 'ramp_down';
}

function runAtCurrentStage(endpoint, request) {
  const phase = currentPhase();
  exec.vu.metrics.tags.jfr_stage = phase;
  const result = request();
  const metrics = stageMetrics[phase];
  if (metrics) {
    metrics.requests.add(1);
    metrics.all.add(result.duration);
    metrics[endpoint].add(result.duration);
  }
  return result;
}

function meetingIndex() {
  return Number(exec.scenario.iterationInTest);
}

export function categoriesRequest() {
  const result = runAtCurrentStage('categories', () => getCategories());
  check(result, { 'categories contract succeeds': (response) => response.ok });
}

export function meetingListRequest(data) {
  const pageCount = datasetMeetingIds.length > 0
    ? Math.ceil(datasetMeetingIds.length / 20)
    : 1;
  const result = runAtCurrentStage(
    'meeting_list',
    () => getMeetings(data.accessToken, meetingIndex() % pageCount),
  );
  check(result, { 'meeting list contract succeeds': (response) => response.ok });
}

export function meetingDetailRequest(data) {
  const meetingId = datasetMeetingIds.length > 0
    ? datasetMeetingIds[meetingIndex() % datasetMeetingIds.length]
    : config.meetingId;
  const result = runAtCurrentStage(
    'meeting_detail',
    () => getMeetingDetail(data.accessToken, meetingId),
  );
  check(result, { 'meeting detail contract succeeds': (response) => response.ok });
}

export function memberProfileRequest(data) {
  const result = runAtCurrentStage('member_me', () => getMyProfile(data.accessToken));
  check(result, { 'member profile contract succeeds': (response) => response.ok });
}
