package com.ming.rag.unit.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ming.rag.application.ingestion.IngestionStateMachine;
import com.ming.rag.domain.ingestion.ChunkRecord;
import com.ming.rag.domain.common.exception.ConflictException;
import com.ming.rag.domain.ingestion.DocumentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IngestionStateMachineTest {

    private final IngestionStateMachine stateMachine = new IngestionStateMachine();

    @Test
    void shouldAllowHappyPathTransition() {
        var processing = stateMachine.transition(DocumentStatus.RECEIVED, DocumentStatus.PROCESSING);
        var ready = stateMachine.transition(processing, DocumentStatus.READY);

        assertThat(ready).isEqualTo(DocumentStatus.READY);
    }

    @Test
    void shouldAllowRetryAfterFailure() {
        var failed = stateMachine.transition(DocumentStatus.PROCESSING, DocumentStatus.FAILED);
        var processing = stateMachine.transition(failed, DocumentStatus.PROCESSING);

        assertThat(processing).isEqualTo(DocumentStatus.PROCESSING);
    }

    @Test
    void shouldRejectInvalidTransitionFromReadyBackToProcessing() {
        assertThatThrownBy(() -> stateMachine.transition(DocumentStatus.READY, DocumentStatus.PROCESSING))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldOnlySkipReadyDocumentsWhenForceReingestIsDisabled() {
        assertThat(stateMachine.shouldSkip(DocumentStatus.READY, false)).isTrue();
        assertThat(stateMachine.shouldSkip(DocumentStatus.FAILED, false)).isFalse();
        assertThat(stateMachine.shouldSkip(DocumentStatus.READY, true)).isFalse();
    }

    @Test
    void chunkRecordShouldExposeReadyRetrievalFields() {
        var now = Instant.now();
        var record = new ChunkRecord(
                "doc_001_0001_deadbeef",
                "doc_001",
                "default",
                1,
                "normalized text",
                Map.of("document_id", "doc_001", "chunk_index", 1, "source_path", "docs/sample.md"),
                List.of(0.1f, 0.2f, 0.3f),
                Map.of("normalized", 2),
                true,
                now,
                now
        );

        assertThat(record.ready()).isTrue();
        assertThat(record.metadata()).containsEntry("document_id", "doc_001");
        assertThat(record.metadata()).containsEntry("chunk_index", 1);
        assertThat(record.metadata()).containsKey("source_path");
        assertThat(record.denseVector()).hasSize(3);
        assertThat(record.sparseTerms()).containsEntry("normalized", 2);
    }
}
