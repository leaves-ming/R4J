package com.ming.rag.domain.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class DocumentIdPolicy {

    public DocumentId generate(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new IllegalArgumentException("fileBytes must not be empty");
        }

        return new DocumentId(toHex(sha256(fileBytes)));
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
