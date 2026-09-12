import { randomUUID } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { performance } from 'node:perf_hooks';
import { StompConnection } from './stomp-connection.mjs';

const args = parseArgs(process.argv.slice(2));
const manifest = JSON.parse(await readFile(required(args, 'manifest'), 'utf8'));
const scenario = args.scenario || 'focused';
const targetRps = integerArg(args, 'rps', 20, 1, 1000);
const durationSeconds = integerArg(args, 'duration', 60, 1, 3600);
const warmupSeconds = integerArg(args, 'warmup-seconds', 5, 0, 300);
const settleSeconds = integerArg(args, 'settle-seconds', 20, 1, 300);
const messageBytes = integerArg(args, 'message-bytes', 256, 80, 1000);
const runId = args['run-id'] || `${scenario}-${new Date().toISOString().replace(/\D/g, '').slice(0, 14)}`;
const outputPath = args.output || `chat-load-${runId}.json`;

if (!['focused', 'distributed'].includes(scenario)) {
  throw new Error('--scenario must be focused or distributed.');
}
if (!/^[A-Za-z0-9-]{1,64}$/.test(runId)) {
  throw new Error('--run-id must contain 1-64 letters, digits, or hyphens.');
}
if (!Array.isArray(manifest.clients) || manifest.clients.length !== 10) {
  throw new Error('The manifest must contain exactly 10 clients.');
}
if (!Array.isArray(manifest.roomIds) || manifest.roomIds.length !== 10
    || manifest.roomIds.some((roomId) => !/^\d+$/.test(String(roomId)))) {
  throw new Error('The manifest must contain exactly 10 numeric roomIds.');
}

const baseUrl = new URL(manifest.baseUrl || 'http://127.0.0.1:8080');
if (!['127.0.0.1', 'localhost', '::1'].includes(baseUrl.hostname)) {
  throw new Error('Remote targets are blocked. This runner is local-only.');
}
const wsUrl = new URL('/ws', baseUrl);
wsUrl.protocol = baseUrl.protocol === 'https:' ? 'wss:' : 'ws:';

const tokens = await Promise.all(manifest.clients.map(login));
await api('/api/v1/performance/chat-send/reset', tokens[0], {
  method: 'POST',
  query: { runId },
});
const connections = tokens.map((token, index) => new StompConnection(
  wsUrl,
  token,
  index,
));
await Promise.all(connections.map((connection) => connection.connect()));

const receivedByMessage = new Map();
const stompErrors = [];
for (const connection of connections) {
  connection.onMessage = (message, receivedAt) => {
    if (typeof message?.clientMessageId !== 'string') return;
    const entry = receivedByMessage.get(message.clientMessageId) || {
      messageId: message.id,
      roomId: String(message.roomId),
      sequence: Number(message.sequence),
      receivers: new Map(),
    };
    const previous = entry.receivers.get(connection.index);
    entry.receivers.set(connection.index, previous || receivedAt);
    entry.duplicateReceipts = (entry.duplicateReceipts || 0) + (previous ? 1 : 0);
    receivedByMessage.set(message.clientMessageId, entry);
  };
  connection.onErrorMessage = (body) => stompErrors.push({
    client: connection.index,
    receivedAt: new Date().toISOString(),
    body,
  });
  connection.subscribe('/user/queue/chat/errors', `errors-${connection.index}`);
  for (const roomId of manifest.roomIds) {
    connection.subscribe(`/topic/chat/rooms/${roomId}`, `room-${connection.index}-${roomId}`);
  }
}

