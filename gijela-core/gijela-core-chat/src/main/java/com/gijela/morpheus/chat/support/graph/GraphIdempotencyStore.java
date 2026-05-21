package com.gijela.morpheus.chat.support.graph;

import com.gijela.morpheus.chat.domain.vo.graph.GraphImportResultResponse;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class GraphIdempotencyStore {

    private final Map<String, GraphImportResultResponse> results = new ConcurrentHashMap<>();

    public Optional<GraphImportResultResponse> find(String key) {
        return Optional.ofNullable(results.get(key));
    }

    public void save(String key, GraphImportResultResponse response) {
        results.put(key, response);
    }
}