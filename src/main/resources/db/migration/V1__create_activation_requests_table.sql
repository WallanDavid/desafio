-- Criação da tabela principal de solicitações de ativação
CREATE TABLE IF NOT EXISTS activation_requests (
    id UUID PRIMARY KEY,
    external_id VARCHAR(64) NOT NULL,
    customer_id VARCHAR(64) NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    provider_protocol VARCHAR(100),
    failure_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_activation_request_external_id UNIQUE (external_id)
);

-- Índices estratégicos para otimização de consultas e listagem paginada
CREATE INDEX IF NOT EXISTS idx_activation_requests_customer_id ON activation_requests (customer_id);
CREATE INDEX IF NOT EXISTS idx_activation_requests_status ON activation_requests (status);
CREATE INDEX IF NOT EXISTS idx_activation_requests_created_at ON activation_requests (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_activation_requests_reconciliation ON activation_requests (status, updated_at);
