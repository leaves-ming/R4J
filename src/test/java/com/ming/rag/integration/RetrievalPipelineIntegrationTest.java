package com.ming.rag.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ming.rag.application.ingestion.IngestionApplicationService;
import com.ming.rag.application.ingestion.IngestionCommand;
import com.ming.rag.application.query.QueryCommand;
import com.ming.rag.application.query.RetrievalPipelineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = com.ming.rag.bootstrap.RagApplication.class)
@TestPropertySource(properties = {
        "rag.storage.file.base-path=target/test-retrieval-files",
        "rag.storage.search.initialize-index-on-startup=false"
})
class RetrievalPipelineIntegrationTest {

    @Autowired
    private IngestionApplicationService ingestionApplicationService;

    @Autowired
    private RetrievalPipelineService retrievalPipelineService;

    @Test
    void shouldFallbackToSinglePathWhenDenseFails() {
        ingest("# Hybrid Search\nhybrid retrieval combines semantic and keyword matching");

        var result = retrievalPipelineService.retrieve(new QueryCommand(
                "faildense hybrid retrieval",
                "default",
                java.util.Map.of(),
                10,
                10,
                10,
                5,
                true
        ));

        assertThat(result.partialFallback()).isTrue();
        assertThat(result.topKResults()).isNotEmpty();
        assertThat(result.debug()).containsKey("dense_failure");
    }

    @Test
    void shouldReturnEmptyResultWhenNoChunkMatches() {
        ingest("# Existing Doc\nthis content is about ingestion only");

        var result = retrievalPipelineService.retrieve(new QueryCommand(
                "unmatched astronomy topic",
                "default",
                java.util.Map.of(),
                10,
                10,
                10,
                5,
                false
        ));

        assertThat(result.partialFallback()).isFalse();
        assertThat(result.topKResults()).isEmpty();
    }

    @Test
    void shouldOnlyReadReadyVisibleChunksFromStore() {
        ingest("# Visible Doc\nsemantic retrieval answer");

        var result = retrievalPipelineService.retrieve(new QueryCommand(
                "semantic retrieval",
                "default",
                java.util.Map.of(),
                10,
                10,
                10,
                5,
                false
        ));

        assertThat(result.topKResults()).allMatch(item -> item.metadata().containsKey("document_id"));
    }

    private void ingest(String content) {
        ingestionApplicationService.ingest(new IngestionCommand(
                "default",
                "retrieval.md",
                "text/markdown",
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                false,
                128,
                0
        ));
    }
}
