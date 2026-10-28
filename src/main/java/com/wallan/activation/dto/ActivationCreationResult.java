package com.wallan.activation.dto;

/**
 * Resultado da tentativa de criação de solicitação de ativação.
 * Indica se um novo registro foi criado (HTTP 201) ou se trata-se de um retry idempotente de registro existente (HTTP 200).
 */
public record ActivationCreationResult(
        ActivationResponseDto response,
        boolean created
) {
}
