package com.ming.rag.unit.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ming.rag.application.ingestion.IngestionStateMachine;
import com.ming.rag.domain.common.exception.ConflictException;
import com.ming.rag.domain.ingestion.DocumentStatus;
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
}
