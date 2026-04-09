package com.ming.rag.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ming.rag.application.ingestion.IngestionApplicationService;
import com.ming.rag.application.ingestion.IngestionCommand;
import com.ming.rag.bootstrap.RagApplication;
import com.ming.rag.integration.support.IntegrationTestContainers;
import com.ming.rag.infrastructure.persistence.DocumentRegistryRepository;
import com.ming.rag.infrastructure.search.SearchChunkStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = RagApplication.class)
@TestPropertySource(properties = {
        "rag.storage.file.base-path=target/test-ingestion-files",
        "rag.storage.search.initialize-index-on-startup=true",
        "rag.storage.search.dev-fallback-enabled=false"
})
class IngestionIntegrationTest extends IntegrationTestContainers {

    @Autowired
    private IngestionApplicationService ingestionApplicationService;

    @Autowired
    private DocumentRegistryRepository documentRegistryRepository;

    @Autowired
    private SearchChunkStore searchChunkStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanState() {
        jdbcTemplate.execute("DELETE FROM ingestion_job");
        jdbcTemplate.execute("DELETE FROM document_registry");
    }

    @Test
    void shouldSkipDuplicateReadyDocument() {
        var first = ingest("# Title\nHello integration world");
        var second = ingest("# Title\nHello integration world");

        assertThat(first.skipped()).isFalse();
        assertThat(second.skipped()).isTrue();
        assertThat(first.documentId()).isEqualTo(second.documentId());
        assertThat(searchChunkStore.countVisibleChunks("default", first.documentId())).isGreaterThan(0);
        assertThat(documentRegistryRepository.statusOf("default", first.documentId()))
                .isEqualTo(com.ming.rag.domain.ingestion.DocumentStatus.READY);
    }

    @Test
    void shouldCompensateChunksWhenMarkReadyFails() {
        documentRegistryRepository.failNextMarkReady();

        assertThatThrownBy(() -> ingest("this document fails on mark ready"))
                .hasMessageContaining("Ingestion failed");
    }

    @Test
    void shouldKeepFailedDocumentsInvisibleUntilReady() {
        documentRegistryRepository.failNextMarkReady();

        try {
            ingest("invisible while failed");
        } catch (RuntimeException ignored) {
        }

        var documentId = new com.ming.rag.domain.common.DocumentIdPolicy()
                .generate("invisible while failed".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .value();
        assertThat(documentRegistryRepository.statusOf("default", documentId))
                .isEqualTo(com.ming.rag.domain.ingestion.DocumentStatus.FAILED);
        assertThat(searchChunkStore.countVisibleChunks("default", documentId)).isZero();
    }

    @Test
    void shouldPersistRegistryAndChunkRecordsUsingRealInfrastructure() {
        var result = ingest("# Title\nHello postgres and elastic");

        var chunkCount = jdbcTemplate.queryForObject(
                "SELECT chunk_count FROM document_registry WHERE collection_id = ? AND document_id = ?",
                Integer.class,
                "default",
                result.documentId()
        );
        var readyAt = jdbcTemplate.queryForObject(
                "SELECT ready_at FROM document_registry WHERE collection_id = ? AND document_id = ?",
                java.time.OffsetDateTime.class,
                "default",
                result.documentId()
        );
        var records = searchChunkStore.findRecordsByCollection("default");

        assertThat(chunkCount).isEqualTo(result.chunkCount());
        assertThat(readyAt).isNotNull();
        assertThat(records).isNotEmpty();
        assertThat(records).allMatch(com.ming.rag.domain.ingestion.ChunkRecord::ready);
        assertThat(records)
                .filteredOn(record -> record.documentId().equals(result.documentId()))
                .allSatisfy(record -> {
                    assertThat(record.createdAt()).isNotNull();
                    assertThat(record.updatedAt()).isNotNull();
                    assertThat(record.metadata()).containsKeys("document_id", "chunk_index", "source_path");
                    assertThat(record.denseVector()).isNotEmpty();
                    assertThat(record.sparseTerms()).isNotEmpty();
                });
    }

    private com.ming.rag.application.ingestion.IngestionResult ingest(String content) {
        return ingestionApplicationService.ingest(new IngestionCommand(
                "default",
                "sample.md",
                "text/markdown",
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                false,
                32,
                0
        ));
    }
}
