package com.ming.rag.application.query;

import com.ming.rag.bootstrap.config.RagProperties;
import com.ming.rag.domain.common.exception.RetrievalFailedException;
import com.ming.rag.domain.query.ProcessedQuery;
import com.ming.rag.domain.query.RankedResult;
import com.ming.rag.domain.query.RetrievalCandidate;
import com.ming.rag.domain.query.RetrievalResult;
import com.ming.rag.domain.query.port.DenseSearchPort;
import com.ming.rag.domain.query.port.LexicalSearchPort;
import com.ming.rag.domain.query.port.RerankPort;
import com.ming.rag.observability.TraceContextAccessor;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RetrievalPipelineService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalPipelineService.class);

    private final QueryProcessorService queryProcessorService;
    private final DenseSearchPort denseSearchPort;
    private final LexicalSearchPort lexicalSearchPort;
    private final RrfFusionPolicy rrfFusionPolicy;
    private final RerankPort rerankPort;
    private final RagProperties ragProperties;
    private final QueryObservationService queryObservationService;
    private final TraceContextAccessor traceContextAccessor;

    public RetrievalPipelineService(
            QueryProcessorService queryProcessorService,
            DenseSearchPort denseSearchPort,
            LexicalSearchPort lexicalSearchPort,
            RrfFusionPolicy rrfFusionPolicy,
            RerankPort rerankPort,
            RagProperties ragProperties,
            QueryObservationService queryObservationService,
            TraceContextAccessor traceContextAccessor,
            MeterRegistry meterRegistry
    ) {
        this.queryProcessorService = queryProcessorService;
        this.denseSearchPort = denseSearchPort;
        this.lexicalSearchPort = lexicalSearchPort;
        this.rrfFusionPolicy = rrfFusionPolicy;
        this.rerankPort = rerankPort;
        this.ragProperties = ragProperties;
        this.queryObservationService = queryObservationService;
        this.traceContextAccessor = traceContextAccessor;
    }

    public RetrievalResult retrieve(QueryCommand command) {
        var collectionId = command.collectionId() == null || command.collectionId().isBlank() ? "default" : command.collectionId();
        var filters = extractStructuredFilters(command.options());
        var queryProcessingStart = System.nanoTime();
        ProcessedQuery processedQuery = queryProcessorService.process(command.query(), collectionId, filters);
        var debug = new LinkedHashMap<String, Object>();
        var traceId = traceContextAccessor.currentTraceId();
        queryObservationService.onRetrievalStarted(traceId, collectionId);
        queryObservationService.onQueryProcessed(
                traceId,
                collectionId,
                elapsedMs(queryProcessingStart),
                processedQuery.keywords().size()
        );

        var denseStart = System.nanoTime();
        var sparseStart = System.nanoTime();
        var denseFuture = CompletableFuture.supplyAsync(() ->
                denseSearchPort.search(collectionId, processedQuery, topK(command.denseTopK(), ragProperties.query().denseTopK())));
        var sparseFuture = CompletableFuture.supplyAsync(() ->
                lexicalSearchPort.search(collectionId, processedQuery, topK(command.sparseTopK(), ragProperties.query().sparseTopK())));

        List<RetrievalCandidate> denseCandidates = List.of();
        List<RetrievalCandidate> sparseCandidates = List.of();
        boolean denseFailed = false;
        boolean sparseFailed = false;

        try {
            denseCandidates = denseFuture.join();
            debug.put("denseLatencyMs", elapsedMs(denseStart));
        } catch (CompletionException exception) {
            denseFailed = true;
            debug.put("denseFailure", rootCauseMessage(exception));
            debug.put("denseLatencyMs", elapsedMs(denseStart));
        }
        queryObservationService.onDenseCompleted(traceId, collectionId, (Long) debug.get("denseLatencyMs"), denseFailed);

        try {
            sparseCandidates = sparseFuture.join();
            debug.put("sparseLatencyMs", elapsedMs(sparseStart));
        } catch (CompletionException exception) {
            sparseFailed = true;
            debug.put("sparseFailure", rootCauseMessage(exception));
            debug.put("sparseLatencyMs", elapsedMs(sparseStart));
        }
        queryObservationService.onSparseCompleted(traceId, collectionId, (Long) debug.get("sparseLatencyMs"), sparseFailed);

        if (denseFailed && sparseFailed) {
            queryObservationService.onFailure(collectionId);
            throw new RetrievalFailedException("Both dense and sparse retrieval failed");
        }

        var fusionStart = System.nanoTime();
        List<RankedResult> candidates;
        if (denseFailed || sparseFailed) {
            debug.put("partialFallback", true);
            queryObservationService.onFallback(collectionId, denseFailed ? "dense" : "sparse");
            candidates = rankSinglePath(denseFailed ? sparseCandidates : denseCandidates);
        } else {
            candidates = rrfFusionPolicy.fuse(
                    denseCandidates,
                    sparseCandidates,
                    topK(command.fusionTopK(), ragProperties.query().fusionTopK())
            );
            debug.put("partialFallback", false);
        }
        debug.put("fusionElapsedMs", elapsedMs(fusionStart));
        queryObservationService.onFusionCompleted(
                traceId,
                collectionId,
                (Long) debug.get("fusionElapsedMs"),
                Boolean.TRUE.equals(debug.get("partialFallback")),
                candidates.size()
        );

        var rerankStart = System.nanoTime();
        var reranked = rerankPort.rerank(processedQuery, candidates, topK(command.rerankTopK(), ragProperties.rerank().topK()));
        var rerankEnabled = ragProperties.rerank().enabled() && !"none".equalsIgnoreCase(ragProperties.rerank().provider());
        debug.put("rerankApplied", rerankEnabled);
        debug.put("rerankProvider", ragProperties.rerank().provider());
        debug.put("rerankLatencyMs", elapsedMs(rerankStart));
        queryObservationService.onRerankCompleted(
                traceId,
                collectionId,
                (Long) debug.get("rerankLatencyMs"),
                ragProperties.rerank().provider(),
                rerankEnabled
        );
        if (rerankEnabled && reranked.equals(candidates.stream().limit(reranked.size()).toList())) {
            debug.put("rerankFallbackReason", "provider_unavailable_or_no_change");
        }
        return new RetrievalResult(processedQuery, reranked, denseFailed || sparseFailed, traceId, Map.copyOf(debug));
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private String rootCauseMessage(Throwable throwable) {
        var current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private Map<String, Object> extractStructuredFilters(Map<String, Object> options) {
        if (options == null || options.isEmpty()) {
            return Map.of();
        }
        Object filters = options.get("filters");
        if (filters instanceof Map<?, ?> map) {
            var normalized = new LinkedHashMap<String, Object>();
            map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            return Map.copyOf(normalized);
        }
        return Map.of();
    }

    private int topK(Integer requested, int defaultValue) {
        return requested == null || requested <= 0 ? defaultValue : requested;
    }

    private List<RankedResult> rankSinglePath(List<RetrievalCandidate> candidates) {
        var sorted = candidates.stream()
                .sorted(Comparator.comparingDouble(RetrievalCandidate::score).reversed().thenComparing(RetrievalCandidate::chunkId))
                .toList();
        return java.util.stream.IntStream.range(0, sorted.size())
                .mapToObj(index -> {
                    var candidate = sorted.get(index);
                    return new RankedResult(candidate.chunkId(), candidate.score(), index + 1, candidate.content(), candidate.metadata());
                })
                .toList();
    }
}
