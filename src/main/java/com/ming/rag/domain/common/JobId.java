package com.ming.rag.domain.common;

public record JobId(String value) {

    public JobId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("jobId must not be blank");
        }
    }
}
