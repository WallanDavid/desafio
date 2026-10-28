package com.wallan.activation.domain;

import com.wallan.activation.domain.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Testes de Unidade - Domínio e Máquina de Estados da Solicitação")
class ActivationRequestTest {

    @Test
    @DisplayName("Deve inicializar a solicitação no estado PENDING com timestamps e contadores zerados")
    void shouldInitializeInPendingState() {
        ActivationRequest request = new ActivationRequest("EXT-001", "CUST-100", "INT-500");

        assertThat(request.getId()).isNotNull();
        assertThat(request.getExternalId()).isEqualTo("EXT-001");
        assertThat(request.getCustomerId()).isEqualTo("CUST-100");
        assertThat(request.getProductCode()).isEqualTo("INT-500");
        assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(request.getRetryCount()).isZero();
        assertThat(request.getCreatedAt()).isNotNull();
        assertThat(request.getUpdatedAt()).isEqualTo(request.getCreatedAt());
        assertThat(request.getProviderProtocol()).isNull();
        assertThat(request.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("Deve transicionar de PENDING para PROCESSING com sucesso")
    void shouldTransitionFromPendingToProcessing() {
        ActivationRequest request = new ActivationRequest("EXT-001", "CUST-100", "INT-500");

        request.startProcessing();

        assertThat(request.getStatus()).isEqualTo(RequestStatus.PROCESSING);
        assertThat(request.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Deve transicionar de PROCESSING para COMPLETED com protocolo preenchido")
    void shouldTransitionFromProcessingToCompleted() {
        ActivationRequest request = new ActivationRequest("EXT-001", "CUST-100", "INT-500");
        request.startProcessing();

        request.complete("PROT-123456");

        assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(request.getProviderProtocol()).isEqualTo("PROT-123456");
        assertThat(request.getFailureReason()).isNull();
        assertThat(request.getStatus().isTerminal()).isTrue();
    }

    @Test
    @DisplayName("Deve transicionar de PROCESSING para FAILED com motivo preenchido")
    void shouldTransitionFromProcessingToFailed() {
        ActivationRequest request = new ActivationRequest("EXT-001", "CUST-100", "INT-500");
        request.startProcessing();

        request.fail("Produto indisponível na região");

        assertThat(request.getStatus()).isEqualTo(RequestStatus.FAILED);
        assertThat(request.getFailureReason()).isEqualTo("Produto indisponível na região");
        assertThat(request.getStatus().isTerminal()).isTrue();
    }

    @Test
    @DisplayName("Deve rejeitar transição inválida de COMPLETED para PROCESSING (estado terminal)")
    void shouldRejectTransitionFromCompletedToProcessing() {
        ActivationRequest request = new ActivationRequest("EXT-001", "CUST-100", "INT-500");
        request.startProcessing();
        request.complete("PROT-123456");

        assertThatThrownBy(request::startProcessing)
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Transição de estado inválida: não é permitido transicionar de COMPLETED para PROCESSING");
    }

    @Test
    @DisplayName("Deve rejeitar transição inválida de FAILED para PROCESSING (estado terminal)")
    void shouldRejectTransitionFromFailedToProcessing() {
        ActivationRequest request = new ActivationRequest("EXT-001", "CUST-100", "INT-500");
        request.startProcessing();
        request.fail("Erro de negócio irremediável");

        assertThatThrownBy(request::startProcessing)
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Transição de estado inválida: não é permitido transicionar de FAILED para PROCESSING");
    }

    @Test
    @DisplayName("Deve rejeitar transição direta de PENDING para COMPLETED sem passar por PROCESSING")
    void shouldRejectDirectTransitionFromPendingToCompleted() {
        ActivationRequest request = new ActivationRequest("EXT-001", "CUST-100", "INT-500");

        assertThatThrownBy(() -> request.complete("PROT-999"))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("não é permitido transicionar de PENDING para COMPLETED");
    }

    @Test
    @DisplayName("Deve registrar falhas transitórias incrementando contador de retentativas")
    void shouldRegisterTransientFailureAndIncrementRetryCount() {
        ActivationRequest request = new ActivationRequest("EXT-001", "CUST-100", "INT-500");
        request.startProcessing();

        request.registerTransientFailure("Timeout de rede");
        assertThat(request.getRetryCount()).isEqualTo(1);
        assertThat(request.getFailureReason()).isEqualTo("Timeout de rede");
        assertThat(request.getStatus()).isEqualTo(RequestStatus.PROCESSING);

        request.registerTransientFailure("Erro 503");
        assertThat(request.getRetryCount()).isEqualTo(2);
        assertThat(request.getFailureReason()).isEqualTo("Erro 503");
    }

    @Test
    @DisplayName("Deve validar correspondência de payload (idempotência)")
    void shouldMatchPayloadCorrectly() {
        ActivationRequest request = new ActivationRequest("EXT-001", "CUST-100", "INT-500");

        assertThat(request.matchesPayload("CUST-100", "INT-500")).isTrue();
        assertThat(request.matchesPayload("CUST-999", "INT-500")).isFalse();
        assertThat(request.matchesPayload("CUST-100", "INT-1000")).isFalse();
    }
}
