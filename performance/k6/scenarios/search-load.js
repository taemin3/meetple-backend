import exec from 'k6/execution';
import { check } from 'k6';

import {
    login,
    loginTokens,
    searchMeetings,
} from '../lib/api.js';

import {
    assertCredentials,
    config,
} from '../lib/config.js';

const TARGET_RPS = Number(__ENV.TARGET_RPS || 50);

export const options = {
    scenarios: {
        meeting_search: {
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
    assertCredentials();

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
    const result = searchMeetings(
        data.accessToken,
        '러닝',
        0,
    );

    check(result, {
        'search succeeds': (response) => response.ok,
    });
}