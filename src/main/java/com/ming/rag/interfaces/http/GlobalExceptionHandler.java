package com.ming.rag.interfaces.http;

import com.ming.rag.domain.common.exception.ErrorCode;
import com.ming.rag.domain.common.exception.RagException;
import com.ming.rag.interfaces.http.dto.ErrorResponse;
import com.ming.rag.observability.TraceContextAccessor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final TraceContextAccessor traceContextAccessor;

    public GlobalExceptionHandler(TraceContextAccessor traceContextAccessor) {
        this.traceContextAccessor = traceContextAccessor;
    }

    @ExceptionHandler(RagException.class)
    public ResponseEntity<ErrorResponse> handleRagException(RagException exception, HttpServletRequest request) {
        return ResponseEntity.status(toStatus(exception.errorCode()))
                .body(new ErrorResponse(
                        exception.errorCode().name(),
                        exception.getMessage(),
                        resolveTraceId(request),
                        exception.details()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        var details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        error -> error.getField(),
                        error -> (Object) (error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage()),
                        (left, right) -> right
                ));

        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        ErrorCode.INVALID_ARGUMENT.name(),
                        "Request validation failed",
                        resolveTraceId(request),
                        details
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        ErrorCode.PROVIDER_FAILURE.name(),
                        exception.getMessage(),
                        resolveTraceId(request),
                        Map.of()
                ));
    }

    private HttpStatus toStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PROVIDER_FAILURE -> HttpStatus.BAD_GATEWAY;
            case RETRIEVAL_FAILED -> HttpStatus.SERVICE_UNAVAILABLE;
            case INGESTION_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private String resolveTraceId(HttpServletRequest request) {
        var requestTraceId = request.getAttribute(TraceContextFilter.REQUEST_TRACE_ID);
        if (requestTraceId instanceof String traceId) {
            return traceId;
        }
        return traceContextAccessor.currentTraceId();
    }
}
