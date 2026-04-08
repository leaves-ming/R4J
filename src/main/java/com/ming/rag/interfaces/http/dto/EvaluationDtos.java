package com.ming.rag.interfaces.http.dto;

import java.util.List;
import java.util.Map;

public final class EvaluationDtos {

    private EvaluationDtos() {
    }

    public record EvaluationRequest(
            String testSetPath,
            String collectionId,
            Integer topK
    ) {
    }

    public record EvaluationQueryResult(
            String query,
            List<String> retrievedTopKChunkIds,
            String generatedAnswer,
            Map<String, Object> metrics,
            double elapsedMs
    ) {
    }

    public record EvaluationResponse(
            String runId,
            String evaluatorName,
            String testSetPath,
            double totalElapsedMs,
            Map<String, Object> aggregateMetrics,
            List<EvaluationQueryResult> queryResults
    ) {
    }
}
