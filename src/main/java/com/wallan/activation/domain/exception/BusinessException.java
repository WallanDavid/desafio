package com.wallan.activation.domain.exception;

/**
 * Exceção base para regras de negócio da aplicação.
 */
public abstract class BusinessException extends RuntimeException {

    protected BusinessException(String message) {
        super(message);
    }

    protected BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
