package com.ming.rag.application.ingestion;

import com.ming.rag.domain.common.exception.ConflictException;
import com.ming.rag.domain.ingestion.DocumentStatus;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class IngestionStateMachine {

    private static final Map<DocumentStatus, Set<DocumentStatus>> ALLOWED_TRANSITIONS = Map.of(
            DocumentStatus.RECEIVED, EnumSet.of(DocumentStatus.PROCESSING),
            DocumentStatus.PROCESSING, EnumSet.of(DocumentStatus.READY, DocumentStatus.FAILED),
            DocumentStatus.FAILED, EnumSet.of(DocumentStatus.PROCESSING),
            DocumentStatus.READY, EnumSet.noneOf(DocumentStatus.class)
    );

    public DocumentStatus transition(DocumentStatus currentStatus, DocumentStatus nextStatus) {
        if (currentStatus == null || nextStatus == null) {
            throw new IllegalArgumentException("currentStatus and nextStatus must not be null");
        }

        var allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(nextStatus)) {
            throw new ConflictException(
                    "Invalid ingestion state transition",
                    Map.of("currentStatus", currentStatus.name(), "nextStatus", nextStatus.name())
            );
        }
        return nextStatus;
    }

    public boolean shouldSkip(DocumentStatus status, boolean forceReingest) {
        return !forceReingest && status == DocumentStatus.READY;
    }
}
