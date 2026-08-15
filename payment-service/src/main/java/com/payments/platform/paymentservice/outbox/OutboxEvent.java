package com.payments.platform.paymentservice.outbox;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Convert;
import com.payments.platform.paymentservice.outbox.JsonNodeConverter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
@Table(name = "outbox_event")
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String aggregateId;
    private String aggregateType;
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = JsonNodeConverter.class)
    @Column(columnDefinition = "jsonb")
    private JsonNode payload;

    private LocalDateTime createdAt = LocalDateTime.now();
    private boolean published = false;

}
