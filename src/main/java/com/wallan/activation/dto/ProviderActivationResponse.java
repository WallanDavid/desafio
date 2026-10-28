package com.wallan.activation.dto;

/**
 * Resposta retornada pelo serviço externo de ativação.
 */
public record ProviderActivationResponse(
        String protocol,
        String status,
        String message
) {
}