if (warmupSeconds > 0) {
  const warmupCount = Math.max(10, Math.ceil(targetRps * warmupSeconds));
  const warmupIds = new Set();
  await sendScheduled({
    count: warmupCount,
    rps: Math.min(targetRps, 10),
    connections,
    roomIds: manifest.roomIds,
    scenario: 'distributed',
    createPayload: (index) => ({
      clientMessageId: randomUUID(),
      content: `[CHAT-WARMUP:${runId}] index=${index}`,
    }),
    onSent: (payload) => warmupIds.add(payload.clientMessageId),
  });
  const warmupDeadline = performance.now() + 10000;
  while (performance.now() < warmupDeadline) {
    const ready = [...warmupIds].every(
      (id) => receivedByMessage.get(id)?.receivers.size === connections.length,
    );
    if (ready) break;
    await delay(100);
  }
  const incompleteWarmup = [...warmupIds].filter(
    (id) => receivedByMessage.get(id)?.receivers.size !== connections.length,
  );
  if (incompleteWarmup.length > 0 || stompErrors.length > 0) {
    throw new Error(
      `Subscription readiness failed: ${incompleteWarmup.length} warmup message(s) `
      + `were not observed by all clients; ${stompErrors.length} STOMP error(s).`,
    );
  }
}

const baselineSequences = await latestSequences(tokens[0], manifest.roomIds);
receivedByMessage.clear();

const metricSamples = [];
let metricsRunning = true;
const metricLoop = (async () => {
  while (metricsRunning) {
    metricSamples.push(await captureRuntimeMetrics(tokens[0]));
    await delay(1000);
  }
})();

const totalScheduled = targetRps * durationSeconds;
const sent = new Map();
const scheduleStartedAt = performance.now() + 1000;
const sendStats = await sendScheduled({
  count: totalScheduled,
  rps: targetRps,
  connections,
  roomIds: manifest.roomIds,
  scenario,
  scheduledStart: scheduleStartedAt,
  createPayload: (index) => {
    const clientMessageId = randomUUID();
    const prefix = `[CHAT-LOAD:${runId}] scenario=${scenario} index=${index} `;
    const content = prefix + 'x'.repeat(Math.max(0, messageBytes - Buffer.byteLength(prefix)));
    return { clientMessageId, content };
  },
  onSent: (payload, sentAt, roomId, senderIndex) => sent.set(payload.clientMessageId, {
    sentAt,
    roomId: String(roomId),
    senderIndex,
  }),
});

const settleDeadline = performance.now() + settleSeconds * 1000;
while (performance.now() < settleDeadline) {
  const fullyObserved = [...sent.keys()].filter(
    (id) => receivedByMessage.get(id)?.receivers.size === connections.length,
  ).length;
  if (fullyObserved === sent.size) break;
  await delay(250);
}
metricsRunning = false;
await metricLoop;

const databaseMessages = await messagesAfter(tokens[0], manifest.roomIds, baselineSequences);
const measurementReport = (await api(
  '/api/v1/performance/chat-send/report',
  tokens[0],
  { query: { runId } },
)).data;

const sentIds = new Set(sent.keys());
const dbByClientId = new Map(
  databaseMessages
    .filter((message) => message.content.startsWith(`[CHAT-LOAD:${runId}]`))
    .map((message) => [message.clientMessageId, message]),
);
const committedByClientId = new Map(
  (measurementReport.records || [])
    .filter((record) => record.transactionStatus === 'COMMITTED')
    .map((record) => [record.clientMessageId, record.messageId]),
);
const receivedIds = new Set(
  [...receivedByMessage.keys()].filter((id) => sentIds.has(id)),
);
const latencies = [];
const firstReceiptLatencies = [];
const allObserverLatencies = [];
let duplicateReceipts = 0;
let incompleteObserverMessages = 0;
for (const [id, sentRecord] of sent) {
  const received = receivedByMessage.get(id);
  if (!received) continue;
  duplicateReceipts += received.duplicateReceipts || 0;
  if (received.receivers.size !== connections.length) incompleteObserverMessages++;
  const receiverTimes = [...received.receivers.values()].sort((a, b) => a - b);
  firstReceiptLatencies.push(Math.max(0, receiverTimes[0] - sentRecord.sentAt));
  if (receiverTimes.length === connections.length) {
    allObserverLatencies.push(Math.max(0, receiverTimes.at(-1) - sentRecord.sentAt));
  }
  for (const receivedAt of receiverTimes) {
    latencies.push(Math.max(0, receivedAt - sentRecord.sentAt));
  }
}
latencies.sort((a, b) => a - b);

