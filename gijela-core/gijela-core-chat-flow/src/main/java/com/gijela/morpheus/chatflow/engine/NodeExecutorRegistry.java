package com.gijela.morpheus.chatflow.engine;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Component
public class NodeExecutorRegistry {

    private final Map<String, NodeExecutor> executors = new HashMap<>();

    public NodeExecutorRegistry(List<NodeExecutor> executorList) {
        for (NodeExecutor executor : executorList) {
            executors.put(executor.type(), executor);
        }
    }

    public NodeExecutor get(String type) {
        NodeExecutor executor = executors.get(type);
        if (executor == null) {
            throw new NoSuchElementException("不支持的节点类型: " + type);
        }
        return executor;
    }
}
