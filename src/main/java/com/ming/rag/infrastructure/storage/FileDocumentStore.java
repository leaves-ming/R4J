package com.ming.rag.infrastructure.storage;

import com.ming.rag.bootstrap.config.RagProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class FileDocumentStore {

    private final RagProperties ragProperties;

    public FileDocumentStore(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public Path store(String documentId, String originalFileName, byte[] fileBytes) {
        try {
            var basePath = Path.of(ragProperties.storage().file().basePath());
            Files.createDirectories(basePath);
            var targetFile = basePath.resolve(documentId + "-" + originalFileName);
            Files.write(targetFile, fileBytes);
            return targetFile;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist uploaded file", exception);
        }
    }
}
