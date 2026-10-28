package com.wallan.activation.service;

import com.wallan.activation.domain.RequestStatus;
import com.wallan.activation.dto.ActivationCreationResult;
import com.wallan.activation.dto.ActivationResponseDto;
import com.wallan.activation.dto.CreateActivationRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Contrato de serviços para regras de negócio de solicitações de ativação.
 */
public interface ActivationService {

    /**
     * Registra uma nova solicitação de ativação com garantia de idempotência e unicidade de externalId.
     *
     * @param dto Dados da solicitação de entrada
     * @return Resultado contendo a representação da solicitação e flag indicando se foi criada (201) ou reutilizada (200)
     */
    ActivationCreationResult createRequest(CreateActivationRequestDto dto);

    /**
     * Recupera uma solicitação de ativação pelo seu identificador único.
     *
     * @param id UUID da solicitação
     * @return DTO com os dados da solicitação
     */
    ActivationResponseDto getById(UUID id);

    /**
     * Consulta paginada com suporte a filtros opcionais por customerId e status.
     *
     * @param customerId Filtro opcional por cliente
     * @param status     Filtro opcional por status
     * @param pageable   Configuração de paginação e ordenação
     * @return Página de solicitações de ativação
     */
    Page<ActivationResponseDto> list(String customerId, RequestStatus status, Pageable pageable);
}
