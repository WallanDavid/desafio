package com.wallan.activation.service;

import com.wallan.activation.domain.ActivationRequest;
import com.wallan.activation.domain.ActivationRequestRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Componente auxiliar para isolamento transacional da tentativa de inserção.
 * Utiliza Propagation.REQUIRES_NEW para que uma eventual violação de constraint UNIQUE
 * em cenários concorrentes não marque a transação principal como rollback-only.
 */
@Component
public class ActivationPersister {

    private final ActivationRequestRepository repository;

    public ActivationPersister(ActivationRequestRepository repository) {
        this.repository = repository;
    }

    /**
     * Tenta persistir e dar flush na solicitação em transação isolada.
     *
     * @param request Nova solicitação
     * @return Entidade persistida
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ActivationRequest persistAndFlush(ActivationRequest request) {
        return repository.saveAndFlush(request);
    }
}
