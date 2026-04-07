package com.ming.rag.unit.domain.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.ming.rag.domain.common.ChunkIdPolicy;
import org.junit.jupiter.api.Test;

class ChunkIdPolicyTest {

    private final ChunkIdPolicy policy = new ChunkIdPolicy();

    @Test
    void shouldGenerateChunkIdFromNormalizedCanonicalText() {
        var chunkId = policy.generate("doc_123", 1, "Line 1\r\nLine 2  ");

        assertThat(chunkId.value()).isEqualTo("doc_123_0001_9140ddc6");
    }
}
