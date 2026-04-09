package com.ming.rag.application.ingestion;

import com.ming.rag.domain.common.ChunkIdPolicy;
import com.ming.rag.domain.common.DocumentIdPolicy;
import com.ming.rag.domain.common.JobId;
import com.ming.rag.domain.common.exception.IngestionFailedException;
import com.ming.rag.domain.ingestion.Chunk;
import com.ming.rag.domain.ingestion.ChunkRecord;
import com.ming.rag.domain.ingestion.SourceDocument;
import com.ming.rag.domain.ingestion.port.ChunkStorePort;
import com.ming.rag.domain.ingestion.port.DocumentLoaderPort;
import com.ming.rag.domain.ingestion.port.DocumentRegistryPort;
import com.ming.rag.domain.ingestion.port.EmbeddingPort;
import com.ming.rag.domain.ingestion.port.LexicalEncodingPort;
import com.ming.rag.infrastructure.persistence.IngestionJobRepository;
import com.ming.rag.observability.TraceContextAccessor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IngestionApplicationService {

    private final DocumentIdPolicy documentIdPolicy;
    private final ChunkIdPolicy chunkIdPolicy;
    private final DocumentLoaderPort documentLoaderPort;
    private final DocumentSplitterService documentSplitterService;
    private final ChunkRefinerService chunkRefinerService;
    private final MetadataEnricherService metadataEnricherService;
    private final EmbeddingPort embeddingPort;
    private final LexicalEncodingPort lexicalEncodingPort;
    private final DocumentRegistryPort documentRegistryPort;
    private final ChunkStorePort chunkStorePort;
    private final IngestionJobRepository ingestionJobRepository;
    private final IngestionObservationService ingestionObservationService;
    private final IngestionStateMachine ingestionStateMachine;
    private final TraceContextAccessor traceContextAccessor;
    private final MeterRegistry meterRegistry;

    public IngestionApplicationService(
            DocumentIdPolicy documentIdPolicy,
            ChunkIdPolicy chunkIdPolicy,
            DocumentLoaderPort documentLoaderPort,
            DocumentSplitterService documentSplitterService,
            ChunkRefinerService chunkRefinerService,
            MetadataEnricherService metadataEnricherService,
            EmbeddingPort embeddingPort,
            LexicalEncodingPort lexicalEncodingPort,
            DocumentRegistryPort documentRegistryPort,
            ChunkStorePort chunkStorePort,
            IngestionJobRepository ingestionJobRepository,
            IngestionObservationService ingestionObservationService,
            IngestionStateMachine ingestionStateMachine,
            TraceContextAccessor traceContextAccessor,
            MeterRegistry meterRegistry
    ) {
        this.documentIdPolicy = documentIdPolicy;
        this.chunkIdPolicy = chunkIdPolicy;
        this.documentLoaderPort = documentLoaderPort;
        this.documentSplitterService = documentSplitterService;
        this.chunkRefinerService = chunkRefinerService;
        this.metadataEnricherService = metadataEnricherService;
        this.embeddingPort = embeddingPort;
        this.lexicalEncodingPort = lexicalEncodingPort;
        this.documentRegistryPort = documentRegistryPort;
        this.chunkStorePort = chunkStorePort;
        this.ingestionJobRepository = ingestionJobRepository;
        this.ingestionObservationService = ingestionObservationService;
        this.ingestionStateMachine = ingestionStateMachine;
        this.traceContextAccessor = traceContextAccessor;
        this.meterRegistry = meterRegistry;
    }

    public IngestionResult ingest(IngestionCommand command) {
        return Timer.builder("rag.ingestion.duration")
                .register(meterRegistry)
                .record(() -> doIngest(command));
    }

    private IngestionResult doIngest(IngestionCommand command) {
        var traceId = traceContextAccessor.currentTraceId();
        var jobId = new JobId(UUID.randomUUID().toString());
        var collectionId = command.collectionId() == null || command.collectionId().isBlank() ? "default" : command.collectionId();
        var documentId = documentIdPolicy.generate(command.fileBytes()).value();
        ingestionObservationService.onStarted(traceId, collectionId, documentId);
        if (documentRegistryPort.shouldSkip(collectionId, documentId) && !command.forceReingest()) {
            ingestionJobRepository.markSkipped(
                    jobId,
                    collectionId,
                    documentId,
                    command.forceReingest(),
                    command.chunkSizeOverride(),
                    command.chunkOverlapOverride(),
                    traceId
            );
            ingestionObservationService.onSkipped(traceId, collectionId, documentId);
            return new IngestionResult(jobId.value(), documentId, "READY", true, chunkStorePort.countVisibleChunks(collectionId, documentId), traceId);
        }

        var sourceDocument = documentLoaderPort.storeAndPrepare(
                new SourceDocument(
                        documentId,
                        collectionId,
                        "",
                        command.originalFileName(),
                        command.mediaType()
                ),
                command.fileBytes()
        );

        ingestionStateMachine.transition(com.ming.rag.domain.ingestion.DocumentStatus.RECEIVED, com.ming.rag.domain.ingestion.DocumentStatus.PROCESSING);
        documentRegistryPort.markProcessing(collectionId, documentId, sourceDocument.sourcePath(), sourceDocument.originalFileName(), sourceDocument.mediaType());
        ingestionJobRepository.markProcessing(
                jobId,
                collectionId,
                documentId,
                command.forceReingest(),
                command.chunkSizeOverride(),
                command.chunkOverlapOverride(),
                traceId
        );

        try {
            ingestionObservationService.onProcessing(traceId, collectionId, documentId);
            var parsedDocument = documentLoaderPort.load(sourceDocument);
            var chunks = documentSplitterService.split(parsedDocument, command.chunkSizeOverride(), command.chunkOverlapOverride());
            var canonicalChunks = chunkRefinerService.refine(chunks);
            var enrichedChunks = metadataEnricherService.enrich(canonicalChunks);
            var denseVectors = embeddingPort.embed(enrichedChunks.stream().map(Chunk::content).toList());
            var sparseTerms = lexicalEncodingPort.encode(enrichedChunks.stream().map(Chunk::content).toList());

            var finalChunks = enrichedChunks.stream()
                    .map(chunk -> new Chunk(
                            chunkIdPolicy.generate(documentId, chunk.chunkIndex(), chunk.content()).value(),
                            chunk.documentId(),
                            chunk.collectionId(),
                            chunk.chunkIndex(),
                            chunk.content(),
                            canonicalMetadata(chunk)
                    ))
                    .toList();
            var now = Instant.now();
            var records = new ArrayList<ChunkRecord>(finalChunks.size());
            for (int index = 0; index < finalChunks.size(); index++) {
                var chunk = finalChunks.get(index);
                records.add(new ChunkRecord(
                        chunk.chunkId(),
                        chunk.documentId(),
                        chunk.collectionId(),
                        chunk.chunkIndex(),
                        chunk.content(),
                        canonicalMetadata(chunk),
                        toFloatList(denseVectors.get(index)),
                        sparseTerms.get(index),
                        true,
                        now,
                        now
                ));
            }

            chunkStorePort.deleteByDocumentId(collectionId, documentId);
            chunkStorePort.upsert(collectionId, records);
            try {
                documentRegistryPort.markReady(collectionId, documentId, finalChunks.size());
            } catch (RuntimeException readyException) {
                chunkStorePort.deleteByDocumentId(collectionId, documentId);
                ingestionObservationService.onCompensation(traceId, collectionId, documentId, readyException.getMessage());
                throw readyException;
            }
            ingestionJobRepository.markReady(
                    jobId,
                    collectionId,
                    documentId,
                    command.forceReingest(),
                    command.chunkSizeOverride(),
                    command.chunkOverlapOverride(),
                    traceId
            );
            ingestionObservationService.onReady(traceId, collectionId, documentId, finalChunks.size());
            return new IngestionResult(jobId.value(), documentId, "READY", false, finalChunks.size(), traceId);
        } catch (RuntimeException exception) {
            chunkStorePort.deleteByDocumentId(collectionId, documentId);
            documentRegistryPort.markFailed(collectionId, documentId, exception.getMessage());
            ingestionJobRepository.markFailed(
                    jobId,
                    collectionId,
                    documentId,
                    command.forceReingest(),
                    command.chunkSizeOverride(),
                    command.chunkOverlapOverride(),
                    traceId,
                    exception.getMessage()
            );
            ingestionObservationService.onFailed(traceId, collectionId, documentId, exception.getMessage());
            throw new IngestionFailedException(
                    "Ingestion failed",
                    ingestionObservationService.failureDetails(collectionId, documentId, exception.getMessage())
            );
        }
    }

    private LinkedHashMap<String, Object> canonicalMetadata(Chunk chunk) {
        var metadata = new LinkedHashMap<String, Object>(chunk.metadata());
        metadata.put("document_id", chunk.documentId());
        metadata.put("chunk_index", chunk.chunkIndex());
        metadata.putIfAbsent("source_path", metadata.getOrDefault("source_path", ""));
        metadata.putIfAbsent("doc_type", metadata.getOrDefault("doc_type", "text"));
        metadata.putIfAbsent("source_ref", chunk.documentId());
        return metadata;
    }

    private List<Float> toFloatList(float[] vector) {
        var result = new ArrayList<Float>(vector.length);
        for (float value : vector) {
            result.add(value);
        }
        return List.copyOf(result);
    }
}