const sequenceChecks = manifest.roomIds.map((roomId) => {
  const rows = databaseMessages
    .filter((message) => String(message.roomId) === String(roomId))
    .filter((message) => message.content.startsWith(`[CHAT-LOAD:${runId}]`))
    .sort((a, b) => a.sequence - b.sequence);
  const duplicates = rows.length - new Set(rows.map((row) => row.sequence)).size;
  const gaps = rows.slice(1).filter((row, index) => row.sequence !== rows[index].sequence + 1).length;
  return { roomId: String(roomId), messages: rows.length, duplicateSequences: duplicates, gaps };
});

const result = {
  runId,
  scenario,
  target: baseUrl.origin,
  conditions: {
    clients: connections.length,
    rooms: manifest.roomIds.length,
    subscribersPerRoom: connections.length,
    targetRps,
    durationSeconds,
    totalScheduled,
    messageBytes,
    warmupSeconds,
  },
  transmission: {
    scheduled: totalScheduled,
    attempted: sendStats.attempted,
    unsent: sendStats.unsent,
    scheduleLagMs: summarize(sendStats.scheduleLags),
  },
  delivery: {
    uniqueSent: sentIds.size,
    committedInHistory: [...sentIds].filter((id) => dbByClientId.has(id)).length,
    receivedByAnyClient: [...sentIds].filter((id) => receivedIds.has(id)).length,
    missingFromDatabase: [...sentIds].filter((id) => !dbByClientId.has(id)),
    storedButNotReceived: [...sentIds].filter((id) => dbByClientId.has(id) && !receivedIds.has(id)),
    missingCommitObservation: [...sentIds].filter((id) => !committedByClientId.has(id)),
    commitIdMismatches: [...sentIds].filter((id) => {
      const database = dbByClientId.get(id);
      const observedCommitId = committedByClientId.get(id);
      return database && observedCommitId && Number(database.id) !== Number(observedCommitId);
    }),
    incompleteObserverMessages,
    duplicateReceipts,
    clientReceiveLatencyMs: summarize(latencies),
    firstReceiptLatencyMs: summarize(firstReceiptLatencies),
    allObserversLatencyMs: summarize(allObserverLatencies),
    sequenceChecks,
    stompErrors,
  },
  server: measurementReport,
  runtimeMetrics: summarizeRuntimeMetrics(metricSamples),
  generatedAt: new Date().toISOString(),
};

await writeFile(outputPath, `${JSON.stringify(result, null, 2)}\n`, 'utf8');
console.log(JSON.stringify({
  outputPath,
  runId,
  scenario,
  scheduled: totalScheduled,
  attempted: sendStats.attempted,
  unsent: sendStats.unsent,
  committed: result.delivery.committedInHistory,
  received: result.delivery.receivedByAnyClient,
  latencyP95Ms: result.delivery.clientReceiveLatencyMs.p95,
  lockLookupP95Us: measurementReport.phaseMicros?.lockLookup?.p95,
  transactionP95Us: measurementReport.phaseMicros?.transactionCommit?.p95,
}, null, 2));

await Promise.all(connections.map((connection) => connection.close()));

async function login(client) {
  const response = await fetch(new URL('/api/v1/auth/login', baseUrl), {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email: client.email, password: client.password }),
  });
  const body = await response.json();
  if (!response.ok || !body?.data?.accessToken) {
    throw new Error(`Login failed for ${client.email}: HTTP ${response.status}`);
  }
  return body.data.accessToken;
}

async function api(path, token, options = {}) {
  const url = new URL(path, baseUrl);
  for (const [key, value] of Object.entries(options.query || {})) {
    url.searchParams.set(key, value);
  }
  const response = await fetch(url, {
    method: options.method || 'GET',
    headers: { Authorization: `Bearer ${token}` },
  });
  const body = await response.json();
  if (!response.ok || body?.success === false) {
    throw new Error(`${options.method || 'GET'} ${url.pathname} failed: HTTP ${response.status}`);
  }
  return body;
}

async function latestSequences(token, roomIds) {
  const result = {};
  for (const roomId of roomIds) {
    const body = await api(`/api/v1/chat/rooms/${roomId}/messages`, token, {
      query: { size: 1 },
    });
    result[String(roomId)] = Number(body.data?.latestSequence || 0);
  }
  return result;
}

