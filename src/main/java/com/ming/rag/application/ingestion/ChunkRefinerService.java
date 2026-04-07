package com.ming.rag.application.ingestion;

import com.ming.rag.domain.ingestion.Chunk;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ChunkRefinerService {

    private static final Pattern HTML_COMMENTS = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern HTML_TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern HORIZONTAL_RULES = Pattern.compile("(?m)^[-=_]{3,}\\s*$");
    private static final Pattern MULTI_SPACES = Pattern.compile("[ \\t]{2,}");
    private static final Pattern MANY_BLANKS = Pattern.compile("\\n{3,}");

    public List<Chunk> refine(List<Chunk> chunks) {
        return chunks.stream()
                .map(this::refineChunk)
                .toList();
    }

    private Chunk refineChunk(Chunk chunk) {
        var refined = chunk.content()
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        refined = HTML_COMMENTS.matcher(refined).replaceAll("");
        refined = HORIZONTAL_RULES.matcher(refined).replaceAll("");
        refined = HTML_TAGS.matcher(refined).replaceAll("");
        refined = MULTI_SPACES.matcher(refined).replaceAll(" ");
        refined = MANY_BLANKS.matcher(refined).replaceAll("\n\n");
        refined = refined.trim();

        var metadata = new java.util.HashMap<String, Object>(chunk.metadata());
        metadata.put("refined_by", "rule");
        return new Chunk(chunk.chunkId(), chunk.documentId(), chunk.collectionId(), chunk.chunkIndex(), refined, Map.copyOf(metadata));
    }
}
