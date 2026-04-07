package com.ming.rag.unit.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.rag.application.evaluation.EvaluationApplicationService;
import com.ming.rag.application.evaluation.EvaluationCommand;
import com.ming.rag.application.query.RetrievalPipelineService;
import com.ming.rag.bootstrap.config.RagProperties;
import com.ming.rag.domain.evaluation.EvalReport;
import com.ming.rag.domain.query.ProcessedQuery;
import com.ming.rag.domain.query.RankedResult;
import com.ming.rag.domain.query.RetrievalResult;
import com.ming.rag.domain.response.AnswerResponse;
import com.ming.rag.domain.response.Citation;
import com.ming.rag.domain.response.port.AnswerGeneratorPort;
import com.ming.rag.infrastructure.persistence.EvaluationReportRepository;
import com.ming.rag.observability.TraceContextAccessor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EvaluationUsesRetrievalTopKTest {

    @Test
    void shouldPersistRetrievedTopKChunkIdsInsteadOfDisplayCitations() {
        RetrievalPipelineService retrievalPipelineService = Mockito.mock(RetrievalPipelineService.class);
        Mockito.when(retrievalPipelineService.retrieve(Mockito.any())).thenReturn(new RetrievalResult(
                new ProcessedQuery("q", "q", List.of("q"), Map.of()),
                List.of(new RankedResult("chunk-topk", 0.9, 1, "content", Map.of("document_id", "doc-1", "source_path", "source.md"))),
                false,
                "trace",
                Map.of()
        ));
        AnswerGeneratorPort answerGeneratorPort = (query, rankedResults, traceId, debug) -> new AnswerResponse(
                false,
                "answer",
                List.of(new Citation(1, "chunk-citation", "doc-2", "other.md", null, 0.5, "snippet", Map.of())),
                traceId,
                Map.of()
        );

        var service = new EvaluationApplicationService(
                retrievalPipelineService,
                answerGeneratorPort,
                new EvaluationReportRepository(),
                properties(),
                new ObjectMapper(),
                new SimpleMeterRegistry(),
                Mockito.mock(TraceContextAccessor.class)
        );
        var testSet = Paths.get("target", "eval-unit.json");
        try {
            java.nio.file.Files.writeString(testSet, """
                    [
                      {
                        "query": "q",
                        "expectedChunkIds": ["chunk-topk"],
                        "expectedSources": ["source.md"],
                        "referenceAnswer": "answer"
                      }
                    ]
                    """);
        } catch (java.io.IOException exception) {
            throw new RuntimeException(exception);
        }

        EvalReport report = service.evaluate(new EvaluationCommand(testSet.toString(), "default", 10));

        assertThat(report.queryResults().getFirst().retrievedTopKChunkIds()).containsExactly("chunk-topk");
    }

    private RagProperties properties() {
        return new RagProperties(
                new RagProperties.Ingestion(1000, 200, 100, 5),
                new RagProperties.Query(20, 20, 10, 60),
                new RagProperties.Rerank(false, "none", 5),
                new RagProperties.Ai(
                        new RagProperties.Model("none", null, null, null),
                        new RagProperties.Model("none", null, null, null)
                ),
                new RagProperties.Storage(
                        new RagProperties.Metadata("jdbc:postgresql://localhost:5432/rag", "rag", "rag"),
                        new RagProperties.Search("http://localhost:9200", "rag_chunks", false),
                        new RagProperties.File("./data/files")
                ),
                new RagProperties.Observability(true, true),
                new RagProperties.Evaluation(10, "default-evaluator")
        );
    }
}
