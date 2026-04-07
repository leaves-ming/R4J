package com.ming.rag.domain.evaluation.port;

import com.ming.rag.domain.evaluation.EvalReport;

public interface EvaluationReportPort {

    void save(EvalReport report, String collectionId, double totalElapsedMs);
}
