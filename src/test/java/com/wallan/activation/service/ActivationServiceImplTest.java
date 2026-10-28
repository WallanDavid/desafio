package com.wallan.activation.service;

import com.wallan.activation.domain.ActivationRequest;
import com.wallan.activation.domain.ActivationRequestRepository;
import com.wallan.activation.domain.RequestStatus;
import com.wallan.activation.domain.exception.ActivationNotFoundException;
import com.wallan.activation.domain.exception.DuplicateExternalIdException;
import com.wallan.activation.dto.ActivationCreationResult;
import com.wallan.activation.dto.ActivationResponseDto;
import com.wallan.activation.dto.CreateActivationRequestDto;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Camada de Serviço de Ativação")
class ActivationServiceImplTest {

    @Mock
    private ActivationRequestRepository repository;

    @Mock
    private ActivationPersister persister;

    @Mock
    private ActivationProcessor processor;

    private ActivationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ActivationServiceImpl(repository, persister, processor, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("Deve criar nova solicitação com sucesso quando externalId não existir")
    void shouldCreateNewRequestWhenExternalIdDoesNotExist() {
        CreateActivationRequestDto dto = new CreateActivationRequestDto("EXT-100", "CUST-1", "PROD-A");

        when(repository.findByExternalId("EXT-100")).thenReturn(Optional.empty());
        when(persister.persistAndFlush(any(ActivationRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActivationCreationResult result = service.createRequest(dto);

        assertThat(result.created()).isTrue();
        assertThat(result.response().externalId()).isEqualTo("EXT-100");
        assertThat(result.response().customerId()).isEqualTo("CUST-1");
        assertThat(result.response().productCode()).isEqualTo("PROD-A");
        assertThat(result.response().status()).isEqualTo(RequestStatus.PENDING);

        verify(persister).persistAndFlush(any(ActivationRequest.class));
        verify(processor).processActivation(any(UUID.class));
    }

    @Test
    @DisplayName("Deve retornar registro existente (idempotência 200) quando externalId e payload forem idênticos")
    void shouldReturnExistingRecordWhenExternalIdAndPayloadMatch() {
        CreateActivationRequestDto dto = new CreateActivationRequestDto("EXT-100", "CUST-1", "PROD-A");
        ActivationRequest existing = new ActivationRequest("EXT-100", "CUST-1", "PROD-A");

        when(repository.findByExternalId("EXT-100")).thenReturn(Optional.of(existing));

        ActivationCreationResult result = service.createRequest(dto);

        assertThat(result.created()).isFalse();
        assertThat(result.response().id()).isEqualTo(existing.getId());
        assertThat(result.response().externalId()).isEqualTo("EXT-100");

        verify(persister, never()).persistAndFlush(any());
        verify(processor, never()).processActivation(any());
    }

    @Test
    @DisplayName("Deve lançar DuplicateExternalIdException quando externalId existir com payload divergente")
    void shouldThrowDuplicateExternalIdExceptionWhenPayloadDiffers() {
        CreateActivationRequestDto dto = new CreateActivationRequestDto("EXT-100", "CUST-DIFFERENT", "PROD-A");
        ActivationRequest existing = new ActivationRequest("EXT-100", "CUST-ORIGINAL", "PROD-A");

        when(repository.findByExternalId("EXT-100")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createRequest(dto))
                .isInstanceOf(DuplicateExternalIdException.class)
                .hasMessageContaining("já foi utilizado por outra solicitação com dados divergentes");

        verify(persister, never()).persistAndFlush(any());
        verify(processor, never()).processActivation(any());
    }

    @Test
    @DisplayName("Deve recuperar concorrência via DataIntegrityViolationException e responder com idempotência se payload for idêntico")
    void shouldHandleConcurrentRaceConditionGracefully() {
        CreateActivationRequestDto dto = new CreateActivationRequestDto("EXT-RACE", "CUST-1", "PROD-A");
        ActivationRequest existing = new ActivationRequest("EXT-RACE", "CUST-1", "PROD-A");

        // Simula primeiro findByExternalId retornando vazio
        when(repository.findByExternalId("EXT-RACE"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));

        // Simula colisão na gravação (outra thread inseriu antes do flush)
        when(persister.persistAndFlush(any(ActivationRequest.class)))
                .thenThrow(new DataIntegrityViolationException("Constraint violation: uk_activation_request_external_id"));

        ActivationCreationResult result = service.createRequest(dto);

        assertThat(result.created()).isFalse();
        assertThat(result.response().id()).isEqualTo(existing.getId());
    }

    @Test
    @DisplayName("Deve buscar solicitação por ID com sucesso")
    void shouldGetRequestByIdSuccessfully() {
        UUID id = UUID.randomUUID();
        ActivationRequest request = new ActivationRequest("EXT-001", "CUST-1", "PROD-A");

        when(repository.findById(id)).thenReturn(Optional.of(request));

        ActivationResponseDto response = service.getById(id);

        assertThat(response).isNotNull();
        assertThat(response.externalId()).isEqualTo("EXT-001");
    }

    @Test
    @DisplayName("Deve lançar ActivationNotFoundException quando ID não existir")
    void shouldThrowActivationNotFoundExceptionWhenIdDoesNotExist() {
        UUID id = UUID.randomUUID();

        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(ActivationNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    @DisplayName("Deve listar solicitações de forma paginada aplicando filtros")
    void shouldListRequestsWithPaginationAndFilters() {
        ActivationRequest request = new ActivationRequest("EXT-001", "CUST-1", "PROD-A");
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<ActivationRequest> page = new PageImpl<>(List.of(request), pageRequest, 1);

        when(repository.findByFilters("CUST-1", RequestStatus.PENDING, pageRequest)).thenReturn(page);

        Page<ActivationResponseDto> result = service.list("CUST-1", RequestStatus.PENDING, pageRequest);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).externalId()).isEqualTo("EXT-001");
    }
}
