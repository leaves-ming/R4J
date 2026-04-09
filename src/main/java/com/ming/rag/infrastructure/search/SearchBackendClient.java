package com.ming.rag.infrastructure.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.rag.bootstrap.config.RagProperties;
import com.ming.rag.domain.ingestion.ChunkRecord;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SearchBackendClient {

    private static final Logger log = LoggerFactory.getLogger(SearchBackendClient.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;

    public SearchBackendClient(RagProperties ragProperties, ObjectMapper objectMapper) {
        this.ragProperties = ragProperties;
        this.objectMapper = objectMapper;
    }

    public void ensureIndex() {
        try (var client = restClient()) {
            var index = indexName();
            if (indexExists(client, index)) {
                return;
            }

            var request = new Request("PUT", "/" + index);
            request.setJsonEntity(indexDefinitionJson());
            client.performRequest(request);
        } catch (Exception exception) {
            throw new IllegalStateException("search index ensure failed for index=" + indexName(), exception);
        }
    }

    public void upsertRecords(List<ChunkRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        try (var client = restClient()) {
            ensureIndex();
            for (var record : records) {
                var request = new Request("PUT", "/" + indexName() + "/_doc/" + record.collectionId() + ":" + record.chunkId());
                request.setJsonEntity(objectMapper.writeValueAsString(toSearchRecord(record)));
                client.performRequest(request);
            }
            refreshIndex(client);
        } catch (Exception exception) {
            throw new IllegalStateException("search upsert failed for index=" + indexName(), exception);
        }
    }

    public void deleteByDocumentId(String collectionId, String documentId) {
        try (var client = restClient()) {
            var request = new Request("POST", "/" + indexName() + "/_delete_by_query");
            request.setJsonEntity(objectMapper.writeValueAsString(Map.of(
                    "query", Map.of(
                            "bool", Map.of(
                                    "must", List.of(
                                            Map.of("term", Map.of("collection_id", collectionId)),
                                            Map.of("term", Map.of("document_id", documentId))
                                    )
                            )
                    )
            )));
            client.performRequest(request);
            refreshIndex(client);
        } catch (ResponseException exception) {
            if (exception.getResponse().getStatusLine().getStatusCode() == 404) {
                return;
            }
            throw new IllegalStateException(
                    "search delete failed for index=" + indexName() + " collectionId=" + collectionId + " documentId=" + documentId,
                    exception
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "search delete failed for index=" + indexName() + " collectionId=" + collectionId + " documentId=" + documentId,
                    exception
            );
        }
    }

    public long countVisibleChunks(String collectionId, String documentId) {
        try (var client = restClient()) {
            var request = new Request("GET", "/" + indexName() + "/_count");
            request.setJsonEntity(objectMapper.writeValueAsString(Map.of(
                    "query", Map.of(
                            "bool", Map.of(
                                    "must", List.of(
                                            Map.of("term", Map.of("collection_id", collectionId)),
                                            Map.of("term", Map.of("document_id", documentId)),
                                            Map.of("term", Map.of("ready", true))
                                    )
                            )
                    )
            )));
            var response = client.performRequest(request);
            var body = objectMapper.readValue(response.getEntity().getContent(), MAP_TYPE);
            return ((Number) body.getOrDefault("count", 0)).longValue();
        } catch (ResponseException exception) {
            if (exception.getResponse().getStatusLine().getStatusCode() == 404) {
                return 0;
            }
            throw new IllegalStateException(
                    "search count failed for index=" + indexName() + " collectionId=" + collectionId + " documentId=" + documentId,
                    exception
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "search count failed for index=" + indexName() + " collectionId=" + collectionId + " documentId=" + documentId,
                    exception
            );
        }
    }

    public List<ChunkRecord> findByCollection(String collectionId) {
        try (var client = restClient()) {
            var request = new Request("GET", "/" + indexName() + "/_search");
            request.setJsonEntity(objectMapper.writeValueAsString(Map.of(
                    "size", 1000,
                    "sort", List.of(
                            Map.of("chunk_index", Map.of("order", "asc"))
                    ),
                    "query", Map.of(
                            "bool", Map.of(
                                    "must", List.of(
                                            Map.of("term", Map.of("collection_id", collectionId)),
                                            Map.of("term", Map.of("ready", true))
                                    )
                            )
                    )
            )));
            var response = client.performRequest(request);
            var body = objectMapper.readValue(response.getEntity().getContent(), MAP_TYPE);
            var hits = body.get("hits") instanceof Map<?, ?> hitsMap ? hitsMap : Map.of();
            var rawHits = hits.get("hits");
            var hitList = rawHits instanceof List<?> list ? list : List.of();
            var results = new ArrayList<ChunkRecord>();
            for (var hit : hitList) {
                if (hit instanceof Map<?, ?> hitMap && hitMap.get("_source") instanceof Map<?, ?> sourceMap) {
                    results.add(toChunkRecord((Map<String, Object>) sourceMap));
                }
            }
            return results;
        } catch (ResponseException exception) {
            if (exception.getResponse().getStatusLine().getStatusCode() == 404) {
                return List.of();
            }
            throw new IllegalStateException("search query failed for index=" + indexName() + " collectionId=" + collectionId, exception);
        } catch (Exception exception) {
            throw new IllegalStateException("search query failed for index=" + indexName() + " collectionId=" + collectionId, exception);
        }
    }

    private RestClient restClient() {
        return RestClient.builder(HttpHost.create(ragProperties.storage().search().url())).build();
    }

    private boolean indexExists(RestClient client, String index) throws IOException {
        try {
            client.performRequest(new Request("HEAD", "/" + index));
            return true;
        } catch (ResponseException exception) {
            if (exception.getResponse().getStatusLine().getStatusCode() == 404) {
                return false;
            }
            throw exception;
        }
    }

    private String indexDefinitionJson() throws IOException {
        return objectMapper.writeValueAsString(Map.of(
                "mappings", Map.of(
                        "properties", Map.of(
                                "collection_id", Map.of("type", "keyword"),
                                "chunk_id", Map.of("type", "keyword"),
                                "document_id", Map.of("type", "keyword"),
                                "chunk_index", Map.of("type", "integer"),
                                "content", Map.of("type", "text"),
                                "metadata_json", Map.of("type", "object", "enabled", true),
                                "dense_vector", Map.of("type", "dense_vector", "dims", 3, "index", false),
                                "sparse_terms", Map.of("type", "object", "enabled", true),
                                "ready", Map.of("type", "boolean"),
                                "updated_at", Map.of("type", "date")
                        )
                )
        ));
    }

    private void refreshIndex(RestClient client) throws IOException {
        client.performRequest(new Request("POST", "/" + indexName() + "/_refresh"));
    }

    private Map<String, Object> toSearchRecord(ChunkRecord record) {
        var source = new LinkedHashMap<String, Object>();
        source.put("collection_id", record.collectionId());
        source.put("chunk_id", record.chunkId());
        source.put("document_id", record.documentId());
        source.put("chunk_index", record.chunkIndex());
        source.put("content", record.content());
        source.put("metadata_json", record.metadata());
        source.put("dense_vector", record.denseVector());
        source.put("sparse_terms", record.sparseTerms());
        source.put("ready", record.ready());
        source.put("created_at", record.createdAt().toString());
        source.put("updated_at", record.updatedAt().toString());
        return source;
    }

    @SuppressWarnings("unchecked")
    private ChunkRecord toChunkRecord(Map<String, Object> source) {
        var metadata = (Map<String, Object>) source.getOrDefault("metadata_json", Map.of());
        var denseVector = ((List<?>) source.getOrDefault("dense_vector", List.of())).stream()
                .map(value -> ((Number) value).floatValue())
                .toList();
        var sparseTerms = new LinkedHashMap<String, Integer>();
        ((Map<?, ?>) source.getOrDefault("sparse_terms", Map.of())).forEach((key, value) ->
                sparseTerms.put(String.valueOf(key), ((Number) value).intValue()));
        return new ChunkRecord(
                String.valueOf(source.get("chunk_id")),
                String.valueOf(source.get("document_id")),
                String.valueOf(source.get("collection_id")),
                ((Number) source.getOrDefault("chunk_index", 0)).intValue(),
                String.valueOf(source.get("content")),
                Map.copyOf(metadata),
                List.copyOf(denseVector),
                Map.copyOf(sparseTerms),
                Boolean.TRUE.equals(source.get("ready")),
                Instant.parse(String.valueOf(source.getOrDefault("created_at", nowIso()))),
                Instant.parse(String.valueOf(source.getOrDefault("updated_at", nowIso())))
        );
    }

    public String indexName() {
        return ragProperties.storage().search().chunkIndex();
    }

    public String nowIso() {
        return Instant.now().toString();
    }
}
