package com.wallan.activation.domain.exception;

/**
 * Lançada quando há tentativa de transição proibida na máquina de estados de ativação.
 */
public class InvalidStatusTransitionException extends BusinessException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
