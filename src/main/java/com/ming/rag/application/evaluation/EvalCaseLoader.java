package com.ming.rag.application.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;
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
        if (!"1.0".equalsIgnoreCase(testSet.version())) {
            throw new IllegalStateException("Unsupported evaluation schema version: " + testSet.version());
        }
        if (testSet.description() == null || testSet.description().isBlank()) {
            throw new IllegalStateException("Evaluation test set must declare description");
        }
        if (testSet.testCases() == null || testSet.testCases().isEmpty()) {
            throw new IllegalStateException("Evaluation test set test_cases must not be empty");
        }
        var seenCaseIds = new java.util.HashSet<String>();
        for (int index = 0; index < testSet.testCases().size(); index++) {
            var testCase = testSet.testCases().get(index);
            var caseId = testCase.caseId() == null || testCase.caseId().isBlank() ? "case-" + index : testCase.caseId();
            if (!seenCaseIds.add(caseId)) {
                throw new IllegalStateException("Duplicate evaluation caseId: " + caseId);
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
            String description,
            String version,
            @JsonProperty("test_cases")
            List<EvalCase> testCases
    ) {
        public List<EvalCase> cases() {
            return testCases;
        }
    }

    public record EvalCase(
            String caseId,
            String query,
            @JsonProperty("expected_chunk_ids")
            List<String> expectedChunkIds,
            @JsonProperty("expected_sources")
            List<String> expectedSources,
            @JsonProperty("reference_answer")
            String referenceAnswer
    ) {
    }
}
