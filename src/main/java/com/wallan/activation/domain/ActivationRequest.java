package com.wallan.activation.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidade de domínio representando uma solicitação de ativação de produto.
 * Possui controle de concorrência otimista via @Version e transições de estado encapsuladas.
 */
@Entity
@Table(
        name = "activation_requests",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_activation_request_external_id", columnNames = "external_id")
        },
        indexes = {
                @Index(name = "idx_activation_requests_customer_id", columnList = "customer_id"),
                @Index(name = "idx_activation_requests_status", columnList = "status"),
                @Index(name = "idx_activation_requests_created_at", columnList = "created_at DESC")
        }
)
public class ActivationRequest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "external_id", nullable = false, length = 64, updatable = false)
    private String externalId;

    @Column(name = "customer_id", nullable = false, length = 64, updatable = false)
    private String customerId;

    @Column(name = "product_code", nullable = false, length = 64, updatable = false)
    private String productCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RequestStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "provider_protocol", length = 100)
    private String providerProtocol;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Construtor protegido exigido pela especificação JPA.
     */
    protected ActivationRequest() {
    }

    /**
     * Fábrica para criação de novas solicitações no estado inicial PENDING.
     */
    public ActivationRequest(String externalId, String customerId, String productCode) {
        this.id = UUID.randomUUID();
        this.externalId = Objects.requireNonNull(externalId, "externalId não pode ser nulo").trim();
        this.customerId = Objects.requireNonNull(customerId, "customerId não pode ser nulo").trim();
        this.productCode = Objects.requireNonNull(productCode, "productCode não pode ser nulo").trim();
        this.status = RequestStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    // ==========================================
    // Métodos de Domínio (Transições de Estado)
    // ==========================================

    /**
     * Inicia o processamento da solicitação junto ao provedor.
     */
    public void startProcessing() {
        this.status.validateTransitionTo(RequestStatus.PROCESSING);
        this.status = RequestStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    /**
     * Conclui a solicitação com sucesso após confirmação do provedor (2xx).
     *
     * @param protocol Código ou protocolo gerado pelo provedor externo
     */
    public void complete(String protocol) {
        this.status.validateTransitionTo(RequestStatus.COMPLETED);
        this.status = RequestStatus.COMPLETED;
        this.providerProtocol = protocol;
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }

    /**
     * Registra a falha definitiva da solicitação (erro não reprocessável ou limite de retentativas atingido).
     *
     * @param reason Descrição do motivo da falha
     */
    public void fail(String reason) {
        this.status.validateTransitionTo(RequestStatus.FAILED);
        this.status = RequestStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    /**
     * Registra uma falha temporária e agenda retentativa, incrementando o contador.
     *
     * @param reason Motivo da falha transitória
     */
    public void registerTransientFailure(String reason) {
        this.retryCount++;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    /**
     * Verifica se os dados desta solicitação são idênticos aos da requisição recebida (idempotência).
     */
    public boolean matchesPayload(String customerId, String productCode) {
        return Objects.equals(this.customerId, customerId)
                && Objects.equals(this.productCode, productCode);
    }

    // ==========================================
    // Getters
    // ==========================================

    public UUID getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProductCode() {
        return productCode;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public Long getVersion() {
        return version;
    }

    public String getProviderProtocol() {
        return providerProtocol;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActivationRequest that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
