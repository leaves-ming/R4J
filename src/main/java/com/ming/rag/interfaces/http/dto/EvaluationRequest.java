package com.ming.rag.interfaces.http.dto;

public record EvaluationRequest(
        String testSetPath,
        String collectionId,
        Integer topK
) {
}
