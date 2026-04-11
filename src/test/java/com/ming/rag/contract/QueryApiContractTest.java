package com.ming.rag.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ming.rag.bootstrap.RagApplication;
import com.ming.rag.integration.support.IntegrationTestContainers;
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
        "rag.storage.file.base-path=target/test-query-contract-files",
        "rag.storage.search.initialize-index-on-startup=true",
        "rag.storage.search.dev-fallback-enabled=false",
        "rag.rerank.enabled=true",
        "rag.rerank.provider=llm",
        "rag.ai.chat.provider=none"
})
class QueryApiContractTest extends IntegrationTestContainers {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void loadDocument() throws Exception {
        var file = new MockMultipartFile(
                "file",
                "query.md",
                "text/markdown",
                "# Query Guide\nhybrid retrieval combines semantic and keyword matching".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/v1/ingestions").file(file).param("collectionId", "default"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldMatchQueryResponseContract() throws Exception {
        mockMvc.perform(post("/api/v1/queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "hybrid retrieval",
                                  "collectionId": "default",
                                  "denseTopK": 10,
                                  "sparseTopK": 10,
                                  "fusionTopK": 10,
                                  "rerankTopK": 5,
                                  "debug": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.empty").value(false))
                .andExpect(jsonPath("$.answer").isString())
                .andExpect(jsonPath("$.citations[0].chunkId").isString())
                .andExpect(jsonPath("$.citations[0].documentId").isString())
                .andExpect(jsonPath("$.toolSources").isArray())
                .andExpect(jsonPath("$.debug.advisorRoute").value("never"))
                .andExpect(jsonPath("$.debug.mcpInvoked").value(false))
                .andExpect(jsonPath("$.traceId").isString());
    }

    @Test
    void shouldExposePartialFallbackWhenSinglePathFails() throws Exception {
        mockMvc.perform(post("/api/v1/queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "faildense hybrid retrieval",
                                  "collectionId": "default",
                                  "denseTopK": 10,
                                  "sparseTopK": 10,
                                  "fusionTopK": 10,
                                  "rerankTopK": 5,
                                  "debug": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empty").value(false))
                .andExpect(jsonPath("$.debug.partialFallback").value(true))
                .andExpect(jsonPath("$.debug.denseFailure").isString())
                .andExpect(jsonPath("$.debug.rerankApplied").value(true));
    }

    @Test
    void shouldReturnRetrievalFailedWhenBothPathsFail() throws Exception {
        mockMvc.perform(post("/api/v1/queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "faildense failsparse hybrid retrieval",
                                  "collectionId": "default",
                                  "debug": true
                                }
                                """))
                .andExpect(status().is5xxServerError())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.errorCode").value("RETRIEVAL_FAILED"))
                .andExpect(jsonPath("$.traceId").isString());
    }
}
