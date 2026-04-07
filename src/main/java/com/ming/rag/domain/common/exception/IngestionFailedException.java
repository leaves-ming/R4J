package com.ming.rag.domain.common.exception;

import java.util.Map;

public final class IngestionFailedException extends RagException {

    public IngestionFailedException(String message) {
        super(ErrorCode.INGESTION_FAILED, message);
    }

    public IngestionFailedException(String message, Map<String, Object> details) {
        super(ErrorCode.INGESTION_FAILED, message, details);
    }
}
