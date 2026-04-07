package com.ming.rag.domain.ingestion.port;

import com.ming.rag.domain.ingestion.ParsedDocument;
import com.ming.rag.domain.ingestion.SourceDocument;

public interface DocumentLoaderPort {

    SourceDocument storeAndPrepare(SourceDocument sourceDocument, byte[] fileBytes);

    ParsedDocument load(SourceDocument sourceDocument);
}
