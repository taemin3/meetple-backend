import { Counter, Rate, Trend } from 'k6/metrics';

export const apiSuccessRate = new Rate('api_success_rate');
export const httpErrorRate = new Rate('http_error_rate');
export const authFailures = new Counter('auth_failures');
export const businessFailures = new Counter('business_failures');
export const contractFailures = new Counter('contract_failures');
export const http5xx = new Counter('http_5xx');

const endpointTrends = Object.freeze({
  readiness: new Trend('readiness_duration', true),
  categories: new Trend('categories_duration', true),
  login: new Trend('login_duration', true),
  token_reissue: new Trend('token_reissue_duration', true),
  member_me: new Trend('member_me_duration', true),
  hosted_meetings: new Trend('hosted_meetings_duration', true),
  meeting_list: new Trend('meeting_list_duration', true),
  meeting_detail: new Trend('meeting_detail_duration', true),
});

const AUTH_ERROR_CODES = new Set([10101, 10301, 10302, 12410, 12411, 12413, 12414, 12415]);

function parseJson(response) {
  try {
    return response.json();
  } catch (_) {
    return null;
  }
}

export function recordResponse(response, options) {
  const endpoint = options.endpoint;
  const expectedStatus = options.expectedStatus || 200;
  const expectsEnvelope = options.expectsEnvelope !== false;
  const trend = endpointTrends[endpoint];
  if (!trend) {
    throw new Error(`Unknown endpoint metric: ${endpoint}`);
  }

  const duration = response.timings.duration;
  trend.add(duration);

  const status = Number(response.status || 0);
  const body = parseJson(response);
  const httpError = status === 0 || status >= 400;
  const authFailure = status === 401
    || status === 403
    || AUTH_ERROR_CODES.has(Number(body && body.code));
  const businessFailure = !authFailure && (
    (status >= 400 && status < 500)
    || (expectsEnvelope && body && body.success === false)
  );
  const serverFailure = status >= 500;
  const envelopeValid = !expectsEnvelope || (
    body !== null
    && body.success === true
    && typeof body.code === 'number'
  );
  const contractValid = status === expectedStatus && envelopeValid;
  const success = contractValid && !authFailure && !businessFailure && !serverFailure;

  httpErrorRate.add(httpError);
  apiSuccessRate.add(success);

  authFailures.add(authFailure ? 1 : 0);
  businessFailures.add(businessFailure ? 1 : 0);
  http5xx.add(serverFailure ? 1 : 0);
  contractFailures.add(
    !authFailure && !businessFailure && !serverFailure && !contractValid ? 1 : 0,
  );

  return Object.freeze({
    body,
    duration,
    ok: success,
    status,
  });
}
