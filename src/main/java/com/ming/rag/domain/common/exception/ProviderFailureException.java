package com.ming.rag.domain.common.exception;

import java.util.Map;

public final class ProviderFailureException extends RagException {

    public ProviderFailureException(String message) {
        super(ErrorCode.PROVIDER_FAILURE, message);
    }

    public ProviderFailureException(String message, Map<String, Object> details) {
        super(ErrorCode.PROVIDER_FAILURE, message, details);
    }
}
