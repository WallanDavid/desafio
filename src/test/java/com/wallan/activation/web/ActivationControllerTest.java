package com.wallan.activation.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallan.activation.config.SecurityConfig;
import com.wallan.activation.domain.RequestStatus;
import com.wallan.activation.domain.exception.ActivationNotFoundException;
import com.wallan.activation.domain.exception.DuplicateExternalIdException;
import com.wallan.activation.dto.ActivationCreationResult;
import com.wallan.activation.dto.ActivationResponseDto;
import com.wallan.activation.dto.CreateActivationRequestDto;
import com.wallan.activation.service.ActivationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ActivationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("Testes de Integração Web - ActivationController")
class ActivationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ActivationService activationService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("POST /api/v1/requests - Deve retornar 201 Created com header Location para nova solicitação válida")
    void shouldReturn201CreatedForValidNewRequest() throws Exception {
        UUID id = UUID.randomUUID();
        CreateActivationRequestDto dto = new CreateActivationRequestDto("EXT-12345", "CUST-10001", "INTERNET-500");
        ActivationResponseDto responseDto = new ActivationResponseDto(
                id,
                "EXT-12345",
                "CUST-10001",
                "INTERNET-500",
                RequestStatus.PENDING,
                Instant.now(),
                Instant.now()
        );

        when(activationService.createRequest(any(CreateActivationRequestDto.class)))
                .thenReturn(new ActivationCreationResult(responseDto, true));

        mockMvc.perform(post("/api/v1/requests")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_activation:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/requests/" + id)))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.externalId").value("EXT-12345"))
                .andExpect(jsonPath("$.customerId").value("CUST-10001"))
                .andExpect(jsonPath("$.productCode").value("INTERNET-500"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/v1/requests - Deve retornar 200 OK para retry legítimo (mesmo externalId e mesmo conteúdo)")
    void shouldReturn200OkForIdempotentRetry() throws Exception {
        UUID id = UUID.randomUUID();
        CreateActivationRequestDto dto = new CreateActivationRequestDto("EXT-12345", "CUST-10001", "INTERNET-500");
        ActivationResponseDto responseDto = new ActivationResponseDto(
                id,
                "EXT-12345",
                "CUST-10001",
                "INTERNET-500",
                RequestStatus.PROCESSING,
                Instant.now(),
                Instant.now()
        );

        when(activationService.createRequest(any(CreateActivationRequestDto.class)))
                .thenReturn(new ActivationCreationResult(responseDto, false));

        mockMvc.perform(post("/api/v1/requests")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_activation:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    @DisplayName("POST /api/v1/requests - Deve retornar 400 Bad Request com ProblemDetail RFC 9457 quando campos forem inválidos")
    void shouldReturn400BadRequestWhenPayloadIsInvalid() throws Exception {
        CreateActivationRequestDto invalidDto = new CreateActivationRequestDto("", "  ", "");

        mockMvc.perform(post("/api/v1/requests")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_activation:write")))
                        .header("X-Correlation-Id", "test-corr-id-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://api.exemplo.com/problems/validation-error"))
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.traceId").value("test-corr-id-123"))
                .andExpect(jsonPath("$.errors", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test
    @DisplayName("POST /api/v1/requests - Deve retornar 409 Conflict quando externalId já existir com payload divergente")
    void shouldReturn409ConflictWhenDuplicateExternalIdHasDifferentPayload() throws Exception {
        CreateActivationRequestDto dto = new CreateActivationRequestDto("EXT-CONFLITO", "CUST-2", "PROD-X");

        when(activationService.createRequest(any(CreateActivationRequestDto.class)))
                .thenThrow(new DuplicateExternalIdException("EXT-CONFLITO"));

        mockMvc.perform(post("/api/v1/requests")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_activation:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conflito de duplicidade"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("GET /api/v1/requests/{id} - Deve retornar 200 OK com dados da solicitação quando existir")
    void shouldReturn200OkWhenRequestExists() throws Exception {
        UUID id = UUID.randomUUID();
        ActivationResponseDto responseDto = new ActivationResponseDto(
                id,
                "EXT-12345",
                "CUST-10001",
                "INTERNET-500",
                RequestStatus.COMPLETED,
                Instant.now(),
                Instant.now()
        );

        when(activationService.getById(id)).thenReturn(responseDto);

        mockMvc.perform(get("/api/v1/requests/" + id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_activation:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET /api/v1/requests/{id} - Deve retornar 404 Not Found quando solicitação não existir")
    void shouldReturn404NotFoundWhenRequestDoesNotExist() throws Exception {
        UUID id = UUID.randomUUID();

        when(activationService.getById(id)).thenThrow(new ActivationNotFoundException(id));

        mockMvc.perform(get("/api/v1/requests/" + id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_activation:read"))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Registro não encontrado"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/v1/requests - Deve retornar 200 OK com página de resultados")
    void shouldReturnPaginatedListings() throws Exception {
        UUID id = UUID.randomUUID();
        ActivationResponseDto dto = new ActivationResponseDto(
                id,
                "EXT-100",
                "CUST-1",
                "PROD-1",
                RequestStatus.PENDING,
                Instant.now(),
                Instant.now()
        );

        when(activationService.list(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/requests?customerId=CUST-1&status=PENDING&page=0&size=20")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_activation:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].externalId").value("EXT-100"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
