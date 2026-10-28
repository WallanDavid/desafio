package com.wallan.activation.web;

import com.wallan.activation.config.CorrelationIdFilter;
import com.wallan.activation.domain.exception.ActivationNotFoundException;
import com.wallan.activation.domain.exception.DuplicateExternalIdException;
import com.wallan.activation.domain.exception.InvalidStatusTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Tratamento global e padronizado de exceções conforme a RFC 9457 (Problem Details for HTTP APIs).
 * Garante que nenhum stack trace interno seja exposto ao consumidor e anexa o traceId em todas as respostas.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String PROBLEM_BASE_URL = "https://api.exemplo.com/problems/";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String instance = getPath(request);
        String traceId = CorrelationIdFilter.getCurrentTraceId();

        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> Map.of(
                        "field", err.getField(),
                        "message", err.getDefaultMessage() != null ? err.getDefaultMessage() : "inválido"
                ))
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Um ou mais campos não passaram na validação."
        );
        problem.setType(URI.create(PROBLEM_BASE_URL + "validation-error"));
        problem.setTitle("Dados inválidos");
        problem.setInstance(URI.create(instance));
        problem.setProperty("traceId", traceId);
        problem.setProperty("errors", fieldErrors);

        log.warn("Erro de validação em {}: traceId={}, errors={}", instance, traceId, fieldErrors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(ActivationNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ActivationNotFoundException ex, WebRequest request) {
        String instance = getPath(request);
        String traceId = CorrelationIdFilter.getCurrentTraceId();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create(PROBLEM_BASE_URL + "not-found"));
        problem.setTitle("Registro não encontrado");
        problem.setInstance(URI.create(instance));
        problem.setProperty("traceId", traceId);

        log.info("Recurso não encontrado em {}: traceId={}, msg={}", instance, traceId, ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(DuplicateExternalIdException.class)
    public ResponseEntity<ProblemDetail> handleDuplicate(DuplicateExternalIdException ex, WebRequest request) {
        String instance = getPath(request);
        String traceId = CorrelationIdFilter.getCurrentTraceId();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create(PROBLEM_BASE_URL + "conflict"));
        problem.setTitle("Conflito de duplicidade");
        problem.setInstance(URI.create(instance));
        problem.setProperty("traceId", traceId);
        problem.setProperty("externalId", ex.getExternalId());

        log.warn("Conflito de externalId em {}: traceId={}, externalId={}", instance, traceId, ex.getExternalId());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ProblemDetail> handleInvalidTransition(InvalidStatusTransitionException ex, WebRequest request) {
        String instance = getPath(request);
        String traceId = CorrelationIdFilter.getCurrentTraceId();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setType(URI.create(PROBLEM_BASE_URL + "invalid-transition"));
        problem.setTitle("Transição de estado inválida");
        problem.setInstance(URI.create(instance));
        problem.setProperty("traceId", traceId);

        log.warn("Transição de estado rejeitada em {}: traceId={}, msg={}", instance, traceId, ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        String instance = getPath(request);
        String traceId = CorrelationIdFilter.getCurrentTraceId();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "Acesso negado: o token fornecido não possui o escopo de autorização necessário."
        );
        problem.setType(URI.create(PROBLEM_BASE_URL + "forbidden"));
        problem.setTitle("Acesso negado");
        problem.setInstance(URI.create(instance));
        problem.setProperty("traceId", traceId);

        log.warn("Acesso negado em {}: traceId={}", instance, traceId);

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, WebRequest request) {
        String instance = getPath(request);
        String traceId = CorrelationIdFilter.getCurrentTraceId();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Autenticação obrigatória ou token inválido/expirado."
        );
        problem.setType(URI.create(PROBLEM_BASE_URL + "unauthorized"));
        problem.setTitle("Não autenticado");
        problem.setInstance(URI.create(instance));
        problem.setProperty("traceId", traceId);

        log.warn("Falha de autenticação em {}: traceId={}", instance, traceId);

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneralException(Exception ex, WebRequest request) {
        String instance = getPath(request);
        String traceId = CorrelationIdFilter.getCurrentTraceId();

        // Log completo com stack trace apenas internamente nos servidores
        log.error("Erro interno não tratado em {}: traceId={}", instance, traceId, ex);

        // Resposta higienizada sem vazar detalhes de infraestrutura ou stack traces
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro interno inesperado ao processar sua solicitação. Informe o traceId ao suporte técnico."
        );
        problem.setType(URI.create(PROBLEM_BASE_URL + "internal-server-error"));
        problem.setTitle("Erro interno do servidor");
        problem.setInstance(URI.create(instance));
        problem.setProperty("traceId", traceId);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private String getPath(WebRequest request) {
        if (request instanceof ServletWebRequest swr) {
            return swr.getRequest().getRequestURI();
        }
        return "/api/v1/requests";
    }
}
