package com.ming.rag.unit.domain.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.ming.rag.domain.common.DocumentIdPolicy;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DocumentIdPolicyTest {

    private final DocumentIdPolicy policy = new DocumentIdPolicy();

    @Test
    void shouldGenerateStableSha256FromFileBytes() {
        var documentId = policy.generate("hello world".getBytes(StandardCharsets.UTF_8));

        assertThat(documentId.value())
                .isEqualTo("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
    }
}
