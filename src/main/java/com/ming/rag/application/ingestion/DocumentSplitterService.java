package com.ming.rag.application.ingestion;

import com.ming.rag.bootstrap.config.RagProperties;
import com.ming.rag.domain.ingestion.Chunk;
import com.ming.rag.domain.ingestion.ParsedDocument;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DocumentSplitterService {

    private final RagProperties ragProperties;

    public DocumentSplitterService(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public List<Chunk> split(ParsedDocument parsedDocument, Integer chunkSizeOverride, Integer chunkOverlapOverride) {
        var chunkSize = chunkSizeOverride != null ? chunkSizeOverride : ragProperties.ingestion().chunkSize();
        var chunkOverlap = chunkOverlapOverride != null ? chunkOverlapOverride : ragProperties.ingestion().chunkOverlap();
        var text = parsedDocument.content() == null ? "" : parsedDocument.content();
        if (text.isBlank()) {
            return List.of();
        }

        var chunks = new ArrayList<Chunk>();
        var step = Math.max(1, chunkSize - Math.max(0, chunkOverlap));
        var index = 0;
        for (int start = 0; start < text.length(); start += step) {
            var end = Math.min(text.length(), start + chunkSize);
            var metadata = new HashMap<String, Object>(parsedDocument.metadata());
            metadata.put("chunk_index", index);
            metadata.put("source_ref", parsedDocument.documentId());
            chunks.add(new Chunk(
                    null,
                    parsedDocument.documentId(),
                    parsedDocument.collectionId(),
                    index,
                    text.substring(start, end),
                    metadata
            ));
            if (end == text.length()) {
                break;
            }
            index++;
        }
        return chunks;
    }
}
