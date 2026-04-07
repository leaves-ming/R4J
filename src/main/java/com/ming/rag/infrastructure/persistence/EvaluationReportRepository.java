package com.ming.rag.infrastructure.persistence;

import com.ming.rag.domain.evaluation.EvalReport;
import com.ming.rag.domain.evaluation.port.EvaluationReportPort;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class EvaluationReportRepository implements EvaluationReportPort {

    private final Map<String, EvalReport> reports = new ConcurrentHashMap<>();

    @Override
    public void save(EvalReport report, String collectionId, double totalElapsedMs) {
        reports.put(key(collectionId, report.runId()), report);
    }

    public EvalReport findByRunId(String collectionId, String runId) {
        return reports.get(key(collectionId, runId));
    }

    private String key(String collectionId, String runId) {
        return collectionId + "::" + runId;
    }
}
