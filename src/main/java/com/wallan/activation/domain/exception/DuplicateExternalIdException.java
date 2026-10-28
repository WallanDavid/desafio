package com.wallan.activation.domain.exception;

/**
 * Lançada quando há tentativa de cadastrar um externalId já existente com dados divergentes (conflito de negócio).
 */
public class DuplicateExternalIdException extends BusinessException {

    private final String externalId;

    public DuplicateExternalIdException(String externalId, String message) {
        super(message);
        this.externalId = externalId;
    }

    public DuplicateExternalIdException(String externalId) {
        this(externalId, "O externalId '%s' já foi utilizado por outra solicitação com dados divergentes.".formatted(externalId));
    }

    public String getExternalId() {
        return externalId;
    }
}
