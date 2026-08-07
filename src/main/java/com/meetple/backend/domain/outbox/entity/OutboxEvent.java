package com.meetple.backend.domain.outbox.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "outbox_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_outbox_events_deduplication_key",
                columnNames = "deduplication_key"
        ),
        indexes = @Index(
                name = "idx_outbox_events_occurred_at",
                columnList = "occurred_at"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 255)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_key", nullable = false, length = 255)
    private String eventKey;

    @Column(nullable = false, length = 255)
    private String topic;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "deduplication_key", nullable = false, length = 255)
    private String deduplicationKey;

    private OutboxEvent(
            UUID id,
            String aggregateType,
            String aggregateId,
            String eventType,
            String eventKey,
            String topic,
            int schemaVersion,
            JsonNode payload,
            Instant occurredAt,
            String deduplicationKey
    ) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventKey = eventKey;
        this.topic = topic;
        this.schemaVersion = schemaVersion;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.deduplicationKey = deduplicationKey;
    }

    public static OutboxEvent create(
            UUID id,
            String aggregateType,
            String aggregateId,
            String eventType,
            String eventKey,
            String topic,
            int schemaVersion,
            JsonNode payload,
            Instant occurredAt,
            String deduplicationKey
    ) {
        return new OutboxEvent(
                id,
                aggregateType,
                aggregateId,
                eventType,
                eventKey,
                topic,
                schemaVersion,
                payload,
                occurredAt,
                deduplicationKey
        );
    }
}
