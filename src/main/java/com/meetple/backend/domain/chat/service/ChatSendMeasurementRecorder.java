package com.meetple.backend.domain.chat.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class ChatSendMeasurementRecorder {

    private static final Pattern RUN_MARKER = Pattern.compile(
            "^\\[CHAT-LOAD:([A-Za-z0-9-]{1,64})]"
    );
    private static final int MAX_RUNS = 20;
    private static final int MAX_SAMPLES_PER_RUN = 5_000;

    private final boolean enabled;
    private final Map<String, ConcurrentLinkedQueue<Sample>> samplesByRun =
            new ConcurrentHashMap<>();

    public ChatSendMeasurementRecorder(
            @Value("${meetple.performance.chat-send.enabled:false}") boolean enabled
    ) {
        this.enabled = enabled;
    }

    public Observation start(UUID clientMessageId, String content) {
        if (!enabled || clientMessageId == null || content == null) {
            return Observation.noop();
        }
        Matcher matcher = RUN_MARKER.matcher(content);
        if (!matcher.find()) {
            return Observation.noop();
        }
        String runId = matcher.group(1);
        if (!samplesByRun.containsKey(runId) && samplesByRun.size() >= MAX_RUNS) {
            return Observation.noop();
        }

        Observation observation = new Observation(this, runId, clientMessageId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            observation.complete("COMMITTED");
                        }

                        @Override
                        public void afterCompletion(int status) {
                            if (status != STATUS_COMMITTED) {
                                observation.complete("ROLLED_BACK");
                            }
                        }
                    }
            );
        } else {
            observation.noTransaction();
        }
        return observation;
    }

    public RunReport report(String runId) {
        validateRunId(runId);
        List<Sample> samples = new ArrayList<>(
                samplesByRun.getOrDefault(runId, new ConcurrentLinkedQueue<>())
        );
        samples.sort(Comparator.comparing(Sample::clientMessageId));

        Map<String, Percentiles> phasePercentiles = new LinkedHashMap<>();
        for (String phase : Observation.PHASES) {
            List<Long> values = samples.stream()
                    .map(sample -> sample.phaseMicros().get(phase))
                    .filter(value -> value != null)
                    .sorted()
                    .toList();
            phasePercentiles.put(phase, Percentiles.from(values));
        }
        List<Long> transactionValues = samples.stream()
                .filter(sample -> "COMMITTED".equals(sample.transactionStatus()))
                .map(Sample::transactionMicros)
                .sorted()
                .toList();
        phasePercentiles.put("transactionCommit", Percentiles.from(transactionValues));

        return new RunReport(
                runId,
                samples.size(),
                countStatus(samples, "COMMITTED"),
                countStatus(samples, "ROLLED_BACK"),
                countStatus(samples, "NO_TRANSACTION"),
                phasePercentiles,
                samples
        );
    }

    public void reset(String runId) {
        validateRunId(runId);
        samplesByRun.remove(runId);
    }

    private long countStatus(List<Sample> samples, String status) {
        return samples.stream().filter(sample -> status.equals(sample.transactionStatus())).count();
    }

    private void record(Sample sample) {
        ConcurrentLinkedQueue<Sample> samples = samplesByRun.computeIfAbsent(
                sample.runId(),
                ignored -> new ConcurrentLinkedQueue<>()
        );
        if (samples.size() >= MAX_SAMPLES_PER_RUN) {
            return;
        }
        samples.add(sample);
    }

    private void validateRunId(String runId) {
        if (runId == null || !runId.matches("[A-Za-z0-9-]{1,64}")) {
            throw new IllegalArgumentException(
                    "runId must contain 1-64 letters, digits, or hyphens."
            );
        }
    }

    public static final class Observation {

        public static final String LOCK_LOOKUP = "lockLookup";
        public static final String DUPLICATE_LOOKUP = "duplicateLookup";
        public static final String SEQUENCE_LOOKUP = "sequenceLookup";
        public static final String MESSAGE_SAVE = "messageSave";
        public static final String READ_STATE_UPDATE = "readStateUpdate";
        public static final String PUSH_RECIPIENT_LOOKUP = "pushRecipientLookup";
        public static final String OUTBOX_SAVE = "outboxSave";
        public static final String SERVICE_BODY = "serviceBody";
        private static final List<String> PHASES = List.of(
                LOCK_LOOKUP,
                DUPLICATE_LOOKUP,
                SEQUENCE_LOOKUP,
                MESSAGE_SAVE,
                READ_STATE_UPDATE,
                PUSH_RECIPIENT_LOOKUP,
                OUTBOX_SAVE,
                SERVICE_BODY
        );
        private static final Observation NOOP = new Observation();

        private final ChatSendMeasurementRecorder recorder;
        private final String runId;
        private final UUID clientMessageId;
        private final long startedNanos;
        private final Map<String, Long> phaseMicros;
        private final AtomicBoolean completed;
        private boolean noTransaction;
        private Long messageId;

        private Observation() {
            this.recorder = null;
            this.runId = null;
            this.clientMessageId = null;
            this.startedNanos = 0;
            this.phaseMicros = Map.of();
            this.completed = new AtomicBoolean(true);
        }

        private Observation(
                ChatSendMeasurementRecorder recorder,
                String runId,
                UUID clientMessageId
        ) {
            this.recorder = recorder;
            this.runId = runId;
            this.clientMessageId = clientMessageId;
            this.startedNanos = System.nanoTime();
            this.phaseMicros = new LinkedHashMap<>();
            this.completed = new AtomicBoolean(false);
        }

        public static Observation noop() {
            return NOOP;
        }

        public <T> T measure(String phase, Supplier<T> action) {
            if (recorder == null) {
                return action.get();
            }
            long started = System.nanoTime();
            try {
                return action.get();
            } finally {
                phaseMicros.merge(phase, microsSince(started), Long::sum);
            }
        }

        public void markMessageId(Long messageId) {
            this.messageId = messageId;
        }

        public void markServiceReturned() {
            if (recorder == null) {
                return;
            }
            phaseMicros.put(SERVICE_BODY, microsSince(startedNanos));
            if (noTransaction) {
                complete("NO_TRANSACTION");
            }
        }

        private void noTransaction() {
            this.noTransaction = true;
        }

        private void complete(String transactionStatus) {
            if (recorder == null || !completed.compareAndSet(false, true)) {
                return;
            }
            recorder.record(new Sample(
                    runId,
                    clientMessageId,
                    messageId,
                    transactionStatus,
                    microsSince(startedNanos),
                    Map.copyOf(phaseMicros)
            ));
        }

        private long microsSince(long started) {
            return Math.max(0L, (System.nanoTime() - started) / 1_000L);
        }
    }

    public record Sample(
            String runId,
            UUID clientMessageId,
            Long messageId,
            String transactionStatus,
            long transactionMicros,
            Map<String, Long> phaseMicros
    ) {
    }

    public record RunReport(
            String runId,
            int samples,
            long committed,
            long rolledBack,
            long noTransaction,
            Map<String, Percentiles> phaseMicros,
            List<Sample> records
    ) {
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
