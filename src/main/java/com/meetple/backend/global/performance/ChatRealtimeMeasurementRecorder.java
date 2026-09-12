package com.meetple.backend.global.performance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ChatRealtimeMeasurementRecorder {

    public static final String INBOUND_AUTH = "inboundAuth";
    public static final String INBOUND_QUEUE = "inboundQueue";
    public static final String OUTBOUND_AUTH = "outboundAuth";
    public static final String OUTBOUND_QUEUE = "outboundQueue";
    public static final String LOCAL_FAN_OUT = "localFanOut";
    public static final String REDIS_PUBLISH = "redisPublish";

    private static final Pattern RUN_MARKER = Pattern.compile(
            "^\\[CHAT-LOAD:([A-Za-z0-9-]{1,64})]"
    );
    private static final List<String> PHASES = List.of(
            INBOUND_AUTH,
            INBOUND_QUEUE,
            OUTBOUND_AUTH,
            OUTBOUND_QUEUE,
            LOCAL_FAN_OUT,
            REDIS_PUBLISH
    );
    private static final int MAX_RUNS = 20;
    private static final int MAX_SAMPLES_PER_PHASE = 100_000;

    private final boolean enabled;
    private final Map<String, RunState> runs = new ConcurrentHashMap<>();
    private final Map<UUID, Trace> traces = new ConcurrentHashMap<>();

    public ChatRealtimeMeasurementRecorder(
            @Value("${meetple.performance.chat-send.enabled:false}") boolean enabled
    ) {
        this.enabled = enabled;
    }

    public Timer startInbound(UUID clientMessageId, String content) {
        Trace trace = register(clientMessageId, content);
        return timer(trace, INBOUND_AUTH);
    }

    public Timer start(UUID clientMessageId, String phase) {
        if (!enabled || clientMessageId == null) {
            return Timer.noop();
        }
        return timer(traces.get(clientMessageId), phase);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void markInboundEnqueued(UUID clientMessageId) {
        if (!enabled || clientMessageId == null) {
            return;
        }
        Trace trace = traces.get(clientMessageId);
        if (trace != null) {
            trace.inboundEnqueuedNanos = System.nanoTime();
        }
    }

    public void markInboundDequeued(UUID clientMessageId) {
        if (!enabled || clientMessageId == null) {
            return;
        }
        Trace trace = traces.get(clientMessageId);
        if (trace == null || trace.inboundEnqueuedNanos == 0L) {
            return;
        }
        record(trace, INBOUND_QUEUE, microsSince(trace.inboundEnqueuedNanos));
        trace.inboundEnqueuedNanos = 0L;
    }

    public void markOutboundEnqueued(UUID clientMessageId, String deliveryKey) {
        if (!enabled || clientMessageId == null) {
            return;
        }
        Trace trace = traces.get(clientMessageId);
        if (trace != null && deliveryKey != null) {
            trace.outboundEnqueuedNanos.put(deliveryKey, System.nanoTime());
        }
    }

    public void markOutboundDequeued(UUID clientMessageId, String deliveryKey) {
        if (!enabled || clientMessageId == null) {
            return;
        }
        Trace trace = traces.get(clientMessageId);
        if (trace == null || deliveryKey == null) {
            return;
        }
        Long enqueuedNanos = trace.outboundEnqueuedNanos.remove(deliveryKey);
        if (enqueuedNanos != null) {
            record(trace, OUTBOUND_QUEUE, microsSince(enqueuedNanos));
        }
    }

    public RunReport report(String runId) {
        validateRunId(runId);
        RunState state = runs.get(runId);
        Map<String, Percentiles> phaseMicros = new LinkedHashMap<>();
        for (String phase : PHASES) {
            List<Long> values = state == null
                    ? new ArrayList<>()
                    : new ArrayList<>(state.samplesByPhase.get(phase).values);
            values.sort(Comparator.naturalOrder());
            phaseMicros.put(phase, Percentiles.from(values));
        }
        return new RunReport(runId, phaseMicros);
    }

    public void reset(String runId) {
        validateRunId(runId);
        runs.remove(runId);
        traces.entrySet().removeIf(entry -> runId.equals(entry.getValue().runId));
    }

    void recordSample(
            UUID clientMessageId,
            String content,
            String phase,
            long micros
    ) {
        Trace trace = content == null
                ? traces.get(clientMessageId)
                : register(clientMessageId, content);
        if (trace != null && PHASES.contains(phase)) {
            record(trace, phase, micros);
        }
    }

    private Trace register(UUID clientMessageId, String content) {
        if (!enabled || clientMessageId == null || content == null) {
            return null;
        }
        Matcher matcher = RUN_MARKER.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        String runId = matcher.group(1);
        if (!runs.containsKey(runId) && runs.size() >= MAX_RUNS) {
            return null;
        }
        RunState state = runs.computeIfAbsent(runId, ignored -> new RunState());
        return traces.computeIfAbsent(clientMessageId, ignored -> new Trace(runId, state));
    }

    private Timer timer(Trace trace, String phase) {
        if (trace == null || !PHASES.contains(phase)) {
            return Timer.noop();
        }
        return new Timer(this, trace, phase);
    }

    private void record(Trace trace, String phase, long micros) {
        trace.state.samplesByPhase.get(phase).add(Math.max(0L, micros));
    }

    private static long microsSince(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000L);
    }

    private void validateRunId(String runId) {
        if (runId == null || !runId.matches("[A-Za-z0-9-]{1,64}")) {
            throw new IllegalArgumentException(
                    "runId must contain 1-64 letters, digits, or hyphens."
            );
        }
    }

    private static final class RunState {

        private final Map<String, PhaseSamples> samplesByPhase =
                new ConcurrentHashMap<>();

        private RunState() {
            PHASES.forEach(phase -> samplesByPhase.put(
                    phase,
                    new PhaseSamples()
            ));
        }
    }

    private static final class PhaseSamples {

        private final ConcurrentLinkedQueue<Long> values = new ConcurrentLinkedQueue<>();
        private final AtomicInteger count = new AtomicInteger();

        private void add(long value) {
            int current;
            do {
                current = count.get();
                if (current >= MAX_SAMPLES_PER_PHASE) {
                    return;
                }
            } while (!count.compareAndSet(current, current + 1));
            values.add(value);
        }
    }

    private static final class Trace {

        private final String runId;
        private final RunState state;
        private final Map<String, Long> outboundEnqueuedNanos = new ConcurrentHashMap<>();
        private volatile long inboundEnqueuedNanos;

        private Trace(String runId, RunState state) {
            this.runId = runId;
            this.state = state;
        }
    }

    public static final class Timer implements AutoCloseable {

        private static final Timer NOOP = new Timer();

        private final ChatRealtimeMeasurementRecorder recorder;
        private final Trace trace;
        private final String phase;
        private final long startedNanos;

        private Timer() {
            this.recorder = null;
            this.trace = null;
            this.phase = null;
            this.startedNanos = 0L;
        }

        private Timer(
                ChatRealtimeMeasurementRecorder recorder,
                Trace trace,
                String phase
        ) {
            this.recorder = recorder;
            this.trace = trace;
            this.phase = phase;
            this.startedNanos = System.nanoTime();
        }

        private static Timer noop() {
            return NOOP;
        }

        @Override
        public void close() {
            if (recorder != null) {
                recorder.record(trace, phase, microsSince(startedNanos));
            }
        }
    }

    public record RunReport(String runId, Map<String, Percentiles> phaseMicros) {
    }

    public record Percentiles(int count, Long p50, Long p95, Long p99, Long max) {

        private static Percentiles from(List<Long> sortedValues) {
            if (sortedValues.isEmpty()) {
                return new Percentiles(0, null, null, null, null);
            }
            return new Percentiles(
                    sortedValues.size(),
                    percentile(sortedValues, 0.50),
                    percentile(sortedValues, 0.95),
                    percentile(sortedValues, 0.99),
                    sortedValues.getLast()
            );
        }

        private static Long percentile(List<Long> sortedValues, double percentile) {
            int index = Math.max(0, (int) Math.ceil(percentile * sortedValues.size()) - 1);
            return sortedValues.get(index);
        }
    }
}
