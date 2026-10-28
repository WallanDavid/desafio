package com.wallan.activation.dto;

/**
 * Payload enviado ao serviço externo de ativação (POST /provider/v1/activate).
 */
public record ProviderActivationPayload(
        String requestId,
        String externalId,
        String customerId,
        String productCode
) {
}
