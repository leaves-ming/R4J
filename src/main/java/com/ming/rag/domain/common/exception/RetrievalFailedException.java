package com.ming.rag.domain.common.exception;

import java.util.Map;

public final class RetrievalFailedException extends RagException {

    public RetrievalFailedException(String message) {
        super(ErrorCode.RETRIEVAL_FAILED, message);
    }

    public RetrievalFailedException(String message, Map<String, Object> details) {
        super(ErrorCode.RETRIEVAL_FAILED, message, details);
    }
}
