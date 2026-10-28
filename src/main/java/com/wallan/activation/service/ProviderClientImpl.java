package com.wallan.activation.service;

import com.wallan.activation.domain.ActivationRequest;
import com.wallan.activation.domain.exception.ProviderIntegrationException;
import com.wallan.activation.dto.ProviderActivationPayload;
import com.wallan.activation.dto.ProviderActivationResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;

import java.util.UUID;

/**
 * Implementação do cliente de integração com o provedor externo de ativação.
 * Implementa resiliência com Circuit Breaker, timeouts explícitos e chave de idempotência.
 */
@Service
public class ProviderClientImpl implements ProviderClient {

    private static final Logger log = LoggerFactory.getLogger(ProviderClientImpl.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient restClient;
    private final Timer providerLatencyTimer;
    private final Counter providerSuccessCounter;
    private final Counter providerFailureCounter;

    public ProviderClientImpl(RestClient providerRestClient, MeterRegistry meterRegistry) {
        this.restClient = providerRestClient;
        this.providerLatencyTimer = Timer.builder("activation.provider.call.duration")
                .description("Tempo de resposta das chamadas ao provedor externo")
                .register(meterRegistry);
        this.providerSuccessCounter = Counter.builder("activation.provider.call.total")
                .tag("result", "success")
                .register(meterRegistry);
        this.providerFailureCounter = Counter.builder("activation.provider.call.total")
                .tag("result", "failure")
                .register(meterRegistry);
    }

    @Override
    @CircuitBreaker(name = "providerClient", fallbackMethod = "circuitBreakerFallback")
    public ProviderActivationResponse activate(ActivationRequest request) {
        log.info("Chamando provedor externo para ativação: requestId={}, externalId={}, productCode={}",
                request.getId(), request.getExternalId(), request.getProductCode());

        ProviderActivationPayload payload = new ProviderActivationPayload(
                request.getId().toString(),
                request.getExternalId(),
                request.getCustomerId(),
                request.getProductCode()
        );

        long startTime = System.currentTimeMillis();
        try {
            ResponseEntity<ProviderActivationResponse> response = restClient.post()
                    .uri("/provider/v1/activate")
                    .header(IDEMPOTENCY_KEY_HEADER, request.getId().toString())
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        int code = res.getStatusCode().value();
                        String body = new String(res.getBody().readAllBytes());
                        log.warn("Provedor retornou erro 4xx: code={}, body={}", code, body);
                        if (code == 429) {
                            throw new ProviderIntegrationException(
                                    "Provedor aplicou limitação de taxa (Rate Limit 429). Retry-After: " + res.getHeaders().getFirst("Retry-After"),
                                    429,
                                    true
                            );
                        }
                        // HTTP 400 indica validação de negócio rejeitada pelo provedor (não passível de retentativa)
                        throw new ProviderIntegrationException(
                                "Provedor rejeitou a solicitação com erro 4xx: " + body,
                                code,
                                false
                        );
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        int code = res.getStatusCode().value();
                        String body = new String(res.getBody().readAllBytes());
                        log.error("Provedor retornou erro 5xx: code={}, body={}", code, body);
                        // HTTP 500/503 é falha transitória (passível de retentativa)
                        throw new ProviderIntegrationException(
                                "Provedor retornou erro interno 5xx: " + body,
                                code,
                                true
                        );
                    })
                    .toEntity(ProviderActivationResponse.class);

            providerSuccessCounter.increment();
            ProviderActivationResponse body = response.getBody();
            if (body == null) {
                body = new ProviderActivationResponse(UUID.randomUUID().toString(), "SUCCESS", "Ativação confirmada");
            }

            log.info("Provedor respondeu com sucesso: requestId={}, protocol={}", request.getId(), body.protocol());
            return body;

        } catch (ResourceAccessException e) {
            providerFailureCounter.increment();
            log.error("Timeout ou erro de I/O na chamada ao provedor para requestId={}: {}", request.getId(), e.getMessage());
            throw new ProviderIntegrationException("Timeout na chamada ao provedor externo", e, 504, true);

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            providerFailureCounter.increment();
            throw new ProviderIntegrationException("Erro HTTP do provedor: " + e.getMessage(), e, e.getStatusCode().value(), e.getStatusCode().is5xxServerError());

        } catch (RestClientException e) {
            providerFailureCounter.increment();
            log.error("Erro RestClientException na chamada ao provedor para requestId={}: {}", request.getId(), e.getMessage());
            if (isTimeoutException(e)) {
                throw new ProviderIntegrationException("Timeout na chamada ao provedor externo", e, 504, true);
            }
            throw new ProviderIntegrationException("Erro de comunicação com o provedor: " + e.getMessage(), e, 500, true);

        } catch (ProviderIntegrationException e) {
            providerFailureCounter.increment();
            throw e;

        } catch (Exception e) {
            providerFailureCounter.increment();
            log.error("Erro inesperado na chamada ao provedor para requestId={}: {}", request.getId(), e.getMessage(), e);
            if (isTimeoutException(e)) {
                throw new ProviderIntegrationException("Timeout na chamada ao provedor externo", e, 504, true);
            }
            throw new ProviderIntegrationException("Erro inesperado na integração: " + e.getMessage(), e, 500, true);

        } finally {
            providerLatencyTimer.record(java.time.Duration.ofMillis(System.currentTimeMillis() - startTime));
        }
    }

    private boolean isTimeoutException(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof java.net.SocketTimeoutException
                    || current instanceof java.util.concurrent.TimeoutException
                    || (current.getMessage() != null && current.getMessage().toLowerCase().contains("timed out"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Fallback executado quando o Circuit Breaker está em estado ABERTO.
     */
    public ProviderActivationResponse circuitBreakerFallback(ActivationRequest request, Throwable t) {
        log.warn("Circuit Breaker ABERTO para o provedor externo. Chamada rejeitada preventivamente para requestId={}: {}",
                request.getId(), t.getMessage());
        throw new ProviderIntegrationException(
                "Circuito de integração temporariamente indisponível (Circuit Breaker aberto)",
                t,
                503,
                true
        );
    }
}
