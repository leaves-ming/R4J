package com.ming.rag.domain.common.exception;

import java.util.Map;

public final class NotFoundException extends RagException {

    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }

    public NotFoundException(String message, Map<String, Object> details) {
        super(ErrorCode.NOT_FOUND, message, details);
    }
}
