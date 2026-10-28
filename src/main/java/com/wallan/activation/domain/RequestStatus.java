package com.wallan.activation.domain;

import com.wallan.activation.domain.exception.InvalidStatusTransitionException;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados da solicitação de ativação e regras de transição de domínio.
 */
public enum RequestStatus {

    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    private Set<RequestStatus> allowedNextStates;

    static {
        PENDING.allowedNextStates = EnumSet.of(PROCESSING, FAILED);
        PROCESSING.allowedNextStates = EnumSet.of(COMPLETED, FAILED, PENDING);
        COMPLETED.allowedNextStates = EnumSet.noneOf(RequestStatus.class);
        FAILED.allowedNextStates = EnumSet.noneOf(RequestStatus.class);
    }

    /**
     * Verifica se a transição para o estado informado é permitida pelas regras de domínio.
     *
     * @param target Estado de destino
     * @return true se a transição for permitida, false caso contrário
     */
    public boolean canTransitionTo(RequestStatus target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            return true; // Idempotente
        }
        return allowedNextStates.contains(target);
    }

    /**
     * Valida a transição de estado, lançando exceção de domínio caso seja inválida.
     *
     * @param target Estado de destino
     * @throws InvalidStatusTransitionException se a transição for proibida
     */
    public void validateTransitionTo(RequestStatus target) {
        if (!canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(
                    "Transição de estado inválida: não é permitido transicionar de %s para %s"
                            .formatted(this.name(), target != null ? target.name() : "NULL")
            );
        }
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
