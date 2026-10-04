package com.example.support.api;

import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.NoCredentialsException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.example.support.assistant.QuestionTooLargeException;
import com.google.genai.errors.ClientException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.errors.ServerException;
import com.example.support.ingest.IngestionFailedException;
import com.example.support.service.ConflictException;
import com.example.support.service.NotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Translates exceptions into RFC 9457 problem responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getTitle(), ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Request conflicts with current state", ex.getMessage());
    }

    /**
     * A database constraint (unique email, foreign key, check) rejected the write: typically two requests
     * raced past the service's own checks. The SQL error is logged, not returned, so table and constraint
     * names don't leak to clients.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Constraint violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Request conflicts with current state",
                "The change violates a data constraint");
    }

    /** {@code @Version} check failed: someone else updated the same row in the meantime. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent update",
                "The resource was modified by another request; reload it and try again");
    }

    /**
     * An ingestion source (file, CRM, legacy database) failed: 502, because the problem is upstream of us. The
     * partial report shows what was loaded before the failure.
     */
    @ExceptionHandler(IngestionFailedException.class)
    ProblemDetail handleIngestionFailed(IngestionFailedException ex) {
        log.warn("Ingestion failed", ex);
        ProblemDetail problem = problem(HttpStatus.BAD_GATEWAY, "Ingestion source failed", ex.getMessage());
        problem.setProperty("report", ex.getReport());
        return problem;
    }

    @ExceptionHandler(QuestionTooLargeException.class)
    ProblemDetail handleQuestionTooLarge(QuestionTooLargeException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Question too large", ex.getMessage());
    }

    // ---- Day 5: failures of the model provider. Provider messages are logged, not returned. ----

    /** Claude's rate limit (requests or tokens per minute). Retrying later helps, so tell the client. */
    @ExceptionHandler(RateLimitException.class)
    ProblemDetail handleModelRateLimit(RateLimitException ex) {
        log.warn("Model rate limit: {}", ex.getMessage());
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Model rate limit reached", "Too many model requests; retry later");
    }

    /** Missing or invalid API key: our configuration problem, not the client's, so 503 rather than 401. */
    @ExceptionHandler({NoCredentialsException.class, UnauthorizedException.class, PermissionDeniedException.class})
    ProblemDetail handleModelNotConfigured(RuntimeException ex) {
        log.error("Model credentials rejected or missing (is ANTHROPIC_API_KEY set?): {}", ex.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Model not configured", "The assistant is not available");
    }

    /** Any other error response from Claude (400, 5xx, overloaded): upstream of us, hence 502. */
    @ExceptionHandler({AnthropicServiceException.class, NonTransientAiException.class})
    ProblemDetail handleModelError(RuntimeException ex) {
        log.warn("Model call failed", ex);
        return problem(HttpStatus.BAD_GATEWAY, "Model call failed", "The model could not answer this request");
    }

    /**
     * Gemini (Google GenAI SDK). One exception type for all 4xx answers, so the status code decides: 429 = rate
     * limit or quota, 400 with an invalid key / 401 / 403 = our configuration, anything else = the call failed.
     */
    @ExceptionHandler(ClientException.class)
    ProblemDetail handleGeminiClientError(ClientException ex) {
        if (ex.code() == 429) {
            log.warn("Gemini rate limit or quota: {}", ex.message());
            return problem(HttpStatus.TOO_MANY_REQUESTS, "Model rate limit reached", "Too many model requests; retry later");
        }
        String message = ex.message() == null ? "" : ex.message();
        if (ex.code() == 401 || ex.code() == 403 || message.toLowerCase(java.util.Locale.ROOT).contains("api key")) {
            log.error("Gemini credentials rejected or missing (is GEMINI_API_KEY set?): {}", message);
            return problem(HttpStatus.SERVICE_UNAVAILABLE, "Model not configured", "The assistant is not available");
        }
        log.warn("Gemini call failed with {}: {}", ex.code(), message);
        return problem(HttpStatus.BAD_GATEWAY, "Model call failed", "The model could not answer this request");
    }

    /** Gemini 5xx (overloaded, internal error). */
    @ExceptionHandler(ServerException.class)
    ProblemDetail handleGeminiServerError(ServerException ex) {
        log.warn("Gemini server error {}: {}", ex.code(), ex.message());
        return problem(HttpStatus.BAD_GATEWAY, "Model call failed", "The model could not answer this request");
    }

    /** Network failure, or Ollama not running. */
    @ExceptionHandler({AnthropicIoException.class, GenAiIOException.class, TransientAiException.class,
            ResourceAccessException.class})
    ProblemDetail handleModelUnreachable(RuntimeException ex) {
        log.warn("Model unreachable: {}", ex.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Model unreachable", "The model service is not reachable");
    }

    /** {@code @Valid @RequestBody} failed. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleBodyValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return validationProblem("Request body is invalid", errors);
    }

    /** Constraints on {@code @PathVariable} / {@code @RequestParam} failed, e.g. {@code /api/customers/abc}. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail handleParameterValidation(HandlerMethodValidationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> errors.putIfAbsent(
                result.getMethodParameter().getParameterName(),
                result.getResolvableErrors().getFirst().getDefaultMessage()));
        return validationProblem("Request parameters are invalid", errors);
    }

    /** Wrong type in the URL, e.g. {@code ?status=SOLVED} or {@code /api/tickets/abc}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return validationProblem("Request parameters are invalid",
                Map.of(ex.getName(), "has an unsupported value"));
    }

    @ExceptionHandler(InvalidSortException.class)
    ProblemDetail handleInvalidSort(InvalidSortException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid sort", ex.getMessage());
    }

    /** Malformed JSON or an unknown enum value such as {@code "tier": "GOLD"}. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request",
                "Request body is not valid JSON or contains an unsupported value");
    }

    private static ProblemDetail validationProblem(String detail, Map<String, String> errors) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", detail);
        problem.setProperty("errors", errors);
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
