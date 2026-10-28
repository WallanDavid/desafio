package com.wallan.activation.domain.exception;

/**
 * Lançada em caso de erro na comunicação ou resposta de erro do provedor externo.
 */
public class ProviderIntegrationException extends BusinessException {

    private final Integer statusCode;
    private final boolean retryable;

    public ProviderIntegrationException(String message, Integer statusCode, boolean retryable) {
        super(message);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public ProviderIntegrationException(String message, Throwable cause, Integer statusCode, boolean retryable) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
