package com.ming.rag.domain.common;

public record DocumentId(String value) {

    public DocumentId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
