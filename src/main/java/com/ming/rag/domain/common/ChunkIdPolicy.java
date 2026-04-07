package com.ming.rag.domain.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ChunkIdPolicy {

    public ChunkId generate(String documentId, int chunkIndex, String canonicalChunkText) {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative");
        }
        if (canonicalChunkText == null || canonicalChunkText.isBlank()) {
            throw new IllegalArgumentException("canonicalChunkText must not be blank");
        }

        var normalizedChunkText = canonicalChunkText
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
        var contentHash = toHex(sha256(normalizedChunkText.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .substring(0, 8);
        return new ChunkId("%s_%04d_%s".formatted(documentId, chunkIndex, contentHash));
    }

    private byte[] sha256(byte[] source) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(source);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    private String toHex(byte[] bytes) {
        var builder = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            builder.append(String.format("%02x", current));
        }
        return builder.toString();
    }
}
