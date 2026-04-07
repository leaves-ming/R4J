package com.ming.rag.domain.common;

public record CollectionId(String value) {

    public CollectionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("collectionId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
