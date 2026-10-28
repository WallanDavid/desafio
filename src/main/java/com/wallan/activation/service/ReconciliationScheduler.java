package com.wallan.activation.service;

import com.wallan.activation.domain.ActivationRequest;
import com.wallan.activation.domain.ActivationRequestRepository;
import com.wallan.activation.domain.RequestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Agendador de reconciliação para recuperação de solicitações presas em PENDING ou PROCESSING.
 * Garante resiliência contra crashes de aplicação ou falhas de rede entre o commit no banco e a chamada remota.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "activation.reconciliation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    private final ActivationRequestRepository repository;
    private final ActivationProcessor processor;
    private final int stuckThresholdSeconds;

    public ReconciliationScheduler(
            ActivationRequestRepository repository,
            ActivationProcessor processor,
            @Value("${activation.reconciliation.stuck-threshold-seconds:30}") int stuckThresholdSeconds
    ) {
        this.repository = repository;
        this.processor = processor;
        this.stuckThresholdSeconds = stuckThresholdSeconds;
    }

    @Scheduled(fixedDelayString = "${activation.reconciliation.fixed-delay-ms:15000}", initialDelay = 10000)
    public void reconcileStuckRequests() {
        Instant threshold = Instant.now().minus(stuckThresholdSeconds, ChronoUnit.SECONDS);

        // 1. Reconciliação de solicitações presas em PENDING (ex: crash da JVM antes do disparo assíncrono)
        List<ActivationRequest> stuckPending = repository.findStuckRequests(
                RequestStatus.PENDING,
                threshold,
                PageRequest.of(0, 20)
        );

        if (!stuckPending.isEmpty()) {
            log.info("Reconciliação encontrou {} solicitações presas em PENDING. Reprocessando...", stuckPending.size());
            for (ActivationRequest req : stuckPending) {
                processor.processActivation(req.getId());
            }
        }

        // 2. Reconciliação de solicitações em PROCESSING com falha transitória aguardando retentativa
        List<ActivationRequest> stuckProcessing = repository.findStuckRequests(
                RequestStatus.PROCESSING,
                threshold,
                PageRequest.of(0, 20)
        );

        if (!stuckProcessing.isEmpty()) {
            log.info("Reconciliação encontrou {} solicitações presas em PROCESSING. Retentando envio...", stuckProcessing.size());
            for (ActivationRequest req : stuckProcessing) {
                processor.processActivation(req.getId());
            }
        }
    }
}
