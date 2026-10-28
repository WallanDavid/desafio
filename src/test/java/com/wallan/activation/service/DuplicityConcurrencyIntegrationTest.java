package com.wallan.activation.service;

import com.wallan.activation.domain.ActivationRequest;
import com.wallan.activation.domain.ActivationRequestRepository;
import com.wallan.activation.dto.ActivationCreationResult;
import com.wallan.activation.dto.CreateActivationRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Testes de Concorrência e Duplicidade - Garantia de Unicidade de externalId")
class DuplicityConcurrencyIntegrationTest {

    @Autowired
    private ActivationService activationService;

    @Autowired
    private ActivationRequestRepository repository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private ProviderClient providerClient;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("Concorrência: Múltiplas requisições simultâneas com o mesmo externalId devem resultar em exatamente 1 registro no banco")
    void shouldEnsureExactlyOneRecordWhenMultipleIdenticalRequestsArriveConcurrently() throws Exception {
        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        String externalId = "CONCURRENT-EXT-9999";
        CreateActivationRequestDto dto = new CreateActivationRequestDto(externalId, "CUST-CONCURRENT", "PROD-CONCURRENT");

        List<ActivationCreationResult> results = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Todas as threads disparam no mesmo milissegundo
                    ActivationCreationResult result = activationService.createRequest(dto);
                    results.add(result);
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Disparo em massa

        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        // 1. Nenhuma exceção de servidor não tratada
        assertThat(errors).isEmpty();

        // 2. Todos os 10 chamadores receberam resposta
        assertThat(results).hasSize(numberOfThreads);

        // 3. Exatamente 1 criador (HTTP 201) e exatamente 9 reutilizadores idempotentes (HTTP 200)
        long createdCount = results.stream().filter(ActivationCreationResult::created).count();
        long existingCount = results.stream().filter(r -> !r.created()).count();

        assertThat(createdCount).isEqualTo(1);
        assertThat(existingCount).isEqualTo(numberOfThreads - 1);

        // 4. No banco de dados, existe ESTRITAMENTE 1 registro com esse externalId
        Optional<ActivationRequest> recordInDb = repository.findByExternalId(externalId);
        assertThat(recordInDb).isPresent();
        assertThat(repository.count()).isEqualTo(1);

        // 5. Todos os resultados apontam para o mesmo UUID de solicitação
        results.forEach(res -> assertThat(res.response().id()).isEqualTo(recordInDb.get().getId()));
    }
}
