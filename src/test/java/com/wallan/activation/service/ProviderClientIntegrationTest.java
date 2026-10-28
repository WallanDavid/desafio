package com.wallan.activation.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.wallan.activation.domain.ActivationRequest;
import com.wallan.activation.domain.exception.ProviderIntegrationException;
import com.wallan.activation.dto.ProviderActivationResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Testes de Integração com Provedor Externo - WireMock")
class ProviderClientIntegrationTest {

    private static WireMockServer wireMockServer;
    private ProviderClientImpl providerClient;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(800));
        factory.setReadTimeout(Duration.ofMillis(1200));

        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMockServer.port())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();

        providerClient = new ProviderClientImpl(restClient, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("Cenário 1: Provedor responde HTTP 200 OK -> Sucesso e retorno do protocolo")
    void shouldHandleProvider200Success() {
        ActivationRequest request = new ActivationRequest("EXT-001", "CUST-100", "INTERNET-500");

        stubFor(post(urlEqualTo("/provider/v1/activate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "protocol": "PROT-EXT-8877",
                                  "status": "SUCCESS",
                                  "message": "Ativação realizada com sucesso"
                                }
                                """)));

        ProviderActivationResponse response = providerClient.activate(request);

        assertThat(response).isNotNull();
        assertThat(response.protocol()).isEqualTo("PROT-EXT-8877");
        assertThat(response.status()).isEqualTo("SUCCESS");

        verify(postRequestedFor(urlEqualTo("/provider/v1/activate"))
                .withHeader("Idempotency-Key", equalTo(request.getId().toString())));
    }

    @Test
    @DisplayName("Cenário 2: Provedor responde HTTP 400 Bad Request -> Falha de negócio (não passível de retentativa)")
    void shouldHandleProvider400BadRequest() {
        ActivationRequest request = new ActivationRequest("EXT-002", "CUST-100", "PROD-INVALIDO");

        stubFor(post(urlEqualTo("/provider/v1/activate"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "error": "Código de produto inexistente no catálogo"
                                }
                                """)));

        assertThatThrownBy(() -> providerClient.activate(request))
                .isInstanceOf(ProviderIntegrationException.class)
                .satisfies(e -> {
                    ProviderIntegrationException pie = (ProviderIntegrationException) e;
                    assertThat(pie.getStatusCode()).isEqualTo(400);
                    assertThat(pie.isRetryable()).isFalse();
                });
    }

    @Test
    @DisplayName("Cenário 3: Provedor responde HTTP 500 Internal Server Error -> Falha temporária (passível de retentativa)")
    void shouldHandleProvider500ServerError() {
        ActivationRequest request = new ActivationRequest("EXT-003", "CUST-100", "INTERNET-500");

        stubFor(post(urlEqualTo("/provider/v1/activate"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Database connection pool timeout in provider")));

        assertThatThrownBy(() -> providerClient.activate(request))
                .isInstanceOf(ProviderIntegrationException.class)
                .satisfies(e -> {
                    ProviderIntegrationException pie = (ProviderIntegrationException) e;
                    assertThat(pie.getStatusCode()).isEqualTo(500);
                    assertThat(pie.isRetryable()).isTrue();
                });
    }

    @Test
    @DisplayName("Cenário 4: Provedor estoura Timeout de leitura -> Falha temporária (passível de retentativa com idempotency key)")
    void shouldHandleProviderTimeout() {
        ActivationRequest request = new ActivationRequest("EXT-004", "CUST-100", "INTERNET-500");

        stubFor(post(urlEqualTo("/provider/v1/activate"))
                .willReturn(aResponse()
                        .withFixedDelay(2500) // Delay superior ao timeout configurado (1200ms)
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "protocol": "DELAYED-PROT",
                                  "status": "SUCCESS"
                                }
                                """)));

        assertThatThrownBy(() -> providerClient.activate(request))
                .isInstanceOf(ProviderIntegrationException.class)
                .satisfies(e -> {
                    ProviderIntegrationException pie = (ProviderIntegrationException) e;
                    assertThat(pie.isRetryable()).isTrue();
                    assertThat(pie.getStatusCode()).isEqualTo(504);
                });
    }

    @Test
    @DisplayName("Cenário 5: Provedor responde HTTP 429 Rate Limit com Retry-After -> Falha temporária")
    void shouldHandleProvider429RateLimit() {
        ActivationRequest request = new ActivationRequest("EXT-005", "CUST-100", "INTERNET-500");

        stubFor(post(urlEqualTo("/provider/v1/activate"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "5")
                        .withBody("Too Many Requests")));

        assertThatThrownBy(() -> providerClient.activate(request))
                .isInstanceOf(ProviderIntegrationException.class)
                .satisfies(e -> {
                    ProviderIntegrationException pie = (ProviderIntegrationException) e;
                    assertThat(pie.getStatusCode()).isEqualTo(429);
                    assertThat(pie.isRetryable()).isTrue();
                });
    }
}
