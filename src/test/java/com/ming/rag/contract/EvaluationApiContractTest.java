package com.ming.rag.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ming.rag.bootstrap.RagApplication;
import com.ming.rag.integration.support.IntegrationTestContainers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = RagApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rag.storage.file.base-path=target/test-eval-contract-files",
        "rag.storage.search.initialize-index-on-startup=true",
        "rag.storage.search.dev-fallback-enabled=false"
})
class EvaluationApiContractTest extends IntegrationTestContainers {

    @Autowired
    private MockMvc mockMvc;

    private String testSetPath;

    @BeforeEach
    void seed() throws Exception {
        var body = "# Eval Contract\nhybrid retrieval combines semantic and keyword matching";
        var file = new MockMultipartFile("file", "eval-contract.md", "text/markdown", body.getBytes(StandardCharsets.UTF_8));
        var documentId = new com.ming.rag.domain.common.DocumentIdPolicy().generate(body.getBytes(StandardCharsets.UTF_8)).value();
        var chunkId = new com.ming.rag.domain.common.ChunkIdPolicy().generate(documentId, 0, body).value();
        mockMvc.perform(multipart("/api/v1/ingestions").file(file).param("collectionId", "default"))
                .andExpect(status().isOk());

        var path = Path.of("target/test-eval-contract-files/golden-set.json");
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
                {
                  "version": "v1",
                  "cases": [
                    {
                      "caseId": "eval-contract-1",
                      "query": "hybrid retrieval",
                      "expectedChunkIds": ["%s"],
                      "expectedSources": ["target/test-eval-contract-files/eval-contract.md"],
                      "referenceAnswer": "hybrid retrieval combines semantic and keyword matching"
                    }
                  ]
                }
                """.formatted(chunkId));
        testSetPath = path.toString();
    }

    @Test
    void shouldMatchEvaluationResponseContract() throws Exception {
        mockMvc.perform(post("/api/v1/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "testSetPath": "%s",
                                  "collectionId": "default",
                                  "topK": 10
                                }
                                """.formatted(testSetPath)))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.runId").isString())
                .andExpect(jsonPath("$.evaluatorName").value("default-evaluator"))
                .andExpect(jsonPath("$.testSetPath").value(testSetPath))
                .andExpect(jsonPath("$.schemaVersion").value("v1"))
                .andExpect(jsonPath("$.totalElapsedMs").isNumber())
                .andExpect(jsonPath("$.aggregateMetrics").exists())
                .andExpect(jsonPath("$.queryResults[0].caseId").value("eval-contract-1"))
                .andExpect(jsonPath("$.queryResults[0].query").isString());
    }

    @Test
    void shouldRejectInvalidEvaluationSchema() throws Exception {
        var path = Path.of("target/test-eval-contract-files/invalid-golden-set.json");
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
                {
                  "version": "v1",
                  "cases": []
                }
                """);

        mockMvc.perform(post("/api/v1/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "testSetPath": "%s",
                                  "collectionId": "default",
                                  "topK": 10
                                }
                                """.formatted(path)))
                .andExpect(status().is5xxServerError())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.errorCode").value("PROVIDER_FAILURE"))
                .andExpect(jsonPath("$.traceId").isString());
    }
}
