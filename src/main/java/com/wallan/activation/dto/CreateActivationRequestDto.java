package com.wallan.activation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO para criação de uma nova solicitação de ativação de produto.
 */
@Schema(description = "Payload para registro de solicitação de ativação")
public record CreateActivationRequestDto(

        @Schema(description = "Identificador único da solicitação no sistema de origem", example = "EXT-12345")
        @NotBlank(message = "externalId é obrigatório")
        @Size(min = 3, max = 64, message = "externalId deve possuir entre 3 e 64 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "externalId deve conter apenas caracteres alfanuméricos, hífen ou underscore")
        String externalId,

        @Schema(description = "Identificador único do cliente contratante", example = "CUST-10001")
        @NotBlank(message = "customerId é obrigatório")
        @Size(min = 3, max = 64, message = "customerId deve possuir entre 3 e 64 caracteres")
        String customerId,

        @Schema(description = "Código do produto ou serviço a ser ativado", example = "INTERNET-500")
        @NotBlank(message = "productCode é obrigatório")
        @Size(min = 2, max = 64, message = "productCode deve possuir entre 2 e 64 caracteres")
        String productCode
) {
}
