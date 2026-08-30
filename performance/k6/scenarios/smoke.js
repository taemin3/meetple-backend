import { check, fail, sleep } from 'k6';
import {
  getCategories,
  getMeetingDetail,
  getMeetings,
  getMyHostedMeetings,
  getMyProfile,
  getReadiness,
  login,
  loginTokens,
  meetingIdFromList,
  reissue,
} from '../lib/api.js';
import { assertCredentials, assertSafeTarget, config } from '../lib/config.js';

export const options = {
  vus: 1,
  iterations: 1,
  tags: {
    test_type: 'smoke',
  },
  summaryTrendStats: ['avg', 'min', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    checks: ['rate==1'],
    api_success_rate: ['rate==1'],
    http_req_failed: ['rate==0'],
    http_5xx: ['count==0'],
    auth_failures: ['count==0'],
    business_failures: ['count==0'],
    contract_failures: ['count==0'],
  },
};

export default function () {
  assertSafeTarget();
  assertCredentials();

  const readiness = getReadiness();
  check(readiness, { 'readiness contract succeeds': (result) => result.ok });
  sleep(0.5);

  const categories = getCategories();
  check(categories, { 'categories contract succeeds': (result) => result.ok });
  sleep(0.5);

  const loginResult = login(config.email, config.password);
  const tokens = loginTokens(loginResult);
  check(tokens, { 'login returns access and refresh tokens': (result) => result !== null });
  if (!tokens) {
    fail('Smoke login failed. Check the dedicated staging account credentials.');
  }
  sleep(0.5);

  const profile = getMyProfile(tokens.accessToken);
  check(profile, { 'member profile contract succeeds': (result) => result.ok });
  sleep(0.5);

  const meetings = getMeetings(tokens.accessToken);
  check(meetings, { 'meeting list contract succeeds': (result) => result.ok });
  sleep(0.5);

  const hostedMeetings = getMyHostedMeetings(tokens.accessToken);
  check(hostedMeetings, { 'hosted meeting list contract succeeds': (result) => result.ok });
  sleep(0.5);

  const meetingId = config.meetingId || meetingIdFromList(hostedMeetings);
  check(meetingId, { 'hosted meeting detail target exists': (value) => value !== null && value !== '' });
  if (!meetingId) {
    fail('No hosted meeting is available. Create a dedicated test meeting or set K6_MEETING_ID.');
  }
  console.log(`Hosted test meeting ID: ${meetingId}`);

  const detail = getMeetingDetail(tokens.accessToken, meetingId);
  check(detail, { 'meeting detail contract succeeds': (result) => result.ok });
  sleep(0.5);

  const reissueResult = reissue(tokens.refreshToken);
  const rotatedTokens = loginTokens(reissueResult);
  check(rotatedTokens, { 'token reissue rotates both tokens': (result) => result !== null });
}
