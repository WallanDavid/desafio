package com.wallan.activation.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório Spring Data JPA para gerenciamento da persistência de solicitações de ativação.
 */
@Repository
public interface ActivationRequestRepository extends JpaRepository<ActivationRequest, UUID>, JpaSpecificationExecutor<ActivationRequest> {

    /**
     * Busca uma solicitação pelo identificador externo (externalId).
     */
    Optional<ActivationRequest> findByExternalId(String externalId);

    /**
     * Busca paginada por cliente e/ou status.
     */
    @Query("""
            SELECT r FROM ActivationRequest r
            WHERE (:customerId IS NULL OR r.customerId = :customerId)
              AND (:status IS NULL OR r.status = :status)
            """)
    Page<ActivationRequest> findByFilters(
            @Param("customerId") String customerId,
            @Param("status") RequestStatus status,
            Pageable pageable
    );

    /**
     * Busca solicitações que permaneceram em determinado estado anterior a um timestamp de corte,
     * utilizadas pelo processo de reconciliação para recuperação de falhas transitórias/crashes.
     */
    @Query("""
            SELECT r FROM ActivationRequest r
            WHERE r.status = :status
              AND r.updatedAt < :threshold
            ORDER BY r.updatedAt ASC
            """)
    List<ActivationRequest> findStuckRequests(
            @Param("status") RequestStatus status,
            @Param("threshold") Instant threshold,
            Pageable pageable
    );
}
