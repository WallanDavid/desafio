package com.wallan.activation.domain.exception;

import java.util.UUID;

/**
 * Lançada quando uma solicitação de ativação não é encontrada pelo identificador informado.
 */
public class ActivationNotFoundException extends BusinessException {

    private final UUID id;

    public ActivationNotFoundException(UUID id) {
        super("Solicitação de ativação com identificador '%s' não foi encontrada.".formatted(id));
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
