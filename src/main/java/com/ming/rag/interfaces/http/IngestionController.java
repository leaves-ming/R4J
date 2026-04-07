package com.ming.rag.interfaces.http;

import com.ming.rag.application.ingestion.IngestionApplicationService;
import com.ming.rag.application.ingestion.IngestionCommand;
import com.ming.rag.interfaces.http.dto.IngestionResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ingestions")
public class IngestionController {

    private final IngestionApplicationService ingestionApplicationService;

    public IngestionController(IngestionApplicationService ingestionApplicationService) {
        this.ingestionApplicationService = ingestionApplicationService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IngestionResponse ingest(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "collectionId", defaultValue = "default") String collectionId,
            @RequestParam(value = "forceReingest", defaultValue = "false") boolean forceReingest,
            @RequestParam(value = "chunkSize", required = false) Integer chunkSize,
            @RequestParam(value = "chunkOverlap", required = false) Integer chunkOverlap
    ) throws IOException {
        var result = ingestionApplicationService.ingest(new IngestionCommand(
                collectionId,
                file.getOriginalFilename(),
                file.getContentType() == null ? MediaType.TEXT_PLAIN_VALUE : file.getContentType(),
                file.getBytes(),
                forceReingest,
                chunkSize,
                chunkOverlap
        ));
        return new IngestionResponse(
                result.jobId(),
                result.documentId(),
                result.status(),
                result.skipped(),
                result.chunkCount(),
                result.traceId()
        );
    }
}
