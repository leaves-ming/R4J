package com.ming.rag.application.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.rag.bootstrap.config.RagProperties;
import com.ming.rag.application.query.QueryCommand;
import com.ming.rag.application.query.RetrievalPipelineService;
import com.ming.rag.domain.evaluation.EvalQueryResult;
import com.ming.rag.domain.evaluation.EvalReport;
import com.ming.rag.domain.evaluation.port.EvaluationReportPort;
import com.ming.rag.domain.response.port.AnswerGeneratorPort;
import com.ming.rag.observability.TraceContextAccessor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EvaluationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationApplicationService.class);

    private final RetrievalPipelineService retrievalPipelineService;
    private final AnswerGeneratorPort answerGeneratorPort;
    private final EvaluationReportPort evaluationReportPort;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final TraceContextAccessor traceContextAccessor;

    public EvaluationApplicationService(
            RetrievalPipelineService retrievalPipelineService,
            AnswerGeneratorPort answerGeneratorPort,
            EvaluationReportPort evaluationReportPort,
            RagProperties ragProperties,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            TraceContextAccessor traceContextAccessor
    ) {
        this.retrievalPipelineService = retrievalPipelineService;
        this.answerGeneratorPort = answerGeneratorPort;
        this.evaluationReportPort = evaluationReportPort;
        this.ragProperties = ragProperties;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.traceContextAccessor = traceContextAccessor;
    }

    public EvalReport evaluate(EvaluationCommand command) {
        return Timer.builder("rag.evaluation.duration").register(meterRegistry).record(() -> doEvaluate(command));
    }

    private EvalReport doEvaluate(EvaluationCommand command) {
        var traceId = traceContextAccessor.currentTraceId();
        var started = System.nanoTime();
        var collectionId = command.collectionId() == null || command.collectionId().isBlank() ? "default" : command.collectionId();
        var topK = command.topK() == null || command.topK() <= 0 ? ragProperties.evaluation().defaultTopK() : command.topK();
        meterRegistry.counter("rag.evaluation.run.total", "collectionId", collectionId).increment();
        log.info("evaluation started traceId={} collectionId={} stage=evaluation_load_test_set", traceId, collectionId);

        var testCases = loadTestCases(command.testSetPath());
        var queryResults = new ArrayList<EvalQueryResult>();
        double hitCount = 0;
        double reciprocalRankSum = 0;
        double answerPresenceCount = 0;

        for (int index = 0; index < testCases.size(); index++) {
            var testCase = testCases.get(index);
            var queryStarted = System.nanoTime();
            var retrievalResult = retrievalPipelineService.retrieve(new QueryCommand(
                    testCase.query(),
                    collectionId,
                    Map.of(),
                    topK,
                    topK,
                    topK,
                    topK,
                    false
            ));
            var generated = answerGeneratorPort.generate(testCase.query(), retrievalResult.topKResults(), retrievalResult.traceId(), false);
            var retrievedIds = retrievalResult.topKResults().stream().map(item -> item.chunkId()).toList();
            var metrics = score(testCase, retrievedIds, generated.answer());
            hitCount += ((Number) metrics.get("hit_rate")).doubleValue();
            reciprocalRankSum += ((Number) metrics.get("mrr")).doubleValue();
            answerPresenceCount += ((Number) metrics.get("answer_presence")).doubleValue();
            queryResults.add(new EvalQueryResult(
                    testCase.query(),
                    retrievedIds,
                    generated.answer(),
                    metrics,
                    elapsedMs(queryStarted)
            ));
        }

        var totalElapsedMs = elapsedMs(started);
        var aggregateMetrics = new LinkedHashMap<String, Object>();
        var totalCases = Math.max(1, queryResults.size());
        aggregateMetrics.put("hit_rate@" + topK, hitCount / totalCases);
        aggregateMetrics.put("mrr@" + topK, reciprocalRankSum / totalCases);
        aggregateMetrics.put("answer_presence", answerPresenceCount / totalCases);

        var report = new EvalReport(
                UUID.randomUUID().toString(),
                ragProperties.evaluation().evaluatorName(),
                command.testSetPath(),
                totalElapsedMs,
                Map.copyOf(aggregateMetrics),
                List.copyOf(queryResults)
        );
        evaluationReportPort.save(report, collectionId, totalElapsedMs);
        log.info("evaluation completed traceId={} collectionId={} stage=evaluation_aggregate totalCases={}", traceId, collectionId, queryResults.size());
        return report;
    }

    private List<EvalCase> loadTestCases(String testSetPath) {
        try {
            return objectMapper.readValue(Path.of(testSetPath).toFile(), new TypeReference<List<EvalCase>>() {
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load evaluation test set", exception);
        }
    }

    private Map<String, Object> score(EvalCase testCase, List<String> retrievedChunkIds, String generatedAnswer) {
        var metrics = new LinkedHashMap<String, Object>();
        double hitRate = testCase.expectedChunkIds().stream().anyMatch(retrievedChunkIds::contains) ? 1.0d : 0.0d;
        metrics.put("hit_rate", hitRate);

        double mrr = 0.0d;
        for (int i = 0; i < retrievedChunkIds.size(); i++) {
            if (testCase.expectedChunkIds().contains(retrievedChunkIds.get(i))) {
                mrr = 1.0d / (i + 1);
                break;
            }
        }
        metrics.put("mrr", mrr);
        metrics.put("answer_presence", generatedAnswer == null || generatedAnswer.isBlank() ? 0.0d : 1.0d);
        return Map.copyOf(metrics);
    }

    private double elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000.0d;
    }

    private record EvalCase(
            String query,
            List<String> expectedChunkIds,
            List<String> expectedSources,
            String referenceAnswer
    ) {
    }
}
