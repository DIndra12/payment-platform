package com.payments.platform.paymentservice.unit.api;

import com.payments.platform.paymentservice.api.PaymentController;
import com.payments.platform.paymentservice.api.PaymentResponse;
import com.payments.platform.paymentservice.orchestration.PaymentOrchestratorService;
import com.payments.platform.paymentservice.orchestration.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentControllerGetPaymentTest {

    private PaymentOrchestratorService orchestrator;
    private PaymentController controller;

    @BeforeEach
    void setUp() {
        orchestrator = mock(PaymentOrchestratorService.class);
        controller = new PaymentController(orchestrator);
    }

    @Test
    void returnsPaymentWhenFound() {
        UUID id = UUID.randomUUID();
        PaymentResponse payment = PaymentResponse.builder()
                .paymentId(id).status(PaymentStatus.COMPLETED).message("Payment successful").build();
        when(orchestrator.getPayment(id)).thenReturn(Optional.of(payment));

        ResponseEntity<PaymentResponse> response = controller.getPayment(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(payment);
    }

    @Test
    void returns404WhenPaymentDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(orchestrator.getPayment(id)).thenReturn(Optional.empty());

        ResponseEntity<PaymentResponse> response = controller.getPayment(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
    }
}
