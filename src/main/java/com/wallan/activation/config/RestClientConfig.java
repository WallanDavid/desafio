package com.wallan.activation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Configuração do RestClient para comunicação com o provedor externo,
 * com timeouts explícitos e propagação automática de CorrelationId.
 */
@Configuration
public class RestClientConfig {

    @Value("${activation.provider.base-url:http://localhost:8089}")
    private String baseUrl;

    @Value("${activation.provider.connect-timeout-ms:2000}")
    private int connectTimeoutMs;

    @Value("${activation.provider.read-timeout-ms:3000}")
    private int readTimeoutMs;

    @Bean
    public RestClient providerRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor((request, body, execution) -> {
                    String traceId = CorrelationIdFilter.getCurrentTraceId();
                    request.getHeaders().add(CorrelationIdFilter.CORRELATION_ID_HEADER, traceId);
                    return execution.execute(request, body);
                })
                .build();
    }
}
