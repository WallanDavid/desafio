package com.wallan.activation.service;

import com.wallan.activation.domain.ActivationRequest;
import com.wallan.activation.domain.ActivationRequestRepository;
import com.wallan.activation.domain.exception.ProviderIntegrationException;
import com.wallan.activation.dto.ProviderActivationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Processador de ativações responsável pela orquestração do ciclo de vida assíncrono.
 * CRUCIAL: Separa estritamente as transações de banco de dados da chamada HTTP remota,
 * garantindo que conexões do pool Hikari não fiquem presas durante lentidões do provedor.
 */
@Service
public class ActivationProcessor {

    private static final Logger log = LoggerFactory.getLogger(ActivationProcessor.class);

    private final ActivationRequestRepository repository;
    private final ProviderClient providerClient;
    private final int maxRetries;

    public ActivationProcessor(
            ActivationRequestRepository repository,
            ProviderClient providerClient,
            @Value("${activation.provider.max-retries:3}") int maxRetries
    ) {
        this.repository = repository;
        this.providerClient = providerClient;
        this.maxRetries = maxRetries;
    }

    /**
     * Ponto de entrada assíncrono para processar uma ativação recém-criada ou em reconciliação.
     * Note que este método NÃO possui anotação @Transactional, evitando segurar conexão de banco.
     */
    @Async
    public void processActivation(UUID requestId) {
        log.info("Iniciando orquestração de ativação para requestId={}", requestId);

        // Passo 1: Atualizar status para PROCESSING em transação curta
        Optional<ActivationRequest> requestOpt = markAsProcessing(requestId);
        if (requestOpt.isEmpty()) {
            log.warn("Solicitação requestId={} não encontrada ou em estado terminal. Interrompendo.", requestId);
            return;
        }

        ActivationRequest request = requestOpt.get();

        // Passo 2: Executar chamada HTTP FORA de qualquer transação de banco de dados
        try {
            ProviderActivationResponse response = providerClient.activate(request);

            // Passo 3a: Sucesso -> marcar COMPLETED em transação curta
            markAsCompleted(requestId, response.protocol());
            log.info("Ativação concluída com sucesso: requestId={}, protocol={}", requestId, response.protocol());

        } catch (ProviderIntegrationException e) {
            log.warn("Erro de integração com provedor para requestId={}: status={}, retryable={}, msg={}",
                    requestId, e.getStatusCode(), e.isRetryable(), e.getMessage());

            if (!e.isRetryable()) {
                // Erro 400 ou validação de negócio rejeitada pelo provedor -> Falha definitiva imediata
                markAsFailed(requestId, e.getMessage());
            } else {
                // Erro transitório (500, timeout, 429) -> Avaliar limite de retentativas
                handleTransientFailure(requestId, e.getMessage());
            }
        } catch (Exception e) {
            log.error("Exceção inesperada durante o processamento de ativação para requestId={}: {}",
                    requestId, e.getMessage(), e);
            handleTransientFailure(requestId, "Erro inesperado: " + e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ActivationRequest> markAsProcessing(UUID id) {
        return repository.findById(id).map(req -> {
            if (req.getStatus().isTerminal()) {
                return null;
            }
            if (req.getStatus() != com.wallan.activation.domain.RequestStatus.PROCESSING) {
                req.startProcessing();
                return repository.save(req);
            }
            return req;
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsCompleted(UUID id, String protocol) {
        repository.findById(id).ifPresent(req -> {
            if (!req.getStatus().isTerminal()) {
                req.complete(protocol);
                repository.save(req);
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(UUID id, String reason) {
        repository.findById(id).ifPresent(req -> {
            if (!req.getStatus().isTerminal()) {
                req.fail(reason);
                repository.save(req);
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleTransientFailure(UUID id, String reason) {
        repository.findById(id).ifPresent(req -> {
            if (!req.getStatus().isTerminal()) {
                if (req.getRetryCount() + 1 >= maxRetries) {
                    log.error("Limite máximo de retentativas ({}) atingido para requestId={}. Marcando como FAILED.",
                            maxRetries, id);
                    req.fail("Limite de retentativas excedido. Último erro: " + reason);
                } else {
                    req.registerTransientFailure(reason);
                    log.info("Falha temporária registrada para requestId={}. Tentativa {}/{}.",
                            id, req.getRetryCount(), maxRetries);
                }
                repository.save(req);
            }
        });
    }
}
