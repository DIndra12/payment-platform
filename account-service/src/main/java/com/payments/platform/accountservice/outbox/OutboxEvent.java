package com.payments.platform.accountservice.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * An event awaiting publication, written in the same transaction as the ledger
 * entry that caused it.
 *
 * <p>{@code eventType} doubles as the Kafka topic name and {@code aggregateId}
 * becomes the message key, matching payment-service's outbox so the two services
 * behave identically from a consumer's point of view.
 */
@Entity
@Getter
@Setter
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** For account events this is the accountId, so partitioning is per account. */
    private String aggregateId;

    private String aggregateType;

    /** Also the topic name, e.g. {@code account.debited}. */
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = JsonNodeConverter.class)
    @Column(columnDefinition = "jsonb")
    private JsonNode payload;

    private LocalDateTime createdAt = LocalDateTime.now();

    private boolean published = false;
}
