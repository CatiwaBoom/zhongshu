package org.cycle.workflow.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

public final class WorkflowDesignJsonHelper {

    private WorkflowDesignJsonHelper() {
    }

    public static String toJsonString(ObjectMapper objectMapper, Object designJson) {
        if (designJson == null) {
            return defaultDesignJson();
        }
        if (designJson instanceof String) {
            String text = ((String) designJson).trim();
            return text.isEmpty() ? defaultDesignJson() : text;
        }
        try {
            return objectMapper.writeValueAsString(designJson);
        } catch (IOException e) {
            throw new IllegalArgumentException("designJson 格式不正确", e);
        }
    }

    public static String removeNode(String designJson, String nodeId, ObjectMapper objectMapper) {
        try {
            JsonNode rootNode = objectMapper.readTree(normalizeDesignJson(designJson));
            if (!(rootNode instanceof ObjectNode)) {
                return defaultDesignJson();
            }
            ObjectNode root = (ObjectNode) rootNode;
            removeNodeById(root, nodeId);
            removeRelatedEdges(root, nodeId);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalArgumentException("删除节点失败，设计数据不是有效 JSON", e);
        }
    }

    private static void removeNodeById(ObjectNode root, String nodeId) {
        JsonNode nodes = root.get("nodes");
        ArrayNode remaining = root.arrayNode();
        if (nodes != null && nodes.isArray()) {
            for (JsonNode node : nodes) {
                if (!nodeId.equals(node.path("id").asText())) {
                    remaining.add(node);
                }
            }
        }
        root.set("nodes", remaining);
    }

    private static void removeRelatedEdges(ObjectNode root, String nodeId) {
        JsonNode edges = root.get("edges");
        ArrayNode remaining = root.arrayNode();
        if (edges != null && edges.isArray()) {
            for (JsonNode edge : edges) {
                String source = edge.path("source").asText();
                String target = edge.path("target").asText();
                if (!nodeId.equals(source) && !nodeId.equals(target)) {
                    remaining.add(edge);
                }
            }
        }
        root.set("edges", remaining);
    }

    public static String normalizeDesignJson(String designJson) {
        String text = designJson == null ? "" : designJson.trim();
        return text.isEmpty() ? defaultDesignJson() : text;
    }

    public static String defaultDesignJson() {
        return "{\"nodes\":[],\"edges\":[],\"viewport\":{\"x\":0,\"y\":0,\"zoom\":1}}";
    }
}