async function messagesAfter(token, roomIds, baselines) {
  const messages = [];
  for (const roomId of roomIds) {
    let cursor = baselines[String(roomId)] || 0;
    while (true) {
      const body = await api(`/api/v1/chat/rooms/${roomId}/messages`, token, {
        query: { afterSequence: cursor, size: 100 },
      });
      const page = body.data?.content || [];
      messages.push(...page);
      if (!body.data?.hasMore || page.length === 0) break;
      cursor = Number(page.at(-1).sequence);
    }
  }
  return messages;
}

async function sendScheduled(options) {
  const scheduledStart = options.scheduledStart || performance.now() + 100;
  const interval = 1000 / options.rps;
  const scheduleLags = [];
  let attempted = 0;
  let unsent = 0;
  const tasks = [];
  for (let index = 0; index < options.count; index++) {
    const due = scheduledStart + index * interval;
    tasks.push(new Promise((resolve) => {
      setTimeout(() => {
        const now = performance.now();
        scheduleLags.push(Math.max(0, now - due));
        const senderIndex = index % options.connections.length;
        const roomIndex = options.scenario === 'focused' ? 0 : index % options.roomIds.length;
        const roomId = options.roomIds[roomIndex];
        const payload = options.createPayload(index);
        try {
          options.connections[senderIndex].send(
            `/app/chat/rooms/${roomId}/messages`,
            payload,
          );
          attempted++;
          options.onSent?.(payload, performance.now(), roomId, senderIndex);
        } catch {
          unsent++;
        }
        resolve();
      }, Math.max(0, due - performance.now()));
    }));
  }
  await Promise.all(tasks);
  return { attempted, unsent, scheduleLags };
}

async function captureRuntimeMetrics(token) {
  const names = [
    'hikaricp.connections.active',
    'hikaricp.connections.pending',
    'process.cpu.usage',
    'system.cpu.usage',
  ];
  const values = { at: new Date().toISOString() };
  for (const name of names) {
    try {
      const body = await api(`/actuator/metrics/${name}`, token);
      values[name] = body.measurements?.find((entry) => entry.statistic === 'VALUE')?.value ?? null;
    } catch {
      values[name] = null;
    }
  }
  return values;
}

function summarizeRuntimeMetrics(samples) {
  const result = { samples: samples.length };
  for (const name of [
    'hikaricp.connections.active',
    'hikaricp.connections.pending',
    'process.cpu.usage',
    'system.cpu.usage',
  ]) {
    const values = samples.map((sample) => sample[name]).filter(Number.isFinite);
    result[name] = values.length ? { max: Math.max(...values), average: average(values) } : null;
  }
  return result;
}

function summarize(values) {
  const sorted = [...values].sort((a, b) => a - b);
  return {
    count: sorted.length,
    p50: percentile(sorted, 0.50),
    p95: percentile(sorted, 0.95),
    p99: percentile(sorted, 0.99),
    max: sorted.length ? sorted.at(-1) : null,
  };
}

function percentile(sorted, fraction) {
  if (!sorted.length) return null;
  return sorted[Math.max(0, Math.ceil(sorted.length * fraction) - 1)];
}

function average(values) {
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

function delay(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

function parseArgs(argv) {
  const parsed = {};
  for (let index = 0; index < argv.length; index += 2) {
    const key = argv[index]?.replace(/^--/, '');
    if (!key || index + 1 >= argv.length) throw new Error(`Invalid argument: ${argv[index]}`);
    parsed[key] = argv[index + 1];
  }
  return parsed;
}

function required(values, name) {
  if (!values[name]) throw new Error(`--${name} is required.`);
  return values[name];
}

function integerArg(values, name, fallback, minimum, maximum) {
  const value = values[name] === undefined ? fallback : Number.parseInt(values[name], 10);
  if (!Number.isInteger(value) || value < minimum || value > maximum) {
    throw new Error(`--${name} must be between ${minimum} and ${maximum}.`);
  }
  return value;
}
