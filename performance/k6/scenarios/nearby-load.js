import exec from 'k6/execution';
import { check } from 'k6';

import {
    getNearbyMeetings,
    login,
    loginTokens,
} from '../lib/api.js';

import {
    assertCredentials,
    assertSafeTarget,
    config,
} from '../lib/config.js';

const TARGET_RPS = config.targetRps;

const LATITUDE = Number(__ENV.K6_NEARBY_LATITUDE || '37.5700');
const LONGITUDE = Number(__ENV.K6_NEARBY_LONGITUDE || '126.8200');
const RADIUS_METERS = Number(__ENV.K6_NEARBY_RADIUS_METERS || '5000');

function assertNearbyConfig() {
    if (!Number.isFinite(LATITUDE) || LATITUDE < -90 || LATITUDE > 90) {
        throw new Error('K6_NEARBY_LATITUDE must be between -90 and 90.');
    }

    if (!Number.isFinite(LONGITUDE) || LONGITUDE < -180 || LONGITUDE > 180) {
        throw new Error('K6_NEARBY_LONGITUDE must be between -180 and 180.');
    }

    if (
        !Number.isInteger(RADIUS_METERS) ||
        RADIUS_METERS < 100 ||
        RADIUS_METERS > 50000
    ) {
        throw new Error(
            'K6_NEARBY_RADIUS_METERS must be an integer between 100 and 50000.',
        );
    }
}

export const options = {
    scenarios: {
        nearby_meeting: {
            executor: 'constant-arrival-rate',

            rate: TARGET_RPS,
            timeUnit: '1s',
            duration: '2m',

            preAllocatedVUs: Math.max(10, TARGET_RPS),
            maxVUs: Math.max(20, TARGET_RPS * 2),
        },
    },

    summaryTrendStats: [
        'avg',
        'min',
        'p(50)',
        'p(90)',
        'p(95)',
        'p(99)',
        'max',
    ],

    thresholds: {
        checks: ['rate==1'],
        http_req_failed: ['rate==0'],
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
    assertNearbyConfig();

    const result = login(config.email, config.password);
    const tokens = loginTokens(result);

    if (!tokens) {
        exec.test.abort('Login failed.');
    }

    return {
        accessToken: tokens.accessToken,
    };
}

export default function (data) {
    const result = getNearbyMeetings(
        data.accessToken,
        LATITUDE,
        LONGITUDE,
        RADIUS_METERS,
        0,
    );

    check(result, {
        'nearby meeting succeeds': (response) => response.ok,
    });
}
