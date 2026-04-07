package com.ming.rag.application.evaluation;

public record EvaluationCommand(
        String testSetPath,
        String collectionId,
        Integer topK
) {
}
