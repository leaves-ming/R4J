package com.ming.rag.infrastructure.persistence;

import com.ming.rag.domain.evaluation.EvalReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.rag.domain.evaluation.port.EvaluationReportPort;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class EvaluationReportRepository implements EvaluationReportPort {

    private static final String INSERT_RUN = """
            INSERT INTO evaluation_run (run_id, evaluator_name, test_set_path, collection_id, aggregate_metrics_json, total_elapsed_ms, created_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (run_id) DO UPDATE SET
                aggregate_metrics_json = EXCLUDED.aggregate_metrics_json,
                total_elapsed_ms = EXCLUDED.total_elapsed_ms
            """;
    private static final String INSERT_CASE = """
            INSERT INTO evaluation_case_result (run_id, query_index, query, retrieved_top_k_chunk_ids_json, generated_answer, metrics_json, elapsed_ms)
            VALUES (?, ?, ?, ?::jsonb, ?, ?::jsonb, ?)
            ON CONFLICT (run_id, query_index) DO UPDATE SET
                retrieved_top_k_chunk_ids_json = EXCLUDED.retrieved_top_k_chunk_ids_json,
                generated_answer = EXCLUDED.generated_answer,
                metrics_json = EXCLUDED.metrics_json,
                elapsed_ms = EXCLUDED.elapsed_ms
            """;
    private static final String SELECT_RUN = """
            SELECT run_id, evaluator_name, test_set_path, aggregate_metrics_json, total_elapsed_ms
              FROM evaluation_run
             WHERE run_id = ? AND collection_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, EvalReport> reports = new ConcurrentHashMap<>();

    public EvaluationReportRepository(ObjectProvider<JdbcTemplate> jdbcTemplateProvider, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(EvalReport report, String collectionId, double totalElapsedMs) {
        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.update(
                        INSERT_RUN,
                        java.util.UUID.fromString(report.runId()),
                        report.evaluatorName(),
                        report.testSetPath(),
                        collectionId,
                        objectMapper.writeValueAsString(report.aggregateMetrics()),
                        totalElapsedMs
                );
                for (int i = 0; i < report.queryResults().size(); i++) {
                    var item = report.queryResults().get(i);
                    jdbcTemplate.update(
                            INSERT_CASE,
                            java.util.UUID.fromString(report.runId()),
                            i,
                            item.query(),
                            objectMapper.writeValueAsString(item.retrievedTopKChunkIds()),
                            item.generatedAnswer(),
                            objectMapper.writeValueAsString(item.metrics()),
                            item.elapsedMs()
                    );
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to persist evaluation report", exception);
            }
        }
        reports.put(key(collectionId, report.runId()), report);
    }

    public EvalReport findByRunId(String collectionId, String runId) {
        if (jdbcTemplate != null) {
            var reports = jdbcTemplate.query(SELECT_RUN, this::mapRun, java.util.UUID.fromString(runId), collectionId);
            if (!reports.isEmpty()) {
                return reports.getFirst();
            }
        }
        return reports.get(key(collectionId, runId));
    }

    @SuppressWarnings("unchecked")
    private EvalReport mapRun(ResultSet rs, int rowNum) throws java.sql.SQLException {
        try {
            return new EvalReport(
                    rs.getObject("run_id", java.util.UUID.class).toString(),
                    rs.getString("evaluator_name"),
                    rs.getString("test_set_path"),
                    "v1",
                    rs.getDouble("total_elapsed_ms"),
                    objectMapper.readValue(rs.getString("aggregate_metrics_json"), Map.class),
                    java.util.List.of()
            );
        } catch (Exception exception) {
            throw new java.sql.SQLException("Failed to map evaluation run", exception);
        }
    }

    private String key(String collectionId, String runId) {
        return collectionId + "::" + runId;
    }
}
