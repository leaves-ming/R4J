package com.ming.rag.unit.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.ming.rag.application.query.RrfFusionPolicy;
import com.ming.rag.bootstrap.config.RagProperties;
import com.ming.rag.domain.query.RetrievalCandidate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RrfFusionPolicyTest {

    private final RrfFusionPolicy policy = new RrfFusionPolicy(new RagProperties(
            new RagProperties.Ingestion(1000, 200, 100, 5),
            new RagProperties.Query(20, 20, 10, 60),
            new RagProperties.Rerank(false, "none", 5),
            new RagProperties.Ai(
                    new RagProperties.Model("none", null, null, null),
                    new RagProperties.Model("none", null, null, null)
            ),
            new RagProperties.Storage(
                    new RagProperties.Metadata("jdbc:postgresql://localhost:5432/rag", "rag", "rag", true),
                    new RagProperties.Search("http://localhost:9200", "rag_chunks", false, true, false),
                    new RagProperties.File("./data/files")
            ),
            new RagProperties.Observability(true, true),
            new RagProperties.Evaluation(10, "default")
    ));

    @Test
    void shouldFuseByRankAndBreakTiesByChunkId() {
        var dense = List.of(
                candidate("chunk-b", 0.9),
                candidate("chunk-a", 0.8)
        );
        var sparse = List.of(
                candidate("chunk-a", 0.7),
                candidate("chunk-b", 0.6)
        );

        var results = policy.fuse(dense, sparse, 2);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).chunkId()).isEqualTo("chunk-a");
        assertThat(results.get(0).rank()).isEqualTo(1);
        assertThat(results.get(1).chunkId()).isEqualTo("chunk-b");
        assertThat(results.get(1).rank()).isEqualTo(2);
    }

    @Test
    void shouldPreserveFusionTopKLimit() {
        var dense = List.of(candidate("chunk-a", 0.9), candidate("chunk-b", 0.8), candidate("chunk-c", 0.7));
        var sparse = List.of(candidate("chunk-c", 0.9), candidate("chunk-b", 0.8), candidate("chunk-a", 0.7));

        var results = policy.fuse(dense, sparse, 2);

        assertThat(results).hasSize(2);
        assertThat(results).extracting("rank").containsExactly(1, 2);
    }

    private RetrievalCandidate candidate(String chunkId, double score) {
        return new RetrievalCandidate(chunkId, score, "dense", "content-" + chunkId, Map.of("source_path", chunkId + ".md"));
    }
}
