package com.ming.rag.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ming.rag.bootstrap.RagApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = RagApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rag.storage.file.base-path=target/test-contract-files",
        "rag.storage.search.initialize-index-on-startup=false"
})
class IngestionApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldMatchIngestionResponseContract() throws Exception {
        var file = new MockMultipartFile(
                "file",
                "guide.md",
                "text/markdown",
                "# Guide\nContract body".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/ingestions")
                        .file(file)
                        .param("collectionId", "default"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.jobId").isString())
                .andExpect(jsonPath("$.documentId").isString())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.skipped").value(false))
                .andExpect(jsonPath("$.chunkCount").isNumber())
                .andExpect(jsonPath("$.storage.metadata").value("postgresql"))
                .andExpect(jsonPath("$.storage.search").value("elasticsearch"))
                .andExpect(jsonPath("$.traceId").isString());
    }
}
