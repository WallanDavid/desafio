package com.wallan.activation.service;

import com.wallan.activation.domain.ActivationRequest;
import com.wallan.activation.domain.ActivationRequestRepository;
import com.wallan.activation.domain.RequestStatus;
import com.wallan.activation.domain.exception.ActivationNotFoundException;
import com.wallan.activation.domain.exception.DuplicateExternalIdException;
import com.wallan.activation.dto.ActivationCreationResult;
import com.wallan.activation.dto.ActivationResponseDto;
import com.wallan.activation.dto.CreateActivationRequestDto;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Implementação das regras de negócio de ativação.
 * Implementa estratégia de idempotência resiliente a concorrência no externalId
 * e dispara o processamento assíncrono fora da transação de inserção.
 */
@Service
public class ActivationServiceImpl implements ActivationService {

    private static final Logger log = LoggerFactory.getLogger(ActivationServiceImpl.class);

    private final ActivationRequestRepository repository;
    private final ActivationPersister persister;
    private final ActivationProcessor processor;
    private final Counter requestsCreatedCounter;
    private final Counter requestsDuplicateCounter;

    public ActivationServiceImpl(
            ActivationRequestRepository repository,
            ActivationPersister persister,
            ActivationProcessor processor,
            MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.persister = persister;
        this.processor = processor;
        this.requestsCreatedCounter = Counter.builder("activation.requests.created.total")
                .description("Total de solicitações de ativação criadas")
                .register(meterRegistry);
        this.requestsDuplicateCounter = Counter.builder("activation.requests.duplicate.total")
                .description("Total de solicitações idempotentes/duplicadas interceptadas")
                .register(meterRegistry);
    }

    @Override
    public ActivationCreationResult createRequest(CreateActivationRequestDto dto) {
        log.info("Recebida solicitação de criação de ativação: externalId={}, customerId={}, productCode={}",
                dto.externalId(), dto.customerId(), dto.productCode());

        // 1. Verificação prévia de idempotência
        Optional<ActivationRequest> existingOpt = repository.findByExternalId(dto.externalId());
        if (existingOpt.isPresent()) {
            return handleExistingRecord(existingOpt.get(), dto);
        }

        // 2. Tentativa de inserção em transação isolada com tratamento de concorrência simultânea
        try {
            ActivationRequest newRequest = new ActivationRequest(
                    dto.externalId(),
                    dto.customerId(),
                    dto.productCode()
            );

            ActivationRequest saved = persister.persistAndFlush(newRequest);
            requestsCreatedCounter.increment();

            log.info("Solicitação criada com sucesso: id={}, externalId={}", saved.getId(), saved.getExternalId());

            // 3. Disparo do processamento assíncrono junto ao provedor
            processor.processActivation(saved.getId());

            return new ActivationCreationResult(ActivationResponseDto.fromEntity(saved), true);

        } catch (DataIntegrityViolationException e) {
            // Em caso de requisições paralelas que passaram simultaneamente pelo findByExternalId,
            // a constraint UNIQUE no banco garante a integridade e lança DataIntegrityViolationException.
            log.info("Concorrência detectada no banco para externalId={}. Reavaliando registro gravado pela outra thread.",
                    dto.externalId());

            ActivationRequest concurrentRecord = repository.findByExternalId(dto.externalId())
                    .orElseThrow(() -> e);

            return handleExistingRecord(concurrentRecord, dto);
        }
    }

    private ActivationCreationResult handleExistingRecord(ActivationRequest existing, CreateActivationRequestDto dto) {
        requestsDuplicateCounter.increment();

        if (existing.matchesPayload(dto.customerId(), dto.productCode())) {
            log.info("Retry legítimo detectado para externalId={}. Retornando registro existente.", dto.externalId());
            return new ActivationCreationResult(ActivationResponseDto.fromEntity(existing), false);
        }

        log.warn("Conflito de payload detectado para externalId={}. Dados divergentes.", dto.externalId());
        throw new DuplicateExternalIdException(
                dto.externalId(),
                "O externalId '%s' já foi utilizado por outra solicitação com dados divergentes (cliente ou produto diferente)."
                        .formatted(dto.externalId())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ActivationResponseDto getById(UUID id) {
        log.debug("Consultando solicitação por id={}", id);
        return repository.findById(id)
                .map(ActivationResponseDto::fromEntity)
                .orElseThrow(() -> new ActivationNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ActivationResponseDto> list(String customerId, RequestStatus status, Pageable pageable) {
        log.debug("Listando solicitações com filtros: customerId={}, status={}, pageable={}",
                customerId, status, pageable);
        return repository.findByFilters(customerId, status, pageable)
                .map(ActivationResponseDto::fromEntity);
    }
}
