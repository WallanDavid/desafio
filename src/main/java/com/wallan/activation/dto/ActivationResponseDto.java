package com.wallan.activation.dto;

import com.wallan.activation.domain.ActivationRequest;
import com.wallan.activation.domain.RequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de resposta contendo os dados e status da solicitação de ativação.
 */
@Schema(description = "Dados detalhados da solicitação de ativação")
public record ActivationResponseDto(

        @Schema(description = "Identificador único da solicitação (UUID)", example = "0b7f3c1e-5d2a-4c4e-9a51-2f8d7b1e6c90")
        UUID id,

        @Schema(description = "Identificador externo de origem", example = "EXT-12345")
        String externalId,

        @Schema(description = "Identificador do cliente", example = "CUST-10001")
        String customerId,

        @Schema(description = "Código do produto", example = "INTERNET-500")
        String productCode,

        @Schema(description = "Status atual do ciclo de vida da solicitação", example = "PENDING")
        RequestStatus status,

        @Schema(description = "Data e hora de criação em UTC (ISO-8601)", example = "2026-10-26T14:03:11Z")
        Instant createdAt,

        @Schema(description = "Data e hora da última atualização em UTC (ISO-8601)", example = "2026-10-26T14:03:11Z")
        Instant updatedAt
) {

    public static ActivationResponseDto fromEntity(ActivationRequest entity) {
        return new ActivationResponseDto(
                entity.getId(),
                entity.getExternalId(),
                entity.getCustomerId(),
                entity.getProductCode(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
