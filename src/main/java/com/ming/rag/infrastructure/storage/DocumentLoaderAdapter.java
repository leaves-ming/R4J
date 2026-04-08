package com.ming.rag.infrastructure.storage;

import com.ming.rag.bootstrap.config.RagProperties;
import com.ming.rag.domain.ingestion.ParsedDocument;
import com.ming.rag.domain.ingestion.SourceDocument;
import com.ming.rag.domain.ingestion.port.DocumentLoaderPort;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DocumentLoaderAdapter implements DocumentLoaderPort {

    private final FileDocumentStore fileDocumentStore;

    public DocumentLoaderAdapter(FileDocumentStore fileDocumentStore) {
        this.fileDocumentStore = fileDocumentStore;
    }

    @Override
    public SourceDocument storeAndPrepare(SourceDocument sourceDocument, byte[] fileBytes) {
        var targetFile = fileDocumentStore.store(sourceDocument.documentId(), sourceDocument.originalFileName(), fileBytes);
        return new SourceDocument(
                sourceDocument.documentId(),
                sourceDocument.collectionId(),
                targetFile.toString(),
                sourceDocument.originalFileName(),
                sourceDocument.mediaType()
        );
    }

    @Override
    public ParsedDocument load(SourceDocument sourceDocument) {
        try {
            var content = Files.readString(Path.of(sourceDocument.sourcePath()), StandardCharsets.UTF_8);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source_path", sourceDocument.sourcePath());
            metadata.put("doc_type", sourceDocument.mediaType());
            return new ParsedDocument(sourceDocument.documentId(), sourceDocument.collectionId(), content, Map.copyOf(metadata));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read source document", exception);
        }
    }
}
