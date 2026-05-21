package com.gijela.morpheus.chat.support.graph;

import com.gijela.morpheus.chat.domain.vo.graph.GraphExtractPreviewResponse;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class GraphPreviewStore {

    private final Map<String, GraphExtractPreviewResponse> store = new ConcurrentHashMap<>();

    public void save(GraphExtractPreviewResponse preview) {
        store.put(preview.previewId(), preview);
    }

    public Optional<GraphExtractPreviewResponse> find(String previewId) {
        return Optional.ofNullable(store.get(previewId));
    }
}