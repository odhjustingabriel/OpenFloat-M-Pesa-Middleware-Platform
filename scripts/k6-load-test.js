import http from 'k6/http';
import { check, sleep } from 'k6';

// k6 Load Test Configuration: Ramp up to 100 VUs to stress test gateway & trigger HPA
export const options = {
  stages: [
    { duration: '30s', target: 20 },  // Warmup
    { duration: '2m', target: 100 },  // Heavy load spike to trigger HPA scaling (>70% CPU)
    { duration: '1m', target: 100 },  // Sustained peak load
    { duration: '30s', target: 0 },   // Cool down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests must complete under 500ms
    http_req_failed: ['rate<0.01'],    // Error rate must be less than 1%
  },
};

const BASE_URL = __ENV.TARGET_URL || 'https://api.openfloat.co.ke';
const BEARER_TOKEN = __ENV.TOKEN || 'demo-load-test-bearer-token';

export default function () {
  const headers = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${BEARER_TOKEN}`,
  };

  // 1. Simulate STK Push Payment Initiation
  const stkPayload = JSON.stringify({
    msisdn: '254712345678',
    amount: Math.floor(Math.random() * 500) + 10,
    accountReference: `REF-K6-${Math.floor(Math.random() * 10000)}`,
    paybill: '174379',
  });

  const stkRes = http.post(`${BASE_URL}/api/v1/payments/stk-push`, stkPayload, { headers });
  check(stkRes, {
    'STK Push status is 200/202': (r) => r.status === 200 || r.status === 202,
  });

  sleep(0.5);

  // 2. Simulate Transaction Query
  const queryRes = http.get(`${BASE_URL}/api/v1/transactions?size=20`, { headers });
  check(queryRes, {
    'Transactions query status is 200': (r) => r.status === 200,
  });

  sleep(0.5);
}
