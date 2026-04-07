package com.ming.rag.interfaces.http.dto;

import java.util.Map;

public record ErrorResponse(
        String errorCode,
        String message,
        String traceId,
        Map<String, Object> details
) {
}
