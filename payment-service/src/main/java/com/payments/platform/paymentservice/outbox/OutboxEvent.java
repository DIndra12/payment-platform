package com.payments.platform.paymentservice.outbox;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

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

    @Column(columnDefinition = "jsonb")
    private String payload;

    private LocalDateTime createdAt = LocalDateTime.now();
    private boolean published = false;

}
