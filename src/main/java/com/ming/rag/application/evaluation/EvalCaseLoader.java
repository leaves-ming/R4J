package com.ming.rag.application.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
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

    public List<EvalCase> load(String testSetPath) {
        try {
            return objectMapper.readValue(Path.of(testSetPath).toFile(), new TypeReference<List<EvalCase>>() {
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load evaluation test set", exception);
        }
    }

    public record EvalCase(
            String query,
            List<String> expectedChunkIds,
            List<String> expectedSources,
            String referenceAnswer
    ) {
    }
}
