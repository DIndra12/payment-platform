package com.payments.platform.transactionhistoryservice.support;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Wire-format event payloads, written as literal JSON on purpose.
 *
 * <p>Building these by serializing the service's own DTOs would make the tests
 * agree with themselves rather than with the producer. Hand-written JSON pins the
 * actual contract, including that {@code account.*} events carry an
 * {@code entryType} field the consumer DTO does not declare.
 */
public final class EventFixtures {

    private EventFixtures() {
    }

    private static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public static String paymentCompleted(UUID paymentId, UUID payer, UUID payee,
                                          BigDecimal amount, String currency) {
        return """
                {"eventId":"%s","paymentId":"%s","payerAccountId":"%s","payeeAccountId":"%s",\
                "amount":%s,"currency":"%s","status":"COMPLETED","occurredAt":"%s","traceId":null}"""
                .formatted(UUID.randomUUID(), paymentId, payer, payee, amount, currency, now());
    }

    public static String paymentFailed(UUID paymentId, UUID payer, UUID payee,
                                       BigDecimal amount, String currency, String reason) {
        return """
                {"eventId":"%s","paymentId":"%s","payerAccountId":"%s","payeeAccountId":"%s",\
                "amount":%s,"currency":"%s","status":"FAILED","failureReason":"%s",\
                "occurredAt":"%s","traceId":null}"""
                .formatted(UUID.randomUUID(), paymentId, payer, payee, amount, currency, reason, now());
    }

    public static String accountDebited(UUID paymentId, UUID accountId, UUID ledgerEntryId,
                                        BigDecimal amount) {
        return """
                {"eventId":"%s","referenceId":"%s","accountId":"%s","ledgerEntryId":"%s",\
                "amount":%s,"entryType":"DEBIT","occurredAt":"%s","traceId":null}"""
                .formatted(UUID.randomUUID(), paymentId, accountId, ledgerEntryId, amount, now());
    }

    public static String accountCredited(UUID paymentId, UUID accountId, UUID ledgerEntryId,
                                         BigDecimal amount) {
        return """
                {"eventId":"%s","referenceId":"%s","accountId":"%s","ledgerEntryId":"%s",\
                "amount":%s,"entryType":"CREDIT","occurredAt":"%s","traceId":null}"""
                .formatted(UUID.randomUUID(), paymentId, accountId, ledgerEntryId, amount, now());
    }

    /**
     * The five-field payload payment-service published before Phase B: no
     * eventId, status, occurredAt or traceId. Kept because consumers must keep
     * working against it.
     */
    public static String paymentCompletedLegacyFiveFields(UUID paymentId, UUID payer, UUID payee,
                                                          BigDecimal amount, String currency) {
        return """
                {"paymentId":"%s","payerAccountId":"%s","payeeAccountId":"%s","amount":%s,"currency":"%s"}"""
                .formatted(paymentId, payer, payee, amount, currency);
    }
}
