package com.ming.rag.application.ingestion;

import com.ming.rag.domain.ingestion.Chunk;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MetadataEnricherService {

    public List<Chunk> enrich(List<Chunk> chunks) {
        return chunks.stream().map(this::enrichChunk).toList();
    }

    private Chunk enrichChunk(Chunk chunk) {
        var content = chunk.content();
        var lines = content.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
        var title = lines.stream()
                .filter(line -> line.startsWith("#"))
                .map(line -> line.replaceFirst("^#+\\s*", ""))
                .findFirst()
                .orElseGet(() -> lines.isEmpty() ? "Untitled chunk" : truncate(lines.getFirst(), 80));
        var summary = truncate(content.replace('\n', ' '), 160);
        Set<String> tags = new LinkedHashSet<>();
        Arrays.stream(content.split("[^A-Za-z0-9_]+"))
                .map(token -> token.toLowerCase(Locale.ROOT))
                .filter(token -> token.length() >= 4)
                .limit(5)
                .forEach(tags::add);

        var metadata = new java.util.HashMap<String, Object>(chunk.metadata());
        metadata.put("title", title);
        metadata.put("summary", summary);
        metadata.put("tags", List.copyOf(tags));
        metadata.put("enriched_by", "rule");
        return new Chunk(chunk.chunkId(), chunk.documentId(), chunk.collectionId(), chunk.chunkIndex(), content, Map.copyOf(metadata));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
