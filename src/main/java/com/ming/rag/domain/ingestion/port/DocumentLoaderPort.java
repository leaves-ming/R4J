package com.ming.rag.domain.ingestion.port;

import com.ming.rag.domain.ingestion.ParsedDocument;
import com.ming.rag.domain.ingestion.SourceDocument;

public interface DocumentLoaderPort {

    ParsedDocument load(SourceDocument sourceDocument);
}
