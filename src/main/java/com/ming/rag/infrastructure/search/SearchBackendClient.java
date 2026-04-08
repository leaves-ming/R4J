package com.ming.rag.infrastructure.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.rag.bootstrap.config.RagProperties;
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

    public boolean ensureIndex() {
        try (var client = restClient()) {
            var index = indexName();
            if (indexExists(client, index)) {
                return true;
            }

            var request = new Request("PUT", "/" + index);
            request.setJsonEntity(indexDefinitionJson());
            client.performRequest(request);
            return true;
        } catch (Exception exception) {
            log.warn("search index ensure failed index={} reason={}", indexName(), exception.getMessage());
            return false;
        }
    }

    public boolean upsertRecords(List<Map<String, Object>> records) {
        if (records.isEmpty()) {
            return true;
        }
        try (var client = restClient()) {
            ensureIndex();
            for (var record : records) {
                var request = new Request("PUT", "/" + indexName() + "/_doc/" + record.get("id"));
                request.setJsonEntity(objectMapper.writeValueAsString(record));
                client.performRequest(request);
            }
            return true;
        } catch (Exception exception) {
            log.warn("search upsert failed index={} reason={}", indexName(), exception.getMessage());
            return false;
        }
    }

    public boolean deleteByDocumentId(String collectionId, String documentId) {
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
            return true;
        } catch (Exception exception) {
            log.warn("search delete failed index={} collectionId={} documentId={} reason={}", indexName(), collectionId, documentId, exception.getMessage());
            return false;
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
        } catch (Exception exception) {
            log.warn("search count failed index={} collectionId={} documentId={} reason={}", indexName(), collectionId, documentId, exception.getMessage());
            return -1;
        }
    }

    public List<Map<String, Object>> findByCollection(String collectionId) {
        try (var client = restClient()) {
            var request = new Request("GET", "/" + indexName() + "/_search");
            request.setJsonEntity(objectMapper.writeValueAsString(Map.of(
                    "size", 1000,
                    "sort", List.of(
                            Map.of("document_id", Map.of("order", "asc")),
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
            var results = new ArrayList<Map<String, Object>>();
            for (var hit : hitList) {
                if (hit instanceof Map<?, ?> hitMap && hitMap.get("_source") instanceof Map<?, ?> sourceMap) {
                    results.add(new LinkedHashMap<>((Map<String, Object>) sourceMap));
                }
            }
            return results;
        } catch (Exception exception) {
            log.warn("search query failed index={} collectionId={} reason={}", indexName(), collectionId, exception.getMessage());
            return List.of();
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

    public String indexName() {
        return ragProperties.storage().search().chunkIndex();
    }

    public String nowIso() {
        return Instant.now().toString();
    }
}
