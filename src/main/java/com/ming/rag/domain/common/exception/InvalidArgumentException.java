package com.ming.rag.domain.common.exception;

import java.util.Map;

public final class InvalidArgumentException extends RagException {

    public InvalidArgumentException(String message) {
        super(ErrorCode.INVALID_ARGUMENT, message);
    }

    public InvalidArgumentException(String message, Map<String, Object> details) {
        super(ErrorCode.INVALID_ARGUMENT, message, details);
    }
}
