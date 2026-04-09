package com.ming.rag.application.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EvalCaseLoader {

    private final ObjectMapper objectMapper;

    public EvalCaseLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EvalTestSet load(String testSetPath) {
        try {
            var testSet = objectMapper.readValue(Path.of(testSetPath).toFile(), EvalTestSet.class);
            validate(testSet);
            return testSet;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load evaluation test set", exception);
        }
    }

    private void validate(EvalTestSet testSet) {
        if (testSet == null || testSet.version() == null || testSet.version().isBlank()) {
            throw new IllegalStateException("Evaluation test set must declare version");
        }
        if (!"v1".equalsIgnoreCase(testSet.version())) {
            throw new IllegalStateException("Unsupported evaluation schema version: " + testSet.version());
        }
        if (testSet.cases() == null || testSet.cases().isEmpty()) {
            throw new IllegalStateException("Evaluation test set cases must not be empty");
        }
        var seenCaseIds = new java.util.HashSet<String>();
        for (var testCase : testSet.cases()) {
            if (testCase.caseId() == null || testCase.caseId().isBlank()) {
                throw new IllegalStateException("Evaluation caseId must not be blank");
            }
            if (!seenCaseIds.add(testCase.caseId())) {
                throw new IllegalStateException("Duplicate evaluation caseId: " + testCase.caseId());
            }
            if (testCase.query() == null || testCase.query().isBlank()) {
                throw new IllegalStateException("Evaluation query must not be blank");
            }
            if (testCase.expectedChunkIds() == null || testCase.expectedChunkIds().isEmpty()) {
                throw new IllegalStateException("Evaluation expectedChunkIds must not be empty");
            }
            if (testCase.expectedChunkIds().stream().anyMatch(id -> id == null || id.isBlank())) {
                throw new IllegalStateException("Evaluation expectedChunkIds must not contain blank values");
            }
        }
    }

    public record EvalTestSet(
            String version,
            List<EvalCase> cases
    ) {
    }

    public record EvalCase(
            String caseId,
            String query,
            List<String> expectedChunkIds,
            List<String> expectedSources,
            String referenceAnswer
    ) {
    }
}
