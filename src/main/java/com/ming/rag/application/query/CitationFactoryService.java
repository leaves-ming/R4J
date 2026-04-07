package com.ming.rag.application.query;

import com.ming.rag.domain.query.RankedResult;
import com.ming.rag.domain.response.Citation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CitationFactoryService {

    public List<Citation> from(List<RankedResult> rankedResults) {
        List<Citation> citations = new ArrayList<>();
        for (int i = 0; i < rankedResults.size(); i++) {
            var result = rankedResults.get(i);
            var metadata = result.metadata();
            citations.add(new Citation(
                    i + 1,
                    result.chunkId(),
                    String.valueOf(metadata.getOrDefault("document_id", "")),
                    String.valueOf(metadata.getOrDefault("source_path", "")),
                    pageOf(metadata),
                    result.score(),
                    snippetOf(result.content()),
                    filterMetadata(metadata)
            ));
        }
        return List.copyOf(citations);
    }

    private Integer pageOf(Map<String, Object> metadata) {
        Object page = metadata.getOrDefault("page_num", metadata.get("page"));
        if (page instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private String snippetOf(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 200 ? content : content.substring(0, 200);
    }

    private Map<String, Object> filterMetadata(Map<String, Object> metadata) {
        var filtered = new java.util.LinkedHashMap<String, Object>();
        for (String key : List.of("title", "section", "chunk_index", "doc_type")) {
            if (metadata.containsKey(key)) {
                filtered.put(key, metadata.get(key));
            }
        }
        return Map.copyOf(filtered);
    }
}
