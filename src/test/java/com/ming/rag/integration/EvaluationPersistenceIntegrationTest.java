package com.ming.rag.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ming.rag.application.evaluation.EvaluationApplicationService;
import com.ming.rag.application.evaluation.EvaluationCommand;
import com.ming.rag.application.ingestion.IngestionApplicationService;
import com.ming.rag.application.ingestion.IngestionCommand;
import com.ming.rag.bootstrap.RagApplication;
import com.ming.rag.integration.support.IntegrationTestContainers;
import com.ming.rag.infrastructure.persistence.EvaluationReportRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = RagApplication.class)
@TestPropertySource(properties = {
        "rag.storage.file.base-path=target/test-eval-files",
        "rag.storage.search.initialize-index-on-startup=true",
        "rag.storage.search.dev-fallback-enabled=false"
})
class EvaluationPersistenceIntegrationTest extends IntegrationTestContainers {

    @Autowired
    private IngestionApplicationService ingestionApplicationService;

    @Autowired
    private EvaluationApplicationService evaluationApplicationService;

    @Autowired
    private EvaluationReportRepository evaluationReportRepository;

    @BeforeEach
    void seedDocument() {
        ingestionApplicationService.ingest(new IngestionCommand(
                "default",
                "eval-doc.md",
                "text/markdown",
                "# Eval Doc\nhybrid retrieval combines semantic matching and keyword matching".getBytes(StandardCharsets.UTF_8),
                false,
                256,
                0
        ));
    }

    @Test
    void shouldPersistEvaluationReportAndResults() throws Exception {
        var chunkId = new com.ming.rag.domain.common.ChunkIdPolicy().generate(
                new com.ming.rag.domain.common.DocumentIdPolicy().generate(
                        "# Eval Doc\nhybrid retrieval combines semantic matching and keyword matching".getBytes(StandardCharsets.UTF_8)
                ).value(),
                0,
                "# Eval Doc\nhybrid retrieval combines semantic matching and keyword matching"
        ).value();
        var testSetPath = Path.of("target/test-eval-files/golden-set.json");
        Files.createDirectories(testSetPath.getParent());
        Files.writeString(testSetPath, """
                {
                  "version": "v1",
                  "cases": [
                    {
                      "caseId": "eval-001",
                      "query": "hybrid retrieval",
                      "expectedChunkIds": ["%s"],
                      "expectedSources": ["target/test-eval-files/eval-doc.md"],
                      "referenceAnswer": "hybrid retrieval combines semantic matching and keyword matching"
                    }
                  ]
                }
                """.formatted(chunkId));

        var report = evaluationApplicationService.evaluate(new EvaluationCommand(testSetPath.toString(), "default", 10));

        assertThat(report.runId()).isNotBlank();
        assertThat(report.queryResults()).hasSize(1);
        assertThat(report.aggregateMetrics()).containsKey("hit_rate@10");
        assertThat(evaluationReportRepository.findByRunId("default", report.runId())).isNotNull();
    }
}
