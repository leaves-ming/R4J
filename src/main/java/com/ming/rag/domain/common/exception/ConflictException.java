package com.ming.rag.domain.common.exception;

import java.util.Map;

public final class ConflictException extends RagException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }

    public ConflictException(String message, Map<String, Object> details) {
        super(ErrorCode.CONFLICT, message, details);
    }
}
