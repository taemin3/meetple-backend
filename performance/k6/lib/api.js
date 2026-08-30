import http from 'k6/http';
import { config } from './config.js';
import { recordResponse } from './metrics.js';

const JSON_HEADERS = Object.freeze({
  'Content-Type': 'application/json',
});

function authHeaders(accessToken) {
  return {
    Authorization: `Bearer ${accessToken}`,
  };
}

function request(method, path, endpoint, options = {}) {
  const headers = options.headers || {};
  const body = options.body === undefined ? null : JSON.stringify(options.body);
  const response = http.request(method, `${config.baseUrl}${path}`, body, {
    headers,
    redirects: 0,
    tags: { name: endpoint },
    timeout: '10s',
  });

  return recordResponse(response, {
    endpoint,
    expectedStatus: options.expectedStatus || 200,
    expectsEnvelope: options.expectsEnvelope,
  });
}

export function getReadiness() {
  return request('GET', '/readyz', 'readiness', { expectsEnvelope: false });
}

export function getCategories() {
  return request('GET', '/api/v1/categories', 'categories');
}

export function login(email, password) {
  return request('POST', '/api/v1/auth/login', 'login', {
    body: { email, password },
    headers: JSON_HEADERS,
  });
}

export function reissue(refreshToken) {
  return request('POST', '/api/v1/auth/reissue', 'token_reissue', {
    body: { refreshToken },
    headers: JSON_HEADERS,
  });
}

export function getMyProfile(accessToken) {
  return request('GET', '/api/v1/users/me', 'member_me', {
    headers: authHeaders(accessToken),
  });
}

export function getMeetings(accessToken, page = 0) {
  return request(
    'GET',
    `/api/v1/meetings?status=RECRUITING&page=${page}&size=20&sort=meetingDate,asc`,
    'meeting_list',
    { headers: authHeaders(accessToken) },
  );
}

export function getMyHostedMeetings(accessToken) {
  return request(
    'GET',
    '/api/v1/users/me/meetings/hosted?page=0&size=20&sort=createdAt,desc',
    'hosted_meetings',
    { headers: authHeaders(accessToken) },
  );
}

export function getMeetingDetail(accessToken, meetingId) {
  return request('GET', `/api/v1/meetings/${meetingId}`, 'meeting_detail', {
    headers: authHeaders(accessToken),
  });
}

export function loginTokens(result) {
  if (!result.ok || !result.body || !result.body.data) {
    return null;
  }

  const accessToken = result.body.data.accessToken;
  const refreshToken = result.body.data.refreshToken;
  if (!accessToken || !refreshToken) {
    return null;
  }

  return Object.freeze({ accessToken, refreshToken });
}

export function meetingIdFromList(result) {
  const content = result.body && result.body.data && result.body.data.content;
  if (!Array.isArray(content) || content.length === 0 || !content[0].id) {
    return null;
  }
  return String(content[0].id);
}
