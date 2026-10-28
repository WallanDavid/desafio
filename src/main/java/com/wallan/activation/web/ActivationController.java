package com.wallan.activation.web;

import com.wallan.activation.domain.RequestStatus;
import com.wallan.activation.dto.ActivationCreationResult;
import com.wallan.activation.dto.ActivationResponseDto;
import com.wallan.activation.dto.CreateActivationRequestDto;
import com.wallan.activation.service.ActivationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Controller REST expondo as operações do serviço de ativação de produtos.
 */
@RestController
@RequestMapping(value = "/api/v1/requests", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Solicitações de Ativação", description = "Endpoints para registro, consulta e acompanhamento de ativações")
@SecurityRequirement(name = "OAuth2")
public class ActivationController {

    private final ActivationService activationService;

    public ActivationController(ActivationService activationService) {
        this.activationService = activationService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('SCOPE_activation:write')")
    @Operation(
            summary = "Registrar nova solicitação de ativação",
            description = "Cria uma solicitação de ativação ou reprocessa idempotentemente caso o externalId já exista com o mesmo payload."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Solicitação criada com sucesso",
                    headers = @Header(name = "Location", description = "URI da solicitação criada", schema = @Schema(type = "string")),
                    content = @Content(schema = @Schema(implementation = ActivationResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "200",
                    description = "Retry legítimo: solicitação existente retornada idempotentemente",
                    content = @Content(schema = @Schema(implementation = ActivationResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados de entrada inválidos",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class), mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflito: externalId já cadastrado com dados divergentes",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class), mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)
            )
    })
    public ResponseEntity<ActivationResponseDto> createRequest(
            @Valid @RequestBody CreateActivationRequestDto dto
    ) {
        ActivationCreationResult result = activationService.createRequest(dto);

        if (result.created()) {
            URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(result.response().id())
                    .toUri();
            return ResponseEntity.created(location).body(result.response());
        }

        // Retry idempotente legítimo
        return ResponseEntity.ok(result.response());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_activation:read')")
    @Operation(
            summary = "Consultar solicitação por ID",
            description = "Retorna os detalhes e o status atual da solicitação de ativação."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitação encontrada"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Solicitação não encontrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class), mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)
            )
    })
    public ResponseEntity<ActivationResponseDto> getById(@PathVariable("id") UUID id) {
        ActivationResponseDto dto = activationService.getById(id);
        return ResponseEntity.ok(dto);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_activation:read')")
    @Operation(
            summary = "Listar solicitações de forma paginada",
            description = "Consulta solicitações cadastradas com suporte a filtros por status e cliente."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista paginada de solicitações")
    })
    public ResponseEntity<Page<ActivationResponseDto>> listRequests(
            @RequestParam(name = "status", required = false) RequestStatus status,
            @RequestParam(name = "customerId", required = false) String customerId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<ActivationResponseDto> page = activationService.list(customerId, status, pageable);
        return ResponseEntity.ok(page);
    }
}
