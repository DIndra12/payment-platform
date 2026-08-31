package com.payments.platform.accountservice.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.platform.accountservice.ledger.LedgerEntry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Turns a committed ledger entry into an outbox row.
 *
 * <p>Called from inside {@code AccountService}'s transaction, so the event row and
 * the ledger row commit together.
 *
 * <p>The payload deliberately includes {@code referenceId} — the paymentId that
 * payment-service passed in — because that is the only field a downstream read
 * model can use to correlate this ledger movement with the payment it belongs to.
 */
@Slf4j
@Component
public class LedgerEventPublisher {

    public static final String EVENT_ACCOUNT_DEBITED = "account.debited";
    public static final String EVENT_ACCOUNT_CREDITED = "account.credited";

    private static final String AGGREGATE_TYPE = "account";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public LedgerEventPublisher(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public void publishDebited(LedgerEntry entry) {
        record(entry, EVENT_ACCOUNT_DEBITED);
    }

    public void publishCredited(LedgerEntry entry) {
        record(entry, EVENT_ACCOUNT_CREDITED);
    }

    private void record(LedgerEntry entry, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        // The paymentId. Named referenceId because that is what the ledger calls it.
        payload.put("referenceId", entry.getReferenceId());
        payload.put("accountId", entry.getAccountId());
        payload.put("ledgerEntryId", entry.getId());
        payload.put("amount", entry.getAmount());
        // No currency: neither DebitRequest/CreditRequest nor the accounts table
        // carries one, so emitting a null field would imply knowledge this service
        // does not have. Consumers get currency from the payment events instead.
        payload.put("entryType", entry.getEntryType().name());
        // ISO_LOCAL_DATE_TIME so consumers can bind directly to LocalDateTime.
        payload.put("occurredAt", entry.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        payload.put("traceId", MDC.get("traceId"));

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType(AGGREGATE_TYPE);
        // Keyed by account so events for one account stay ordered. Note this means
        // the debit and credit legs of one payment land on different partitions and
        // can be consumed out of order relative to each other — which is exactly
        // why the read model's projection is order-independent.
        outboxEvent.setAggregateId(entry.getAccountId().toString());
        outboxEvent.setEventType(eventType);
        outboxEvent.setPayload(objectMapper.valueToTree(payload));

        outboxEventRepository.save(outboxEvent);
        log.info("Outbox event {} created for account {} (reference {})",
                eventType, entry.getAccountId(), entry.getReferenceId());
    }
}
