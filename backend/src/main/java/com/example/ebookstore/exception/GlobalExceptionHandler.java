package com.example.ebookstore.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.example.ebookstore.dto.ApiFieldError;
import com.example.ebookstore.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * One place that turns every exception into the standard {@link ErrorResponse}.
 *
 * <p>Rules followed here (security policy):
 * <ul>
 *   <li>Clients only get a generic, safe message. Stack traces, SQL and class names stay in the server log.</li>
 *   <li>Rejected values (which may be passwords) are never echoed back.</li>
 *   <li>Unknown problems become {@code 500 INTERNAL_ERROR}.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** A business rule or lookup failed in a service (the normal, expected case). */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        if (code.getStatus().is5xxServerError()) {
            log.error("API error {}", code, ex);
        } else {
            log.info("API error {} on {}", code, request.getRequestURI());
        }
        return build(code, ex.getMessage(), request, null);
    }

    /** {@code @Valid} on a request body failed. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {
        List<ApiFieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ApiFieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.getDefaultMessage(), request, errors);
    }

    /** Validation of query/path parameters failed (for example {@code size=500}). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParameterValidation(HandlerMethodValidationException ex,
                                                                   HttpServletRequest request) {
        List<ApiFieldError> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(e -> new ApiFieldError(
                                result.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        return build(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.getDefaultMessage(), request, errors);
    }

    /** A required header is missing, for example {@code Idempotency-Key}. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex,
                                                             HttpServletRequest request) {
        return build(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.getDefaultMessage(), request,
                List.of(new ApiFieldError(ex.getHeaderName(), "header is required")));
    }

    /** A required query parameter is missing, for example {@code q} on the search endpoint. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                HttpServletRequest request) {
        return build(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.getDefaultMessage(), request,
                List.of(new ApiFieldError(ex.getParameterName(), "parameter is required")));
    }

    /** A parameter has the wrong type, for example {@code /api/books/abc}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        return build(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.getDefaultMessage(), request,
                List.of(new ApiFieldError(ex.getName(), "has an invalid value")));
    }

    /** The JSON body is malformed, has the wrong types, or contains fields the contract does not allow. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                              HttpServletRequest request) {
        // Log only the exception type: a request body (for example a login) may contain a password.
        log.debug("Unreadable request body on {} ({})", request.getRequestURI(), ex.getClass().getSimpleName());
        return build(ErrorCode.VALIDATION_FAILED, "The request body is missing or malformed.", request, null);
    }

    /** The user is logged in but not allowed to do this (for example a {@code @PreAuthorize} check failed). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on {}", request.getRequestURI());
        return build(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getDefaultMessage(), request, null);
    }

    /** No endpoint exists for this URL. Reported as 404 without revealing anything about the server. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(404).body(new ErrorResponse(java.time.Instant.now(), 404, "Not Found",
                "NOT_FOUND", "The requested resource was not found.", request.getRequestURI(), null));
    }

    /** The URL exists but not with this HTTP method. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                  HttpServletRequest request) {
        return ResponseEntity.status(405).body(new ErrorResponse(java.time.Instant.now(), 405, "Method Not Allowed",
                "METHOD_NOT_ALLOWED", "This HTTP method is not supported for this URL.",
                request.getRequestURI(), null));
    }

    /** Safety net: anything unexpected. Details go to the log only. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {}", request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage(), request, null);
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message, HttpServletRequest request,
                                                List<ApiFieldError> fieldErrors) {
        ErrorResponse body = ErrorResponse.of(code, message, request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(code.getStatus()).body(body);
    }
}
