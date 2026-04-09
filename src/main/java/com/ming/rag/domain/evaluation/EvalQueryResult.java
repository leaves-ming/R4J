package com.ming.rag.domain.evaluation;

import java.util.List;
import java.util.Map;

public record EvalQueryResult(
        String caseId,
        String query,
        List<String> retrievedTopKChunkIds,
        String generatedAnswer,
        Map<String, Object> metrics,
        double elapsedMs
) {
}
