package com.ming.rag.domain.common;

public record ChunkId(String value) {

    public ChunkId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("chunkId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
