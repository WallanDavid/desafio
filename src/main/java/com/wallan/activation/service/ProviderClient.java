package com.wallan.activation.service;

import com.wallan.activation.domain.ActivationRequest;
import com.wallan.activation.dto.ProviderActivationResponse;

/**
 * Contrato de integração com o serviço externo de ativação (POST /provider/v1/activate).
 */
public interface ProviderClient {

    /**
     * Executa a chamada remota de ativação do produto junto ao provedor.
     *
     * @param request Solicitação a ser ativada
     * @return Resposta com protocolo de ativação
     */
    ProviderActivationResponse activate(ActivationRequest request);
}
