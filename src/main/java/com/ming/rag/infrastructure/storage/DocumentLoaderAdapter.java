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

    private final RagProperties ragProperties;

    public DocumentLoaderAdapter(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    @Override
    public SourceDocument storeAndPrepare(SourceDocument sourceDocument, byte[] fileBytes) {
        try {
            var basePath = Path.of(ragProperties.storage().file().basePath());
            Files.createDirectories(basePath);
            var targetFile = basePath.resolve(sourceDocument.documentId() + "-" + sourceDocument.originalFileName());
            Files.write(targetFile, fileBytes);
            return new SourceDocument(
                    sourceDocument.documentId(),
                    sourceDocument.collectionId(),
                    targetFile.toString(),
                    sourceDocument.originalFileName(),
                    sourceDocument.mediaType()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist uploaded file", exception);
        }
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
