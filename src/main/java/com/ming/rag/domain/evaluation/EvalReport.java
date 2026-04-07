package com.ming.rag.domain.evaluation;

import java.util.List;
import java.util.Map;

public record EvalReport(
        String runId,
        String evaluatorName,
        String testSetPath,
        Map<String, Object> aggregateMetrics,
        List<EvalQueryResult> queryResults
) {
}
