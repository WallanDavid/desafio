package com.wallan.activation.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallan.activation.config.SecurityConfig;
import com.wallan.activation.domain.RequestStatus;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ActivationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("Testes de Segurança - OAuth 2.0 / JWT Resource Server")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ActivationService activationService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("POST /api/v1/requests - Sem token deve retornar 401 Unauthorized com ProblemDetail")
    void shouldReturn401UnauthorizedWithoutToken() throws Exception {
        CreateActivationRequestDto dto = new CreateActivationRequestDto("EXT-12345", "CUST-1", "PROD-1");

        mockMvc.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não autenticado"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/requests/{id} - Sem token deve retornar 401 Unauthorized com ProblemDetail")
    void shouldReturn401UnauthorizedOnGetWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/requests/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não autenticado"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("POST /api/v1/requests - Token com escopo apenas de leitura (SCOPE_activation:read) deve retornar 403 Forbidden")
    void shouldReturn403ForbiddenWhenTokenHasInsufficientScopeForWrite() throws Exception {
        CreateActivationRequestDto dto = new CreateActivationRequestDto("EXT-12345", "CUST-1", "PROD-1");

        mockMvc.perform(post("/api/v1/requests")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_activation:read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acesso negado"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/requests/{id} - Token com escopo apenas de escrita (SCOPE_activation:write) deve retornar 403 Forbidden")
    void shouldReturn403ForbiddenWhenTokenHasInsufficientScopeForRead() throws Exception {
        mockMvc.perform(get("/api/v1/requests/" + UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_activation:write"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acesso negado"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("POST /api/v1/requests - Token com escopo correto (SCOPE_activation:write) deve ser aceito")
    void shouldAuthorizeWhenTokenHasCorrectWriteScope() throws Exception {
        UUID id = UUID.randomUUID();
        CreateActivationRequestDto dto = new CreateActivationRequestDto("EXT-12345", "CUST-1", "PROD-1");
        ActivationResponseDto responseDto = new ActivationResponseDto(
                id, "EXT-12345", "CUST-1", "PROD-1", RequestStatus.PENDING, Instant.now(), Instant.now()
        );

        when(activationService.createRequest(any())).thenReturn(new ActivationCreationResult(responseDto, true));

        mockMvc.perform(post("/api/v1/requests")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_activation:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }
}
